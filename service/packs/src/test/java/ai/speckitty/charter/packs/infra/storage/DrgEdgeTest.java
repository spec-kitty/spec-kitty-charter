package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DrgEdgeTest {

    private static DrgEdge edge(Optional<Double> impacts, Optional<String> when, Optional<String> reason) {
        return new DrgEdge("tactic:a", "directive:b", "requires", impacts, when, reason);
    }

    @Test
    void recordEqualityIsFieldWiseAndIsTheRoundTripAssertion() {
        DrgEdge one = new DrgEdge(
                "tactic:a", "directive:b", "impacts", Optional.of(-1.0), Optional.of("when"), Optional.of("why"));
        DrgEdge same = new DrgEdge(
                "tactic:a", "directive:b", "impacts", Optional.of(-1.0), Optional.of("when"), Optional.of("why"));
        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
    }

    @Test
    void anAbsentOptionalIsDistinctFromAPresentOneOnEveryOptionalField() {
        DrgEdge absent = edge(Optional.empty(), Optional.empty(), Optional.empty());
        assertThat(absent)
                .isNotEqualTo(edge(Optional.of(0.0), Optional.empty(), Optional.empty()))
                .isNotEqualTo(edge(Optional.empty(), Optional.of("x"), Optional.empty()))
                .isNotEqualTo(edge(Optional.empty(), Optional.empty(), Optional.of("x")));
        assertThat(edge(Optional.empty(), Optional.of("x"), Optional.empty()))
                .isNotEqualTo(edge(Optional.empty(), Optional.empty(), Optional.of("x")));
    }

    @Test
    void differentTokenStringsAreDistinctValues() {
        assertThat(new DrgEdge(
                        "tactic:a", "directive:b", "requires", Optional.empty(), Optional.empty(), Optional.empty()))
                .isNotEqualTo(new DrgEdge(
                        "tactic:a", "directive:b", "suggests", Optional.empty(), Optional.empty(), Optional.empty()))
                .isNotEqualTo(new DrgEdge(
                        "directive:b", "tactic:a", "requires", Optional.empty(), Optional.empty(), Optional.empty()));
    }

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

    @Test
    void itIsAPlainMirrorWithNoCrossFieldRule() {
        // A non-impacts relation may still carry an impacts value, and an impacts relation may carry none: the
        // DSO does not judge the pairing; PackPolicy does on the read side.
        assertThat(edge(Optional.of(0.5), Optional.empty(), Optional.empty()).impacts())
                .contains(0.5);
        assertThat(new DrgEdge(
                                "tactic:a",
                                "directive:b",
                                "impacts",
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty())
                        .impacts())
                .isEmpty();
    }
}
