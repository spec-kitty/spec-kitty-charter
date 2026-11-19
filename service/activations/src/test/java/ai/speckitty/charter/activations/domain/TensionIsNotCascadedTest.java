package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RelationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No cascade follows a tension (ADR-0045 decisions 10 and 13): the relation sets the activation writes and the effective
 * set walk are explicit, and neither names {@code tension}. The packs module proves a reach over that set does not list
 * the other side ({@code TensionIsNotWalkedTest}).
 */
@DisplayName("No cascade follows a tension (ADR-0045)")
class TensionIsNotCascadedTest {

    @Test
    void theCascadeRelationsLeaveTheTensionOutAndKeepRequires() {
        assertThat(CascadeRules.CASCADE_RELATIONS)
                .doesNotContain(RelationType.TENSION)
                .contains(RelationType.REQUIRES, RelationType.SUGGESTS);
    }
}
