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
