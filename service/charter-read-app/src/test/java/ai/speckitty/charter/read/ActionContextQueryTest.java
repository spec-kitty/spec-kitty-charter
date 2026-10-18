package ai.speckitty.charter.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterOffering;
import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.DrgEdge;
import ai.speckitty.charter.domain.DrgNode;
import ai.speckitty.charter.domain.NodeKind;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.RefusalCode;
import ai.speckitty.charter.domain.Relation;
import ai.speckitty.charter.domain.Urn;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActionContextQueryTest {

    private static final Urn TYPE = Urn.missionType("software-dev");
    private static final Urn PLAN = Urn.action("software-dev", "plan");
    private static final Urn D1 = new Urn(NodeKind.DIRECTIVE, "D1");
    private static final Urn D2 = new Urn(NodeKind.DIRECTIVE, "D2");
    private static final Urn T1 = new Urn(NodeKind.TACTIC, "T1");

    private final ActionContextQuery query = new ActionContextQuery(() -> new CharterOffering(List.of(
            new CharterPack("built-in", PackLevel.BUILT_IN, Optional.empty(), new DoctrineGraph(
                    List.of(node(TYPE), node(PLAN), new DrgNode(D1, Optional.of("One")), node(D2)),
                    List.of(DrgEdge.of(TYPE, PLAN, Relation.REQUIRES), DrgEdge.of(TYPE, D1, Relation.SCOPE),
                            DrgEdge.of(TYPE, D2, Relation.SCOPE), DrgEdge.of(PLAN, D2, Relation.SCOPE),
                            DrgEdge.of(PLAN, T1, Relation.SUGGESTS))), List.of()))));

    private static DrgNode node(Urn urn) {
        return new DrgNode(urn, Optional.empty());
    }

    @Test
    void actionScopeFirstThenMissionTypeScopeWithoutRepeats() {
        ActionContext context = query.answer("software-dev", "plan");
        assertThat(context.guidance()).extracting(GuidanceEntry::urn, GuidanceEntry::via)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(D2, PLAN), org.assertj.core.groups.Tuple.tuple(D1, TYPE));
        assertThat(context.guidance().get(1).label()).contains("One");
        // An endpoint the graph does not declare is still listed, without a label.
        assertThat(context.guidance().get(0).label()).isEmpty();
    }

    @Test
    void unknownNamesAreRefused() {
        assertThatThrownBy(() -> query.answer("gardening", "plan")).isInstanceOfSatisfying(CharterRefusal.class,
                r -> assertThat(r.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThatThrownBy(() -> query.answer("software-dev", "deploy")).isInstanceOfSatisfying(CharterRefusal.class,
                r -> assertThat(r.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
    }
}
