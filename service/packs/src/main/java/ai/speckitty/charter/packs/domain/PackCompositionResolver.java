package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.Pep440Range;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Resolves one compose: the configured packs plus their descriptors become an ordered closure, dependencies before the
 * packs that need them. One compose is one {@code depends_on} closure at one level (ADR-0048 decision 6); a shared
 * dependency appears once; a broken graph is refused with the pack that broke it named (ADR-0048 decision 10,
 * ADR-0053 decisions 4, 7, 8 and 9).
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
     * @param refinedArtifacts the artifacts its graph documents say it changes (an {@code enhances} or
     *     {@code overrides} relation on the artifact); empty when they name none
     */
    public record PackUnderResolution(
            ConfiguredPack configured, PackDescriptor descriptor, List<String> refinedArtifacts) {

        /**
         * Checks the fields.
         *
         * @param configured the configured pack
         * @param descriptor its descriptor
         * @param refinedArtifacts the artifacts it changes
         */
        public PackUnderResolution {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(refinedArtifacts, "refinedArtifacts");
            refinedArtifacts = List.copyOf(refinedArtifacts);
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
        CYCLE,
        /** A dependency names a pack id no configured pack declares. */
        UNKNOWN_DEPENDENCY,
        /** The held version fits no declared range for that pack. */
        OUT_OF_RANGE,
        /** A {@code parent_pack} names an id no configured pack declares. */
        PARENT_UNRESOLVED,
        /** A pack depends across a level boundary it may not cross. */
        LEVEL_VIOLATION,
        /** Two packs in the compose declare the same name. */
        NAME_CONFLICT,
        /** Two configured packs declare the same {@code pack_id}. */
        DUPLICATE_ID,
        /** Two siblings at one level change one artifact and no pack of the compose settles it with an {@code overrides} edge. */
        SIBLING_CONFLICT,
        /** A bare reference resolves only in another pack of the compose; the refusal names the holding pack. */
        REFERENCE_UNQUALIFIED,
        /** A pack-qualified reference names a pack outside the compose's closure; the refusal names the missing pack. */
        REFERENCE_PACK_MISSING
    }

    /**
     * A refused compose: the reason and the pack that broke it.
     */
    public static final class Refusal extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** Why the compose cannot be served. */
        private final Failure failure;

        /** The pack that broke the graph. */
        private final String packId;

        /**
         * A refusal.
         *
         * @param failure why the compose cannot be served
         * @param packId the pack that broke the graph
         * @param detail what the caller needs to read
         */
        public Refusal(Failure failure, String packId, String detail) {
            super(detail);
            this.failure = Objects.requireNonNull(failure, "failure");
            this.packId = Objects.requireNonNull(packId, "packId");
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
         * The pack that broke the graph.
         *
         * @return its {@code pack_id}
         */
        public String packId() {
            return packId;
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
     */
    public static List<PackUnderResolution> resolve(List<PackUnderResolution> packs) {
        Objects.requireNonNull(packs, "packs");
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
     * One reference as the compose resolves it: the reference an artifact writes, and the pack that holds what it
     * names. The offering read builds these from the references of every pack's graphs and checks them with
     * {@link #checkReferences} when the compose resolves; the artifact reads build them for one reference at a time.
     *
     * @param reference the reference as the artifact writes it
     * @param holdingPackId the {@code pack_id} of the pack that holds what the reference names
     */
    public record ResolvedReference(PackQualifiedReference reference, String holdingPackId) {

        /**
         * Checks the fields.
         *
         * @param reference the reference as written
         * @param holdingPackId the pack that holds what it names
         */
        public ResolvedReference {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(holdingPackId, "holdingPackId");
        }
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
     *     pack, or with {@link Failure#REFERENCE_PACK_MISSING} when a qualified reference names a pack outside the
     *     referring pack's closure
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
        Set<String> closure = closureOf(referringPackId, byId);
        for (ResolvedReference resolved : references) {
            PackQualifiedReference reference = resolved.reference();
            if (reference.qualified()) {
                String named = reference.packName().orElseThrow();
                PackUnderResolution target = byName.get(named);
                if (target == null) {
                    throw new Refusal(
                            Failure.REFERENCE_PACK_MISSING,
                            named,
                            "the reference " + reference + " names pack " + named
                                    + ", which the compose does not hold");
                }
                if (!closure.contains(target.descriptor().packId())) {
                    throw new Refusal(
                            Failure.REFERENCE_PACK_MISSING,
                            named,
                            "the reference " + reference + " of pack " + referringPackId + " names pack " + named
                                    + ", which is outside the depends_on closure of pack " + referringPackId);
                }
            } else if (!resolved.holdingPackId().equals(referringPackId)) {
                PackUnderResolution holding = byId.get(resolved.holdingPackId());
                String holdingName = holding == null
                        ? resolved.holdingPackId()
                        : holding.descriptor().name();
                throw new Refusal(
                        Failure.REFERENCE_UNQUALIFIED,
                        resolved.holdingPackId(),
                        "the bare reference " + reference + " of pack " + referringPackId
                                + " resolves only in pack " + holdingName
                                + "; a cross-pack reference is written pack-qualified");
            }
        }
    }

    /** The pack and every pack it reaches through {@code depends_on}, by {@code pack_id}. */
    private static Set<String> closureOf(String packId, Map<String, PackUnderResolution> byId) {
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
     * unknown pack or with a malformed range is left to {@link #dependenciesOf}, which refuses it with its own reason.
     */
    private static void refuseDisjointRanges(List<PackUnderResolution> ordered, Map<String, PackUnderResolution> byId) {
        Map<String, List<Declared>> byTarget = new LinkedHashMap<>();
        for (PackUnderResolution pack : ordered) {
            for (PackDependency dependency : pack.descriptor().dependencies()) {
                if (byId.containsKey(dependency.packId()) && Pep440Range.isWellFormed(dependency.range())) {
                    byTarget.computeIfAbsent(dependency.packId(), key -> new ArrayList<>())
                            .add(new Declared(pack.descriptor().packId(), dependency.range()));
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
                                second.packId(),
                                "packs " + first.packId() + " and " + second.packId() + " require " + asked.getKey()
                                        + " in ranges " + first.range() + " and " + second.range()
                                        + ", which no version satisfies together");
                    }
                }
            }
        }
    }

    private record Declared(String packId, String range) {}

    /**
     * Refuses two packs of one configured level that change the same artifact, unless a pack of the compose declares
     * an {@code overrides} edge settling that artifact (FR-012; ADR-0048 decision 9, ADR-0053 decision 5): a
     * conflict must be a decision, not load order. An edge settles a contest only when its {@code member} is one of
     * the packs contesting that artifact at that level; an edge naming any other member settles nothing and the
     * refusal says so. Packs at different levels are no siblings — the layer merge across levels composes them
     * (ADR-0048 decision 6) — and the pack named is the second refiner in the stable order, so the refusal is
     * deterministic.
     */
    private static void refuseSiblingContests(List<PackUnderResolution> ordered) {
        Map<String, List<PackUnderResolution>> refiners = new LinkedHashMap<>();
        for (PackUnderResolution pack : ordered) {
            for (String artifact : pack.refinedArtifacts()) {
                refiners.computeIfAbsent(artifact, key -> new ArrayList<>()).add(pack);
            }
        }
        for (Map.Entry<String, List<PackUnderResolution>> contest : refiners.entrySet()) {
            String artifact = contest.getKey();
            Map<PackLevel, List<PackUnderResolution>> byLevel = new EnumMap<>(PackLevel.class);
            for (PackUnderResolution refiner : contest.getValue()) {
                byLevel.computeIfAbsent(refiner.configured().level(), level -> new ArrayList<>())
                        .add(refiner);
            }
            for (List<PackUnderResolution> siblings : byLevel.values()) {
                if (siblings.size() < 2) {
                    continue;
                }
                List<String> ids = siblings.stream()
                        .map(pack -> pack.descriptor().packId())
                        .toList();
                Set<String> staleMembers = new LinkedHashSet<>();
                boolean settled = false;
                for (PackUnderResolution pack : ordered) {
                    for (PackOverride override : pack.descriptor().overrides()) {
                        if (!override.artifact().equals(artifact)) {
                            continue;
                        }
                        if (ids.contains(override.member())) {
                            settled = true;
                        } else {
                            staleMembers.add(override.member());
                        }
                    }
                }
                if (settled) {
                    continue;
                }
                String reason = staleMembers.isEmpty()
                        ? "no pack of the compose declares an overrides edge settling it"
                        : "an overrides edge settling it names " + String.join(", ", staleMembers)
                                + ", which is not a contestant";
                throw new Refusal(
                        Failure.SIBLING_CONFLICT,
                        ids.get(1),
                        "packs " + String.join(" and ", ids) + " at "
                                + siblings.getFirst().configured().level().token()
                                + " both change the artifact " + artifact + ", and " + reason);
            }
        }
    }

    private static Map<String, PackUnderResolution> indexById(List<PackUnderResolution> packs) {
        Map<String, PackUnderResolution> byId = new HashMap<>();
        for (PackUnderResolution pack : packs) {
            PackUnderResolution previous = byId.putIfAbsent(pack.descriptor().packId(), pack);
            if (previous != null) {
                throw new Refusal(
                        Failure.DUPLICATE_ID,
                        pack.descriptor().packId(),
                        "pack_id " + pack.descriptor().packId() + " is declared by two configured packs");
            }
        }
        return byId;
    }

    private static void indexByName(List<PackUnderResolution> packs) {
        Map<String, String> byName = new HashMap<>();
        for (PackUnderResolution pack : packs) {
            String name = pack.descriptor().name();
            String previous = byName.putIfAbsent(name, pack.descriptor().packId());
            if (previous != null) {
                throw new Refusal(
                        Failure.NAME_CONFLICT,
                        pack.descriptor().packId(),
                        "pack name " + name + " is declared by packs " + previous + " and "
                                + pack.descriptor().packId());
            }
        }
    }

    private static List<String> dependenciesOf(PackUnderResolution pack, Map<String, PackUnderResolution> byId) {
        List<String> dependencies = new ArrayList<>();
        for (PackDependency dependency : pack.descriptor().dependencies()) {
            String targetId = dependency.packId();
            if (targetId.equals(pack.descriptor().packId())) {
                throw new Refusal(
                        Failure.CYCLE,
                        pack.descriptor().packId(),
                        "pack " + pack.descriptor().packId() + " depends on itself");
            }
            PackUnderResolution target = byId.get(targetId);
            if (target == null) {
                throw new Refusal(
                        Failure.UNKNOWN_DEPENDENCY,
                        pack.descriptor().packId(),
                        "pack " + pack.descriptor().packId() + " depends on " + targetId
                                + ", which no configured pack declares");
            }
            if (!Pep440Range.isVersion(target.descriptor().packVersion())) {
                throw new Refusal(
                        Failure.OUT_OF_RANGE,
                        targetId,
                        "pack " + targetId + " has pack_version "
                                + target.descriptor().packVersion()
                                + ", which is not a PEP 440 version, so the range " + dependency.range()
                                + " of pack " + pack.descriptor().packId() + " cannot be decided against it");
            }
            if (!admits(pack, dependency, target)) {
                throw new Refusal(
                        Failure.OUT_OF_RANGE,
                        pack.descriptor().packId(),
                        "pack " + pack.descriptor().packId() + " requires " + targetId + " in range "
                                + dependency.range() + ", and the held version is "
                                + target.descriptor().packVersion());
            }
            requireLevel(pack, target);
            dependencies.add(targetId);
        }
        pack.descriptor().parentPack().ifPresent(parent -> {
            if (!byId.containsKey(parent)) {
                throw new Refusal(
                        Failure.PARENT_UNRESOLVED,
                        pack.descriptor().packId(),
                        "pack " + pack.descriptor().packId() + " names parent_pack " + parent
                                + ", which no configured pack declares");
            }
        });
        return dependencies;
    }

    private static boolean admits(
            PackUnderResolution declaring, PackDependency dependency, PackUnderResolution target) {
        try {
            return Pep440Range.admits(dependency.range(), target.descriptor().packVersion());
        } catch (IllegalArgumentException malformed) {
            throw new Refusal(
                    Failure.OUT_OF_RANGE,
                    declaring.descriptor().packId(),
                    "pack " + declaring.descriptor().packId() + " declares range " + dependency.range() + " on "
                            + target.descriptor().packId() + ", which is not a PEP 440 range: "
                            + malformed.getMessage());
        }
    }

    private static void requireLevel(PackUnderResolution pack, PackUnderResolution target) {
        PackLevel own = pack.configured().level();
        PackLevel dependencyLevel = target.configured().level();
        if (rank(dependencyLevel) > rank(own)) {
            throw new Refusal(
                    Failure.LEVEL_VIOLATION,
                    pack.descriptor().packId(),
                    "pack " + pack.descriptor().packId() + " at " + own.token() + " depends on "
                            + target.descriptor().packId() + " at " + dependencyLevel.token()
                            + "; a pack may depend only on its own level or a lower one");
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
            String offender = packs.stream()
                    .map(pack -> pack.descriptor().packId())
                    .filter(id -> {
                        Integer count = outstanding.get(id);
                        return count != null && count > 0;
                    })
                    .findFirst()
                    .orElse("unknown");
            throw new Refusal(Failure.CYCLE, offender, "the dependency graph contains a cycle at " + offender);
        }
        return List.copyOf(ordered);
    }
}
