package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TensionTest {

    private static final Urn A = new Urn(NodeKind.DIRECTIVE, "a");
    private static final Urn B = new Urn(NodeKind.TACTIC, "b");

    @Test
    void aTensionKeepsItsEndpointsConditionAndRationaleAsWritten() {
        Tension tension = new Tension(A, B, Optional.of("always"), Optional.of("both stay valid"));

        assertThat(tension.from()).isEqualTo(A);
        assertThat(tension.to()).isEqualTo(B);
        assertThat(tension.when()).contains("always");
        assertThat(tension.rationale()).contains("both stay valid");
    }

    @Test
    void aTensionIsComparedAsWrittenSoTheOtherOrientationIsAnotherTension() {
        Tension written = new Tension(A, B, Optional.empty(), Optional.of("r"));

        assertThat(written).isEqualTo(new Tension(A, B, Optional.empty(), Optional.of("r")));
        assertThat(written).isNotEqualTo(new Tension(B, A, Optional.empty(), Optional.of("r")));
        assertThat(written).isNotEqualTo(new Tension(A, B, Optional.empty(), Optional.of("other")));
        assertThat(written).isNotEqualTo(new Tension(A, B, Optional.of("w"), Optional.of("r")));
    }

    @Test
    void aTensionCannotJoinARuleToItself() {
        assertThatThrownBy(() -> new Tension(A, A, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directive:a");
    }

    @Test
    void aMissingFieldIsRefusedWithANullPointerExceptionNamingIt() {
        assertThatThrownBy(() -> new Tension(null, B, Optional.empty(), Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("from");
        assertThatThrownBy(() -> new Tension(A, B, null, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("when");
        assertThatThrownBy(() -> new Tension(A, B, Optional.empty(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rationale");
    }
}
