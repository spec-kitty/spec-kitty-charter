package ai.speckitty.charter.profiles.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.api.model.Problem;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The answer to a parameter the generated interface refuses: the body the activation routes answer, a {@code 400} whose
 * {@code detail} names the parameter and the constraint, and the shared {@code 500} for a violation that is no request's.
 */
class EffectiveProfileProblemsTest {

    private final EffectiveProfileProblems problems = new EffectiveProfileProblems();

    private static Path.Node node(ElementKind kind, String name) {
        Path.Node node = mock(Path.Node.class);
        when(node.getKind()).thenReturn(kind);
        when(node.getName()).thenReturn(name);
        return node;
    }

    private static ConstraintViolation<?> violation(String path, String message, Path.Node... nodes) {
        Path property = mock(Path.class);
        when(property.iterator()).thenAnswer(call -> List.of(nodes).iterator());
        when(property.toString()).thenReturn(path);
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(property);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    private static ConstraintViolation<?> onDepth(String message) {
        return violation(
                "getEffectiveAgentProfile.depth",
                message,
                node(ElementKind.METHOD, "getEffectiveAgentProfile"),
                node(ElementKind.PARAMETER, "depth"));
    }

    private static ConstraintViolationException of(ConstraintViolation<?>... violations) {
        return new ConstraintViolationException(Set.<ConstraintViolation<?>>of(violations));
    }

    @Test
    void aBrokenParameterIsA400ProblemThatNamesTheParameterAndTheConstraint() {
        ResponseEntity<Object> answer = problems.constraintViolations(of(onDepth("must be less than or equal to 1")));

        assertThat(answer.getStatusCode().value()).isEqualTo(400);
        assertThat(answer.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(answer.getBody()).isInstanceOfSatisfying(Problem.class, problem -> {
            assertThat(problem.getTitle()).isEqualTo("Bad Request");
            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getDetail()).isEqualTo("The request's depth must be less than or equal to 1.");
        });
    }

    @Test
    void severalBrokenParametersAreAnsweredByTheFirstInPathOrder() {
        ConstraintViolation<?> id = violation(
                "getEffectiveAgentProfile.activationId",
                "must match the pattern",
                node(ElementKind.METHOD, "getEffectiveAgentProfile"),
                node(ElementKind.PARAMETER, "activationId"));

        for (int attempt = 0; attempt < 5; attempt++) {
            ResponseEntity<Object> answer = problems.constraintViolations(of(onDepth("must be at most 1"), id));

            assertThat(((Problem) answer.getBody()).getDetail())
                    .isEqualTo("The request's activationId must match the pattern.");
        }
    }

    @Test
    void aViolationOfTheServicesOwnAnswerIsTheSharedInternalError() {
        ConstraintViolation<?> returned = violation(
                "getEffectiveAgentProfile.<return value>.governance",
                "must not be null",
                node(ElementKind.METHOD, "getEffectiveAgentProfile"),
                node(ElementKind.RETURN_VALUE, "<return value>"));

        ResponseEntity<Object> answer = problems.constraintViolations(of(returned));

        assertThat(answer.getStatusCode().value()).isEqualTo(500);
        assertThat(answer.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void aRequestFaultBesideAFaultOfTheAnswerIsStillTheInternalError() {
        ConstraintViolation<?> returned = violation(
                "getEffectiveAgentProfile.<return value>",
                "must not be null",
                node(ElementKind.METHOD, "getEffectiveAgentProfile"),
                node(ElementKind.RETURN_VALUE, "<return value>"));

        assertThat(problems.constraintViolations(of(onDepth("must be at most 1"), returned))
                        .getStatusCode()
                        .value())
                .isEqualTo(500);
    }

    @Test
    void aCrossParameterViolationIsARequestFaultToo() {
        ConstraintViolation<?> cross = violation(
                "getEffectiveAgentProfile.<cross-parameter>",
                "must agree",
                node(ElementKind.METHOD, "getEffectiveAgentProfile"),
                node(ElementKind.CROSS_PARAMETER, "<cross-parameter>"));

        assertThat(problems.constraintViolations(of(cross)).getStatusCode().value())
                .isEqualTo(400);
    }
}
