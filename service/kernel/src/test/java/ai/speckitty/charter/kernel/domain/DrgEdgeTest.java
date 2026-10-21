package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DrgEdgeTest {

    private static final Urn A = new Urn(NodeKind.TACTIC, "a");
    private static final Urn B = new Urn(NodeKind.TACTIC, "b");

    @Test
    void anImpactsEdgeCarriesAStrengthFromMinusOneToPlusOne() {
        assertThat(DrgEdge.impacts(A, B, -1.0).impacts()).contains(-1.0);
        assertThat(DrgEdge.impacts(A, B, 1.0).impacts()).contains(1.0);
        assertThatThrownBy(() -> DrgEdge.impacts(A, B, 1.01))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("impacts strength 1.01 is outside [-1, 1]");
        assertThatThrownBy(() -> DrgEdge.impacts(A, B, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAnImpactsEdgeHasAStrengthAndItAlwaysHasOne() {
        assertThatThrownBy(() -> DrgEdge.of(A, B, Relation.IMPACTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an impacts edge needs a strength");
        assertThatThrownBy(
                        () -> new DrgEdge(A, B, Relation.SCOPE, Optional.of(0.5), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a scope edge has no impacts strength");
        assertThat(DrgEdge.of(A, B, Relation.SCOPE).impacts()).isEmpty();
    }
}
