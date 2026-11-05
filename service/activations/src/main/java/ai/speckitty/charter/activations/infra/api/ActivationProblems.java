package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.MalformedPreconditionException;
import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.Problem;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.web.RefusalHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.core.JacksonException;

/**
 * The activation set's answers to a request Spring refuses before the controller runs (#125 build notes), ordered
 * before {@code ProblemHandler}, which would answer each as a bare {@code 400}:
 *
 * <ul>
 *   <li>a body that cannot be bound ({@link HttpMessageNotReadableException}: not JSON, a value of the wrong type, a
 *       token outside an enum) is {@code 422 activation_invalid}, with a violation at the bad value when Jackson
 *       names it, code {@code unreadable_body};
 *   <li>a body that breaks a constraint of its schema (bean validation: {@link MethodArgumentNotValidException}, or
 *       the body's part of a {@link HandlerMethodValidationException}) is {@code 422 activation_invalid}, one violation
 *       per broken constraint, code {@code invalid_value};
 *   <li>a header or path parameter that breaks its pattern (an {@code If-None-Match} other than {@code *} on a write,
 *       an {@code If-Match} that is not one quoted revision) and a {@link MalformedPreconditionException} are a plain
 *       {@code 400 Problem}, without a code (J10).
 * </ul>
 *
 * <p>Each refusal is rendered by {@link RefusalHandler#refused(ai.speckitty.charter.kernel.domain.CharterRefusal)}, so
 * it has the body every other refusal has. A body that is not {@code application/json} stays {@code 415}, which this
 * advice does not handle.
 */
@RestControllerAdvice(assignableTypes = ActivationController.class)
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class ActivationProblems {

    private static final Pattern INDEX = Pattern.compile("\\[(\\d+)]");
    private static final Pattern DOT = Pattern.compile("\\.");
    private static final Pattern UPPER = Pattern.compile("([A-Z])");
    private static final List<String> KIND_MEMBERS = List.of("selected", "deactivated");

    private final RefusalHandler refusals;

    /**
     * The advice over the shared refusal renderer.
     *
     * @param refusals the renderer of every charter refusal
     */
    public ActivationProblems(RefusalHandler refusals) {
        this.refusals = Objects.requireNonNull(refusals, "refusals");
    }

    /**
     * Answers a body that cannot be bound.
     *
     * @param unreadable the converter's refusal
     * @return {@code 422 activation_invalid}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CharterRefusal> unreadable(HttpMessageNotReadableException unreadable) {
        String pointer = unreadable.getCause() instanceof JacksonException jackson ? pointerOf(jackson.getPath()) : "";
        return invalid(List.of(new RefusalViolation(
                pointer, "unreadable_body", "The body is not JSON of the request's schema at '" + pointer + "'.")));
    }

    /**
     * Answers a body that breaks a constraint of its schema, validated by the argument resolver.
     *
     * @param invalid the validation failure
     * @return {@code 422 activation_invalid}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CharterRefusal> invalidBody(MethodArgumentNotValidException invalid) {
        return invalid(violationsOf(invalid.getBindingResult()));
    }

    /**
     * Answers a request whose parameters method validation refuses: a header or path parameter is a {@code 400}, and
     * otherwise the body is {@code 422}.
     *
     * @param invalid the validation failure
     * @return {@code 400} or {@code 422 activation_invalid}
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> invalidParameters(HandlerMethodValidationException invalid) {
        List<RefusalViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : invalid.getParameterValidationResults()) {
            if (!result.getMethodParameter().hasParameterAnnotation(RequestBody.class)) {
                return badRequest(
                        "The request's " + result.getMethodParameter().getParameterName() + " breaks its pattern.");
            }
            if (result instanceof ParameterErrors errors) {
                violations.addAll(violationsOf(errors));
            }
        }
        return invalid(violations);
    }

    /**
     * Answers a request whose parameters the generated interface's method validation refuses, as Spring's
     * {@code MethodValidationPostProcessor} reports it: a violation inside a parameter (a member of the body) is a
     * {@code 422} with its pointer, and one of a whole parameter (a header or the path's id) is a {@code 400}.
     *
     * @param invalid the validation failure
     * @return {@code 400} or {@code 422 activation_invalid}
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> constraintViolations(ConstraintViolationException invalid) {
        List<RefusalViolation> violations = new ArrayList<>();
        for (ConstraintViolation<?> violation : invalid.getConstraintViolations()) {
            List<String> path = nodes(violation.getPropertyPath());
            if (path.size() <= 2) {
                return badRequest("The request's " + path.getLast() + " " + violation.getMessage() + ".");
            }
            String pointer = pointerOfProperty(String.join(".", path.subList(2, path.size())));
            violations.add(new RefusalViolation(
                    pointer, "invalid_value", "The value at '" + pointer + "' " + violation.getMessage() + "."));
        }
        violations.sort(Comparator.comparing(RefusalViolation::pointer));
        return invalid(violations);
    }

    /** The segments of a validation path, an indexed one written {@code name[i]}; a container element node left out. */
    private static List<String> nodes(Path path) {
        return DOT.splitAsStream(path.toString())
                .filter(segment -> !segment.startsWith("<"))
                .toList();
    }

    /**
     * Answers a write whose preconditions cannot be evaluated.
     *
     * @param malformed the failure
     * @return {@code 400} without a code
     */
    @ExceptionHandler(MalformedPreconditionException.class)
    public ResponseEntity<Problem> malformed(MalformedPreconditionException malformed) {
        return badRequest(Objects.requireNonNullElse(malformed.getMessage(), "The preconditions are malformed."));
    }

    /**
     * The RFC 6901 pointer of a Jackson path.
     *
     * @param path the references from the root, a property name or an index each
     * @return the pointer; {@code ""} for the whole body
     */
    static String pointerOf(List<JacksonException.Reference> path) {
        StringBuilder pointer = new StringBuilder();
        for (JacksonException.Reference reference : path) {
            String name = reference.getPropertyName();
            pointer.append('/')
                    .append(
                            name == null
                                    ? String.valueOf(reference.getIndex())
                                    : name.replace("~", "~0").replace("/", "~1"));
        }
        return pointer.toString();
    }

    /**
     * The RFC 6901 pointer of a bean property path, for example {@code selected.agentProfile[2]} to
     * {@code /selected/agent_profile/2}: a kind under {@code selected} or {@code deactivated} is written snake case on
     * the wire, every other member as its property is named.
     *
     * @param propertyPath the path
     * @return the pointer
     */
    static String pointerOfProperty(String propertyPath) {
        StringBuilder pointer = new StringBuilder();
        String parent = "";
        for (String segment : DOT.split(INDEX.matcher(propertyPath).replaceAll(".$1"), -1)) {
            String member = KIND_MEMBERS.contains(parent) ? snakeCase(segment) : segment;
            pointer.append('/').append(member);
            parent = segment;
        }
        return pointer.toString();
    }

    private static String snakeCase(String segment) {
        Matcher upper = UPPER.matcher(segment);
        return upper.replaceAll(match -> "_" + match.group(1).toLowerCase(Locale.ROOT));
    }

    private static List<RefusalViolation> violationsOf(Errors errors) {
        return errors.getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new RefusalViolation(
                        pointerOfProperty(error.getField()),
                        "invalid_value",
                        "The value at '" + pointerOfProperty(error.getField()) + "' " + error.getDefaultMessage()
                                + "."))
                .toList();
    }

    private ResponseEntity<CharterRefusal> invalid(List<RefusalViolation> violations) {
        return refusals.refused(ai.speckitty.charter.kernel.domain.CharterRefusal.withViolations(
                RefusalCode.ACTIVATION_INVALID,
                "The activation request body breaks its schema in " + violations.size() + " place(s).",
                violations));
    }

    private static ResponseEntity<Problem> badRequest(String detail) {
        Problem problem = new Problem(HttpStatus.BAD_REQUEST.getReasonPhrase(), HttpStatus.BAD_REQUEST.value());
        problem.setDetail(detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
