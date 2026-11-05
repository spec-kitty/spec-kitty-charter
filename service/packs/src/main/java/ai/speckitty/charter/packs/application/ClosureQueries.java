package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    private final OfferingReadService offering;
    private final ConsistentRead read;

    /**
     * Queries over the offering's compose.
     *
     * @param offering the service that composes a closure and maps it to the offering's shape
     * @param read the verifier that digests the inputs before and after the read
     */
    @Autowired
    public ClosureQueries(OfferingReadService offering, ConsistentRead read) {
        this.offering = Objects.requireNonNull(offering, "offering");
        this.read = Objects.requireNonNull(read, "read");
    }

    /**
     * The closure of the roots, or of every configured pack when the roots are absent (FR-008: absent packs means every
     * configured pack).
     *
     * @param roots the configured identifiers the closure is rooted at; empty for every configured pack
     * @return the closure's view, with the verified fingerprint
     * @throws CharterRefusal with a composition refusal when the closure cannot compose, {@code PACK_UNREADABLE} when an
     *     input of it cannot be read, or {@code STALE_INPUT} when the inputs changed during the read
     * @throws IllegalArgumentException when a root names no configured pack
     */
    public ClosureView snapshot(Optional<List<PackIdentifier>> roots) {
        Objects.requireNonNull(roots, "roots");
        ConsistentRead.Verified<OfferingReadService.ComposedClosure> verified =
                read.read(STALE, () -> roots.map(offering::composedWithFacts).orElseGet(offering::composedWithFacts));
        return viewOf(verified.value(), verified.fingerprint());
    }

    /**
     * The closure of every configured pack.
     *
     * @return the view, as {@link #snapshot(Optional)} answers it for absent roots
     */
    public ClosureView allConfigured() {
        return snapshot(Optional.empty());
    }

    private static ClosureView viewOf(OfferingReadService.ComposedClosure composed, String fingerprint) {
        List<PackUnderResolution> packs = composed.packs();
        Map<ArtifactKind, Set<String>> heldIds = new EnumMap<>(ArtifactKind.class);
        Map<Urn, String> holderOf = new LinkedHashMap<>();
        heldOf(packs, heldIds, holderOf);
        List<Map.Entry<String, List<Preset>>> presetsByPack = packs.stream()
                .map(pack -> Map.entry(
                        pack.descriptor().packId(), composed.factsOf(pack).presets()))
                .toList();
        return new ClosureView(
                OfferingReadService.entriesOf(packs),
                heldIds,
                holderOf,
                heldIds.getOrDefault(ArtifactKind.MISSION_TYPE, Set.of()),
                RequiredFloor.of(presetsByPack),
                policyOf(composed),
                packs.stream().map(pack -> pack.descriptor().packId()).toList(),
                presetsOf(presetsByPack),
                fingerprint);
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

    /** Each preset id with its holder; the last holder in merge order wins (ADR-0053 d6). */
    private static Map<String, ClosureView.HeldPreset> presetsOf(List<Map.Entry<String, List<Preset>>> presetsByPack) {
        Map<String, ClosureView.HeldPreset> presets = new LinkedHashMap<>();
        presetsByPack.forEach(pack -> pack.getValue()
                .forEach(preset -> presets.put(preset.id(), new ClosureView.HeldPreset(preset, pack.getKey()))));
        return presets;
    }

    /** The policy of each closure pack, by descriptor {@code pack_id}, from the facts the compose read. */
    private static PerPackPolicy policyOf(OfferingReadService.ComposedClosure composed) {
        Map<String, MutabilityPolicySource> byPack = new HashMap<>();
        composed.packs()
                .forEach(pack -> byPack.put(
                        pack.descriptor().packId(), composed.factsOf(pack).policy()));
        return new PerPackPolicy(byPack);
    }
}
