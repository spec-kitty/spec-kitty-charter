package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ModularRulesetTest {

    private static final Constraint<String> NOT_BLANK = Constraints.isNotBlank();
    private static final Constraint<String> SHORT =
            Constraints.of("must be at most 3 characters", s -> s.length() <= 3);
    private static final Constraint<String> LOWER =
            Constraints.of("must be lower case", s -> s.equals(s.toLowerCase(Locale.ROOT)));

    private final ModularRuleset<String> ruleset = ModularRuleset.of(List.of(NOT_BLANK, SHORT, LOWER));

    @Test
    void guardThrowsOnTheFirstBreachAndNamesIt() {
        assertThatThrownBy(() -> ruleset.guard("  "))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("mustn't be blank")
                .extracting(e -> ((ConstraintBreachException) e).constraintDescription())
                .isEqualTo("mustn't be blank");
    }

    @Test
    void guardStopsAtTheFirstBreachEvenWhenLaterConstraintsAlsoFail() {
        // "ABCDE" breaches SHORT and LOWER; only the first, SHORT, is reported.
        assertThatThrownBy(() -> ruleset.guard("ABCDE"))
                .isInstanceOf(ConstraintBreachException.class)
                .extracting(e -> ((ConstraintBreachException) e).constraintDescription())
                .isEqualTo("must be at most 3 characters");
    }

    @Test
    void guardDoesNotThrowForAValidValue() {
        assertThatCode(() -> ruleset.guard("abc")).doesNotThrowAnyException();
    }

    @Test
    void checkAccumulatesEveryBreach() {
        var rationale = ruleset.check("ABCDE");

        assertThat(rationale.isFailing()).isTrue();
        assertThat(rationale.failures())
                .containsExactly(Reason.failed("must be at most 3 characters"), Reason.failed("must be lower case"));
        assertThat(rationale.details()).hasSize(3);
    }

    @Test
    void checkOfAValidValuePassesWithNoFailures() {
        var rationale = ruleset.check("abc");

        assertThat(rationale.isPassing()).isTrue();
        assertThat(rationale.failures()).isEmpty();
        assertThat(rationale.details()).allMatch(r -> r.result() == Evaluation.PASS);
    }

    @Test
    void constrainedGuardReturnsTheValueWhenValidAndThrowsWhenNot() {
        assertThat(ruleset.constrain("abc").guard()).isEqualTo("abc");
        assertThatThrownBy(() -> ruleset.constrain("ABCDE").guard()).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    void constraintsWithTheSameDescriptionButDifferentPredicatesAreNotEqual() {
        Predicate<String> p1 = s -> true;
        Predicate<String> p2 = s -> false;

        assertThat(new Constraint<>("same words", p1)).isNotEqualTo(new Constraint<>("same words", p2));
        // control: the same description and the same predicate are equal, with equal hash codes
        assertThat(new Constraint<>("same words", p1)).isEqualTo(new Constraint<>("same words", p1));
        assertThat(new Constraint<>("same words", p1)).hasSameHashCodeAs(new Constraint<>("same words", p1));
    }
}
