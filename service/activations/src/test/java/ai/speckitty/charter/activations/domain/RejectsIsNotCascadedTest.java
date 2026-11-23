package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.ANTI_PATTERN;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.STYLEGUIDE;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No cascade follows a {@code rejects} edge (#154; ADR-0052 decision 13, ADR-0064 decision 12): a guidance artifact that
 * rejects an anti-pattern names a practice to avoid, so selecting it does not activate the anti-pattern. The
 * anti-pattern is activatable all the same, and selecting it activates it. The walk's relations are
 * {@link CascadeRules#CASCADE_RELATIONS}, which does not name {@code rejects}; packs walks only the relations the plan
 * names, so the reach of a select of the styleguide is the styleguide alone.
 */
@DisplayName("No cascade follows a rejects edge (#154)")
class RejectsIsNotCascadedTest {

    private static final ArtifactRef STYLE = ref("styleguide:boring-code-review");

    private static final ArtifactRef CLEVER = ref("anti_pattern:clever-over-direct");

    private static final ActivationDocument EMPTY =
            ActivationDocument.builder().build().document();

    @Test
    void theCascadeRelationsLeaveRejectsOut() {
        assertThat(CascadeRules.CASCADE_RELATIONS).doesNotContain(RelationType.REJECTS);
        assertThat(CascadeRules.planSelect(EMPTY, STYLE, CascadeScope.all(), false)
                        .relations())
                .doesNotContain(RelationType.REJECTS);
    }

    @Test
    void selectingAStyleguideThatRejectsAnAntiPatternDoesNotActivateIt() {
        ReachPlan plan = CascadeRules.planSelect(EMPTY, STYLE, CascadeScope.all(), false);
        // The walk packs answers for that plan: the styleguide's rejects edge is no relation of the plan.
        Reach reached = plan.relations().contains(RelationType.REJECTS)
                ? reach().start(STYLE.urn())
                        .reached(CLEVER.urn(), STYLE.urn(), RelationType.REJECTS)
                        .build()
                : reach().start(STYLE.urn()).build();

        ActivationChange change = CascadeRules.applySelect(EMPTY, STYLE, CascadeScope.all(), false, reached);

        assertThat(change.after().selected(STYLEGUIDE).orElseThrow()).containsExactly("boring-code-review");
        assertThat(change.after().selected()).doesNotContainKey(ANTI_PATTERN);
        assertThat(change.applied()).containsExactly(STYLE);
    }

    @Test
    void selectingTheAntiPatternItselfActivatesIt() {
        ActivationChange change = CascadeRules.applySelect(
                EMPTY,
                CLEVER,
                CascadeScope.all(),
                false,
                reach().start(CLEVER.urn()).build());

        assertThat(change.after().selected(ANTI_PATTERN).orElseThrow()).isEqualTo(List.of("clever-over-direct"));
        assertThat(change.applied()).containsExactly(CLEVER);
    }
}
