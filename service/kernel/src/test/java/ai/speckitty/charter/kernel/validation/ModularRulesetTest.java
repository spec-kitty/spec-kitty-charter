package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ModularRulesetTest {

    // Instance fields, not static ones, so the factories run inside each test and PIT can attribute their coverage to
    // the individual tests that use them. A broken factory would not hide behind a static field either: the class
    // would fail to initialise, and every test with it (ExceptionInInitializerError).
    private final Constraint<String> notBlank = Constraints.notBlank();
    private final Constraint<String> shortText = Constraints.of("must be at most 3 characters", s -> s.length() <= 3);
    private final Constraint<String> lower =
            Constraints.of("must be lower case", s -> s.equals(s.toLowerCase(Locale.ROOT)));

    private final ModularRuleset<String> ruleset = ModularRuleset.of(List.of(notBlank, shortText, lower));

    @Test
    void guardThrowsOnTheFirstBreachAndNamesIt() {
        assertThatThrownBy(() -> ruleset.guard("  "))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("mustn't be blank")
                .extracting(e -> ((ConstraintBreachException) e).reason().detail())
                .isEqualTo("mustn't be blank");
    }

    @Test
    void guardStopsAtTheFirstBreachEvenWhenLaterConstraintsAlsoFail() {
        // "ABCDE" breaches shortText and lower; only the first, shortText, is reported.
        assertThatThrownBy(() -> ruleset.guard("ABCDE"))
                .isInstanceOf(ConstraintBreachException.class)
                .extracting(e -> ((ConstraintBreachException) e).reason().detail())
                .isEqualTo("must be at most 3 characters");
    }

    @Test
    void theBreachCarriesTheWholeReason() {
        assertThatThrownBy(() -> ruleset.guard("ABCDE"))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessage("Constraint breached: must be at most 3 characters")
                .extracting(e -> ((ConstraintBreachException) e).reason())
                .isEqualTo(Reason.failed("must be at most 3 characters"));
    }

    @Test
    void aRulesetStopsAtItsFirstBreachSoALaterRuleMayRelyOnAnEarlierOne() {
        // the second rule would throw on null; the guard never reaches it
        ModularRuleset<String> guarded = ModularRuleset.<String>emptyRules()
                .must(Constraints.of("present", s -> s != null))
                .must(Constraints.of("short", s -> s.length() <= 3));

        assertThatThrownBy(() -> guarded.guard(null))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("present");
        // control: a present, short value passes both
        assertThatCode(() -> guarded.guard("abc")).doesNotThrowAnyException();
    }

    @Test
    void guardDoesNotThrowForAValidValue() {
        assertThatCode(() -> ruleset.guard("abc")).doesNotThrowAnyException();
    }

    @Test
    void checkAccumulatesEveryBreach() {
        var rationale = ruleset.check("ABCDE");

        assertThat(rationale.failures())
                .containsExactly(Reason.failed("must be at most 3 characters"), Reason.failed("must be lower case"));
        assertThat(rationale.details()).hasSize(3);
    }

    @Test
    void checkOfAValidValuePassesWithNoFailures() {
        var rationale = ruleset.check("abc");

        assertThat(rationale.failures()).isEmpty();
        assertThat(rationale.details()).allMatch(r -> r.result() == Evaluation.PASS);
    }

    @Test
    void theReadyMadeNotBlankConstraintRefusesNullAndBlankTextAndAcceptsText() {
        assertThat(Constraints.notBlank().applyTo(" \t")).isEqualTo(Reason.failed("mustn't be blank"));
        assertThat(Constraints.notBlank().applyTo(null)).isEqualTo(Reason.failed("mustn't be blank"));
        // control: the same constraint accepts text
        assertThat(Constraints.notBlank().applyTo("abc")).isEqualTo(Reason.passed("mustn't be blank"));
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
                .extracting(e -> ((ConstraintBreachException) e).reason().detail())
                .isEqualTo("mustn't be blank");
        // must appends: the rules run in the order they were added
        assertThat(extended.check("ABCDE").details())
                .containsExactly(Reason.passed("mustn't be blank"), Reason.failed("must be at most 3 characters"));
        // control: the extended ruleset accepts a value that meets both rules, and the original stays empty
        assertThat(extended.check("abc").isPassing()).isTrue();
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

        assertThat(Constraints.of("same words", p1)).isNotEqualTo(Constraints.of("same words", p2));
        // control: the same description and the same predicate are equal, with equal hash codes
        assertThat(Constraints.of("same words", p1)).isEqualTo(Constraints.of("same words", p1));
        assertThat(Constraints.of("same words", p1)).hasSameHashCodeAs(Constraints.of("same words", p1));
        // a different code or severity makes a different constraint
        assertThat(Constraints.of("same words", p1).coded(ReasonCode.of("other")))
                .isNotEqualTo(Constraints.of("same words", p1));
        assertThat(Constraints.of("same words", p1).as(Severity.NOTICE)).isNotEqualTo(Constraints.of("same words", p1));
    }
}
