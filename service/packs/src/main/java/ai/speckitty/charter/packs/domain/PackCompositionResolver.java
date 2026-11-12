package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.Pep440Range;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
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
     * @param definedArtifacts the artifacts the pack itself holds, as bare URNs; empty when it holds none
     */
    public record PackUnderResolution(
            ConfiguredPack configured,
            PackDescriptor descriptor,
            List<String> changedArtifacts,
            List<String> definedArtifacts) {

        /**
         * Checks the fields.
         *
         * @param configured the configured pack
         * @param descriptor its descriptor
         * @param changedArtifacts the artifacts it changes
         * @param definedArtifacts the artifacts it holds
         */
        public PackUnderResolution {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(changedArtifacts, "changedArtifacts");
            Objects.requireNonNull(definedArtifacts, "definedArtifacts");
            changedArtifacts = List.copyOf(changedArtifacts);
            definedArtifacts = List.copyOf(definedArtifacts);
        }

        /**
         * Whether the pack holds the artifact a bare URN names: the one answer, from the list the pack was read with.
         * A reference of a kind the pack holds no artifacts of is held by no pack, so it fails open to nothing.
         *
         * @param urn the bare URN text, {@code <kind>:<id>}
         * @return {@code true} when the pack's held artifacts name it
         */
        public boolean holds(String urn) {
            return definedArtifacts.contains(urn);
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
        /** Two siblings at one level change or define one artifact and no pack of the compose settles it with an {@code overrides} edge. */
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
         * @return its descriptor {@code pack_id}
         */
        public String packId() {
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
         * What the caller needs to read; never null.
         *
         * @return the detail
         */
        public String detail() {
            return Objects.requireNonNull(getMessage(), "detail");
        }
    }

    /**
     * Resolves the compose.
     *
     * @param packs the configured packs with their descriptors
     * @return the closure, dependencies first, each pack once
     * @throws Refusal when the graph cannot be served
     * @throws IllegalStateException when a descriptor is not well formed: the resolver decides the graph, not a
     *     descriptor, so a pack whose {@code pack_version} is no PEP 440 version or whose {@code depends_on} range is
     *     no specifier set is {@code pack_unreadable} at the read ({@link PackPolicy#descriptorFindings}, the one
     *     predicate), and never reaches here
     */
    public static List<PackUnderResolution> resolve(List<PackUnderResolution> packs) {
        Objects.requireNonNull(packs, "packs");
        for (PackUnderResolution pack : packs) {
            List<PackFinding> findings =
                    PackPolicy.descriptorFindings(pack.descriptor().name(), pack.descriptor());
            if (!findings.isEmpty()) {
                throw new IllegalStateException("the resolver takes only well-formed descriptors, and "
                        + findings.getFirst().detail());
            }
        }
        List<PackUnderResolution> ordered = new ArrayList<>(packs);
        ordered.sort(STABLE_ORDER);

        Map<String, PackUnderResolution> byId = indexById(ordered);
        indexByName(ordered);
        refuseDisjointRanges(ordered, byId);
        Map<String, List<String>> edges = new LinkedHashMap<>();
        for (PackUnderResolution pack : ordered) {
            edges.put(pack.descriptor().packId(), dependenciesOf(pack, byId));
        }
        refuseSiblingContests(ordered);
        return topologicalOrder(ordered, byId, edges);
    }

    /**
     * Resolves the compose rooted at a set of configured packs (#360): every check {@link #resolve} runs, over the
     * {@linkplain #compositionClosure composition closure} of the roots only, so a pack outside it is never judged. A
     * duplicate {@code pack_id} or {@code name} outside the closure does not refuse; one inside it does, naming both
     * holders, because every pack declaring an id of the closure is selected. A {@code depends_on} or
     * {@code parent_pack} target no available pack declares stays missing, and {@link #resolve} refuses it as it
     * always has. With every available pack a root, the answer and every refusal are those of {@link #resolve}.
     *
     * @param available the packs the compose may draw on, each with its descriptor
     * @param roots the configured identifiers of the packs the compose is rooted at
     * @return the closure of the roots, dependencies first, each pack once
     * @throws Refusal when the closure's graph cannot be served
     * @throws IllegalArgumentException when a root names no available pack: the caller chose the roots, so it is the
     *     caller's error, not a refusal of the graph
     */
    public static List<PackUnderResolution> resolveRooted(
            List<PackUnderResolution> available, Collection<PackIdentifier> roots) {
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
        Set<String> closure = compositionClosure(rootPackIds, firstByPackId);
        return resolve(available.stream()
                .filter(pack -> closure.contains(pack.descriptor().packId()))
                .toList());
    }

    /**
     * The composition closure of a set of packs: the roots and every pack they reach over {@code depends_on} and
     * {@code parent_pack}, by descriptor {@code pack_id}, in first-seen order. It is the one closure rule of a rooted
     * compose. A target no pack of {@code byPackId} declares is recorded but not descended from: the walk tolerates it,
     * and {@link #resolve} refuses it. A cycle terminates, since each id is walked once.
     *
     * @param rootPackIds the descriptor {@code pack_id}s of the roots
     * @param byPackId the packs the walk may descend into, by descriptor {@code pack_id}
     * @return the {@code pack_id}s of the closure, missing targets included
     */
    public static Set<String> compositionClosure(
            Collection<String> rootPackIds, Map<String, PackUnderResolution> byPackId) {
        Objects.requireNonNull(rootPackIds, "rootPackIds");
        Objects.requireNonNull(byPackId, "byPackId");
        Set<String> reached = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(rootPackIds);
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            PackUnderResolution pack = byPackId.get(next);
            if (reached.add(next) && pack != null) {
                pending.addAll(successorsOf(pack));
            }
        }
        return reached;
    }

    /** The packs one pack builds on: its {@code depends_on} targets, then its {@code parent_pack}, by {@code pack_id}. */
    private static List<String> successorsOf(PackUnderResolution pack) {
        List<String> successors = new ArrayList<>();
        pack.descriptor().dependencies().forEach(dependency -> successors.add(dependency.packId()));
        pack.descriptor().parentPack().ifPresent(successors::add);
        return successors;
    }

    /**
     * One reference as the compose resolves it: the reference an artifact writes, and the pack that holds what it
     * names. The offering read builds these from the references of every pack's graphs and checks them with
     * {@link #checkReferences} when the compose resolves; the artifact reads build them for one reference at a time.
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
     * the holder is a legal one is {@link #checkReferences}.
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
     * One reference with the pack that holds what it names ({@link #holderOf}), ready for {@link #checkReferences}.
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
     * @throws Refusal with {@link Failure#REFERENCE_UNQUALIFIED} when a bare reference resolves only in another
     *     pack, with {@link Failure#REFERENCE_PACK_MISSING} when a qualified reference names a pack outside the
     *     referring pack's closure, or with {@link Failure#REFERENCE_ARTIFACT_MISSING} when it names a pack of the
     *     closure that does not hold the artifact
     */
    public static void checkReferences(
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
        List<RefusalPack> referringPack = List.of(
                referring == null
                        ? new RefusalPack(referringPackId, referringPackId, referringPackId)
                        : refusalPack(referring));
        String referringLabel = referring == null ? "pack `" + referringPackId + "`" : label(referring);
        for (ResolvedReference resolved : references) {
            PackQualifiedReference reference = resolved.reference();
            if (reference.qualified()) {
                checkQualifiedReference(reference, byName, closure, referringPack, referringLabel);
            } else if (resolved.holdingPackId()
                    .filter(id -> !id.equals(referringPackId))
                    .isPresent()) {
                String holdingId = resolved.holdingPackId().orElseThrow();
                PackUnderResolution holding = byId.get(holdingId);
                List<RefusalPack> holder = holding == null ? List.of() : named(holding);
                String holdingLabel = holding == null ? "pack `" + holdingId + "`" : label(holding);
                throw new Refusal(
                        Failure.REFERENCE_UNQUALIFIED,
                        concat(referringPack, holder),
                        referringLabel + " writes the bare reference `" + reference + "`, which resolves only in "
                                + holdingLabel + "; a cross-pack reference is written pack-qualified");
            }
        }
    }

    /**
     * Refuses a pack-qualified reference whose pack the compose does not hold, whose pack lies outside the referring
     * pack's {@code depends_on} closure, or whose pack does not hold the graph-authored artifact it names.
     */
    private static void checkQualifiedReference(
            PackQualifiedReference reference,
            Map<String, PackUnderResolution> byName,
            Set<String> closure,
            List<RefusalPack> referringPack,
            String referringLabel) {
        String missing = reference.packName().orElseThrow();
        PackUnderResolution target = byName.get(missing);
        if (target == null) {
            throw new Refusal(
                    Failure.REFERENCE_PACK_MISSING,
                    referringPack,
                    referringLabel + " writes the reference `" + reference + "`, but the compose holds no pack"
                            + " named `" + missing + "`");
        }
        if (!closure.contains(target.descriptor().packId())) {
            throw new Refusal(
                    Failure.REFERENCE_PACK_MISSING,
                    concat(referringPack, named(target)),
                    referringLabel + " writes the reference `" + reference + "`, but " + label(target)
                            + " is outside its depends_on closure");
        }
        Optional<Urn> urn = reference.asUrn();
        if (urn.isPresent()
                && urn.get().kind().authoredInGraphs()
                && urn.get().kind().artifactKind().isPresent()
                && !target.holds(urn.get().toString())) {
            throw new Refusal(
                    Failure.REFERENCE_ARTIFACT_MISSING,
                    concat(referringPack, named(target)),
                    referringLabel + " writes the reference `" + reference + "`, but " + label(target)
                            + " does not hold `" + urn.get() + "`");
        }
    }

    /**
     * The pack and every pack it reaches through {@code depends_on} only, by {@code pack_id}: the packs a qualified
     * reference may name (FR-015), and the packs that may hold the step a step enhancement names (C37,
     * {@link EnhancementCompose#targetHeld}). It is not the {@linkplain #compositionClosure composition closure}, which
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
     * Refuses two packs that ask one pack for ranges no version can satisfy together (ADR-0053 decision 7; the operator
     * ruling of 2026-11-03): the compose holds one copy of the depended-on pack, so two declarations whose ranges
     * share no version, including ranges that only touch ({@code <2} and {@code >=2}), cannot both be honoured. The
     * refusal names both declaring packs and both ranges, and it comes before the held version is checked against
     * either range. A range pair that overlaps, even only partly, is fine and is not reported. A dependency on an
     * unknown pack is left to {@link #dependenciesOf}, which refuses it with its own reason.
     */
    private static void refuseDisjointRanges(List<PackUnderResolution> ordered, Map<String, PackUnderResolution> byId) {
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
                        throw new Refusal(
                                Failure.OUT_OF_RANGE,
                                named(second.pack(), first.pack()),
                                "packs `" + first.pack().descriptor().name() + "` ("
                                        + first.pack().descriptor().packId() + ") and `"
                                        + second.pack().descriptor().name() + "` ("
                                        + second.pack().descriptor().packId() + ") require pack `"
                                        + nameOf(asked.getKey(), byId) + "` (" + asked.getKey() + ") in ranges `"
                                        + first.range() + "` and `" + second.range()
                                        + "`, which no version satisfies together");
                    }
                }
            }
        }
    }

    private record Declared(PackUnderResolution pack, String range) {}

    /**
     * Refuses two packs of one configured level that contest an artifact, unless a pack of the compose declares an
     * {@code overrides} edge settling it (FR-012; ADR-0048 decision 9, ADR-0053 decision 5): a conflict must be a
     * decision, not load order. Two packs contest an artifact when both change it (an
     * {@code overrides} relation on it) or when both define it (each holds an artifact with that id); a definer and an
     * enhancer, or two enhancers, contest nothing (A14, decision {@code DM-01M6RA92BS6GGCYTRCFQDYYY5S}, rule C14), and
     * a settlement names exactly one winner (rule C15). An edge settles
     * a contest only when its {@code member} is one of the packs contesting that artifact at that level; an edge
     * naming any other member settles nothing and the refusal says so. The edges of the whole compose are read
     * together, so a contest is settled only when they agree on one winner: two packs naming different winners leave
     * the winner to load order, and the refusal says so. Packs at different levels are no siblings —
     * the layer merge across levels composes them (ADR-0053 decision 6) — and the pack named is the second contestant
     * in the stable order, so the refusal is deterministic.
     *
     * <p>Artifacts are compared by their bare URN: a {@code <pack>::} qualifier on a reference or an
     * {@code overrides.artifact} names where it is read, not which artifact it is.
     */
    private static void refuseSiblingContests(List<PackUnderResolution> ordered) {
        refuseContests(ordered, PackUnderResolution::changedArtifacts, "change");
        refuseContests(ordered, PackCompositionResolver::contestableDefinitions, "define");
    }

    /**
     * What a pack defines that two siblings can contest: everything but a mission type's steps. A step
     * {@code action:<M>/<step>} is part of its mission type {@code M}, so siblings that define one step define {@code M}
     * too, and that contest (or the {@code overrides} edge settling it) decides; a step adds no contest of its own.
     */
    private static List<String> contestableDefinitions(PackUnderResolution pack) {
        return pack.definedArtifacts().stream()
                .filter(urn -> Urn.parse(bare(urn))
                        .filter(parsed -> parsed.kind() == NodeKind.ACTION)
                        .isEmpty())
                .toList();
    }

    private static void refuseContests(
            List<PackUnderResolution> ordered, Function<PackUnderResolution, List<String>> artifactsOf, String verb) {
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
                List<String> ids = siblings.stream()
                        .map(pack -> pack.descriptor().packId())
                        .toList();
                Set<String> staleMembers = new LinkedHashSet<>();
                Set<String> winners = new LinkedHashSet<>();
                for (PackUnderResolution pack : ordered) {
                    for (PackOverride override : pack.descriptor().overrides()) {
                        if (!bare(override.artifact()).equals(artifact)) {
                            continue;
                        }
                        if (ids.contains(override.member())) {
                            winners.add(override.member());
                        } else {
                            staleMembers.add(override.member());
                        }
                    }
                }
                if (winners.size() == 1) {
                    continue;
                }
                String reason = winners.size() > 1
                        ? "overrides edges settling it name different winners, " + String.join(", ", winners)
                        : staleMembers.isEmpty()
                                ? "no pack of the compose declares an overrides edge settling it"
                                : "an overrides edge settling it names " + String.join(", ", staleMembers)
                                        + ", which is not a contestant";
                List<PackUnderResolution> named = new ArrayList<>(siblings);
                named.addFirst(named.remove(1));
                throw new Refusal(
                        Failure.SIBLING_CONFLICT,
                        named(named.toArray(PackUnderResolution[]::new)),
                        labels(siblings) + " at "
                                + siblings.getFirst().configured().level().token()
                                + " both " + verb + " the artifact `" + artifact + "`, and " + reason);
            }
        }
    }

    /**
     * Refuses two packs of one configured level that hold one preset id and are not related by {@code parent_pack}
     * (the operator ruling of 2026-11-06, as {@link #refuseSiblingContests} refuses two siblings that contest an
     * artifact): neither would win by anything but load order. A child and a pack on its {@code parent_pack} line are
     * related, the child merging after the parent it extends, so its copy takes the place of the parent's. A preset id
     * held at different levels is no contest: the layer merge applies (ADR-0053 decision 6, ADR-0059 D3), the pack
     * nearer the project winning. Presets are not artifacts, so no {@code overrides} edge settles a contest.
     *
     * @param ordered the packs of one compose, in merge order
     * @param presetIdsOf the ids of the presets a pack holds
     * @throws Refusal {@link Failure#SIBLING_CONFLICT} naming the second contestant first, as the artifact contest does
     */
    public static void refusePresetContests(
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
        for (Map.Entry<String, List<PackUnderResolution>> held : holders.entrySet()) {
            List<PackUnderResolution> packs = held.getValue();
            for (int later = 1; later < packs.size(); later++) {
                for (int earlier = 0; earlier < later; earlier++) {
                    PackUnderResolution first = packs.get(earlier);
                    PackUnderResolution second = packs.get(later);
                    if (first.configured().level() == second.configured().level() && !related(first, second, byId)) {
                        throw new Refusal(
                                Failure.SIBLING_CONFLICT,
                                named(second, first),
                                labels(List.of(first, second)) + " at "
                                        + first.configured().level().token() + " both hold the preset `"
                                        + held.getKey() + "`, and neither extends the other by parent_pack");
                    }
                }
            }
        }
    }

    /**
     * Refuses a preset item or a policy lock that names an artifact of the closure only by case (the operator ruling of
     * 2026-11-06): {@code directive_001} where the closure holds {@code DIRECTIVE_001}. Ids are matched exactly, so
     * such a mention would silently govern nothing, and a deny entry the author meant to be refused would pass. A
     * mention of an id no pack of the closure holds is no mistake this check can tell from a pack not yet configured:
     * it is left alone, and a lock on it changes no write. The refusal is {@link Failure#REFERENCE_ARTIFACT_MISSING}:
     * the nearest existing code, a pack's reference to an artifact that the closure does not hold as written.
     *
     * @param ordered the packs of one compose
     * @param mentionsOf the URNs, as written, that a pack's presets and policy file name
     * @throws Refusal naming the mentioning pack first, then the pack that holds the id
     */
    public static void refuseCaseOnlyMatches(
            List<PackUnderResolution> ordered, Function<PackUnderResolution, Collection<String>> mentionsOf) {
        Objects.requireNonNull(ordered, "ordered");
        Objects.requireNonNull(mentionsOf, "mentionsOf");
        Set<String> held = new HashSet<>();
        Map<String, PackUnderResolution> holderByFolded = new LinkedHashMap<>();
        for (PackUnderResolution pack : ordered) {
            for (String urn : pack.definedArtifacts()) {
                held.add(urn);
                holderByFolded.putIfAbsent(urn.toLowerCase(java.util.Locale.ROOT), pack);
            }
        }
        for (PackUnderResolution pack : ordered) {
            for (String mentioned : mentionsOf.apply(pack)) {
                PackUnderResolution holder = holderByFolded.get(mentioned.toLowerCase(java.util.Locale.ROOT));
                if (holder == null || held.contains(mentioned)) {
                    continue;
                }
                String declared = holder.definedArtifacts().stream()
                        .filter(urn -> urn.equalsIgnoreCase(mentioned))
                        .findFirst()
                        .orElse(mentioned);
                throw new Refusal(
                        Failure.REFERENCE_ARTIFACT_MISSING,
                        named(pack, holder),
                        label(pack) + " names `" + mentioned + "`, but " + label(holder) + " holds `" + declared
                                + "`: ids differ only by case and are matched exactly");
            }
        }
    }

    /** Whether one pack is on the other's {@code parent_pack} line. A cycle was refused before, so the walk ends. */
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

    private static void indexByName(List<PackUnderResolution> packs) {
        Map<String, PackUnderResolution> byName = new HashMap<>();
        for (PackUnderResolution pack : packs) {
            String name = pack.descriptor().name();
            PackUnderResolution previous = byName.putIfAbsent(name, pack);
            if (previous != null) {
                throw new Refusal(
                        Failure.NAME_CONFLICT,
                        named(pack, previous),
                        labels(List.of(previous, pack)) + " both declare the name `" + name + "`");
            }
        }
    }

    /**
     * The packs one pack must be placed after: its {@code depends_on} targets and its {@code parent_pack}, each checked
     * as the compose checks it. Both are edges of the order and of the cycle check, so a parent cycle, a pack that
     * names itself as parent included, is {@link Failure#CYCLE}.
     */
    private static List<String> dependenciesOf(PackUnderResolution pack, Map<String, PackUnderResolution> byId) {
        List<String> dependencies = new ArrayList<>();
        for (PackDependency dependency : pack.descriptor().dependencies()) {
            String targetId = dependency.packId();
            if (targetId.equals(pack.descriptor().packId())) {
                throw new Refusal(Failure.CYCLE, named(pack), label(pack) + " depends on itself");
            }
            PackUnderResolution target = byId.get(targetId);
            if (target == null) {
                throw new Refusal(
                        Failure.UNKNOWN_DEPENDENCY,
                        named(pack),
                        label(pack) + " depends on pack `" + targetId + "`, which no configured pack declares");
            }
            if (!admits(dependency, target)) {
                throw new Refusal(
                        Failure.OUT_OF_RANGE,
                        named(pack, target),
                        label(pack) + " requires " + label(target) + " in range `" + dependency.range()
                                + "`, but `" + target.descriptor().name() + "` is at version "
                                + target.descriptor().packVersion());
            }
            requireLevel(pack, target, false);
            dependencies.add(targetId);
        }
        pack.descriptor().parentPack().ifPresent(parent -> {
            if (parent.equals(pack.descriptor().packId())) {
                throw new Refusal(Failure.CYCLE, named(pack), label(pack) + " names itself as its parent_pack");
            }
            PackUnderResolution parentPack = byId.get(parent);
            if (parentPack == null) {
                throw new Refusal(
                        Failure.PARENT_UNRESOLVED,
                        named(pack),
                        label(pack) + " names parent_pack `" + parent + "`, which no configured pack declares");
            }
            requireLevel(pack, parentPack, true);
            // The parent is an ordering and cycle edge as a dependency is: the child merges after the pack it extends.
            if (!dependencies.contains(parent)) {
                dependencies.add(parent);
            }
        });
        return dependencies;
    }

    private static boolean admits(PackDependency dependency, PackUnderResolution target) {
        return Pep440Range.admits(dependency.range(), target.descriptor().packVersion());
    }

    /**
     * A pack may take a dependency or a parent only at its own level or one nearer built-in (ADR-0048 decision 7); the
     * levels run built-in, org, project, and a pack at one level reaches no pack further from built-in than itself.
     */
    private static void requireLevel(PackUnderResolution pack, PackUnderResolution target, boolean parentEdge) {
        PackLevel own = pack.configured().level();
        PackLevel targetLevel = target.configured().level();
        if (rank(targetLevel) > rank(own)) {
            String edge = parentEdge
                    ? " names " + label(target) + " at " + targetLevel.token() + " as its parent_pack"
                    : " depends on " + label(target) + " at " + targetLevel.token();
            throw new Refusal(
                    Failure.LEVEL_VIOLATION,
                    named(pack, target),
                    label(pack) + " at " + own.token() + edge
                            + "; a pack may take a dependency or a parent only at its own level or one nearer built-in");
        }
    }

    private static int rank(PackLevel level) {
        return switch (level) {
            case BUILT_IN -> 0;
            case ORG -> 1;
            case PROJECT -> 2;
        };
    }

    private static List<PackUnderResolution> topologicalOrder(
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
        if (ordered.size() != packs.size()) {
            List<PackUnderResolution> cycle = cycleOf(ordered, byId, dependenciesByPack);
            String path = cycle.stream()
                    .map(member -> "`" + member.descriptor().name() + "`")
                    .collect(java.util.stream.Collectors.joining(" -> "));
            throw new Refusal(
                    Failure.CYCLE,
                    named(cycle.toArray(PackUnderResolution[]::new)),
                    label(cycle.getFirst()) + " is on a dependency cycle: " + path + " -> `"
                            + cycle.getFirst().descriptor().name() + "`");
        }
        return List.copyOf(ordered);
    }

    /**
     * The packs of one cycle, starting at a pack on it. What Kahn's pass leaves behind is the cycles and everything
     * downstream of them, and the first leftover in the stable order may be only downstream. Every leftover has an
     * unplaced dependency, so walking from one along unplaced dependencies must repeat a pack, and the walk from the
     * first repeat back to it is the cycle.
     */
    private static List<PackUnderResolution> cycleOf(
            List<PackUnderResolution> placed,
            Map<String, PackUnderResolution> byId,
            Map<String, List<String>> dependenciesByPack) {
        Set<String> done = new LinkedHashSet<>();
        placed.forEach(pack -> done.add(pack.descriptor().packId()));
        String current = dependenciesByPack.keySet().stream()
                .filter(id -> !done.contains(id))
                .findFirst()
                .orElseThrow();
        Set<String> walked = new LinkedHashSet<>();
        while (walked.add(current)) {
            current = dependenciesByPack.getOrDefault(current, List.of()).stream()
                    .filter(id -> !done.contains(id))
                    .findFirst()
                    .orElseThrow();
        }
        List<String> path = new ArrayList<>(walked);
        return path.subList(path.indexOf(current), path.size()).stream()
                .map(id -> Objects.requireNonNull(byId.get(id), "a leftover pack is in the compose"))
                .toList();
    }

    // ---- how a refusal names a pack -----------------------------------------------------------------------------

    private static RefusalPack refusalPack(PackUnderResolution pack) {
        return new RefusalPack(
                pack.descriptor().packId(),
                pack.configured().id().value(),
                pack.descriptor().name());
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
