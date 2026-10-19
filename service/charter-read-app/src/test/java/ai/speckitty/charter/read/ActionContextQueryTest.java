package ai.speckitty.charter.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

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
    private static final Urn UNDECLARED = new Urn(NodeKind.STYLEGUIDE, "undeclared");

    private static ActionContextQuery over(List<DrgNode> nodes, List<DrgEdge> edges) {
        return new ActionContextQuery(() -> new CharterOffering(List.of(new CharterPack(
                "built-in", PackLevel.BUILT_IN, Optional.empty(), new DoctrineGraph(nodes, edges), List.of()))));
    }

    private static DrgNode node(Urn urn) {
        return new DrgNode(urn, Optional.empty());
    }

    private final ActionContextQuery query = over(
            List.of(node(TYPE), node(PLAN), new DrgNode(D1, Optional.of("One")), node(D2), node(T1)),
            List.of(
                    DrgEdge.of(TYPE, PLAN, Relation.REQUIRES), DrgEdge.of(PLAN, T1, Relation.SCOPE),
                    DrgEdge.of(TYPE, D2, Relation.SCOPE), DrgEdge.of(PLAN, D2, Relation.SCOPE),
                    DrgEdge.of(TYPE, D1, Relation.SCOPE), DrgEdge.of(PLAN, D1, Relation.SUGGESTS)));

    @Test
    void scopeOfTheActionAndOfItsMissionTypeOnceEachSortedByUrn() {
        ActionContext context = query.answer("software-dev", "plan", 1);
        assertThat(context.guidance())
                .extracting(GuidanceEntry::urn, GuidanceEntry::via)
                .containsExactly(tuple(D1, TYPE), tuple(D2, PLAN), tuple(T1, PLAN));
        assertThat(context.guidance().get(0).label()).contains("One");
        // D2 is declared without a label.
        assertThat(context.guidance().get(1).label()).isEmpty();
    }

    @Test
    void anArtifactScopedByBothTheActionAndItsMissionTypeIsAnsweredOnceThroughTheAction() {
        ActionContextQuery both = over(
                List.of(node(TYPE), node(PLAN), node(D1)),
                List.of(DrgEdge.of(TYPE, D1, Relation.SCOPE), DrgEdge.of(PLAN, D1, Relation.SCOPE)));
        assertThat(both.answer("software-dev", "plan", 1).guidance())
                .extracting(GuidanceEntry::urn, GuidanceEntry::via)
                .containsExactly(tuple(D1, PLAN));
    }

    @Test
    void aScopeEdgeToANodeTheGraphDoesNotDeclareIsListedWithoutALabel() {
        ActionContextQuery dangling =
                over(List.of(node(TYPE), node(PLAN)), List.of(DrgEdge.of(PLAN, UNDECLARED, Relation.SCOPE)));
        assertThat(dangling.answer("software-dev", "plan", 1).guidance())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.urn()).isEqualTo(UNDECLARED);
                    assertThat(entry.kind()).isEqualTo(NodeKind.STYLEGUIDE);
                    assertThat(entry.label()).isEmpty();
                    assertThat(entry.via()).isEqualTo(PLAN);
                });
    }

    @Test
    void aPackThatCannotLoadRefusesTheReadWithItsCode() {
        ActionContextQuery broken = new ActionContextQuery(() -> {
            throw new CharterRefusal(
                    RefusalCode.DRG_SCHEMA_ERROR, "built-in/broken.graph.yaml: unknown key 'surprise'");
        });
        assertThatThrownBy(() -> broken.answer("software-dev", "plan", 1))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR));
    }

    @Test
    void unknownNamesAreRefused() {
        assertThatThrownBy(() -> query.answer("gardening", "plan", 1))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThatThrownBy(() -> query.answer("software-dev", "deploy", 1))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
    }

    @Test
    void theAnswerEchoesItsDepthAndCarriesTheFingerprintOfTheGovernanceItWasComputedFrom() {
        ActionContext context = query.answer("software-dev", "plan", 1);
        assertThat(context.depth()).isEqualTo(1);
        assertThat(context.governanceFingerprint()).matches("sha256:[0-9a-f]{64}");
        assertThat(query.answer("software-dev", "plan", 1).governanceFingerprint())
                .isEqualTo(context.governanceFingerprint());
    }

    @Test
    void aDepthOutsideWhatThisVersionAnswersIsAProgrammingError() {
        assertThatThrownBy(() -> query.answer("software-dev", "plan", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query.answer("software-dev", "plan", ActionContextQuery.MAX_DEPTH + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anImpactsEdgeDoesNotBringAnArtifactIntoScopeAndAScopeEntryHasNoStrength() {
        ActionContextQuery withImpacts = over(
                List.of(node(TYPE), node(PLAN), node(D1), node(D2)),
                List.of(DrgEdge.of(PLAN, D1, Relation.SCOPE), DrgEdge.impacts(PLAN, D2, -1.0)));
        assertThat(withImpacts.answer("software-dev", "plan", 1).guidance())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.urn()).isEqualTo(D1);
                    assertThat(e.impacts()).isEmpty();
                });
    }
}
