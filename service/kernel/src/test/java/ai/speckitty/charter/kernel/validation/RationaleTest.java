package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RationaleTest {

    private static final Reason ID_PRESENT = Reason.passed("A pack identifier must not be blank.");
    private static final Reason ID_BLANK = Reason.failed("A pack identifier must not be blank.");
    private static final Reason HAS_STEP = Reason.passed("A mission type needs at least one step.");
    private static final Reason NO_TITLE = Reason.failed("A mission type needs a title that is not blank.");
    private static final Reason UNMODELLED = new Reason(
            "a pack holds only modelled files",
            Evaluation.FAIL,
            Severity.WARNING,
            ReasonCode.of("file_not_held"),
            Location.in("notes/README.md"),
            "The file 'notes/README.md' is not modelled.");
    private static final Reason ORDER_HINT = new Reason(
            "relations are sorted",
            Evaluation.FAIL,
            Severity.NOTICE,
            ReasonCode.of("relations_unsorted"),
            Location.in("drg/graph.yaml").at("/relations"),
            "The relations are not sorted.");

    @Test
    void anEmptyRationalePassesAndDoesNotFail() {
        var empty = new Rationale(List.of());

        assertThat(empty.isPassing()).isTrue();
        assertThat(empty.isFailing()).isFalse();
        assertThat(empty.failures()).isEmpty();
        // control: one failing reason turns it
        assertThat(new Rationale(List.of(ID_BLANK)).isFailing()).isTrue();
    }

    @Test
    void oneBreachMakesItFailingAndFailuresKeepsOnlyTheBreachesInOrder() {
        var mixed = new Rationale(List.of(HAS_STEP, ID_BLANK, ID_PRESENT, NO_TITLE));

        assertThat(mixed.isFailing()).isTrue();
        assertThat(mixed.isPassing()).isFalse();
        assertThat(mixed.failures()).containsExactly(ID_BLANK, NO_TITLE);
        assertThat(mixed.details()).containsExactly(HAS_STEP, ID_BLANK, ID_PRESENT, NO_TITLE);

        // control: the same passing reasons alone pass, and the passing case is not failing
        var passing = new Rationale(List.of(HAS_STEP, ID_PRESENT));
        assertThat(passing.isPassing()).isTrue();
        assertThat(passing.isFailing()).isFalse();
        assertThat(passing.failures()).isEmpty();
    }

    @Test
    void findingsAreFilteredBySeverityAndOnlyAViolationFailsTheRationale() {
        var mixed = new Rationale(List.of(HAS_STEP, UNMODELLED, ID_BLANK, ORDER_HINT));

        assertThat(mixed.violations()).containsExactly(ID_BLANK);
        assertThat(mixed.warnings()).containsExactly(UNMODELLED);
        assertThat(mixed.notices()).containsExactly(ORDER_HINT);
        assertThat(mixed.failures()).containsExactly(UNMODELLED, ID_BLANK, ORDER_HINT);
        assertThat(mixed.isFailing()).isTrue();

        // control: warnings and notices alone are findings, but the rationale passes
        var advisory = new Rationale(List.of(HAS_STEP, UNMODELLED, ORDER_HINT));
        assertThat(advisory.failures()).containsExactly(UNMODELLED, ORDER_HINT);
        assertThat(advisory.violations()).isEmpty();
        assertThat(advisory.isPassing()).isTrue();
    }

    @Test
    void andJoinsTwoRationalesInOrderAndChangesNeither() {
        var first = new Rationale(List.of(HAS_STEP, UNMODELLED));
        var second = new Rationale(List.of(ID_BLANK));

        var joined = first.and(second);

        assertThat(joined.details()).containsExactly(HAS_STEP, UNMODELLED, ID_BLANK);
        assertThat(joined.isFailing()).isTrue();
        // control: the parts are unchanged, and the first alone still passes
        assertThat(first.details()).containsExactly(HAS_STEP, UNMODELLED);
        assertThat(first.isPassing()).isTrue();
    }

    @Test
    void aRationaleIsASnapshotTheCallerCannotChangeAfterTheFact() {
        List<Reason> source = new ArrayList<>(List.of(ID_PRESENT));
        var snapshot = new Rationale(source);

        source.add(ID_BLANK);

        assertThat(snapshot.details()).containsExactly(ID_PRESENT);
        assertThat(snapshot.isPassing()).isTrue();
        assertThatThrownBy(() -> snapshot.details().add(NO_TITLE)).isInstanceOf(UnsupportedOperationException.class);
        // control: a rationale built from the list as it now stands sees the breach
        assertThat(new Rationale(source).isFailing()).isTrue();
    }
}
