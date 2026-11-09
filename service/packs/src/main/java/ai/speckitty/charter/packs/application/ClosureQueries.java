package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Everything a caller may ask about the content of a composition closure, in one verified read (NFR-007): the closure
 * is composed once, inside one {@link ConsistentRead}, and every answer of the {@link ClosureView} is built from that
 * compose and the facts it was judged on. A pack change during the read answers {@code stale_input}; it never gives a
 * mixed answer. A closure that cannot compose refuses as the compose refuses, unchanged.
 */
@Service
public final class ClosureQueries {

    private static final String STALE = "The packs changed while the closure was read; read it again.";
    private static final String CLOSURE_DOMAIN = "charter-closure/v1";

    /** How many times a stale read is tried again (research R-lock); after that {@code stale_input} is the answer. */
    private static final int RETRIES = 3;

    /** The longest a retry waits for a running swap to end before it reads again. */
    private static final Duration SWAP_WAIT = Duration.ofMillis(250);

    private final OfferingReadService offering;
    private final PackCatalogRepository catalog;
    private final ConsistentRead read;
    private final Optional<PackRepository> packs;

    /**
     * Queries over the offering's compose, whose views read no winner entity.
     *
     * @param offering the service that composes a closure and maps it to the offering's shape
     * @param catalog the configured packs, the roots are checked against before the compose
     * @param read the verifier that digests the inputs before and after the read
     */
    public ClosureQueries(OfferingReadService offering, PackCatalogRepository catalog, ConsistentRead read) {
        this(offering, catalog, read, Optional.empty());
    }

    /**
     * Queries over the offering's compose.
     *
     * @param offering the service that composes a closure and maps it to the offering's shape
     * @param catalog the configured packs, the roots are checked against before the compose
     * @param read the verifier that digests the inputs before and after the read
     * @param packs the port a view reads the entity of a winning artifact through ({@link ClosureView#effectiveArtifact})
     */
    @Autowired
    public ClosureQueries(
            OfferingReadService offering, PackCatalogRepository catalog, ConsistentRead read, PackRepository packs) {
        this(offering, catalog, read, Optional.of(packs));
    }

    private ClosureQueries(
            OfferingReadService offering,
            PackCatalogRepository catalog,
            ConsistentRead read,
            Optional<PackRepository> packs) {
        this.offering = Objects.requireNonNull(offering, "offering");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.read = Objects.requireNonNull(read, "read");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * The closure of the roots, or of every configured pack when the roots are absent (FR-008: absent packs means every
     * configured pack).
     *
     * @param roots the configured identifiers the closure is rooted at; empty for every configured pack
     * @return the closure's view, with the verified fingerprint
     * @throws CharterRefusal with a composition refusal when the closure cannot compose, {@code PACK_UNREADABLE} when an
     *     input of it cannot be read, or {@code STALE_INPUT} when the inputs changed during the read and three retries,
     *     each after the running swap ended, changed again
     * @throws UnknownRootException when a root names no configured pack, naming every such root; nothing is composed
     */
    public ClosureView snapshot(Optional<List<PackIdentifier>> roots) {
        Objects.requireNonNull(roots, "roots");
        for (int retry = 0; ; retry++) {
            try {
                return snapshotOnce(roots);
            } catch (CharterRefusal refused) {
                if (refused.code() != RefusalCode.STALE_INPUT || retry >= RETRIES) {
                    throw refused;
                }
                read.awaitWrites(SWAP_WAIT);
            }
        }
    }

    /**
     * The closure of the roots composed inside a verified read the CALLER holds: the same compose and the same view as
     * {@link #snapshot}, with no {@link ConsistentRead} of its own and no retry. Call it in the supplier of a
     * {@link ConsistentRead#read}, whose two digests then judge the compose with whatever else the supplier read, and a
     * change is the caller's {@code stale_input}. The view's {@link ClosureView#fingerprint()} is the digest of the
     * closure's own packs, taken in this call; the caller's verified digest is the whole-source one.
     *
     * @param roots the configured identifiers the closure is rooted at; empty for every configured pack
     * @return the closure's view
     * @throws CharterRefusal as {@link #snapshot} refuses, except that a stale input is the caller's to find
     * @throws UnknownRootException when a root names no configured pack
     */
    public ClosureView snapshotWithin(Optional<List<PackIdentifier>> roots) {
        Objects.requireNonNull(roots, "roots");
        Loaded loaded = loaded(roots);
        return viewOf(loaded.composed(), loaded.closureFingerprint(), loaded.closureFingerprint());
    }

    /** One verified read of the closure. */
    private ClosureView snapshotOnce(Optional<List<PackIdentifier>> roots) {
        ConsistentRead.Verified<Loaded> verified = read.read(STALE, () -> loaded(roots));
        return viewOf(
                verified.value().composed(),
                verified.fingerprint(),
                verified.value().closureFingerprint());
    }

    /** What one load reads: the compose, and the digest of the closure's own packs taken inside the same verified read. */
    private record Loaded(OfferingReadService.ComposedClosure composed, String closureFingerprint) {}

    private Loaded loaded(Optional<List<PackIdentifier>> roots) {
        OfferingReadService.ComposedClosure composed =
                roots.map(this::composedOfKnownRoots).orElseGet(offering::composedWithFacts);
        return new Loaded(composed, closureFingerprint(composed.packs()));
    }

    /**
     * The digest of the packs IN the closure: each pack's configured id and level in closure order, then the digest of
     * those packs' files alone. A configured pack outside the closure is not in it, so it leaves the digest as it is.
     */
    private String closureFingerprint(List<PackUnderResolution> packs) {
        Fingerprint digest = Fingerprint.sha256().add(CLOSURE_DOMAIN);
        packs.forEach(pack -> digest.add(pack.configured().id().value())
                .add(pack.configured().level().token()));
        List<String> names =
                packs.stream().map(pack -> pack.configured().id().value()).toList();
        return digest.add(read.fingerprintOf(names)).value();
    }

    /** The compose of the roots, once every root is a configured pack (checked first, in the same verified read). */
    private OfferingReadService.ComposedClosure composedOfKnownRoots(List<PackIdentifier> roots) {
        Set<PackIdentifier> configured =
                catalog.configured().stream().map(ConfiguredPack::id).collect(Collectors.toSet());
        List<String> unknown = roots.stream()
                .filter(root -> !configured.contains(root))
                .map(PackIdentifier::value)
                .distinct()
                .toList();
        if (!unknown.isEmpty()) {
            throw new UnknownRootException(unknown);
        }
        return offering.composedWithFacts(roots);
    }

    /**
     * The closure of every configured pack.
     *
     * @return the view, as {@link #snapshot(Optional)} answers it for absent roots
     */
    public ClosureView allConfigured() {
        return snapshot(Optional.empty());
    }

    private ClosureView viewOf(
            OfferingReadService.ComposedClosure composed, String fingerprint, String closureFingerprint) {
        List<PackUnderResolution> packs = composed.packs();
        Map<ArtifactKind, Set<String>> heldIds = new EnumMap<>(ArtifactKind.class);
        Map<Urn, String> holderOf = new LinkedHashMap<>();
        heldOf(packs, heldIds, holderOf);
        List<String> closurePackIds =
                packs.stream().map(pack -> pack.descriptor().packId()).toList();
        List<ClosureView.HeldPreset> presets = winningPresets(composed);
        return new ClosureView(
                OfferingReadService.entriesOf(packs),
                heldIds,
                holderOf,
                RequiredFloor.of(byHolder(closurePackIds, presets), holderOf.keySet()),
                policyOf(composed),
                closurePackIds,
                presets,
                parentsOf(packs),
                fingerprint,
                EdgeIndex.of(packs, composed.facts()),
                new ClosureView.MergeInputs(packs, composed.facts(), winnerSource(packs, closureFingerprint)),
                closureFingerprint);
    }

    /**
     * Where a view reads the entity of a winner from: the pack store, each read checked against the digest of the
     * closure's own packs the view was verified at, so an entity of a pack that moved since is {@code stale_input} and
     * never part of a mixed answer.
     */
    private ClosureView.WinnerSource winnerSource(List<PackUnderResolution> closure, String closureFingerprint) {
        return packs.<ClosureView.WinnerSource>map(repository -> (pack, kind, id) -> {
                    Optional<CharterArtifact> found = repository.load(pack).artifacts(kind).stream()
                            .filter(artifact -> artifact.localId().equals(id))
                            .findFirst();
                    if (!closureFingerprint(closure).equals(closureFingerprint)) {
                        throw new CharterRefusal(RefusalCode.STALE_INPUT, STALE);
                    }
                    return found;
                })
                .orElseGet(ClosureView.WinnerSource::none);
    }

    /** The held ids per kind, and the first holder of each URN in closure order. */
    private static void heldOf(
            List<PackUnderResolution> packs, Map<ArtifactKind, Set<String>> heldIds, Map<Urn, String> holderOf) {
        for (PackUnderResolution pack : packs) {
            for (String defined : pack.definedArtifacts()) {
                Urn.parse(defined).ifPresent(urn -> {
                    holderOf.putIfAbsent(urn, pack.descriptor().packId());
                    urn.kind()
                            .artifactKind()
                            .ifPresent(kind -> heldIds.computeIfAbsent(kind, ignored -> new LinkedHashSet<>())
                                    .add(urn.id()));
                });
            }
        }
    }

    /**
     * The closure's presets, each id once, de-duplicated once: when two packs hold one preset id, the last holder in
     * merge order wins (ADR-0053 d6). The compose has refused two packs of one level that hold one id unless one
     * extends the other, so the last holder is a pack of a nearer level or a child, which merges after its parent.
     * The list is in first-seen order of the ids.
     */
    private static List<ClosureView.HeldPreset> winningPresets(OfferingReadService.ComposedClosure composed) {
        Map<String, ClosureView.HeldPreset> byId = new LinkedHashMap<>();
        for (PackUnderResolution pack : composed.packs()) {
            for (Preset preset : composed.factsOf(pack).presets()) {
                byId.put(
                        preset.id(),
                        new ClosureView.HeldPreset(preset, pack.descriptor().packId()));
            }
        }
        return List.copyOf(byId.values());
    }

    /** The winning presets regrouped by holder, in closure order, as the floor reads them: only winners require. */
    private static List<Map.Entry<String, List<Preset>>> byHolder(
            List<String> closurePackIds, List<ClosureView.HeldPreset> presets) {
        return closurePackIds.stream()
                .map(packId -> Map.entry(
                        packId,
                        presets.stream()
                                .filter(held -> held.descriptorPackId().equals(packId))
                                .map(ClosureView.HeldPreset::preset)
                                .toList()))
                .toList();
    }

    /** Each closure pack's {@code parent_pack}, by descriptor {@code pack_id}, from the descriptors already read. */
    private static Map<String, String> parentsOf(List<PackUnderResolution> packs) {
        Map<String, String> parents = new HashMap<>();
        packs.forEach(pack -> pack.descriptor()
                .parentPack()
                .ifPresent(parent -> parents.putIfAbsent(pack.descriptor().packId(), parent)));
        return parents;
    }

    /**
     * The policy of each closure pack, by descriptor {@code pack_id}, from the facts the compose read, scoped to what
     * the pack and the packs it builds on hold (A15, {@link PackMutabilityPolicy#scopedPolicies}).
     */
    private static PerPackPolicy policyOf(OfferingReadService.ComposedClosure composed) {
        return new PerPackPolicy(PackMutabilityPolicy.scopedPolicies(
                composed.packs(), pack -> composed.factsOf(pack).policy()));
    }
}
