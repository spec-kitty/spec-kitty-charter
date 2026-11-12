package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.MalformedPreconditionException;
import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.web.InvalidParameter;
import ai.speckitty.charter.web.Problems;
import ai.speckitty.charter.web.RefusalHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
 *   <li>a header, path or query parameter that breaks its constraint (an {@code If-None-Match} other than {@code *} on
 *       a write, an {@code If-Match} that is neither {@code *} nor one quoted revision, a {@code depth} outside 0 to 1)
 *       is a {@code 400 Problem} without a code whose detail names it in the service's own words, as every route
 *       answers it ({@link InvalidParameter}, #411); a {@link MalformedPreconditionException} is the same {@code 400},
 *       worded as the pack writes word it (J10).
 * </ul>
 *
 * <p>Each refusal is rendered by {@link RefusalHandler#refused(ai.speckitty.charter.kernel.domain.CharterRefusal)}, so
 * it has the body every other refusal has. A body that is not {@code application/json} stays {@code 415}, which this
 * advice does not handle.
 */
@RestControllerAdvice(assignableTypes = ActivationController.class)
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class ActivationProblems {

    private static final Logger LOG = LoggerFactory.getLogger(ActivationProblems.class);
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
        return invalid(List.of(unreadableAt(pointer)));
    }

    /**
     * The violation of a body that cannot be bound at a pointer.
     *
     * @param pointer the RFC 6901 pointer of the bad value; {@code ""} for the whole body
     * @return the violation, code {@code unreadable_body}
     */
    static RefusalViolation unreadableAt(String pointer) {
        return new RefusalViolation(
                pointer, "unreadable_body", "The body is not JSON of the request's schema at '" + pointer + "'.");
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
        List<ParameterValidationResult> parameters = invalid.getParameterValidationResults().stream()
                .filter(result -> !result.getMethodParameter().hasParameterAnnotation(RequestBody.class))
                .toList();
        if (!parameters.isEmpty()) {
            List<String> details = parameters.stream()
                    .flatMap(result -> result.getResolvableErrors().stream()
                            .map(error -> InvalidParameter.of(
                                    result.getMethodParameter(), result.unwrap(error, ConstraintViolation.class))))
                    .flatMap(Optional::stream)
                    .map(InvalidParameter::detail)
                    .toList();
            return details.isEmpty()
                    ? Problems.of(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY)
                    : Problems.badRequest(String.join(" ", details));
        }
        List<RefusalViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : invalid.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                violations.addAll(violationsOf(errors));
            }
        }
        return invalid(violations);
    }

    /**
     * Answers a request whose parameters the generated interface's method validation refuses, as Spring's
     * {@code MethodValidationPostProcessor} reports it: a violation inside a parameter (a member of the body) is a
     * {@code 422} with its pointer, and one of a whole parameter (a header or the path's id) is a {@code 400}. A
     * violation of the return value is no request's fault: any other violation stays a {@code 500}.
     *
     * @param invalid the validation failure
     * @return {@code 400} or {@code 422 activation_invalid}
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> constraintViolations(ConstraintViolationException invalid) {
        if (breaksAReturnValue(invalid)) {
            // The service's own answer broke the contract's constraint: no request is at fault, so it is a 500.
            LOG.error("An activation answer breaks the contract's constraints", invalid);
            return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY);
        }
        List<String> parameters = InvalidParameter.details(invalid.getConstraintViolations().stream()
                .filter(violation -> nodes(violation.getPropertyPath()).size() <= 2)
                .toList());
        if (!parameters.isEmpty()) {
            return Problems.badRequest(String.join(" ", parameters));
        }
        List<RefusalViolation> violations = new ArrayList<>();
        for (ConstraintViolation<?> violation : invalid.getConstraintViolations()) {
            List<String> path = nodes(violation.getPropertyPath());
            if (path.size() <= 2) {
                return Problems.of(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY);
            }
            String pointer = pointerOfProperty(String.join(".", path.subList(2, path.size())));
            violations.add(new RefusalViolation(
                    pointer, "invalid_value", "The value at '" + pointer + "' " + violation.getMessage() + "."));
        }
        violations.sort(Comparator.comparing(RefusalViolation::pointer));
        return invalid(violations);
    }

    /**
     * Whether a violation is not on a request parameter, as the shared {@code ProblemHandler} judges it (#268): a
     * return value, for one, is the service's own failure. A violation inside a parameter (a header, the path's id, a
     * member of the body) is the request's.
     */
    private static boolean breaksAReturnValue(ConstraintViolationException invalid) {
        return invalid.getConstraintViolations().stream().anyMatch(violation -> {
            for (Path.Node node : violation.getPropertyPath()) {
                if (node.getKind() == ElementKind.PARAMETER || node.getKind() == ElementKind.CROSS_PARAMETER) {
                    return false;
                }
            }
            return true;
        });
    }

    /** The segments of a validation path, an indexed one written {@code name[i]}; a container element node left out. */
    private static List<String> nodes(Path path) {
        return DOT.splitAsStream(path.toString())
                .filter(segment -> !segment.startsWith("<"))
                .toList();
    }

    /**
     * Answers a write whose preconditions cannot be evaluated, worded as the pack writes word the same refusal.
     *
     * @param malformed the failure
     * @return {@code 400} without a code, whose detail names the header (#411)
     */
    @ExceptionHandler(MalformedPreconditionException.class)
    public ResponseEntity<Object> malformed(MalformedPreconditionException malformed) {
        String ifNoneMatch = malformed.ifNoneMatch();
        InvalidParameter refused = ifNoneMatch == null
                ? InvalidParameter.excluding(
                        InvalidParameter.In.HEADER, HttpHeaders.IF_MATCH, HttpHeaders.IF_NONE_MATCH)
                : new InvalidParameter(
                        InvalidParameter.In.HEADER, HttpHeaders.IF_NONE_MATCH, ifNoneMatch, "which is not *");
        return Problems.badRequest(refused.detail());
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

    /**
     * The {@code 422}: never with an empty {@code violations}. A failure that names no member of the body (a constraint
     * on the whole body, a validation result with no field error) is one violation at the whole body, pointer
     * {@code ""}.
     */
    private ResponseEntity<CharterRefusal> invalid(List<RefusalViolation> violations) {
        List<RefusalViolation> named = violations.isEmpty()
                ? List.of(new RefusalViolation("", "invalid_value", "The body breaks a constraint of its schema."))
                : violations;
        return refusals.refused(ai.speckitty.charter.kernel.domain.CharterRefusal.withViolations(
                RefusalCode.ACTIVATION_INVALID,
                "The activation request body breaks its schema in " + named.size() + " place(s).",
                named));
    }
}
