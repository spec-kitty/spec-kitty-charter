package ai.speckitty.charter.profiles.infra.api;

import ai.speckitty.charter.api.model.Problem;
import ai.speckitty.charter.web.Problems;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Problem parity of {@code getEffectiveAgentProfile} with the activation routes (fold A6). The activation set answers a
 * request whose path or query parameter breaks its constraint in the generated interface (a {@code depth} outside 0 to 1,
 * an activation id that breaks its pattern) with a {@code 400 Problem} that names the parameter and the constraint in its
 * {@code detail}; its advice is scoped to the activation controller, so the shared handler would answer the same request
 * on this route with a bare {@code 400}. This advice, scoped to {@link EffectiveAgentProfilesController} and ordered before
 * the shared handler, answers the activation routes' body. The route takes no body, so a parameter is all it judges: a
 * violation of the service's own answer is no request's fault and stays the shared {@code 500}.
 *
 * <p>A refusal the service throws is not handled here: the shared refusal handler answers it, as it does for every route
 * (an unknown activation is the same {@code 404 activation_not_found} on both).
 */
@RestControllerAdvice(assignableTypes = EffectiveAgentProfilesController.class)
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class EffectiveProfileProblems {

    private static final Logger LOG = LoggerFactory.getLogger(EffectiveProfileProblems.class);

    /** For Spring, which finds the advice by component scan. */
    public EffectiveProfileProblems() {}

    /**
     * Answers a request whose parameters the generated interface's method validation refuses.
     *
     * @param invalid the validation failure
     * @return {@code 400} with the {@code detail} of the first broken parameter, by property path; {@code 500} when a
     *     violation is not on a parameter
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> constraintViolations(ConstraintViolationException invalid) {
        if (invalid.getConstraintViolations().stream().anyMatch(violation -> !onParameter(violation))) {
            // The service's own answer broke the contract's constraint: no request is at fault, so it is a 500.
            LOG.error("A profile answer breaks the contract's constraints", invalid);
            return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY);
        }
        ConstraintViolation<?> first = invalid.getConstraintViolations().stream()
                .min(Comparator.comparing(
                        violation -> violation.getPropertyPath().toString()))
                .orElseThrow();
        Problem problem = new Problem(HttpStatus.BAD_REQUEST.getReasonPhrase(), HttpStatus.BAD_REQUEST.value());
        problem.setDetail("The request's " + parameterOf(first) + " " + first.getMessage() + ".");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** Whether a violation is on a request parameter: its path passes a parameter node. */
    private static boolean onParameter(ConstraintViolation<?> violation) {
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PARAMETER || node.getKind() == ElementKind.CROSS_PARAMETER) {
                return true;
            }
        }
        return false;
    }

    /** The name of the parameter a violation is on: the last node of its path, as {@code depth} for {@code f.depth}. */
    private static String parameterOf(ConstraintViolation<?> violation) {
        String name = "";
        for (Path.Node node : violation.getPropertyPath()) {
            name = String.valueOf(node.getName());
        }
        return name;
    }
}
