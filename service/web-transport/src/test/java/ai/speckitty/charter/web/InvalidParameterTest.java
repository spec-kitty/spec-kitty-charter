package ai.speckitty.charter.web;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.web.InvalidParameter.In;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The detail of a parameter {@code 400} (#411): where the parameter is, its name as the request spells it, the value it
 * carried and what it accepts, in the service's own words, never a validator's message. The violations come from a real
 * validator over a stand-in for a generated interface, so the property path is the one the method validation proxy
 * builds.
 */
class InvalidParameterTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    /** A stand-in for a generated interface: the web annotations are on the interface, as the generator writes them. */
    interface Api {
        String list(
                @PathVariable("activationId") @Pattern(regexp = "[a-z]+") String activationId,
                @RequestParam(value = "pageSize", required = false) @Min(1) @Max(200) Integer pageSize,
                @RequestParam(value = "pageCursor", required = false) @Size(min = 1) String pageCursor,
                @RequestHeader(value = "If-Match", required = false) @Size(max = 3) String ifMatch);
    }

    /** The controller, which inherits the constraints and names its parameters otherwise in Java. */
    static class Controller implements Api {
        @Override
        public String list(String id, Integer size, String cursor, String match) {
            return "";
        }
    }

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void aQueryParameterBelowItsMinimumNamesTheWireNameTheValueAndTheBound() throws Exception {
        assertThat(details("abc", 0, null, null))
                .containsExactly("The query parameter pageSize is \"0\", which is less than 1.");
    }

    @Test
    void aQueryParameterAboveItsMaximumNamesTheBound() throws Exception {
        assertThat(details("abc", 201, null, null))
                .containsExactly("The query parameter pageSize is \"201\", which is greater than 200.");
    }

    @Test
    void aPathParameterThatBreaksItsPatternNamesThePattern() throws Exception {
        assertThat(details("Not-This", 1, null, null))
                .containsExactly("The path parameter activationId is \"Not-This\", which does not match [a-z]+.");
    }

    @Test
    void aTooShortAndATooLongValueNameTheirLength() throws Exception {
        assertThat(details("abc", 1, "", "\"abcd\""))
                .containsExactly(
                        "The query parameter pageCursor is \"\", which is shorter than 1 character(s).",
                        "The header If-Match is \"\\\"abcd\\\"\", which is longer than 3 character(s).");
    }

    @Test
    void theDetailsAreInTheOrderOfTheParameters() throws Exception {
        assertThat(details("NO", 0, null, null))
                .containsExactly(
                        "The path parameter activationId is \"NO\", which does not match [a-z]+.",
                        "The query parameter pageSize is \"0\", which is less than 1.");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "plain               | plain",
                "a\"b                | a\\\"b",
                "back\\slash         | back\\\\slash",
            })
    void aValueIsQuotedWithItsQuotesAndBackslashesEscaped(String value, String quoted) {
        assertThat(new InvalidParameter(In.QUERY, "kind", value, "which is not a served kind").detail())
                .isEqualTo("The query parameter kind is \"" + quoted + "\", which is not a served kind.");
    }

    @Test
    void aControlCharacterIsWrittenAsItsEscape() {
        assertThat(new InvalidParameter(In.QUERY, "kind", "a\nb\u007f", "which is not a served kind").detail())
                .isEqualTo("The query parameter kind is \"a\\u000ab\\u007f\", which is not a served kind.");
    }

    @Test
    void aLongValueIsCutAtSixtyFourCodePoints() {
        String value = "😀".repeat(70);
        String detail = new InvalidParameter(In.QUERY, "kind", value, "which is not a served kind").detail();
        assertThat(detail)
                .isEqualTo(
                        "The query parameter kind is \"" + "😀".repeat(64) + "…\", which is not a served" + " kind.");
    }

    @Test
    void aValueOutsideALengthRangeNamesTheRange() throws Exception {
        Method method = Ranged.class.getMethod("read", String.class);
        Set<ConstraintViolation<Ranged>> violations =
                FACTORY.getValidator().forExecutables().validateParameters(new Ranged(), method, new Object[] {""});
        assertThat(InvalidParameter.details(Set.copyOf(violations)))
                .containsExactly("The path parameter skeletonName is \"\", which is not 1 to 255 characters long.");
    }

    /** A stand-in whose path parameter has a length range, as the skeleton name has. */
    static class Ranged {
        public String read(@PathVariable("skeletonName") @Size(min = 1, max = 255) String name) {
            return "";
        }
    }

    @Test
    void aC1ControlALineSeparatorAndABidiOverrideAreEscaped() {
        for (int c : new int[] {0x85, 0x2028, 0x202e, 0x2066}) {
            String value = "a" + Character.toString(c) + "b";
            assertThat(new InvalidParameter(In.HEADER, "If-Match", value, "which is not *").detail())
                    .as("U+%04X", c)
                    .isEqualTo("The header If-Match is \"a" + String.format("\\u%04x", c) + "b\", which is not *.");
        }
        // A format character beyond the BMP, a tag, is written as its two UTF-16 escapes, each four digits.
        assertThat(new InvalidParameter(
                                In.HEADER, "If-Match", "a" + Character.toString(0xe0001) + "b", "which is not *")
                        .detail())
                .isEqualTo("The header If-Match is \"a\\udb40\\udc01b\", which is not *.");
    }

    @Test
    void twoParametersThatExcludeEachOtherAreNamedTogether() {
        assertThat(InvalidParameter.excluding(In.HEADER, "If-Match", "If-None-Match")
                        .detail())
                .isEqualTo("The headers If-Match and If-None-Match exclude each other, and the request sends both.");
    }

    @Test
    void aValueOfTheWrongTypeSaysWhetherAnIntegerWasExpected() throws Exception {
        MethodParameter pageSize = new MethodParameter(
                Api.class.getMethod("list", String.class, Integer.class, String.class, String.class), 1);
        MethodParameter cursor = new MethodParameter(
                Api.class.getMethod("list", String.class, Integer.class, String.class, String.class), 2);
        assertThat(InvalidParameter.ofType(pageSize, "abc", Integer.class)
                        .orElseThrow()
                        .detail())
                .isEqualTo("The query parameter pageSize is \"abc\", which is not an integer.");
        assertThat(InvalidParameter.ofType(cursor, "x", java.util.UUID.class)
                        .orElseThrow()
                        .detail())
                .isEqualTo("The query parameter pageCursor is \"x\", which this parameter does not accept.");
    }

    @Test
    void aMissingParameterSaysItIsRequired() {
        assertThat(InvalidParameter.missing(In.QUERY, "from").detail())
                .isEqualTo("The query parameter from is missing, and it is required.");
    }

    @Test
    void theAcceptedValuesAreListedWithOrBeforeTheLast() {
        assertThat(InvalidParameter.oneOf(List.of("active", "inactive", "excluded")))
                .isEqualTo("which is not one of active, inactive or excluded");
        assertThat(InvalidParameter.oneOf(List.of("active"))).isEqualTo("which is not active");
    }

    @Test
    void aViolationOnNoWebParameterHasNoDetail() throws Exception {
        Method method = Plain.class.getMethod("page", int.class);
        Set<ConstraintViolation<Plain>> violations =
                FACTORY.getValidator().forExecutables().validateParameters(new Plain(), method, new Object[] {0});
        assertThat(violations).hasSize(1);
        assertThat(InvalidParameter.of(violations.iterator().next())).isEmpty();
    }

    /** A validated method whose parameter is not bound from the request. */
    static class Plain {
        public String page(@Min(1) int size) {
            return "";
        }
    }

    private static List<String> details(String id, Integer size, String cursor, String match) throws Exception {
        Method method = Controller.class.getMethod("list", String.class, Integer.class, String.class, String.class);
        Set<ConstraintViolation<Controller>> violations = FACTORY.getValidator()
                .forExecutables()
                .validateParameters(new Controller(), method, new Object[] {id, size, cursor, match});
        return InvalidParameter.details(Set.copyOf(violations));
    }
}
