package ai.speckitty.charter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.UrlPathHelper;

/**
 * Answers every failure that is not a charter refusal with a closed {@code Problem} body: the Spring MVC
 * exceptions (an unknown path, a representation the request does not accept, a method the path does not have)
 * with their own status, anything else with the 500 {@code internal_error} refusal. {@link RefusalHandler} answers the refusals; it runs first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProblemHandler extends ResponseEntityExceptionHandler {

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private final String basePath;

    /** The handler of a service whose API is under {@code /api/v1}. */
    public ProblemHandler() {
        this("/api/v1");
    }

    /**
     * For Spring, which finds the advice by component scan.
     *
     * @param basePath the server prefix of the API, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public ProblemHandler(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * A path outside the API that matches nothing ({@code GET /}, {@code GET /info}) is the 404 problem without a code,
     * whose detail points to the API root (#454); a path inside the API that matches nothing stays the bare 404.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return outsideTheApi(request)
                ? Problems.withDetail(status, headers, pointer())
                : super.handleNoResourceFoundException(ex, headers, status, request);
    }

    /** As {@link #handleNoResourceFoundException}, for a dispatcher that reports no handler instead (#454). */
    @Override
    protected @Nullable ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return outsideTheApi(request)
                ? Problems.withDetail(status, headers, pointer())
                : super.handleNoHandlerFoundException(ex, headers, status, request);
    }

    private boolean outsideTheApi(WebRequest request) {
        if (!(request instanceof ServletWebRequest servlet)) {
            return false;
        }
        HttpServletRequest http = servlet.getRequest();
        String path = PATHS.getPathWithinApplication(http);
        return !path.equals(basePath) && !path.startsWith(basePath + "/");
    }

    private String pointer() {
        return "This service answers under " + basePath + "/; start at GET " + basePath + "/.";
    }

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
     * A request parameter a controller refuses, such as a kind filter that names no listed kind or a malformed page
     * cursor: the 400 problem without a code, whose detail names the parameter, its value and what it accepts (#411).
     *
     * @param refused the refusal
     * @return the {@code application/problem+json} 400
     */
    @ExceptionHandler(InvalidParameter.class)
    public ResponseEntity<Object> refusedParameter(InvalidParameter refused) {
        return Problems.badRequest(refused.detail());
    }

    /**
     * A request parameter that breaks a bound the contract declares (a {@code pageSize} of 0, an empty path segment) is
     * a malformed request: the 400 problem without a code, whose detail names each broken parameter in the service's own
     * words ({@link InvalidParameter}, #411), so no validator message reaches the wire. The generated interfaces are
     * {@code @Validated}, so the method validation proxy refuses the call before any controller runs, with this
     * exception; every controller gets the same answer. A violation that is not on a parameter, such as one on a return
     * value, is the service's own failure: the 500 {@code internal_error}, logged.
     *
     * @param invalid the violations
     * @return the {@code application/problem+json} 400, or the 500 when a violation is not on a parameter
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> invalidParameter(ConstraintViolationException invalid) {
        if (!invalid.getConstraintViolations().stream().allMatch(ProblemHandler::onParameter)) {
            return unexpected(invalid);
        }
        List<String> details = InvalidParameter.details(invalid.getConstraintViolations());
        return details.isEmpty()
                ? Problems.of(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY)
                : Problems.badRequest(String.join(" ", details));
    }

    /**
     * A query, path or header value that cannot be converted to its parameter's type ({@code pageSize=abc}): the 400
     * problem whose detail names the parameter and its value (#411).
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch && mismatch.getRequiredType() != null) {
            Optional<InvalidParameter> refused =
                    InvalidParameter.ofType(mismatch.getParameter(), mismatch.getValue(), mismatch.getRequiredType());
            if (refused.isPresent()) {
                return refusedParameter(refused.get());
            }
        }
        return super.handleTypeMismatch(ex, headers, status, request);
    }

    /** A required query parameter the request does not carry: the 400 problem that names it (#411). */
    @Override
    protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return refusedParameter(InvalidParameter.missing(InvalidParameter.In.QUERY, ex.getParameterName()));
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
     * <p>A {@code 501} is no failure: only a reserved operation answers it, by design (ADR-0020 D8). A handler that
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

    /**
     * A representation the request does not accept is the code-less {@code 406} problem, and it varies with the
     * request's {@code Accept} as the answers it stands in for do ({@code Vary: Accept}, RFC 9110 section 12.5.5), so a
     * cache never serves it to a client that accepts what the resource can produce (#451).
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpHeaders varying = new HttpHeaders();
        varying.putAll(headers);
        varying.add(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        return super.handleHttpMediaTypeNotAcceptable(ex, varying, status, request);
    }

    /** Every response of {@link ResponseEntityExceptionHandler} becomes the closed problem of its status. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        return Problems.of(statusCode, headers);
    }
}
