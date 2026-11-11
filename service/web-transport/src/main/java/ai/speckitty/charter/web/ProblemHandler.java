package ai.speckitty.charter.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Answers every failure that is not a charter refusal with a closed {@code Problem} body: the Spring MVC
 * exceptions (an unknown path, a representation the request does not accept, a method the path does not have)
 * with their own status, anything else with the 500 {@code internal_error} refusal. {@link RefusalHandler} answers the refusals; it runs first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProblemHandler extends ResponseEntityExceptionHandler {

    /** For Spring, which finds the advice by component scan. */
    public ProblemHandler() {}

    /**
     * Answers an exception nothing else handles with the {@code internal_error} refusal, and logs it: the body
     * says nothing about it.
     *
     * @param unexpected the exception
     * @return the {@code application/problem+json} 500 response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception unexpected) {
        logger.error("Unexpected failure", unexpected);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY);
    }

    /**
     * A request parameter that breaks a bound the contract declares (a {@code pageSize} of 0, an empty path segment) is
     * a malformed request: the plain 400 problem, without a code or a detail, so no violation text reaches the wire.
     * The generated interfaces are {@code @Validated}, so the method validation proxy refuses the call before any
     * controller runs, with this exception; every controller gets the same answer. A violation that is not on a
     * parameter, such as one on a return value, is the service's own failure: the 500 {@code internal_error}, logged.
     *
     * @param invalid the violations
     * @return the {@code application/problem+json} 400, or the 500 when a violation is not on a parameter
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> invalidParameter(ConstraintViolationException invalid) {
        if (invalid.getConstraintViolations().stream().allMatch(ProblemHandler::onParameter)) {
            return Problems.of(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY);
        }
        return unexpected(invalid);
    }

    private static boolean onParameter(ConstraintViolation<?> violation) {
        ElementKind last = null;
        for (Path.Node node : violation.getPropertyPath()) {
            last = node.getKind();
        }
        return last == ElementKind.PARAMETER || last == ElementKind.CROSS_PARAMETER;
    }

    /**
     * Logs every server error this handler resolves, with its exception, before the response is built: the
     * {@code internal_error} detail says the failure is logged, and the base class logs only when the response
     * is already committed. The exceptions of {@link #unexpected} are logged there, so none is logged twice.
     *
     * <p>A {@code 501} is no failure: today only a reserved operation answers it, by design (ADR-0020 D8). A handler that
     * ever answers {@code 501} for a real gap must not rely on this exclusion. The 501 is logged
     * at {@code DEBUG} with the request, without a stack trace, and raises no operator alert (#406).
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.value() == HttpStatus.NOT_IMPLEMENTED.value()) {
            if (logger.isDebugEnabled()) {
                logger.debug("501 Not Implemented: " + request.getDescription(false));
            }
        } else if (statusCode.is5xxServerError()) {
            logger.error("Server error " + statusCode.value(), ex);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    /** Every response of {@link ResponseEntityExceptionHandler} becomes the closed problem of its status. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        return Problems.of(statusCode, headers);
    }
}
