package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.Pep440Range;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.rules.DescriptorChecks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;

/**
 * Resolves one compose: the configured packs plus their descriptors become an ordered closure, dependencies before the
 * packs that need them. One compose is one {@code depends_on} closure rooted at every configured pack, across levels (ADR-0053
 * decision 6, as amended); a shared dependency appears once; a broken graph is refused with the pack that broke it
 * named (ADR-0048 decision 10, ADR-0053 decisions 4, 7, 8 and 9).
 *
 * <p>The resolver is a pure function: it reads what it is given, orders it and refuses, and it touches no repository.
 * That keeps it testable without a filesystem and lets the offering read stay the one verified read.
 */
public final class PackCompositionResolver {

    /**
     * The merge order the resolver seeds its queue with and breaks ties in: by configured level in {@link PackLevel}'s
     * declaration order (built-in, org, project), then by pack id in code-point order, so a supplementary character
     * sorts after {@code U+FB01}, as no UTF-16 order does. It is the offering's order: dependencies come first, and
     * packs no dependency relates stay in this order.
     */
    private static final Comparator<PackUnderResolution> STABLE_ORDER = Comparator.comparingInt(
                    (PackUnderResolution entry) -> rank(entry.configured().level()))
            .thenComparing(entry -> entry.configured().id().value().codePoints().toArray(), Arrays::compare);

    private PackCompositionResolver() {}

    /**
     * One configured pack with what the offering read already holds.
     *
     * @param configured the configured pack and its level
     * @param descriptor what its {@code pack.yaml} states
     * @param changedArtifacts the artifacts its graph documents say it changes (an {@code overrides} relation on the
     *     artifact; an {@code enhances} relation is additive and changes nothing, A14); empty when they name none
     * @param referableArtifacts what a reference can name in the pack ({@link PackCompositionFacts#referable()}: its
     *     artifact files and the mission type steps its graph documents declare), as bare URNs; empty when none
     * @param heldTerms the terms the pack's glossary contexts hold, as bare URNs ({@code term:<context-id>/<term-id>});
     *     empty when it holds none
     */
    public record PackUnderResolution(
            ConfiguredPack configured,
            PackDescriptor descriptor,
            List<String> changedArtifacts,
            List<String> referableArtifacts,
            Set<String> heldTerms) {

        /**
         * Checks the fields.
         *
         * @param configured the configured pack
         * @param descriptor its descriptor
         * @param changedArtifacts the artifacts it changes
         * @param referableArtifacts the artifacts it holds
         * @param heldTerms the terms it holds
         */
        public PackUnderResolution {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(changedArtifacts, "changedArtifacts");
            Objects.requireNonNull(referableArtifacts, "referableArtifacts");
            Objects.requireNonNull(heldTerms, "heldTerms");
            changedArtifacts = List.copyOf(changedArtifacts);
            referableArtifacts = List.copyOf(referableArtifacts);
            heldTerms = Set.copyOf(heldTerms);
        }

        /**
         * A pack under resolution whose glossary contexts hold no term.
         *
         * @param configured the configured pack
         * @param descriptor its descriptor
         * @param changedArtifacts the artifacts it changes
         * @param referableArtifacts the artifacts it holds
         */
        public PackUnderResolution(
                ConfiguredPack configured,
                PackDescriptor descriptor,
                List<String> changedArtifacts,
                List<String> referableArtifacts) {
            this(configured, descriptor, changedArtifacts, referableArtifacts, Set.of());
        }

        /**
         * The pack's descriptor identity, the key the closure's indexes use: the {@code pack_id} its descriptor declares,
         * which the descriptor read checked against the descriptor schema.
         *
         * @return the descriptor's {@code pack_id}
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the descriptor's {@code pack_id}
         *     is no ULID
         */
        public PackUlid descriptorId() {
            return new PackUlid(descriptor.packId());
        }

        /**
         * Whether the pack holds what a bare URN names: the one answer, from the lists the pack was read with. A term
         * ({@code term:<context-id>/<term-id>}) is an item of its glossary context, not an artifact (ADR-0049 decision
         * 3), so the pack holds it when it holds the context and the context holds the term. A reference of a kind the
         * pack holds no artifacts of is held by no pack, so it fails open to nothing.
         *
         * @param urn the bare URN text, {@code <kind>:<id>}
         * @return {@code true} when the pack's held artifacts, or for a term its held context, name it
         */
        public boolean holds(String urn) {
            return Urn.parse(urn)
                    .map(parsed -> PackCompositionFacts.holds(referableArtifacts, heldTerms, parsed))
                    .orElseGet(() -> referableArtifacts.contains(urn));
        }

        /**
         * A pack under resolution whose graphs name artifacts it changes and that holds no artifact of its own.
         *
         * @param configured the configured pack
         * @param descriptor its descriptor
         * @param changedArtifacts the artifacts it changes
         */
        public PackUnderResolution(
                ConfiguredPack configured, PackDescriptor descriptor, List<String> changedArtifacts) {
            this(configured, descriptor, changedArtifacts, List.of());
        }

        /**
         * A pack under resolution whose graphs name no refined artifact.
         *
         * @param configured the configured pack
         * @param descriptor its descriptor
         */
        public PackUnderResolution(ConfiguredPack configured, PackDescriptor descriptor) {
            this(configured, descriptor, List.of());
        }
    }

    /**
     * Why a compose cannot be served. Each reason names the pack that broke the graph, so a refusal is actionable.
     */
    public enum Failure {
        /** A dependency edge closes a cycle, including a pack that depends on itself. */
        CYCLE(RefusalCode.PACK_DEPENDENCY_CYCLE),
        /** A dependency names a pack id no configured pack declares. */
        UNKNOWN_DEPENDENCY(RefusalCode.PACK_DEPENDENCY_UNKNOWN),
        /** The held version fits no declared range for that pack. */
        OUT_OF_RANGE(RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE),
        /** A {@code parent_pack} names an id no configured pack declares. */
        PARENT_UNRESOLVED(RefusalCode.PACK_PARENT_UNRESOLVED),
        /** A pack depends on, or takes as parent, a pack further from built-in than its own level. */
        LEVEL_VIOLATION(RefusalCode.PACK_LEVEL_VIOLATION),
        /** Two packs in the compose declare the same name. */
        NAME_CONFLICT(RefusalCode.PACK_NAME_CONFLICT),
        /** Two configured packs declare the same {@code pack_id}. */
        DUPLICATE_ID(RefusalCode.PACK_DUPLICATE_ID),
        /**
         * Two siblings at one level change or define one artifact and the distribution pack does not settle it with an
         * {@code overrides} edge: none declares one, a pack that is not the distribution pack does, or two distribution
         * packs do (#358, {@link PackCompositionResolver#settle}).
         */
        SIBLING_CONFLICT(RefusalCode.PACK_SIBLING_CONFLICT),
        /** A bare reference resolves only in another pack of the compose; the refusal names the holding pack. */
        REFERENCE_UNQUALIFIED(RefusalCode.PACK_REFERENCE_UNQUALIFIED),
        /** A pack-qualified reference names a pack outside the referring pack's {@code depends_on} closure; the refusal names the missing pack. */
        REFERENCE_PACK_MISSING(RefusalCode.PACK_REFERENCE_PACK_MISSING),
        /** A pack-qualified reference names a pack of the closure, and that pack does not hold the artifact. */
        REFERENCE_ARTIFACT_MISSING(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);

        private final RefusalCode code;

        Failure(RefusalCode code) {
            this.code = code;
        }

        /**
         * The refusal code the contract serves for this reason: the one mapping from a failure to a code.
         *
         * @return the code
         */
        public RefusalCode code() {
            return code;
        }
    }

    /**
     * A refused compose: the reason and the packs it names, the pack that broke the graph first. Each pack is named
     * by its descriptor {@code pack_id}, its configured id and its {@code name}, so a client never has to tell a name
     * from an id.
     */
    public static final class Refusal extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** Why the compose cannot be served. */
        private final Failure failure;

        /** The packs the refusal names, the violating pack first; never empty. */
        private final transient List<RefusalPack> packs;

        /**
         * A refusal.
         *
         * @param failure why the compose cannot be served
         * @param packs the packs the refusal names, the pack that broke the graph first
         * @param detail what the caller needs to read
         */
        public Refusal(Failure failure, List<RefusalPack> packs, String detail) {
            super(detail);
            this.failure = Objects.requireNonNull(failure, "failure");
            this.packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            if (this.packs.isEmpty()) {
                throw new IllegalArgumentException("a refusal names the pack that broke the graph");
            }
        }

        /**
         * Why the compose cannot be served.
         *
         * @return the reason
         */
        public Failure failure() {
            return failure;
        }

        /**
         * The packs the refusal names, the pack that broke the graph first.
         *
         * @return the packs, each with its descriptor {@code pack_id} and {@code name}
         */
        public List<RefusalPack> packs() {
            return packs;
        }

        /**
         * The pack that broke the graph.
         *
         * @return the id it is registered under
         */
        public PackIdentifier packId() {
            return packs.getFirst().packId();
        }

        /**
         * The wire refusal this one projects onto: the failure's code, the detail and the packs it names.
         *
         * @return the refusal to throw out of a read
         */
        public CharterRefusal toCharterRefusal() {
            return new CharterRefusal(failure.code(), detail(), packs);
        }

        /**
         * The fault this refusal is in a closure: the failure's code, the detail and the packs it names, about the first
         * pack it names (R-o). The one factory of a resolver refusal's fault, which the offering's compose phases, the
         * edge index and {@link #referenceFaultsOf} read (#614).
         *
         * @return the fault
         */
        public ClosureFault toFault() {
            return toFault(ClosureFault.RULE);
        }

        /**
         * The fault this refusal is in a closure under the rule of the phase that found it: the failure's code, the
         * detail and the packs it names, about the first pack it names (R-o).
         *
         * @param rule the phase's rule name
         * @return the fault
         */
        public ClosureFault toFault(String rule) {
            return ClosureFault.of(PackOrigins.firstOf(packs), rule, failure.code(), detail(), packs);
        }

        /**
         * What the caller needs to read; never null.
         *
         * @return the detail
         */
        public String detail() {
            return Objects.requireNonNull(getMessage(), "detail");
        }
    }

    /**
     * Resolves the compose: the first of its {@linkplain #faults faults} refuses it.
     *
     * @param packs the configured packs with their descriptors
     * @return the closure, dependencies first, each pack once
     * @throws Refusal the first fault {@link #faults} collects, when the graph cannot be served
     * @throws IllegalStateException when a descriptor is not well formed: the resolver decides the graph, not a
     *     descriptor, so a pack whose {@code pack_version} is no PEP 440 version or whose {@code depends_on} range is
     *     no specifier set is {@code pack_unreadable} at the read ({@link DescriptorChecks#faults}, the one
     *     predicate), and never reaches here
     */
    public static List<PackUnderResolution> resolve(List<PackUnderResolution> packs) {
        Composition composition = compose(packs);
        refuseFirst(composition.faults());
        return composition.order();
    }

    /**
     * Every composition fault of a set of packs (#554, FR-003), each with the packs it names, in the order the
     * resolver judges them: every duplicate {@code pack_id}, every duplicate name, every pair of disjoint ranges, then
     * pack by pack in the stable order its dependencies (a pack that depends on itself, an unknown pack, a version out of
     * range, a level further from built-in) and its parent (itself, an unknown pack, a level further from built-in),
     * then every sibling contest, the changed artifacts before the held ones, then every dependency cycle. The first
     * is the refusal {@link #resolve} throws. A fault leaves the rest judged: a later check reads the first holder of a
     * duplicated {@code pack_id}, and an edge to the pack itself or to a pack no one declares orders nothing.
     *
     * @param packs the configured packs with their descriptors
     * @return the faults; empty when the compose resolves
     * @throws IllegalStateException when a descriptor is not well formed, as {@link #resolve} refuses it
     */
    public static List<Refusal> faults(List<PackUnderResolution> packs) {
        return compose(packs).faults();
    }

    /**
     * Throws the first of a set of faults, as a caller that answers one refusal needs. The offering answers every fault
     * of a phase instead (#556, FR-006).
     *
     * @param faults the faults, in the order they were judged
     * @throws Refusal the first, when there is one
     */
    public static void refuseFirst(List<Refusal> faults) {
        if (!faults.isEmpty()) {
            throw faults.getFirst();
        }
    }

    /**
     * The packs of a set in merge order, whether or not the set resolves: the order {@link #resolve} returns, then the
     * packs it could not place (a cycle, what lies downstream of one, a second holder of a {@code pack_id}) in the
     * stable order. For a set that resolves it is the resolved order. A judgement that needs an order without
     * refusing first, as the preset phase of {@code CLOSURE} does, reads the packs in this one.
     *
     * @param packs the configured packs with their descriptors
     * @return every pack once, in merge order
     * @throws IllegalStateException when a descriptor is not well formed, as {@link #resolve} refuses it
     */
    public static List<PackUnderResolution> mergeOrder(List<PackUnderResolution> packs) {
        Composition composition = compose(packs);
        List<PackUnderResolution> unplaced = new ArrayList<>(composition.stable());
        composition.order().forEach(unplaced::remove);
        List<PackUnderResolution> merged = new ArrayList<>(composition.order());
        merged.addAll(unplaced);
        return List.copyOf(merged);
    }

    /**
     * What one judgement of a compose found: its faults, the order of the packs it could place, and every pack in the
     * stable order.
     */
    private record Composition(
            List<Refusal> faults, List<PackUnderResolution> order, List<PackUnderResolution> stable) {}

    private static Composition compose(List<PackUnderResolution> packs) {
        Objects.requireNonNull(packs, "packs");
        for (PackUnderResolution pack : packs) {
            List<DescriptorChecks.Fault> faults =
                    DescriptorChecks.faults(pack.descriptor().name(), pack.descriptor());
            if (!faults.isEmpty()) {
                throw new IllegalStateException("the resolver takes only well-formed descriptors, and "
                        + faults.getFirst().detail());
            }
        }
        List<PackUnderResolution> ordered = new ArrayList<>(packs);
        ordered.sort(STABLE_ORDER);

        List<Refusal> faults = new ArrayList<>(duplicateIds(ordered));
        Map<String, PackUnderResolution> byId = firstById(ordered);
        faults.addAll(nameConflicts(ordered));
        faults.addAll(disjointRanges(ordered, byId));
        Map<String, List<String>> edges = new LinkedHashMap<>();
        for (PackUnderResolution pack : ordered) {
            List<String> placedAfter = new ArrayList<>();
            faults.addAll(dependenciesOf(pack, byId, placedAfter));
            // The first holder of a pack_id is the one the compose places, as byId names it.
            edges.putIfAbsent(pack.descriptor().packId(), placedAfter);
        }
        Set<String> seen = new HashSet<>();
        List<PackUnderResolution> distinct = ordered.stream()
                .filter(pack -> seen.add(pack.descriptor().packId()))
                .toList();
        // A twin pack_id is read as its first holder here too, so one cause is reported once.
        faults.addAll(siblingContests(distinct));
        Ordering ordering = topologicalOrder(distinct, byId, edges);
        faults.addAll(ordering.cycles());
        return new Composition(List.copyOf(faults), ordering.order(), List.copyOf(ordered));
    }

    /**
     * A rooted compose with every fault, not the first (#556, FR-006): the closure's merge order, and every composition
     * fault of the closure, in {@link #faults}' order.
     *
     * @param order the closure in merge order; meaningful only when there is no fault
     * @param faults every composition fault of the closure; empty when it resolves
     */
    public record Rooted(List<PackUnderResolution> order, List<Refusal> faults) {

        /**
         * Copies the lists.
         *
         * @param order the closure in merge order
         * @param faults the faults
         */
        public Rooted {
            order = List.copyOf(order);
            faults = List.copyOf(faults);
        }
    }

    /**
     * The compose rooted at a set of configured packs (#360), with every composition fault of its closure (#556,
     * FR-006): every check {@link #resolve} runs, over the {@linkplain PackGraph#compositionClosure composition
     * closure} of the roots only, so a pack outside it is never judged. A duplicate {@code pack_id} or {@code name}
     * outside the closure is no fault; one inside it is, naming both holders, because every pack declaring an id of the
     * closure is selected. A {@code depends_on} or {@code parent_pack} target no available pack declares stays missing,
     * and is a fault as {@link #resolve} refuses it. With every available pack a root, the order and the faults are
     * those of {@link #faults}. The offering refuses the phase with all of them; a caller that answers one refusal
     * takes {@link #refuseFirst}.
     *
     * @param available the configured packs with their descriptors
     * @param roots the configured identifiers the compose is rooted at
     * @return the closure in merge order and its faults
     * @throws IllegalArgumentException when a root names no available pack
     */
    public static Rooted composeRooted(List<PackUnderResolution> available, Collection<PackIdentifier> roots) {
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(roots, "roots");
        Map<String, PackUnderResolution> firstByPackId = new LinkedHashMap<>();
        Map<PackIdentifier, String> packIdByConfigured = new HashMap<>();
        for (PackUnderResolution pack : available) {
            firstByPackId.putIfAbsent(pack.descriptor().packId(), pack);
            packIdByConfigured.putIfAbsent(
                    pack.configured().id(), pack.descriptor().packId());
        }
        List<String> rootPackIds = new ArrayList<>();
        for (PackIdentifier root : roots) {
            String packId = packIdByConfigured.get(root);
            if (packId == null) {
                throw new IllegalArgumentException("no available pack is configured as " + root.value());
            }
            rootPackIds.add(packId);
        }
        Set<String> closure = PackGraph.compositionClosure(
                rootPackIds,
                packId -> Optional.ofNullable(firstByPackId.get(packId)).map(PackUnderResolution::descriptor));
        Composition composition = compose(available.stream()
                .filter(pack -> closure.contains(pack.descriptor().packId()))
                .toList());
        return new Rooted(composition.order(), composition.faults());
    }

    /**
     * One reference as the compose resolves it: the reference an artifact writes, and the pack that holds what it
     * names. The offering read builds these from the references of every pack's graphs and checks them with
     * {@link #referenceFaults} when the compose resolves; the artifact reads build them for one reference at a time.
     * Both build them through {@link #resolveReference}, the one owner of which pack a reference means.
     *
     * @param reference the reference as the artifact writes it
     * @param holdingPackId the {@code pack_id} of the pack that holds what the reference names: the named pack for a
     *     qualified reference, the referring pack or the first other pack holding it for a bare one; empty when the
     *     compose holds no such pack
     */
    public record ResolvedReference(PackQualifiedReference reference, Optional<String> holdingPackId) {

        /**
         * Checks the fields.
         *
         * @param reference the reference as written
         * @param holdingPackId the pack that holds what it names, when there is one
         */
        public ResolvedReference {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(holdingPackId, "holdingPackId");
        }
    }

    /**
     * The pack of the compose that holds what a reference names, as an artifact writes it: the one owner of "which
     * pack does this reference mean" (FR-013, FR-014, FR-015). A qualified reference is held by the pack of that
     * name. A bare one is held by the referring pack when it holds the artifact, which shadows a same-named artifact
     * of another pack (FR-014), else by the first other pack of the compose that holds it. It judges nothing: whether
     * the holder is a legal one is {@link #referenceFaults}.
     *
     * @param packs the resolved compose
     * @param referring the configured identity of the pack whose artifact writes the reference
     * @param reference the reference as written
     * @return the holding pack; empty when the compose holds no pack of the qualifier's name, or when no pack holds
     *     what a bare reference names
     */
    public static Optional<PackUnderResolution> holderOf(
            List<PackUnderResolution> packs, PackIdentifier referring, PackQualifiedReference reference) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(referring, "referring");
        Objects.requireNonNull(reference, "reference");
        if (reference.qualified()) {
            String name = reference.packName().orElseThrow();
            return packs.stream()
                    .filter(pack -> pack.descriptor().name().equals(name))
                    .findFirst();
        }
        Optional<PackUnderResolution> own = packs.stream()
                .filter(pack -> pack.configured().id().equals(referring))
                .findFirst();
        if (own.isPresent() && own.get().holds(reference.urn())) {
            return own;
        }
        return packs.stream()
                .filter(pack -> !pack.configured().id().equals(referring))
                .filter(pack -> pack.holds(reference.urn()))
                .findFirst();
    }

    /**
     * One reference with the pack that holds what it names ({@link #holderOf}), ready for {@link #referenceFaults}.
     *
     * @param packs the resolved compose
     * @param referring the configured identity of the pack whose artifact writes the reference
     * @param reference the reference as written
     * @return the reference and its holding pack's {@code pack_id}, when there is one
     */
    public static ResolvedReference resolveReference(
            List<PackUnderResolution> packs, PackIdentifier referring, PackQualifiedReference reference) {
        return new ResolvedReference(
                reference,
                holderOf(packs, referring, reference)
                        .map(holder -> holder.descriptor().packId()));
    }

    /**
     * Checks one pack's references: a pack-qualified reference must name the referring pack or a pack of the referring
     * pack's own {@code depends_on} closure (FR-017), and a bare reference must resolve in the pack whose artifact
     * writes it (FR-014, FR-015), so an accidental cross-pack coupling cannot resolve by load order. Packs are
     * identified by their descriptor {@code pack_id} throughout.
     *
     * @param packs the resolved compose
     * @param referringPackId the {@code pack_id} of the pack whose artifacts write the references
     * @param references the references as the artifact read resolved them, in the artifact's order
     * @return every fault, in the references' order (#556, FR-006): {@link Failure#REFERENCE_UNQUALIFIED} for a bare
     *     reference that resolves only in another pack, {@link Failure#REFERENCE_PACK_MISSING} for a qualified
     *     reference that names a pack outside the referring pack's closure, and {@link Failure#REFERENCE_ARTIFACT_MISSING}
     *     for one that names a pack of the closure that does not hold the artifact; empty when every reference holds
     * @throws IllegalArgumentException when no pack of the compose has the referring {@code pack_id}
     */
    public static List<Refusal> referenceFaults(
            List<PackUnderResolution> packs, String referringPackId, List<ResolvedReference> references) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(referringPackId, "referringPackId");
        Objects.requireNonNull(references, "references");
        Map<String, PackUnderResolution> byId = indexById(packs);
        Map<String, PackUnderResolution> byName = new HashMap<>();
        for (PackUnderResolution pack : packs) {
            byName.putIfAbsent(pack.descriptor().name(), pack);
        }
        Set<String> closure = dependsOnClosure(referringPackId, byId);
        PackUnderResolution referring = byId.get(referringPackId);
        if (referring == null) {
            throw new IllegalArgumentException("the referring pack " + referringPackId + " is no pack of the compose");
        }
        String referringLabel = label(referring);
        List<Refusal> faults = new ArrayList<>();
        for (ResolvedReference resolved : references) {
            PackQualifiedReference reference = resolved.reference();
            if (reference.qualified()) {
                qualifiedReferenceFault(reference, byName, closure, referring, referringLabel)
                        .ifPresent(faults::add);
            } else if (resolved.holdingPackId()
                    .filter(id -> !id.equals(referringPackId))
                    .isPresent()) {
                String holdingId = resolved.holdingPackId().orElseThrow();
                PackUnderResolution holding = byId.get(holdingId);
                List<RefusalPack> holder = holding == null ? List.of() : named(holding);
                String holdingLabel = holding == null ? "pack `" + holdingId + "`" : label(holding);
                faults.add(new Refusal(
                        Failure.REFERENCE_UNQUALIFIED,
                        concat(named(referring), holder),
                        referringLabel + " writes the bare reference `" + reference + "`, which resolves only in "
                                + holdingLabel + "; a cross-pack reference is written pack-qualified"));
            }
        }
        return List.copyOf(faults);
    }

    /**
     * One pack's references within a closure, every fault kept (#632 item 3, ruling R-s): each reference its facts list
     * as written, resolved in the closure ({@link #resolveReference}) and checked by {@link #referenceFaults}, each fault
     * as {@link Refusal#toFault()} gives it. It is the one body of the reference check, through
     * {@code PackRulesets.references}: the offering's phase runs it for every member of the closure it composes, and
     * pack-doctor's {@code --full} for every member of each root's composed closure (ruling R-ae).
     *
     * @param closure the closure the pack is judged in, the pack among it
     * @param pack the pack whose references are judged
     * @param facts the pack's composition facts
     * @return every fault, in the references' order; empty when every reference holds
     * @throws IllegalArgumentException when the pack is not of the closure
     */
    public static List<ClosureFault> referenceFaultsOf(
            List<PackUnderResolution> closure, PackUnderResolution pack, PackCompositionFacts facts) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(facts, "facts");
        return referenceFaults(
                        closure,
                        pack.descriptor().packId(),
                        facts.references().stream()
                                .map(written -> resolveReference(
                                        closure, pack.configured().id(), PackQualifiedReference.parse(written)))
                                .toList())
                .stream()
                .map(Refusal::toFault)
                .toList();
    }

    /**
     * One pack's substep delegation faults within a closure, every fault kept (#632 item 3, ruling R-s): the candidates
     * its facts list, judged by {@link #unresolvedDelegations}. It is the one body of the delegation check, through
     * {@code PackRulesets.references}: the offering's phase runs it for every member of the closure it composes, and
     * pack-doctor's {@code --full} for every member of each root's composed closure (ruling R-ae).
     *
     * @param closure the closure the pack is judged in, the pack among it
     * @param pack the pack whose candidates are judged
     * @param facts the pack's composition facts
     * @return every fault, in the candidates' order; empty when every candidate resolves
     */
    public static List<ClosureFault> delegationFaultsOf(
            List<PackUnderResolution> closure, PackUnderResolution pack, PackCompositionFacts facts) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(facts, "facts");
        return unresolvedDelegations(closure, pack.descriptor().packId(), facts.delegations());
    }

    /**
     * Every substep delegation candidate a pack writes with another pack's name when that pack is of the compose and of
     * the writer's {@code depends_on} closure and does not hold an artifact of the candidate's kind and id (FR-005).
     * It runs before {@link #referenceFaults}, so such a candidate is refused once, by its own code, and not again as the
     * reference of its {@code scope} edge. A candidate whose pack the compose does not hold, or holds outside the
     * closure, is left to the reference check of its edge ({@code pack_reference_pack_missing}).
     *
     * @param packs the resolved compose
     * @param referringPackId the {@code pack_id} of the pack whose mission types write the candidates
     * @param candidates the candidates, as {@link SubstepDelegationRule#qualifiedCandidates} lists them
     * @return one fault per such candidate, in order (#556, FR-006): {@link RefusalCode#PACK_UNREADABLE}, naming both
     *     packs, with one violation {@value SubstepDelegationRule#UNRESOLVED} at the mission type file
     */
    public static List<ClosureFault> unresolvedDelegations(
            List<PackUnderResolution> packs,
            String referringPackId,
            List<SubstepDelegationRule.QualifiedCandidate> candidates) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(referringPackId, "referringPackId");
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<ClosureFault> faults = new ArrayList<>();
        Map<String, PackUnderResolution> byId = indexById(packs);
        Map<String, PackUnderResolution> byName = new HashMap<>();
        packs.forEach(pack -> byName.putIfAbsent(pack.descriptor().name(), pack));
        Set<String> closure = dependsOnClosure(referringPackId, byId);
        PackUnderResolution referring = byId.get(referringPackId);
        for (SubstepDelegationRule.QualifiedCandidate candidate : candidates) {
            PackUnderResolution target =
                    byName.get(candidate.reference().packName().orElseThrow());
            if (target == null
                    || !closure.contains(target.descriptor().packId())
                    || target.holds(candidate.reference().urn())) {
                continue;
            }
            String reason = candidate.unresolved(label(target));
            List<RefusalPack> named = referring == null ? named(target) : named(referring, target);
            faults.add(ClosureFault.of(
                    candidate
                            .reference()
                            .asUrn()
                            .map(urn -> PackOrigins.of(named.getFirst().packId(), urn))
                            .orElseGet(() -> PackOrigins.firstOf(named)),
                    RefusalCode.PACK_UNREADABLE,
                    (referring == null ? "pack `" + referringPackId + "`" : label(referring)) + ": " + reason,
                    named,
                    List.of(new RefusalViolation(candidate.file(), SubstepDelegationRule.UNRESOLVED, reason)),
                    List.of()));
        }
        return List.copyOf(faults);
    }

    /**
     * The fault of a pack-qualified reference whose pack the compose does not hold, whose pack lies outside the
     * referring pack's {@code depends_on} closure, or whose pack does not hold the graph-authored artifact, or the term,
     * it names.
     */
    private static Optional<Refusal> qualifiedReferenceFault(
            PackQualifiedReference reference,
            Map<String, PackUnderResolution> byName,
            Set<String> closure,
            PackUnderResolution referring,
            String referringLabel) {
        String missing = reference.packName().orElseThrow();
        PackUnderResolution target = byName.get(missing);
        if (target == null) {
            return Optional.of(new Refusal(
                    Failure.REFERENCE_PACK_MISSING,
                    named(referring),
                    referringLabel + " writes the reference `" + reference + "`, but the compose holds no pack"
                            + " named `" + missing + "`"));
        }
        if (!closure.contains(target.descriptor().packId())) {
            return Optional.of(new Refusal(
                    Failure.REFERENCE_PACK_MISSING,
                    named(referring, target),
                    referringLabel + " writes the reference `" + reference + "`, but " + label(target)
                            + " is outside its depends_on closure"));
        }
        Optional<Urn> urn = reference.asUrn();
        if (urn.isPresent()
                && urn.get().kind().edgeTarget()
                && (urn.get().kind().artifactKind().isPresent() || urn.get().kind() == NodeKind.TERM)
                && !target.holds(urn.get().toString())) {
            return Optional.of(new Refusal(
                    Failure.REFERENCE_ARTIFACT_MISSING,
                    named(referring, target),
                    referringLabel + " writes the reference `" + reference + "`, but " + label(target)
                            + " does not hold `" + urn.get() + "`"));
        }
        return Optional.empty();
    }

    /**
     * The pack and every pack it reaches through {@code depends_on} only, by {@code pack_id}: the packs a qualified
     * reference may name (FR-015), and the packs that may hold the step a step enhancement names (C37,
     * {@link EnhancementCompose#targetFaults}). It is not the
     * {@linkplain PackGraph#compositionClosure composition closure}, which
     * also follows {@code parent_pack}.
     *
     * @param packId the descriptor {@code pack_id} of the pack the walk starts at
     * @param byId the packs of the compose, by descriptor {@code pack_id}
     * @return the {@code pack_id}s reached, the start included; a target no pack of {@code byId} declares is included
     *     and not descended from
     */
    static Set<String> dependsOnClosure(String packId, Map<String, PackUnderResolution> byId) {
        Set<String> reached = new LinkedHashSet<>();
        List<String> pending = new ArrayList<>(List.of(packId));
        while (!pending.isEmpty()) {
            String next = pending.remove(pending.size() - 1);
            PackUnderResolution pack = byId.get(next);
            if (reached.add(next) && pack != null) {
                pack.descriptor().dependencies().forEach(dependency -> pending.add(dependency.packId()));
            }
        }
        return reached;
    }

    /**
     * Every two packs that ask one pack for ranges no version can satisfy together (ADR-0053 decision 7; the operator
     * ruling of 2026-11-03): the compose holds one copy of the depended-on pack, so two declarations whose ranges
     * share no version, including ranges that only touch ({@code <2} and {@code >=2}), cannot both be honoured. Each
     * refusal names both declaring packs and both ranges, and they come before the held version is checked against
     * either range. A range pair that overlaps, even only partly, is fine and is not reported. A dependency on an
     * unknown pack is left to {@link #dependenciesOf}, which refuses it with its own reason.
     */
    private static List<Refusal> disjointRanges(
            List<PackUnderResolution> ordered, Map<String, PackUnderResolution> byId) {
        List<Refusal> faults = new ArrayList<>();
        Map<String, List<Declared>> byTarget = new LinkedHashMap<>();
        for (PackUnderResolution pack : ordered) {
            for (PackDependency dependency : pack.descriptor().dependencies()) {
                if (byId.containsKey(dependency.packId())) {
                    byTarget.computeIfAbsent(dependency.packId(), key -> new ArrayList<>())
                            .add(new Declared(pack, dependency.range()));
                }
            }
        }
        for (Map.Entry<String, List<Declared>> asked : byTarget.entrySet()) {
            List<Declared> declarations = asked.getValue();
            for (int later = 1; later < declarations.size(); later++) {
                for (int earlier = 0; earlier < later; earlier++) {
                    Declared first = declarations.get(earlier);
                    Declared second = declarations.get(later);
                    if (!Pep440Range.overlaps(first.range(), second.range())) {
                        faults.add(new Refusal(
                                Failure.OUT_OF_RANGE,
                                named(second.pack(), first.pack()),
                                "packs `" + first.pack().descriptor().name() + "` ("
                                        + first.pack().descriptor().packId() + ") and `"
                                        + second.pack().descriptor().name() + "` ("
                                        + second.pack().descriptor().packId() + ") require pack `"
                                        + nameOf(asked.getKey(), byId) + "` (" + asked.getKey() + ") in ranges `"
                                        + first.range() + "` and `" + second.range()
                                        + "`, which no version satisfies together"));
                    }
                }
            }
        }
        return faults;
    }

    private record Declared(PackUnderResolution pack, String range) {}

    /**
     * Every set of two or more packs of one configured level that contest an artifact, one refusal per artifact and
     * level, unless the distribution pack settles it with an {@code overrides} edge ({@link #settle}; FR-012, ADR-0048
     * decision 9, ADR-0053 decision 5, #358): a conflict must be a decision, not load order. Two packs contest an
     * artifact when both change it (an {@code overrides} relation on it) or when both define it (each holds an artifact
     * with that id); a definer and an enhancer, or two enhancers, contest nothing (A14, decision
     * {@code DM-01M6RA92BS6GGCYTRCFQDYYY5S}, rule C14). Packs at different levels are no siblings — the layer merge
     * across levels composes them (ADR-0053 decision 6).
     *
     * <p>Artifacts are compared by their bare URN: a {@code <pack>::} qualifier on a reference or an
     * {@code overrides.artifact} names where it is read, not which artifact it is.
     */
    private static List<Refusal> siblingContests(List<PackUnderResolution> ordered) {
        List<Refusal> faults = new ArrayList<>(contests(ordered, PackUnderResolution::changedArtifacts, "change"));
        faults.addAll(contests(ordered, PackCompositionResolver::contestableDefinitions, "define"));
        return faults;
    }

    /**
     * What a pack defines that two siblings can contest: everything but a mission type's steps. A step
     * {@code action:<M>/<step>} is part of its mission type {@code M}, so siblings that define one step define {@code M}
     * too, and that contest (or the {@code overrides} edge settling it) decides; a step adds no contest of its own.
     */
    private static List<String> contestableDefinitions(PackUnderResolution pack) {
        return pack.referableArtifacts().stream()
                .filter(urn -> Urn.parse(bare(urn))
                        .filter(parsed -> parsed.kind() == NodeKind.ACTION)
                        .isEmpty())
                .toList();
    }

    private static List<Refusal> contests(
            List<PackUnderResolution> ordered, Function<PackUnderResolution, List<String>> artifactsOf, String verb) {
        List<Refusal> faults = new ArrayList<>();
        Map<String, List<PackUnderResolution>> contestants = new LinkedHashMap<>();
        for (PackUnderResolution pack : ordered) {
            for (String artifact : artifactsOf.apply(pack)) {
                List<PackUnderResolution> held = contestants.computeIfAbsent(bare(artifact), key -> new ArrayList<>());
                if (!held.contains(pack)) {
                    held.add(pack);
                }
            }
        }
        for (Map.Entry<String, List<PackUnderResolution>> contest : contestants.entrySet()) {
            String artifact = contest.getKey();
            Map<PackLevel, List<PackUnderResolution>> byLevel = new EnumMap<>(PackLevel.class);
            for (PackUnderResolution contestant : contest.getValue()) {
                byLevel.computeIfAbsent(contestant.configured().level(), level -> new ArrayList<>())
                        .add(contestant);
            }
            for (List<PackUnderResolution> siblings : byLevel.values()) {
                if (siblings.size() < 2) {
                    continue;
                }
                if (settle(ordered, artifact, siblings) instanceof Settlement.Unsettled unsettled) {
                    faults.add(new Refusal(
                            Failure.SIBLING_CONFLICT,
                            named(unsettled.named().toArray(PackUnderResolution[]::new)),
                            labels(siblings) + " at "
                                    + siblings.getFirst().configured().level().token()
                                    + " both " + verb + " the artifact `" + artifact + "`, and "
                                    + unsettled.reason()));
                }
            }
        }
        return faults;
    }

    /** Who the distribution pack is, as a refusal explains it (#358). */
    private static final String DISTRIBUTION =
            "the pack that depends on every contestant and that no pack of the contest's level depends on";

    /**
     * How a sibling contest is settled (C15, #358): by the member the distribution pack's {@code overrides} edge names,
     * or not at all. The compose and the layer merge both ask here, so they agree on every contest.
     */
    public sealed interface Settlement {

        /**
         * The contest is settled.
         *
         * @param winner the contestant whose version is in force at the contest's level
         */
        record Settled(PackUnderResolution winner) implements Settlement {}

        /**
         * The contest is not settled.
         *
         * @param kind why
         * @param reason the clause a refusal ends with, after "and "
         * @param named the packs a refusal names: the packs that declared an edge that may not settle first, then the
         *     contestants, the second in the stable order first
         */
        record Unsettled(Kind kind, String reason, List<PackUnderResolution> named) implements Settlement {

            /**
             * Copies the list.
             *
             * @param kind why
             * @param reason the clause
             * @param named the packs named
             */
            public Unsettled {
                Objects.requireNonNull(kind, "kind");
                Objects.requireNonNull(reason, "reason");
                named = List.copyOf(named);
            }
        }

        /** Why a contest is not settled. */
        enum Kind {
            /** No pack declares an edge on the artifact naming a contestant, and none names any other member. */
            NO_EDGE,
            /** The only edges on the artifact name a member that is not a contestant. */
            NOT_A_CONTESTANT,
            /** A pack that is not the distribution pack declares an edge naming a contestant. */
            NOT_THE_DISTRIBUTION,
            /** Two distribution packs declare an edge naming a contestant. */
            TWO_DISTRIBUTIONS
        }
    }

    /**
     * Settles a sibling contest (C15; #358, operator ruling (a) of 2026-11-17 as refined on 2026-11-18). Only the
     * distribution pack settles: it is derived, never declared, as the pack that is not a contestant, that reaches every
     * contestant over {@code depends_on} and {@code parent_pack} (ADR-0048 decisions 8 and 12: the distribution depends
     * on its members), and that no pack of the contest's level depends on. An edge naming a contestant that any other
     * pack declares (a member, a contestant, a pack that does not reach the contestants) refuses the contest, naming
     * that pack, even beside the distribution pack's edge; edges of two distribution packs refuse it, naming both,
     * whether or not they name one winner. An edge naming a pack that is not a contestant settles nothing. A pack names
     * one winner per artifact at most ({@link PackDescriptor}).
     *
     * @param compose the packs of the compose, each {@code pack_id} once
     * @param artifact the bare URN of the contested artifact, or for a step the mission type whose contest it shares
     * @param siblings the contestants, two or more packs of one level, in any order
     * @return the settlement
     */
    public static Settlement settle(
            List<PackUnderResolution> compose, String artifact, List<PackUnderResolution> siblings) {
        Objects.requireNonNull(compose, "compose");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(siblings, "siblings");
        Set<String> ids = new LinkedHashSet<>();
        siblings.forEach(pack -> ids.add(pack.descriptor().packId()));
        PackLevel level = siblings.getFirst().configured().level();
        Set<String> staleMembers = new LinkedHashSet<>();
        Map<PackUnderResolution, String> settlers = new LinkedHashMap<>();
        List<PackUnderResolution> notDistribution = new ArrayList<>();
        for (PackUnderResolution pack : compose) {
            for (PackOverride override : pack.descriptor().overrides()) {
                if (!bare(override.artifact()).equals(artifact)) {
                    continue;
                }
                if (!ids.contains(override.member())) {
                    staleMembers.add(override.member());
                } else if (isDistribution(pack, compose, level, ids)) {
                    settlers.putIfAbsent(pack, override.member());
                } else if (!notDistribution.contains(pack)) {
                    notDistribution.add(pack);
                }
            }
        }
        if (settlers.size() == 1 && notDistribution.isEmpty()) {
            String member = settlers.values().iterator().next();
            return new Settlement.Settled(siblings.stream()
                    .filter(pack -> pack.descriptor().packId().equals(member))
                    .findFirst()
                    .orElseThrow());
        }
        return unsettled(siblings, notDistribution, List.copyOf(settlers.keySet()), staleMembers);
    }

    /**
     * Why a contest is not settled, with the packs a refusal names: the declaring packs first, then the contestants in
     * the stable order with the second first, whatever order the caller gave them in.
     */
    private static Settlement.Unsettled unsettled(
            List<PackUnderResolution> siblings,
            List<PackUnderResolution> notDistribution,
            List<PackUnderResolution> settlers,
            Set<String> staleMembers) {
        List<PackUnderResolution> contestants = new ArrayList<>(siblings);
        contestants.sort(STABLE_ORDER);
        contestants.addFirst(contestants.remove(1));
        if (!notDistribution.isEmpty()) {
            return new Settlement.Unsettled(
                    Settlement.Kind.NOT_THE_DISTRIBUTION,
                    (notDistribution.size() == 1
                                    ? "an overrides edge settling it is declared by "
                                            + label(notDistribution.getFirst()) + ", which is not the distribution pack"
                                    : "overrides edges settling it are declared by " + labels(notDistribution)
                                            + ", which are not the distribution pack")
                            + ", " + DISTRIBUTION,
                    declarersFirst(notDistribution, contestants));
        }
        if (settlers.size() > 1) {
            return new Settlement.Unsettled(
                    Settlement.Kind.TWO_DISTRIBUTIONS,
                    "overrides edges settling it are declared by " + labels(settlers)
                            + ", more than one distribution pack, and only one may settle a contest",
                    declarersFirst(settlers, contestants));
        }
        return staleMembers.isEmpty()
                ? new Settlement.Unsettled(
                        Settlement.Kind.NO_EDGE,
                        "no pack of the compose declares an overrides edge settling it",
                        contestants)
                : new Settlement.Unsettled(
                        Settlement.Kind.NOT_A_CONTESTANT,
                        "an overrides edge settling it names " + String.join(", ", staleMembers)
                                + ", which is not a contestant",
                        contestants);
    }

    private static List<PackUnderResolution> declarersFirst(
            List<PackUnderResolution> declarers, List<PackUnderResolution> contestants) {
        List<PackUnderResolution> named = new ArrayList<>(declarers);
        contestants.stream().filter(pack -> !named.contains(pack)).forEach(named::add);
        return named;
    }

    /**
     * Whether a pack is the distribution pack of a contest (#358; operator ruling of 2026-11-18 on Q4, refining ruling
     * (a)): it is not a contestant, it reaches every contestant over {@code depends_on} and {@code parent_pack}, and no
     * pack of the contest's level depends on it. A dependent at another level does not count: a project pack that builds
     * on an org distribution leaves it the distribution of the org contests.
     */
    private static boolean isDistribution(
            PackUnderResolution pack, List<PackUnderResolution> compose, PackLevel level, Set<String> contestants) {
        String packId = pack.descriptor().packId();
        if (contestants.contains(packId)) {
            return false;
        }
        Map<String, PackUnderResolution> byId = new HashMap<>();
        compose.forEach(member -> byId.putIfAbsent(member.descriptor().packId(), member));
        boolean dependedOnAtTheContestsLevel = compose.stream()
                .filter(other -> other.configured().level() == level)
                .filter(other -> !other.descriptor().packId().equals(packId))
                .anyMatch(other -> targetsOf(other).contains(packId));
        return !dependedOnAtTheContestsLevel && reach(pack, byId).containsAll(contestants);
    }

    /** The {@code pack_id}s a pack names by {@code depends_on} and {@code parent_pack}. */
    private static List<String> targetsOf(PackUnderResolution pack) {
        return PackGraph.buildsOn(pack.descriptor());
    }

    /** Every {@code pack_id} a pack reaches over {@code depends_on} and {@code parent_pack} within the compose. */
    private static Set<String> reach(PackUnderResolution pack, Map<String, PackUnderResolution> byId) {
        Set<String> reached = PackGraph.ancestorsOf(
                targetsOf(pack),
                next -> Optional.ofNullable(byId.get(next))
                        .map(PackCompositionResolver::targetsOf)
                        .orElse(List.of()));
        reached.retainAll(byId.keySet());
        return reached;
    }

    /**
     * Every two packs of one configured level that hold one preset id and are not related by {@code parent_pack} (the
     * operator ruling of 2026-11-06, as {@link #siblingContests} judges two siblings that contest an artifact): neither
     * would win by anything but load order. A child and a pack on its {@code parent_pack} line are related, the child
     * merging after the parent it extends, so its copy takes the place of the parent's. A preset id held at different
     * levels is no contest: the layer merge applies (ADR-0053 decision 6, ADR-0059 D3), the pack nearer the project
     * winning. Presets are not artifacts, so no {@code overrides} edge settles a contest. The preset ids are composition
     * facts a {@link PackUnderResolution} does not carry, so this is a phase of its own, which the offering runs after
     * the references.
     *
     * @param ordered the packs of one compose, in merge order
     * @param presetIdsOf the ids of the presets a pack holds
     * @return a {@link Failure#SIBLING_CONFLICT} for each contesting pair, by preset id in first-held order, naming the
     *     second contestant first, as the artifact contest does; empty when there is none
     */
    public static List<Refusal> presetContests(
            List<PackUnderResolution> ordered, Function<PackUnderResolution, Collection<String>> presetIdsOf) {
        Objects.requireNonNull(ordered, "ordered");
        Objects.requireNonNull(presetIdsOf, "presetIdsOf");
        Map<String, PackUnderResolution> byId = new HashMap<>();
        ordered.forEach(pack -> byId.putIfAbsent(pack.descriptor().packId(), pack));
        Map<String, List<PackUnderResolution>> holders = new LinkedHashMap<>();
        for (PackUnderResolution pack : ordered) {
            for (String presetId : new LinkedHashSet<>(presetIdsOf.apply(pack))) {
                holders.computeIfAbsent(presetId, key -> new ArrayList<>()).add(pack);
            }
        }
        List<Refusal> faults = new ArrayList<>();
        for (Map.Entry<String, List<PackUnderResolution>> held : holders.entrySet()) {
            List<PackUnderResolution> packs = held.getValue();
            for (int later = 1; later < packs.size(); later++) {
                for (int earlier = 0; earlier < later; earlier++) {
                    PackUnderResolution first = packs.get(earlier);
                    PackUnderResolution second = packs.get(later);
                    if (first.configured().level() == second.configured().level() && !related(first, second, byId)) {
                        faults.add(new Refusal(
                                Failure.SIBLING_CONFLICT,
                                named(second, first),
                                labels(List.of(first, second)) + " at "
                                        + first.configured().level().token() + " both hold the preset `"
                                        + held.getKey() + "`, and neither extends the other by parent_pack"));
                    }
                }
            }
        }
        return faults;
    }

    /** Whether one pack is on the other's {@code parent_pack} line. The walk visits each pack once, so a cycle ends it. */
    private static boolean related(
            PackUnderResolution first, PackUnderResolution second, Map<String, PackUnderResolution> byId) {
        return extendsOnLine(first, second, byId) || extendsOnLine(second, first, byId);
    }

    private static boolean extendsOnLine(
            PackUnderResolution child, PackUnderResolution ancestor, Map<String, PackUnderResolution> byId) {
        Set<String> walked = new LinkedHashSet<>();
        Optional<String> at = child.descriptor().parentPack();
        while (at.isPresent() && walked.add(at.get())) {
            if (at.get().equals(ancestor.descriptor().packId())) {
                return true;
            }
            PackUnderResolution next = byId.get(at.get());
            at = next == null ? Optional.empty() : next.descriptor().parentPack();
        }
        return false;
    }

    /**
     * The identity an artifact reference names: its bare URN. The {@code <pack>::} qualifier is split off by
     * {@link PackQualifiedReference}, the one parser, and is not part of the identity, since a pack is provenance
     * (ADR-0048 decision 10). A reference that is no URN of a known kind is its own key, so it never contests anything
     * but itself.
     */
    private static String bare(String reference) {
        try {
            PackQualifiedReference parsed = PackQualifiedReference.parse(reference);
            return parsed.asUrn().map(Urn::toString).orElse(parsed.urn());
        } catch (IllegalArgumentException malformed) {
            return reference;
        }
    }

    private static Map<String, PackUnderResolution> indexById(List<PackUnderResolution> packs) {
        Map<String, PackUnderResolution> byId = new HashMap<>();
        for (PackUnderResolution pack : packs) {
            PackUnderResolution previous = byId.putIfAbsent(pack.descriptor().packId(), pack);
            if (previous != null) {
                throw new Refusal(
                        Failure.DUPLICATE_ID,
                        named(pack, previous),
                        labels(List.of(previous, pack)) + " both declare the pack_id "
                                + pack.descriptor().packId());
            }
        }
        return byId;
    }

    /**
     * Every pack whose descriptor declares a {@code pack_id} an earlier pack of the stable order declares, each named
     * with the first holder.
     */
    private static List<Refusal> duplicateIds(List<PackUnderResolution> ordered) {
        List<Refusal> faults = new ArrayList<>();
        Map<String, PackUnderResolution> byId = new HashMap<>();
        for (PackUnderResolution pack : ordered) {
            PackUnderResolution previous = byId.putIfAbsent(pack.descriptor().packId(), pack);
            if (previous != null) {
                faults.add(new Refusal(
                        Failure.DUPLICATE_ID,
                        named(pack, previous),
                        labels(List.of(previous, pack)) + " both declare the pack_id "
                                + pack.descriptor().packId()));
            }
        }
        return faults;
    }

    /** The packs by descriptor {@code pack_id}, the first holder of a duplicated id standing for it. */
    private static Map<String, PackUnderResolution> firstById(List<PackUnderResolution> ordered) {
        Map<String, PackUnderResolution> byId = new HashMap<>();
        ordered.forEach(pack -> byId.putIfAbsent(pack.descriptor().packId(), pack));
        return byId;
    }

    /** Every pack that declares a name an earlier pack of the stable order declares, each named with the first. */
    private static List<Refusal> nameConflicts(List<PackUnderResolution> ordered) {
        List<Refusal> faults = new ArrayList<>();
        Map<String, PackUnderResolution> byName = new HashMap<>();
        for (PackUnderResolution pack : ordered) {
            String name = pack.descriptor().name();
            PackUnderResolution previous = byName.putIfAbsent(name, pack);
            if (previous != null) {
                faults.add(new Refusal(
                        Failure.NAME_CONFLICT,
                        named(pack, previous),
                        labels(List.of(previous, pack)) + " both declare the name `" + name + "`"));
            }
        }
        return faults;
    }

    /**
     * Judges the edges one pack must be placed after: its {@code depends_on} targets and its {@code parent_pack}, each
     * checked as the compose checks it, in that order. Both are edges of the order and of the cycle check, so a parent
     * cycle is {@link Failure#CYCLE}. An edge to a pack that is there, out of range or at a level further from
     * built-in included, joins {@code placedAfter}; an edge to the pack itself or to a pack no one declares does not,
     * since it is refused on its own and orders nothing.
     *
     * @param pack the pack
     * @param byId the packs of the compose, by descriptor {@code pack_id}
     * @param placedAfter receives the {@code pack_id}s the pack is placed after
     * @return the faults of its edges
     */
    private static List<Refusal> dependenciesOf(
            PackUnderResolution pack, Map<String, PackUnderResolution> byId, List<String> placedAfter) {
        List<Refusal> faults = new ArrayList<>();
        for (PackDependency dependency : pack.descriptor().dependencies()) {
            String targetId = dependency.packId();
            if (targetId.equals(pack.descriptor().packId())) {
                faults.add(new Refusal(Failure.CYCLE, named(pack), label(pack) + " depends on itself"));
                continue;
            }
            PackUnderResolution target = byId.get(targetId);
            if (target == null) {
                faults.add(new Refusal(
                        Failure.UNKNOWN_DEPENDENCY,
                        named(pack),
                        label(pack) + " depends on pack `" + targetId + "`, which no configured pack declares"));
                continue;
            }
            if (!admits(dependency, target)) {
                faults.add(new Refusal(
                        Failure.OUT_OF_RANGE,
                        named(pack, target),
                        label(pack) + " requires " + label(target) + " in range `" + dependency.range()
                                + "`, but `" + target.descriptor().name() + "` is at version "
                                + target.descriptor().packVersion()));
            }
            levelViolation(pack, target, false).ifPresent(faults::add);
            placedAfter.add(targetId);
        }
        Optional<String> parent = pack.descriptor().parentPack();
        if (parent.isPresent()) {
            parentFault(pack, parent.get(), byId, placedAfter).ifPresent(faults::add);
        }
        return faults;
    }

    /** The fault of a pack's {@code parent_pack} edge, if any; the parent, when it is there, joins the order. */
    private static Optional<Refusal> parentFault(
            PackUnderResolution pack, String parent, Map<String, PackUnderResolution> byId, List<String> placedAfter) {
        if (parent.equals(pack.descriptor().packId())) {
            return Optional.of(
                    new Refusal(Failure.CYCLE, named(pack), label(pack) + " names itself as its parent_pack"));
        }
        PackUnderResolution parentPack = byId.get(parent);
        if (parentPack == null) {
            return Optional.of(new Refusal(
                    Failure.PARENT_UNRESOLVED,
                    named(pack),
                    label(pack) + " names parent_pack `" + parent + "`, which no configured pack declares"));
        }
        // The parent is an ordering and cycle edge as a dependency is: the child merges after the pack it extends.
        if (!placedAfter.contains(parent)) {
            placedAfter.add(parent);
        }
        return levelViolation(pack, parentPack, true);
    }

    private static boolean admits(PackDependency dependency, PackUnderResolution target) {
        return Pep440Range.admits(dependency.range(), target.descriptor().packVersion());
    }

    /**
     * A pack may take a dependency or a parent only at its own level or one nearer built-in (ADR-0048 decision 7); the
     * levels run built-in, org, project, and a pack at one level reaches no pack further from built-in than itself.
     */
    private static Optional<Refusal> levelViolation(
            PackUnderResolution pack, PackUnderResolution target, boolean parentEdge) {
        PackLevel own = pack.configured().level();
        PackLevel targetLevel = target.configured().level();
        if (rank(targetLevel) <= rank(own)) {
            return Optional.empty();
        }
        String edge = parentEdge
                ? " names " + label(target) + " at " + targetLevel.token() + " as its parent_pack"
                : " depends on " + label(target) + " at " + targetLevel.token();
        return Optional.of(new Refusal(
                Failure.LEVEL_VIOLATION,
                named(pack, target),
                label(pack) + " at " + own.token() + edge
                        + "; a pack may take a dependency or a parent only at its own level or one nearer built-in"));
    }

    private static int rank(PackLevel level) {
        return switch (level) {
            case BUILT_IN -> 0;
            case ORG -> 1;
            case PROJECT -> 2;
        };
    }

    /** The packs Kahn's pass placed, in merge order, and a {@link Failure#CYCLE} for each cycle it left behind. */
    private record Ordering(List<PackUnderResolution> order, List<Refusal> cycles) {}

    private static Ordering topologicalOrder(
            List<PackUnderResolution> packs,
            Map<String, PackUnderResolution> byId,
            Map<String, List<String>> dependenciesByPack) {
        Map<String, Integer> outstanding = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        dependenciesByPack.keySet().forEach(id -> dependents.put(id, new ArrayList<>()));
        dependenciesByPack.forEach((packId, dependencies) -> {
            outstanding.put(packId, dependencies.size());
            for (String dependency : dependencies) {
                dependents.computeIfAbsent(dependency, key -> new ArrayList<>()).add(packId);
            }
        });

        // A priority queue on the stable order, not a FIFO: a pack whose dependency is met late would otherwise land
        // after a dependency-free pack of a nearer level. A dependency points only to the pack's own level or one
        // nearer built-in, so taking the smallest ready pack keeps each level contiguous.
        PriorityQueue<PackUnderResolution> ready = new PriorityQueue<>(STABLE_ORDER);
        packs.stream()
                .filter(pack -> {
                    Integer count = outstanding.get(pack.descriptor().packId());
                    return count != null && count == 0;
                })
                .forEach(ready::add);
        List<PackUnderResolution> ordered = new ArrayList<>(packs.size());
        while (!ready.isEmpty()) {
            PackUnderResolution pack = ready.poll();
            ordered.add(pack);
            for (String dependent : dependents.getOrDefault(pack.descriptor().packId(), List.of())) {
                int remaining = outstanding.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(byId.get(dependent));
                }
            }
        }
        List<Refusal> cycles = new ArrayList<>();
        if (ordered.size() != packs.size()) {
            for (List<PackUnderResolution> cycle : cyclesOf(ordered, byId, dependenciesByPack)) {
                String path = cycle.stream()
                        .map(member -> "`" + member.descriptor().name() + "`")
                        .collect(java.util.stream.Collectors.joining(" -> "));
                cycles.add(new Refusal(
                        Failure.CYCLE,
                        named(cycle.toArray(PackUnderResolution[]::new)),
                        label(cycle.getFirst()) + " is on a dependency cycle: " + path + " -> `"
                                + cycle.getFirst().descriptor().name() + "`"));
            }
        }
        return new Ordering(List.copyOf(ordered), cycles);
    }

    /**
     * The cycles Kahn's pass left behind, each starting at a pack on it. What the pass leaves is the cycles and
     * everything downstream of them, and a leftover may be only downstream. Every leftover has an unplaced dependency,
     * so walking from one along its first unplaced dependency must repeat a pack, and the walk from the first repeat back
     * to it is a cycle. The walks start at each leftover in turn, in the order the packs were judged; a walk that reaches
     * a pack an earlier walk visited finds no new cycle, so the first cycle is the one the resolver has always refused
     * with. A walk follows one dependency only, so it can pass by a pack that lies on a cycle through another edge: each
     * such pack is then named by the shortest cycle through it ({@link #shortestCycleThrough}), after the walks, so
     * every pack on a cycle is named by at least one. A pack only downstream of a cycle lies on none, and is not named.
     */
    private static List<List<PackUnderResolution>> cyclesOf(
            List<PackUnderResolution> placed,
            Map<String, PackUnderResolution> byId,
            Map<String, List<String>> dependenciesByPack) {
        Set<String> done = new HashSet<>();
        placed.forEach(pack -> done.add(pack.descriptor().packId()));
        Set<String> visited = new HashSet<>();
        List<List<String>> cycles = new ArrayList<>();
        for (String start : dependenciesByPack.keySet()) {
            if (done.contains(start) || visited.contains(start)) {
                continue;
            }
            Set<String> walked = new LinkedHashSet<>();
            String current = start;
            while (!visited.contains(current) && walked.add(current)) {
                current = dependenciesByPack.getOrDefault(current, List.of()).stream()
                        .filter(id -> !done.contains(id))
                        .findFirst()
                        .orElseThrow();
            }
            if (walked.contains(current) && !visited.contains(current)) {
                List<String> path = new ArrayList<>(walked);
                cycles.add(List.copyOf(path.subList(path.indexOf(current), path.size())));
            }
            visited.addAll(walked);
        }
        Set<String> named = new HashSet<>();
        cycles.forEach(named::addAll);
        for (String pack : dependenciesByPack.keySet()) {
            if (done.contains(pack) || named.contains(pack)) {
                continue;
            }
            List<String> cycle = shortestCycleThrough(pack, done, dependenciesByPack);
            if (!cycle.isEmpty()) {
                cycles.add(cycle);
                named.addAll(cycle);
            }
        }
        return cycles.stream()
                .map(cycle -> cycle.stream()
                        .map(id -> Objects.requireNonNull(byId.get(id), "a leftover pack is in the compose"))
                        .toList())
                .toList();
    }

    /**
     * The shortest cycle through one unplaced pack, starting at it: a breadth-first search over the unplaced packs'
     * dependencies, each in its declared order, back to the pack.
     *
     * @return the {@code pack_id}s of the cycle, the pack first; empty when the pack is on no cycle
     */
    private static List<String> shortestCycleThrough(
            String pack, Set<String> done, Map<String, List<String>> dependenciesByPack) {
        Map<String, String> reachedFrom = new HashMap<>();
        ArrayDeque<String> pending = new ArrayDeque<>(List.of(pack));
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            for (String dependency : dependenciesByPack.getOrDefault(next, List.of())) {
                if (dependency.equals(pack)) {
                    List<String> cycle = new ArrayList<>();
                    for (String at = next; !at.equals(pack); ) {
                        cycle.addFirst(at);
                        at = Objects.requireNonNull(reachedFrom.get(at), "every pack the search reached has a source");
                    }
                    cycle.addFirst(pack);
                    return List.copyOf(cycle);
                }
                if (!done.contains(dependency) && !reachedFrom.containsKey(dependency)) {
                    reachedFrom.put(dependency, next);
                    pending.addLast(dependency);
                }
            }
        }
        return List.of();
    }

    // ---- how a refusal names a pack -----------------------------------------------------------------------------

    private static RefusalPack refusalPack(PackUnderResolution pack) {
        return new RefusalPack(
                pack.descriptorId(), pack.configured().id(), pack.descriptor().name());
    }

    /** The pack as a sentence names it: {@code `name` (pack_id)}. */
    private static String tag(PackUnderResolution pack) {
        return "`" + pack.descriptor().name() + "` (" + pack.descriptor().packId() + ")";
    }

    /** The pack as a sentence names it: {@code pack `name` (pack_id)}. */
    private static String label(PackUnderResolution pack) {
        return "pack " + tag(pack);
    }

    private static String labels(List<PackUnderResolution> packs) {
        return "packs "
                + packs.stream()
                        .map(PackCompositionResolver::tag)
                        .collect(java.util.stream.Collectors.joining(" and "));
    }

    private static String nameOf(String packId, Map<String, PackUnderResolution> byId) {
        PackUnderResolution pack = byId.get(packId);
        return pack == null ? packId : pack.descriptor().name();
    }

    private static List<RefusalPack> concat(List<RefusalPack> first, List<RefusalPack> second) {
        List<RefusalPack> both = new ArrayList<>(first);
        both.addAll(second);
        return both;
    }

    private static List<RefusalPack> named(PackUnderResolution... packs) {
        return Arrays.stream(packs).map(PackCompositionResolver::refusalPack).toList();
    }
}
