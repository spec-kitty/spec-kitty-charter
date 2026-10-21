package ai.speckitty.charter.packs.infra.api;

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
     * Logs every server error this handler resolves, with its exception, before the response is built: the
     * {@code internal_error} detail says the failure is logged, and the base class logs only when the response
     * is already committed. The exceptions of {@link #unexpected} are logged there, so none is logged twice.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
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
