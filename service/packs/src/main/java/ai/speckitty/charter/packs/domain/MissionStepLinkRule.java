package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every step of a mission type is linked by an authored {@code mission_type -requires-> action} edge, and no such edge
 * names an action that is not one of its steps (operator ruling DM-01M7B1YQR5ZKQPDW6DX64KPNRN, FR-011). The type's file
 * declares its steps and its on-demand steps; the graph holds the edges that say which actions the type requires, and the
 * two must agree, so that a walk from the type reaches exactly its steps.
 *
 * <p>The rule judges the mission types a pack holds, over the edges of its graph documents. An edge from a mission type
 * the pack does not hold, or from another pack's (written with that pack's name), is the compose's to judge once the
 * merge has placed it ({@code EdgeIndex}). A step that has no edge is a fault of the mission type file; an edge that names an
 * action that is not a step of its type is a fault of the graph document that holds it. The code is
 * {@value #CODE}. It is pure.
 */
public final class MissionStepLinkRule {

    /** The violation code of a fault: the one spelling, in validate mode, at registration and at load. */
    public static final String CODE = "mission_step_unlinked";

    private MissionStepLinkRule() {}

    /**
     * One {@code requires} edge as written.
     *
     * @param file the graph document that holds it
     * @param source the source, bare or written with a pack name
     * @param target the target, bare or written with a pack name
     * @param relation the relation token
     */
    public record Edge(String file, String source, String target, String relation) {
        /**
         * Checks the fields.
         *
         * @param file the graph document
         * @param source the source
         * @param target the target
         * @param relation the relation
         */
        public Edge {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(relation, "relation");
        }
    }

    /**
     * One fault.
     *
     * @param file the mission type file, or the graph document of the stray edge
     * @param reason what is wrong, naming the type and the step
     */
    public record Fault(String file, String reason) {
        /**
         * Checks the fields.
         *
         * @param file the file
         * @param reason the reason
         */
        public Fault {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Judges a pack's mission types over the edges of its graph documents.
     *
     * @param packName the name composition knows the pack by; a source written with it is the pack's own
     * @param types the mission types the pack holds, in file order
     * @param edges the edges of the pack's graph documents, in document order
     * @return the faults: per type, each unlinked step in declared order, then each stray edge in edge order
     */
    public static List<Fault> judge(String packName, List<MissionTypeFacts> types, List<Edge> edges) {
        Map<String, Set<String>> linked = new HashMap<>();
        List<Fault> strays = new ArrayList<>();
        Map<String, MissionTypeFacts> held = new HashMap<>();
        types.forEach(type -> held.putIfAbsent(type.id(), type));
        for (Edge edge : edges) {
            Optional<MissionTypeFacts> type = ownMissionType(edge, packName).map(held::get);
            if (type.isEmpty()) {
                continue;
            }
            String typeId = type.get().id();
            Optional<String> step = stepOf(edge.target(), typeId);
            if (step.isPresent() && type.get().stepIds().contains(step.get())) {
                linked.computeIfAbsent(typeId, ignored -> new HashSet<>()).add(step.get());
            } else {
                strays.add(new Fault(edge.file(), stray(typeId, edge.target())));
            }
        }
        List<Fault> faults = new ArrayList<>();
        Set<String> judged = new HashSet<>();
        for (MissionTypeFacts type : types) {
            if (!judged.add(type.id())) {
                // A second file of the same id is refused as a duplicate on its own; its steps are not judged twice.
                continue;
            }
            Set<String> found = linked.getOrDefault(type.id(), Set.of());
            for (String step : type.stepIds()) {
                if (!found.contains(step)) {
                    faults.add(new Fault(type.file(), unlinked(type.id(), step)));
                }
            }
        }
        faults.addAll(strays);
        return List.copyOf(faults);
    }

    /**
     * Whether an edge is one of a mission type's own step links: {@code mission_type:T requires action:T/<step>}, its
     * source written bare or with the pack's own name, its target bare. The one definition the load, the compose, the
     * relationship writes and the removal of the type share.
     *
     * @param packName the name composition knows the pack by
     * @param type the mission type
     * @param edge the edge
     * @return whether the edge starts at the type, as the pack writes it, and requires one of its actions
     */
    public static boolean isOwnStepLink(String packName, Urn type, Relationship edge) {
        return type.kind() == NodeKind.MISSION_TYPE
                && edge.type() == RelationType.REQUIRES
                && edge.from().equals(type)
                && edge.fromPack().map(packName::equals).orElse(true)
                && edge.toPack().isEmpty()
                && edge.to().kind() == NodeKind.ACTION
                && edge.to().id().startsWith(type.id() + "/");
    }

    /** The type id of a {@code requires} edge from a mission type of the pack: bare or written with the pack's name. */
    private static Optional<String> ownMissionType(Edge edge, String packName) {
        if (!RelationType.REQUIRES.token().equals(edge.relation())) {
            return Optional.empty();
        }
        return reference(edge.source())
                .filter(source -> source.packName().map(packName::equals).orElse(true))
                .flatMap(PackQualifiedReference::asUrn)
                .filter(urn -> urn.kind() == NodeKind.MISSION_TYPE)
                .map(Urn::id);
    }

    /**
     * The step id a bare action target names under the type, {@code action:<type>/<step>}; empty for anything else. A
     * target written with a pack name names another pack's action, never a step of the type, so it is a stray edge.
     */
    private static Optional<String> stepOf(String target, String typeId) {
        return reference(target)
                .filter(reference -> reference.packName().isEmpty())
                .flatMap(PackQualifiedReference::asUrn)
                .filter(urn -> urn.kind() == NodeKind.ACTION && urn.id().startsWith(typeId + "/"))
                .map(urn -> urn.id().substring(typeId.length() + 1))
                .filter(step -> !step.isEmpty() && step.indexOf('/') < 0);
    }

    private static Optional<PackQualifiedReference> reference(String written) {
        try {
            return Optional.of(PackQualifiedReference.parse(written));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static String unlinked(String typeId, String step) {
        return "the step " + step + " of mission_type:" + typeId + " has no requires edge from its type: author"
                + " mission_type:" + typeId + " requires action:" + typeId + "/" + step;
    }

    private static String stray(String typeId, String target) {
        return "the requires edge from mission_type:" + typeId + " to " + target + " names no step of the type";
    }
}
