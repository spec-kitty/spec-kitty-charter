package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocationTest {

    @Test
    void aLocationIsAFileAndAJsonPointerIntoIt() {
        Location title = Location.in("pack.yaml").at("/title");

        assertThat(title).isEqualTo(new Location("pack.yaml", "/title"));
        assertThat(title.isPlaced()).isTrue();
        // the empty pointer is the whole file
        assertThat(Location.in("pack.yaml").pointer()).isEmpty();
        assertThat(Location.in("pack.yaml").isPlaced()).isTrue();
        // control: nowhere names no file
        assertThat(Location.NOWHERE.isPlaced()).isFalse();
    }

    @Test
    void aPointerNeedsAFileAndStartsWithASlash() {
        assertThatThrownBy(() -> new Location("", "/title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a file");
        assertThatThrownBy(() -> Location.in("pack.yaml").at("title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("starts with '/'");
        assertThatThrownBy(() -> Location.in("")).isInstanceOf(IllegalArgumentException.class);
        // control: a file with no pointer, and no file with no pointer, are both well-formed
        assertThat(new Location("pack.yaml", "")).isEqualTo(Location.in("pack.yaml"));
        assertThat(new Location("", "")).isEqualTo(Location.NOWHERE);
    }

    @Test
    void aReasonCodeIsNotBlankOwnsItsSeverityAndIsEqualByValueAndSeverity() {
        assertThat(ReasonCode.of("file_not_held")).isEqualTo(new ReasonCode("file_not_held", Severity.VIOLATION));
        assertThat(ReasonCode.of("file_not_held").value()).isEqualTo("file_not_held");
        assertThatThrownBy(() -> ReasonCode.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ReasonCode.INVARIANT.severity()).isEqualTo(Severity.VIOLATION);
        // control: the same value at another severity is another code, and a reason takes its code's severity
        var warning = new ReasonCode("file_not_held", Severity.WARNING);
        assertThat(warning).isNotEqualTo(ReasonCode.of("file_not_held"));
        assertThat(new Reason("r", Evaluation.FAIL, warning, Location.NOWHERE, "d").severity())
                .isEqualTo(Severity.WARNING);
    }

    @Test
    void aBreachOfAHandWrittenCheckKeepsItsRuleFixedAndSaysWhatItFound() {
        Reason reason = Reason.failed("Every state is reachable from the initial state.", "state 'x' is not reachable");

        assertThat(reason.rule()).isEqualTo("Every state is reachable from the initial state.");
        assertThat(reason.detail()).isEqualTo("state 'x' is not reachable");
        assertThat(new ConstraintBreachException(reason)).hasMessage("Constraint breached: state 'x' is not reachable");
        // control: the one-argument form uses the description as both
        assertThat(Reason.failed("fixed").detail()).isEqualTo("fixed");
    }

    @Test
    void aBreachSurvivesSerializationWithItsReason() throws Exception {
        var breach =
                new ConstraintBreachException(Reason.failed("rule", "detail").at(Location.in("pack.yaml")));
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.ObjectOutputStream(bytes)) {
            out.writeObject(breach);
        }
        try (var in = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
            var read = (ConstraintBreachException) in.readObject();
            assertThat(read.reason()).isEqualTo(breach.reason());
        }
    }
}
