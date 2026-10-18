package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.DrgEdge;
import ai.speckitty.charter.domain.Relation;
import ai.speckitty.charter.domain.RefusalCode;
import ai.speckitty.charter.domain.Urn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Action-scoped context, first slice: scope edges of the action node, then of the mission type node,
 * over the merged offering without activation filtering (contracts/charter/features/action_context.feature).
 */
public final class ActionContextQuery {

    private final CharterPackRepository packs;

    public ActionContextQuery(CharterPackRepository packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    public ActionContext answer(String missionType, String action) {
        DoctrineGraph graph = packs.load().mergedGraph();
        Urn missionTypeUrn = Urn.missionType(missionType);
        Urn actionUrn = Urn.action(missionType, action);
        if (!graph.contains(missionTypeUrn)) {
            throw new CharterRefusal(RefusalCode.MISSION_TYPE_NOT_FOUND, missionTypeUrn + " is not a node of the merged graph.");
        }
        if (!graph.contains(actionUrn)) {
            throw new CharterRefusal(RefusalCode.ACTION_NOT_FOUND, actionUrn + " is not a node of the merged graph.");
        }
        List<GuidanceEntry> guidance = new ArrayList<>();
        Set<Urn> seen = new HashSet<>();
        for (Urn via : List.of(actionUrn, missionTypeUrn)) {
            for (DrgEdge edge : graph.outgoing(via, Relation.SCOPE)) {
                if (seen.add(edge.target())) {
                    guidance.add(new GuidanceEntry(edge.target(), edge.target().kind(),
                            graph.node(edge.target()).flatMap(n -> n.label()), edge.relation(), via));
                }
            }
        }
        return new ActionContext(actionUrn, missionTypeUrn, guidance);
    }
}
