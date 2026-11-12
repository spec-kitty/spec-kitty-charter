package ai.speckitty.charter.web;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Problem;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The shared answer to a method-validation violation (issue #268): a parameter out of its declared bound is the 400
 * problem whose detail names it (#411), whichever controller it reaches; a violation elsewhere is the service's own failure, a 500. Real
 * violations come from a real validator, so the property path is the one the method validation proxy builds. It lives
 * in the web transport, where the handler is, with the validator the service runs with.
 */
class ValidationProblemTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    private final ProblemHandler handler = new ProblemHandler();

    /** A stand-in for a generated, validated interface. */
    static class Operation {
        @NotNull public String page(@Min(1) int pageSize) {
            return "";
        }
    }

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    private static Set<ConstraintViolation<Operation>> parameterViolations() throws NoSuchMethodException {
        Method page = Operation.class.getMethod("page", int.class);
        return FACTORY.getValidator().forExecutables().validateParameters(new Operation(), page, new Object[] {0});
    }

    private static Set<ConstraintViolation<Operation>> returnValueViolations() throws NoSuchMethodException {
        Method page = Operation.class.getMethod("page", int.class);
        return FACTORY.getValidator().forExecutables().validateReturnValue(new Operation(), page, null);
    }

    /** A stand-in for a generated interface, whose parameter is bound from the query. */
    interface Paged {
        String page(@RequestParam("pageSize") @Min(1) int pageSize);
    }

    static class PagedController implements Paged {
        @Override
        public String page(int size) {
            return "";
        }
    }

    @Test
    void aQueryParameterOutOfItsDeclaredBoundIsA400ProblemWithoutACodeWhoseDetailNamesIt() throws Exception {
        Method page = PagedController.class.getMethod("page", int.class);
        Set<ConstraintViolation<PagedController>> violations = FACTORY.getValidator()
                .forExecutables()
                .validateParameters(new PagedController(), page, new Object[] {0});
        assertThat(violations).hasSize(1);

        ResponseEntity<Object> answer = handler.invalidParameter(new ConstraintViolationException(violations));

        Problem expected = new Problem("Bad Request", 400);
        expected.setDetail("The query parameter pageSize is \"0\", which is less than 1.");
        assertThat(answer.getStatusCode().value()).isEqualTo(400);
        assertThat(answer.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(answer.getBody()).isEqualTo(expected);
    }

    @Test
    void aParameterNotBoundFromTheRequestIsA400ProblemWithoutACodeOrADetail() throws Exception {
        Set<ConstraintViolation<Operation>> violations = parameterViolations();
        assertThat(violations).hasSize(1);

        ResponseEntity<Object> answer = handler.invalidParameter(new ConstraintViolationException(violations));

        assertThat(answer.getStatusCode().value()).isEqualTo(400);
        assertThat(answer.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(answer.getBody()).isEqualTo(new Problem("Bad Request", 400));
    }

    @Test
    void aViolationOnAReturnValueIsTheServicesOwnFailure() throws Exception {
        Set<ConstraintViolation<Operation>> violations = returnValueViolations();
        assertThat(violations).hasSize(1);

        ResponseEntity<Object> answer = handler.invalidParameter(new ConstraintViolationException(violations));

        assertThat(answer.getStatusCode().value()).isEqualTo(500);
        assertThat(answer.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void aMixOfParameterAndReturnValueViolationsIsA500() throws Exception {
        Set<ConstraintViolation<?>> mixed = new java.util.HashSet<>(parameterViolations());
        mixed.addAll(returnValueViolations());

        assertThat(handler.invalidParameter(new ConstraintViolationException(mixed))
                        .getStatusCode()
                        .value())
                .isEqualTo(500);
    }

    @Test
    void anExceptionWithoutViolationsIsA400() {
        assertThat(handler.invalidParameter(new ConstraintViolationException(Set.of()))
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
    }
}
