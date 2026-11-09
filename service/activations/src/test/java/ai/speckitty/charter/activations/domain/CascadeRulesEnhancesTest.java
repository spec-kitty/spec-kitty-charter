package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.activations.domain.Reaches.refs;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static ai.speckitty.charter.kernel.domain.RelationType.ENHANCES;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ADR-0059 D2 as amended on 2026-11-06 (A4): {@code enhances} is a cascade relation like the others, walked in its
 * declared direction, from the enhancer to its target. Selecting E with a cascade selects T, selecting T selects
 * nothing more, and deactivating E with a cascade unselects T unless another active artifact needs it.
 */
class CascadeRulesEnhancesTest {

    private static final ArtifactRef E = ref("tactic:E");
    private static final ArtifactRef T = ref("tactic:T");
    private static final ArtifactRef D = ref("directive:D");

    /** E enhances T; D requires T. */
    private static final Reaches.Graph CLOSURE =
            new Reaches.Graph().edge("tactic:E", ENHANCES, "tactic:T").edge("directive:D", REQUIRES, "tactic:T");

    private static ActivationDocument selecting(String... urns) {
        ActivationDocument document = ActivationDocument.builder().build().document();
        for (ArtifactRef ref : refs(urns)) {
            document = document.withSelected(ref.kind(), ref.id());
        }
        return document;
    }

    private static ActivationChange select(ArtifactRef target) {
        ActivationDocument document = selecting();
        Reach reach = CLOSURE.apply(CascadeRules.planSelect(document, target, CascadeScope.all(), false));
        return CascadeRules.applySelect(document, target, CascadeScope.all(), false, reach);
    }

    private static ActivationChange deactivate(ActivationDocument document, ArtifactRef target) {
        Reach own = CLOSURE.apply(CascadeRules.planDeactivate(document, target, CascadeScope.all()));
        Reach others = CLOSURE.apply(CascadeRules.planShared(document, target, CascadeScope.all(), own, Set.of()));
        return CascadeRules.applyDeactivate(document, target, CascadeScope.all(), own, others, Set.of());
    }

    @Test
    void enhancesIsACascadeRelationOfEveryWalk() {
        ActivationDocument document = selecting("tactic:E");

        assertThat(CascadeRules.CASCADE_RELATIONS).contains(ENHANCES).hasSize(6);
        assertThat(CascadeRules.planSelect(document, E, CascadeScope.all(), false)
                        .relations())
                .contains(ENHANCES);
        assertThat(CascadeRules.planDeactivate(document, E, CascadeScope.all()).relations())
                .contains(ENHANCES);
    }

    @Test
    void aCascadingSelectOfAnEnhancerSelectsItsTarget() {
        ActivationChange change = select(E);

        assertThat(change.applied()).containsExactly(E, T);
        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("E", "T");
    }

    @Test
    void aCascadingSelectOfATargetSelectsNoEnhancer() {
        ActivationChange change = select(T);

        assertThat(change.applied()).containsExactly(T);
        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T");
    }

    @Test
    void aCascadingDeactivateOfAnEnhancerUnselectsItsTarget() {
        ActivationChange change = deactivate(selecting("tactic:E", "tactic:T"), E);

        assertThat(change.applied()).containsExactly(E, T);
        assertThat(change.after().selected(TACTIC)).isEmpty();
        assertThat(change.after().deactivated(TACTIC)).containsExactly("E");
    }

    @Test
    void aCascadingDeactivateOfAnEnhancerKeepsATargetAnotherActiveArtifactNeeds() {
        ActivationChange change = deactivate(selecting("directive:D", "tactic:E", "tactic:T"), E);

        assertThat(change.applied()).containsExactly(E);
        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T");
        assertThat(change.skipped().skippedShared()).containsExactly(new SkippedReport.SharedSkip(T, D));
    }
}
