package ai.speckitty.charter.routing.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * The value checks the catalog's records share: what one value can state alone. The cross-references between task types,
 * fits and constraints are the pack's content rules, so a whole pack reports every fault rather than the first a
 * constructor throws.
 */
final class Checks {

    private static final ModularRuleset<String> ID = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A routing catalog or task type id is a letter-first kebab slug of at most "
                            + IdentifierGrammar.MAX_SLUG_LENGTH + " characters.",
                    IdentifierGrammar::isLetterFirstSlug));
    private static final ModularRuleset<String> TEXT = ModularRuleset.<String>emptyRules()
            .must(Constraints.notBlank().described("A routing catalog's text is not blank."));
    private static final ModularRuleset<BigDecimal> UNIT = ModularRuleset.<BigDecimal>emptyRules()
            .must(Constraints.of(
                    "A fit score or a weight is from 0 to 1.",
                    value -> value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0));
    private static final ModularRuleset<BigDecimal> NOT_NEGATIVE = ModularRuleset.<BigDecimal>emptyRules()
            .must(Constraints.of("A price is not negative.", value -> value.signum() >= 0));
    private static final ModularRuleset<String> DATE_TIME = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A routing catalog's generation time is an ISO 8601 date-time with its offset.",
                    Checks::isDateTime));
    private static final ModularRuleset<List<?>> NOT_EMPTY = ModularRuleset.<List<?>>emptyRules()
            .must(Constraints.of("A routing catalog's list holds at least one item.", list -> !list.isEmpty()));

    private Checks() {}

    static String id(String value) {
        ID.guard(value);
        return value;
    }

    static String text(String value) {
        TEXT.guard(value);
        return value;
    }

    static String dateTime(String value) {
        DATE_TIME.guard(value);
        return value;
    }

    private static boolean isDateTime(String value) {
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException notOne) {
            return false;
        }
    }

    static BigDecimal unit(BigDecimal value) {
        UNIT.guard(value);
        return value;
    }

    static BigDecimal notNegative(BigDecimal value) {
        NOT_NEGATIVE.guard(value);
        return value;
    }

    static <T> List<T> notEmpty(List<T> list) {
        List<T> copy = List.copyOf(list);
        NOT_EMPTY.guard(copy);
        return copy;
    }
}
