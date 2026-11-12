package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.ResolvedEdge;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The offering (ADR-0020 D1): the packs the service is configured with, in the compose's resolved order (dependencies
 * before the packs that need them, then the merge order across levels), each with its configured level and the name
 * and version its descriptor gives. Merging is not applied yet (#10, #11): the offering lists the packs, not a merged
 * result.
 *
 * <p>It is one read verified by {@link ConsistentRead}: the catalog and every descriptor are read between two
 * digests, so the list and its fingerprint name the same inputs. A pack whose descriptor cannot be read refuses the
 * whole offering; it is never partial. A configured pack without a {@code pack.yaml} is no readable canonical pack
 * either (operator ruling of 2026-10-31): it refuses the offering as unreadable, as an invalid one does.
 */
@Service
public final class OfferingReadService {

    private static final String STALE = "The inputs changed while the offering was read; read it again.";

    private final PackCatalogRepository catalog;
    private final PackDescriptorRepository descriptors;
    private final ConsistentRead read;
    private final Function<PackIdentifier, PackCompositionFacts> factsOf;
    private final Supplier<Resolution> resolution;

    /**
     * The configured packs and each one's descriptor, read and judged: what every compose starts from.
     *
     * @param configured the configured packs, in catalog order
     * @param reads each one's descriptor or the refusal reading it gave
     */
    private record Resolution(List<ConfiguredPack> configured, List<DescriptorRead> reads) {}

    /**
     * One configured pack as the offering names it.
     *
     * @param id the configured identifier of the pack
     * @param descriptorPackId the {@code pack_id} its descriptor declares, the identity a dependency edge names
     * @param level the level the pack is configured at
     * @param name the name its descriptor gives
     * @param version the version its descriptor gives
     * @param dependencies the dependency edges its descriptor declared, each with the range and the satisfying version
     *     the compose resolved (FR-020); empty when it declares none
     */
    public record OfferingEntry(
            PackIdentifier id,
            String descriptorPackId,
            PackLevel level,
            String name,
            String version,
            List<ResolvedEdge> dependencies) {

        /**
         * An entry.
         *
         * @param id the configured identifier
         * @param descriptorPackId the descriptor's {@code pack_id}
         * @param level the configured level
         * @param name the descriptor's name
         * @param version the descriptor's version
         * @param dependencies the resolved edges the descriptor declares
         */
        public OfferingEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(dependencies, "dependencies");
            dependencies = List.copyOf(dependencies);
        }
    }

    /**
     * A service over the catalog, the descriptors and the pack-artifact port.
     *
     * @param catalog the port that lists the configured packs
     * @param descriptors the port that looks each pack's descriptor up
     * @param packArtifacts the port that reads the artifacts each pack changes or defines and the references it writes
     * @param read the verifier that digests the inputs before and after the read
     */
    public OfferingReadService(
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            PackArtifactRepository packArtifacts,
            ConsistentRead read) {
        this(catalog, descriptors, read, Objects.requireNonNull(packArtifacts, "packArtifacts")::compositionFacts);
    }

    /**
     * A service whose composition facts are kept between reads.
     *
     * @param catalog the port that lists the configured packs
     * @param descriptors the port that looks each pack's descriptor up
     * @param packArtifacts the port that reads the artifacts each pack changes or defines and the references it writes
     * @param read the verifier that digests the inputs before and after the read
     * @param facts the cache the facts of each pack are read through
     */
    @Autowired
    public OfferingReadService(
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            PackArtifactRepository packArtifacts,
            ConsistentRead read,
            CompositionFactsCache facts) {
        this(catalog, descriptors, read, cached(facts, packArtifacts));
    }

    /** The facts of a pack as the cache keeps them, read from the port when it holds none for the pack's state. */
    private static Function<PackIdentifier, PackCompositionFacts> cached(
            CompositionFactsCache facts, PackArtifactRepository packArtifacts) {
        Objects.requireNonNull(facts, "facts");
        Function<PackIdentifier, PackCompositionFacts> store =
                Objects.requireNonNull(packArtifacts, "packArtifacts")::compositionFacts;
        return pack -> facts.facts(pack, store);
    }

    private OfferingReadService(
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            ConsistentRead read,
            Function<PackIdentifier, PackCompositionFacts> factsOf) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.read = Objects.requireNonNull(read, "read");
        this.factsOf = factsOf;
        this.resolution = this::resolveNow;
    }

    private OfferingReadService(OfferingReadService source, Supplier<Resolution> resolution) {
        this.catalog = source.catalog;
        this.descriptors = source.descriptors;
        this.read = source.read;
        this.factsOf = source.factsOf;
        this.resolution = resolution;
    }

    /**
     * An offering that reads the configured packs and judges their descriptors once, on its first compose, and starts
     * every later compose from that. Each compose of this service re-reads and re-judges every descriptor of the catalog;
     * a caller that composes many closures over inputs it knows cannot change (the registration impact check, under the
     * registry lock) pins the resolution so that the work of a compose is that of its closure, not of the catalog.
     *
     * @return an offering over the same ports whose catalog and descriptors are resolved once
     */
    public OfferingReadService pinned() {
        Resolution[] kept = new Resolution[1];
        return new OfferingReadService(this, () -> {
            if (kept[0] == null) {
                kept[0] = resolveNow();
            }
            return kept[0];
        });
    }

    private Resolution resolveNow() {
        List<ConfiguredPack> configured = catalog.configured();
        return new Resolution(configured, readDescriptors(configured));
    }

    /**
     * The configured packs in the merge order, with the digest of the inputs they were read from.
     *
     * @return the entries and the verified fingerprint
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when an input cannot be read, a configured pack's directory
     *     is missing or it has no readable descriptor (none, or one that cannot be read), or with {@code STALE_INPUT}
     *     when the inputs changed while the offering was read
     */
    public ConsistentRead.Verified<List<OfferingEntry>> offering() {
        return read.read(STALE, () -> entriesOf(composed()));
    }

    /**
     * The offering, in the compose's resolved order: dependencies before the packs that need them, and the merge
     * order across levels. A graph the resolver refuses refuses the offering, so a broken compose is never served
     * partially — two siblings changing one artifact among them, unless a pack of the compose settles it with an
     * {@code overrides} edge (FR-012), and so is a graph reference that breaks the pack-qualification rule (FR-015,
     * FR-017). Each entry carries the dependency edges its descriptor declared, with the range
     * and the satisfying version the compose resolved (FR-020).
     *
     * <p>It is the one mapping from a resolved compose to the offering's shape; a read that composes inside its own
     * {@link ConsistentRead} (the closure snapshot) maps its compose with it rather than copying it.
     *
     * @param closure the resolved compose
     * @return the entries
     */
    public static List<OfferingEntry> entriesOf(List<PackUnderResolution> closure) {
        Objects.requireNonNull(closure, "closure");
        Map<String, String> heldVersions = new HashMap<>();
        for (PackUnderResolution pack : closure) {
            heldVersions.put(pack.descriptor().packId(), pack.descriptor().packVersion());
        }
        return closure.stream()
                .map(pack -> new OfferingEntry(
                        pack.configured().id(),
                        pack.descriptor().packId(),
                        pack.configured().level(),
                        pack.descriptor().name(),
                        pack.descriptor().packVersion(),
                        dependenciesOf(pack, heldVersions)))
                .toList();
    }

    /**
     * The edges one pack's descriptor declared, each paired with the target's held version from the closure. The
     * resolver already refused a compose whose declared target is absent or whose range the held version misses, so
     * every declared edge resolves here.
     *
     * @param pack the pack under resolution
     * @param heldVersions the closure's {@code pack_id} to held {@code pack_version}
     * @return the resolved edges, in the descriptor's declared order
     */
    private static List<ResolvedEdge> dependenciesOf(PackUnderResolution pack, Map<String, String> heldVersions) {
        return pack.descriptor().dependencies().stream()
                .map(dependency -> new ResolvedEdge(
                        pack.descriptor().packId(),
                        dependency.packId(),
                        dependency.range(),
                        Objects.requireNonNull(
                                heldVersions.get(dependency.packId()), "the resolver guarantees the target is held")))
                .toList();
    }

    /**
     * The compose, resolved now and not verified: for a read that already runs inside its own {@link ConsistentRead},
     * so it resolves the compose once for itself and never nests a second verified read per reference or per hop. It
     * is the compose rooted at every configured pack ({@link #composed(Collection)}), so it reads and judges every pack.
     *
     * @return the compose's closure, dependencies first, each pack once
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when an input cannot be read or a configured pack has no readable
     *     descriptor, or with a composition refusal when the graph cannot be served
     */
    public List<PackUnderResolution> composed() {
        return composedWithFacts().packs();
    }

    /**
     * A resolved compose with the composition facts it was judged on, so a read that needs more of each pack than the
     * resolver keeps (its presets, its policy) never reads the facts a second time.
     *
     * @param packs the closure, dependencies first, each pack once
     * @param facts the facts read for each pack of the closure, by configured identifier ({@link PackIdentifier#value()})
     */
    public record ComposedClosure(List<PackUnderResolution> packs, Map<String, PackCompositionFacts> facts) {

        /**
         * Checks and copies the fields.
         *
         * @param packs the closure
         * @param facts the facts by configured identifier
         */
        public ComposedClosure {
            packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            facts = Map.copyOf(Objects.requireNonNull(facts, "facts"));
        }

        /**
         * The facts one pack of the closure was read with.
         *
         * @param pack a pack of the closure
         * @return its facts
         */
        public PackCompositionFacts factsOf(PackUnderResolution pack) {
            return Objects.requireNonNull(facts.get(pack.configured().id().value()), "a pack of the closure");
        }
    }

    /**
     * The compose rooted at every configured pack with its facts, resolved now and not verified, as {@link #composed()}
     * resolves it.
     *
     * @return the closure and the facts it was judged on
     * @throws CharterRefusal as {@link #composed()} refuses
     */
    public ComposedClosure composedWithFacts() {
        Resolution resolved = resolution.get();
        return composed(
                resolved, resolved.configured().stream().map(ConfiguredPack::id).toList());
    }

    /**
     * The compose rooted at a set of configured packs with its facts, resolved now and not verified, as
     * {@link #composed(Collection)} resolves it: each pack's facts are read once, for the resolve and the caller alike.
     *
     * @param roots the configured identifiers of the packs the compose is rooted at
     * @return the closure and the facts it was judged on
     * @throws CharterRefusal as {@link #composed(Collection)} refuses
     * @throws IllegalArgumentException when a root names no configured pack
     */
    public ComposedClosure composedWithFacts(Collection<PackIdentifier> roots) {
        Objects.requireNonNull(roots, "roots");
        return composed(resolution.get(), roots);
    }

    /**
     * The compose rooted at a set of configured packs (#360), resolved now and not verified: the roots and every pack
     * they reach over {@code depends_on} and {@code parent_pack}, judged by every check the whole compose runs. Every
     * configured descriptor is read, tolerantly; the composition facts are read only for the packs of the closure, so a
     * pack outside it that is unreadable, or that duplicates an id or a name, is never judged.
     *
     * <p>A root without a readable descriptor refuses {@code PACK_UNREADABLE} as the whole compose does, naming it. A
     * pack of the closure that no readable descriptor declares cannot be told apart from one an unreadable pack
     * declares, whose {@code pack_id} cannot be known: when the closure holds such a missing target and some configured
     * descriptor could not be read, the compose refuses {@code PACK_UNREADABLE} naming the missing target and the
     * unreadable packs, so the answer is actionable; when every descriptor was read, the missing target is the
     * resolver's refusal ({@code PACK_DEPENDENCY_UNKNOWN}, or {@code PACK_PARENT_UNRESOLVED} for a parent).
     *
     * @param roots the configured identifiers of the packs the compose is rooted at
     * @return the closure of the roots, dependencies first, each pack once
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when an input of the closure cannot be read, or with a
     *     composition refusal when the closure's graph cannot be served
     * @throws IllegalArgumentException when a root names no configured pack
     */
    public List<PackUnderResolution> composed(Collection<PackIdentifier> roots) {
        return composedWithFacts(roots).packs();
    }

    private ComposedClosure composed(Resolution resolved, Collection<PackIdentifier> roots) {
        try {
            List<DescriptorRead> reads = resolved.reads();
            Set<String> closure = closureMembers(reads, roots);
            Map<String, PackCompositionFacts> facts = new HashMap<>();
            List<PackUnderResolution> members = withFacts(reads, Set.copyOf(roots), closure, facts);
            refuseTargetOnlyAnUnreadablePackCouldDeclare(reads, closure);
            List<PackUnderResolution> composed = PackCompositionResolver.resolveRooted(members, roots);
            checkReferences(composed, facts);
            checkStepEnhancementTargets(composed, facts);
            PackCompositionResolver.refusePresetContests(
                    composed,
                    pack ->
                            Objects.requireNonNull(
                                            facts.get(pack.configured().id().value()), "read above")
                                    .presets()
                                    .stream()
                                    .map(Preset::id)
                                    .toList());
            PackCompositionResolver.refuseCaseOnlyMatches(
                    composed,
                    pack -> mentionsOf(Objects.requireNonNull(
                            facts.get(pack.configured().id().value()), "read above")));
            return new ComposedClosure(composed, facts);
        } catch (PackCompositionResolver.Refusal refusal) {
            throw refusal.toCharterRefusal();
        }
    }

    /** The URNs a pack's presets and policy file name, as written: what must match a held id exactly. */
    private static List<String> mentionsOf(PackCompositionFacts facts) {
        List<String> mentions = new ArrayList<>();
        for (Preset preset : facts.presets()) {
            preset.artifacts()
                    .forEach((kind, items) ->
                            items.forEach(item -> mentions.add(new Urn(NodeKind.of(kind), item.id()).toString())));
        }
        facts.policy().urnEntries().keySet().forEach(urn -> mentions.add(urn.toString()));
        return mentions;
    }

    /**
     * One configured pack's descriptor, read tolerantly: the descriptor, or the {@code PACK_UNREADABLE} refusal
     * reading it gave, which the compose raises only when the pack matters to it.
     */
    private record DescriptorRead(
            ConfiguredPack configured, Optional<PackDescriptor> descriptor, Optional<CharterRefusal> refusal) {}

    /** Every configured descriptor, in catalog order, each read or refused; duplicates are all kept. */
    private List<DescriptorRead> readDescriptors(List<ConfiguredPack> configured) {
        return configured.stream().map(this::tryDescriptor).toList();
    }

    private DescriptorRead tryDescriptor(ConfiguredPack pack) {
        try {
            return new DescriptorRead(pack, Optional.of(descriptorOf(pack)), Optional.empty());
        } catch (CharterRefusal refusal) {
            if (refusal.code() != RefusalCode.PACK_UNREADABLE) {
                throw refusal;
            }
            return new DescriptorRead(pack, Optional.empty(), Optional.of(refusal));
        }
    }

    /**
     * The composition closure of the roots, by descriptor {@code pack_id}, over the readable descriptors. The walk
     * descends from the first readable holder of each id; every holder of an id of the closure is a member, so a
     * duplicate there is refused by the resolver naming both. A root without a readable descriptor is no start of the
     * walk: {@link #withFacts} refuses it.
     */
    private static Set<String> closureMembers(List<DescriptorRead> reads, Collection<PackIdentifier> roots) {
        Map<String, PackUnderResolution> firstByPackId = new LinkedHashMap<>();
        Map<PackIdentifier, DescriptorRead> byConfigured = new HashMap<>();
        for (DescriptorRead read : reads) {
            byConfigured.putIfAbsent(read.configured().id(), read);
            read.descriptor()
                    .ifPresent(descriptor -> firstByPackId.putIfAbsent(
                            descriptor.packId(), new PackUnderResolution(read.configured(), descriptor)));
        }
        List<String> rootPackIds = new ArrayList<>();
        for (PackIdentifier root : roots) {
            DescriptorRead read = byConfigured.get(root);
            if (read == null) {
                throw new IllegalArgumentException("no configured pack is configured as " + root.value());
            }
            read.descriptor().ifPresent(descriptor -> rootPackIds.add(descriptor.packId()));
        }
        return PackCompositionResolver.compositionClosure(rootPackIds, firstByPackId);
    }

    /**
     * The members of the closure with their composition facts, read in catalog order and only for the closure. A root
     * without a readable descriptor refuses at its place in that order, so the whole compose refuses with the first
     * unreadable input in catalog order, as it always has.
     */
    private List<PackUnderResolution> withFacts(
            List<DescriptorRead> reads,
            Set<PackIdentifier> roots,
            Set<String> closure,
            Map<String, PackCompositionFacts> facts) {
        List<PackUnderResolution> members = new ArrayList<>();
        for (DescriptorRead read : reads) {
            ConfiguredPack pack = read.configured();
            if (read.refusal().isPresent() && roots.contains(pack.id())) {
                throw read.refusal().get();
            }
            Optional<PackDescriptor> member =
                    read.descriptor().filter(descriptor -> closure.contains(descriptor.packId()));
            if (member.isPresent()) {
                PackCompositionFacts held = factsOf.apply(pack.id());
                facts.put(pack.id().value(), held);
                members.add(new PackUnderResolution(pack, member.get(), held.changed(), held.defined()));
            }
        }
        return members;
    }

    /**
     * Refuses {@code PACK_UNREADABLE} when the closure names a {@code pack_id} no readable descriptor declares and some
     * configured descriptor could not be read: that pack may be the one declaring it.
     */
    private static void refuseTargetOnlyAnUnreadablePackCouldDeclare(List<DescriptorRead> reads, Set<String> closure) {
        Set<String> declared = new HashSet<>();
        reads.forEach(read -> read.descriptor().ifPresent(descriptor -> declared.add(descriptor.packId())));
        List<String> missing =
                closure.stream().filter(id -> !declared.contains(id)).toList();
        List<DescriptorRead> unreadable =
                reads.stream().filter(read -> read.refusal().isPresent()).toList();
        if (missing.isEmpty() || unreadable.isEmpty()) {
            return;
        }
        throw new CharterRefusal(
                RefusalCode.PACK_UNREADABLE,
                "the compose needs pack_id " + String.join(", ", missing) + ", which no readable pack declares; "
                        + unreadable.stream()
                                .map(read -> "pack " + read.configured().id().value() + " cannot be read ("
                                        + read.refusal().orElseThrow().getMessage() + ")")
                                .collect(Collectors.joining("; ")));
    }

    /** Checks every member's references against the compose, as the offering always has. */
    private static void checkReferences(List<PackUnderResolution> composed, Map<String, PackCompositionFacts> facts) {
        for (PackUnderResolution pack : composed) {
            PackCompositionResolver.checkReferences(
                    composed,
                    pack.descriptor().packId(),
                    Objects.requireNonNull(facts.get(pack.configured().id().value()), "read above")
                            .references()
                            .stream()
                            .map(written -> PackCompositionResolver.resolveReference(
                                    composed, pack.configured().id(), PackQualifiedReference.parse(written)))
                            .toList());
        }
    }

    /**
     * C37 (ADR-0066 decision 8, FR-002): the step each member's step enhancements name is held by a pack of the
     * member's {@code depends_on} closure, judged after the references so a reference refusal keeps its precedence. A
     * registration and an impact-checked write compose through here over the staged packs, so they refuse with the same
     * code, answered {@code 422} there.
     */
    private static void checkStepEnhancementTargets(
            List<PackUnderResolution> composed, Map<String, PackCompositionFacts> facts) {
        for (PackUnderResolution pack : composed) {
            EnhancementCompose.targetHeld(
                            composed,
                            pack,
                            Objects.requireNonNull(
                                            facts.get(pack.configured().id().value()), "read above")
                                    .edges())
                    .ifPresent(violation -> {
                        throw violation.toCharterRefusal();
                    });
        }
    }

    /**
     * The pack's descriptor, judged by the descriptor rules validate mode runs: a {@code requires_core} or a
     * {@code depends_on} range that is no PEP 440 specifier set makes the descriptor unreadable here too, so a
     * malformed range answers {@code pack_unreadable} on the offering as it does on the load path, and never reaches
     * the resolver.
     */
    private PackDescriptor descriptorOf(ConfiguredPack pack) {
        PackDescriptor descriptor = descriptors
                .find(pack.id())
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE, "pack " + pack.id().value() + " has no pack.yaml"));
        PackPolicy.descriptorFindings(pack.id().value(), descriptor).stream()
                .findFirst()
                .ifPresent(finding -> {
                    throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, finding.detail());
                });
        return descriptor;
    }
}
