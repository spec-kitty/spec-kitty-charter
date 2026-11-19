package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.TensionFinding.Resolution;
import ai.speckitty.charter.kernel.domain.NodeKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The resolutions of an unreconciled tension (ADR-0045 decision 12, ruling G3). */
class TensionFindingTest {

    private static final NodeRef A = new NodeRef(NodeKind.DIRECTIVE, "A");
    private static final NodeRef B = new NodeRef(NodeKind.DIRECTIVE, "B");

    private static TensionSnapshot unreconciled() {
        return new TensionSnapshot(A, B, Optional.empty(), Optional.empty(), List.of());
    }

    @Test
    void bothSidesExcludableOfferBothResolutions() {
        assertThat(TensionFinding.of(unreconciled(), true, true).resolutions())
                .containsExactly(Resolution.RECONCILE, Resolution.DEACTIVATE_ONE_SIDE);
    }

    @Test
    void oneSideLockedStillOffersBothResolutions() {
        assertThat(TensionFinding.of(unreconciled(), false, true).resolutions())
                .containsExactly(Resolution.RECONCILE, Resolution.DEACTIVATE_ONE_SIDE);
        assertThat(TensionFinding.of(unreconciled(), true, false).resolutions())
                .containsExactly(Resolution.RECONCILE, Resolution.DEACTIVATE_ONE_SIDE);
    }

    @Test
    void noSideExcludableOffersOnlyReconcile() {
        assertThat(TensionFinding.of(unreconciled(), false, false).resolutions())
                .containsExactly(Resolution.RECONCILE);
    }

    @Test
    void aReconciledTensionHasNoFindingAndTheTokensAreTheContracts() {
        TensionSnapshot reconciled = new TensionSnapshot(
                A, B, Optional.empty(), Optional.empty(), List.of(new NodeRef(NodeKind.TACTIC, "r")));

        assertThatThrownBy(() -> TensionFinding.of(reconciled, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TensionFinding.CODE).isEqualTo("unreconciled_tension");
        assertThat(Resolution.RECONCILE.token()).isEqualTo("reconcile");
        assertThat(Resolution.DEACTIVATE_ONE_SIDE.token()).isEqualTo("deactivate_one_side");
    }
}
