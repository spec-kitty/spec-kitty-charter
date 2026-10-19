package ai.speckitty.charter.rest;

import ai.speckitty.charter.api.model.Problem;
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
 * with their own status, anything else with 500. {@link RefusalHandler} answers the refusals; it runs first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProblemHandler extends ResponseEntityExceptionHandler {

    /** For Spring, which finds the advice by component scan. */
    public ProblemHandler() {}

    /**
     * Answers an exception nothing else handles with 500, and logs it: the body says nothing about it.
     *
     * @param unexpected the exception
     * @return the {@code application/problem+json} 500 response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> unexpected(Exception unexpected) {
        logger.error("Unexpected failure", unexpected);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY);
    }

    /** Every response of {@link ResponseEntityExceptionHandler} becomes the closed problem of its status. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Problem> problem = Problems.of(statusCode, headers);
        return new ResponseEntity<>(problem.getBody(), problem.getHeaders(), problem.getStatusCode());
    }
}
