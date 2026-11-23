package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reason: one rule's outcome, with the element it is about (#614)")
class ReasonTest {

    private static final String RULE = "closure.presets";

    private static final Origin ACME = new Origin("acme");

    private static final Origin BASE = new Origin("base");

    private static Reason failing(Origin origin) {
        return new Reason(RULE, origin, Evaluation.FAIL, ReasonCode.of("pack_sibling_conflict"), Location.NOWHERE, "d");
    }

    @Test
    @DisplayName("a reason carries the origin it was given")
    void aReasonCarriesItsOrigin() {
        Reason reason = failing(ACME);

        assertThat(reason.origin()).isEqualTo(ACME);
        assertThat(reason.origin().id()).isEqualTo("acme");
        assertThat(reason.rule())
                .as("the rule stays the check's name, apart from the element")
                .isEqualTo(RULE);
    }

    @Test
    @DisplayName("two reasons alike in all but their origin are not equal")
    void equalityCoversTheOrigin() {
        Reason acme = failing(ACME);
        Reason base = new Reason(acme.rule(), BASE, acme.result(), acme.code(), acme.location(), acme.detail());

        assertThat(base).isNotEqualTo(acme);
        // control: the same parts, the origin included, are equal
        assertThat(failing(ACME)).isEqualTo(acme).hasSameHashCodeAs(acme);
    }

    @Test
    @DisplayName("a relocated reason keeps its origin")
    void aRelocatedReasonKeepsItsOrigin() {
        assertThat(failing(ACME).at(Location.in("pack.yaml")).origin()).isEqualTo(ACME);
    }

    @Test
    @DisplayName("an invariant's reason is about no element: its origin is NONE, never its rule (R-o)")
    void anInvariantsOriginIsNone() {
        assertThat(Reason.failed("an id is a slug", "`A b` is no slug").origin())
                .isEqualTo(Origin.NONE);
        assertThat(Reason.failed("an id is a slug").origin()).isEqualTo(Origin.NONE);
        assertThat(Reason.passed("an id is a slug").origin()).isEqualTo(Origin.NONE);
        assertThat(Reason.passed("an id is a slug").rule()).isEqualTo("an id is a slug");
    }

    @Test
    @DisplayName("a constraint's reasons, held or breached, are about no element: their origin is NONE (R-o)")
    void aConstraintsOriginIsNone() {
        Constraint<String> slug = new Constraint<>("an id is a slug", text -> !text.contains(" "));

        assertThat(slug.applyTo("ok").origin()).isEqualTo(Origin.NONE);
        assertThat(slug.applyTo("not ok").origin()).isEqualTo(Origin.NONE);
        assertThat(slug.applyTo("not ok").rule()).isEqualTo("an id is a slug");
    }

    @Test
    @DisplayName("a reason refuses a null origin")
    void aReasonRefusesANullOrigin() {
        assertThatThrownBy(() -> new Reason("r", null, Evaluation.FAIL, ReasonCode.INVARIANT, Location.NOWHERE, "d"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("origin");
    }
}
