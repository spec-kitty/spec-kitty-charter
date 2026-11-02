package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Pep440Range;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private static final Comparator<PackUnderResolution> STABLE_ORDER =
            Comparator.comparing(entry -> entry.configured().id().value());

    private PackCompositionResolver() {}

    /**
     * One configured pack with the descriptor the offering read already holds.
     *
     * @param configured the configured pack and its level
     * @param descriptor what its {@code pack.yaml} states
     */
    public record PackUnderResolution(ConfiguredPack configured, PackDescriptor descriptor) {

        /**
         * Checks the fields.
         *
         * @param configured the configured pack
         * @param descriptor its descriptor
         */
        public PackUnderResolution {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptor, "descriptor");
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
        DUPLICATE_ID
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
        Map<String, List<String>> edges = new LinkedHashMap<>();
        for (PackUnderResolution pack : ordered) {
            edges.put(pack.descriptor().packId(), dependenciesOf(pack, byId));
        }
        return topologicalOrder(ordered, byId, edges);
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
            if (!admits(dependency, target)) {
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

    private static boolean admits(PackDependency dependency, PackUnderResolution target) {
        try {
            return Pep440Range.admits(dependency.range(), target.descriptor().packVersion());
        } catch (IllegalArgumentException malformed) {
            throw new Refusal(
                    Failure.OUT_OF_RANGE,
                    target.descriptor().packId(),
                    "pack " + target.descriptor().packId() + " declares range " + dependency.range()
                            + ", which is not a PEP 440 range: " + malformed.getMessage());
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

        List<PackUnderResolution> ready = new ArrayList<>();
        packs.stream()
                .filter(pack -> {
                    Integer count = outstanding.get(pack.descriptor().packId());
                    return count != null && count == 0;
                })
                .forEach(ready::add);
        List<PackUnderResolution> ordered = new ArrayList<>(packs.size());
        while (!ready.isEmpty()) {
            PackUnderResolution pack = ready.remove(0);
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
