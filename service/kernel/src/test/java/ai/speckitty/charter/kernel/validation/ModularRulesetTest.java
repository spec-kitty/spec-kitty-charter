package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ModularRulesetTest {

    // Instance fields, not static ones: the factories run inside each test, so a factory that stopped returning its
    // constraint fails the tests that use it instead of hiding behind a class initialised once per JVM.
    private final Constraint<String> notBlank = Constraints.isNotBlank();
    private final Constraint<String> shortText = Constraints.of("must be at most 3 characters", s -> s.length() <= 3);
    private final Constraint<String> lower =
            Constraints.of("must be lower case", s -> s.equals(s.toLowerCase(Locale.ROOT)));

    private final ModularRuleset<String> ruleset = ModularRuleset.of(List.of(notBlank, shortText, lower));

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
        // "ABCDE" breaches shortText and lower; only the first, shortText, is reported.
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
        assertThat(rationale.isPassing()).isFalse();
        assertThat(rationale.failures())
                .containsExactly(Reason.failed("must be at most 3 characters"), Reason.failed("must be lower case"));
        assertThat(rationale.details()).hasSize(3);
    }

    @Test
    void checkOfAValidValuePassesWithNoFailures() {
        var rationale = ruleset.check("abc");

        assertThat(rationale.isPassing()).isTrue();
        assertThat(rationale.isFailing()).isFalse();
        assertThat(rationale.failures()).isEmpty();
        assertThat(rationale.details()).allMatch(r -> r.result() == Evaluation.PASS);
    }

    @Test
    void theReadyMadeNotBlankConstraintRefusesNullAndBlankTextAndAcceptsText() {
        assertThat(Constraints.isNotBlank().applyTo(" \t")).isEqualTo(Reason.failed("mustn't be blank"));
        assertThat(Constraints.isNotBlank().predicate().test(null)).isFalse();
        // control: the same constraint accepts text
        assertThat(Constraints.isNotBlank().applyTo("abc")).isEqualTo(Reason.passed("mustn't be blank"));
    }

    @Test
    void anEmptyRulesetPassesEveryValueAndMustAddsARuleThatIsEnforcedWithoutChangingTheOriginal() {
        ModularRuleset<String> empty = ModularRuleset.emptyRules();
        ModularRuleset<String> extended = empty.must(notBlank).must(shortText);

        assertThat(empty.check("  ").details()).isEmpty();
        assertThat(empty.check("  ").isPassing()).isTrue();
        assertThatCode(() -> empty.guard("  ")).doesNotThrowAnyException();

        assertThatThrownBy(() -> extended.guard("  "))
                .isInstanceOf(ConstraintBreachException.class)
                .extracting(e -> ((ConstraintBreachException) e).constraintDescription())
                .isEqualTo("mustn't be blank");
        // must appends: the rules run in the order they were added
        assertThat(extended.check("ABCDE").details())
                .containsExactly(Reason.passed("mustn't be blank"), Reason.failed("must be at most 3 characters"));
        // control: the extended ruleset accepts a value that meets both rules, and the original stays empty
        assertThat(extended.check("abc").isPassing()).isTrue();
        assertThat(empty.check("ABCDE").details()).isEmpty();
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
