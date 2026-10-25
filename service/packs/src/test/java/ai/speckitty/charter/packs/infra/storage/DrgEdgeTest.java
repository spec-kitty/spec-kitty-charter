package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DrgEdgeTest {

    @Test
    void everyFieldIsRequiredNonNull() {
        assertThatThrownBy(() -> new DrgEdge(
                        null, "directive:b", "requires", Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new DrgEdge("tactic:a", null, "requires", Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DrgEdge(
                        "tactic:a", "directive:b", null, Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new DrgEdge("tactic:a", "directive:b", "requires", null, Optional.empty(), Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }
}
