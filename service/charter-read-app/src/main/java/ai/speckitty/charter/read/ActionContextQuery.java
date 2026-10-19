package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.CharterOffering;
import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.DrgEdge;
import ai.speckitty.charter.domain.RefusalCode;
import ai.speckitty.charter.domain.Relation;
import ai.speckitty.charter.domain.Urn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Action-scoped context, first slice: the targets of the scope edges of the action node and of the mission
 * type node, each target once (an artifact in both scopes keeps its action entry), sorted by full URN as core
 * sorts the resolved artifacts of an action; over the merged offering without activation filtering
 * (contracts/charter/scenarios/action_context.feature). It answers depth 1 only, one scope edge from each node.
 */
public final class ActionContextQuery {

    /** The deepest walk this version answers. */
    public static final int MAX_DEPTH = 1;

    private final CharterPackRepository packs;

    /**
     * A query over the offering {@code packs} loads.
     *
     * @param packs the repository port; the offering is loaded afresh for every answer
     */
    public ActionContextQuery(CharterPackRepository packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * The context of one action of one mission type.
     *
     * @param missionType the mission type, for example {@code software-dev}
     * @param action the action, for example {@code plan}
     * @param depth how many scope edges to follow, from 1 to {@link #MAX_DEPTH}
     * @return the artifacts in scope of the action and of its mission type
     * @throws IllegalArgumentException when {@code depth} is outside 1 to {@link #MAX_DEPTH}; the inbound adapter
     *     refuses such a request before it asks
     * @throws CharterRefusal with {@link RefusalCode#MISSION_TYPE_NOT_FOUND} or {@link RefusalCode#ACTION_NOT_FOUND}
     *     when the merged graph has no such node, or the repository's refusal when the offering cannot be loaded
     */
    public ActionContext answer(String missionType, String action, int depth) {
        if (depth < 1 || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("depth " + depth + " is outside 1 to " + MAX_DEPTH);
        }
        CharterOffering offering = packs.load();
        DoctrineGraph graph = offering.mergedGraph();
        Urn missionTypeUrn = Urn.missionType(missionType);
        Urn actionUrn = Urn.action(missionType, action);
        if (!graph.contains(missionTypeUrn)) {
            throw new CharterRefusal(
                    RefusalCode.MISSION_TYPE_NOT_FOUND, missionTypeUrn + " is not a node of the merged graph.");
        }
        if (!graph.contains(actionUrn)) {
            throw new CharterRefusal(RefusalCode.ACTION_NOT_FOUND, actionUrn + " is not a node of the merged graph.");
        }
        List<GuidanceEntry> guidance = new ArrayList<>();
        Set<Urn> seen = new HashSet<>();
        for (Urn via : List.of(actionUrn, missionTypeUrn)) {
            for (DrgEdge edge : graph.outgoing(via, Relation.SCOPE)) {
                if (seen.add(edge.target())) {
                    guidance.add(new GuidanceEntry(
                            edge.target(),
                            edge.target().kind(),
                            graph.node(edge.target()).flatMap(n -> n.label()),
                            edge.relation(),
                            edge.impacts(),
                            via));
                }
            }
        }
        guidance.sort(Comparator.comparing(GuidanceEntry::urn));
        return new ActionContext(actionUrn, missionTypeUrn, depth, offering.governanceFingerprint(), guidance);
    }
}
