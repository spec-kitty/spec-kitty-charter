package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.Problem;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PreconditionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain refusals to the contract's {@code CharterRefusal} problem bodies, and a registration's unmet
 * {@code If-Match} precondition to a plain {@code Problem}. It runs before {@link ProblemHandler}, whose catch-all would
 * otherwise answer either as an unexpected failure.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RefusalHandler {

    private static final Log LOGGER = LogFactory.getLog(RefusalHandler.class);

    private static final String INTERNAL_ERROR_DETAIL =
            "The charter service failed unexpectedly; the failure is logged.";

    /** For Spring, which finds the advice by component scan. */
    public RefusalHandler() {}

    /**
     * Answers a refusal with its problem body: 404 when the refusal is about what was asked, 500 otherwise. A
     * server-error (5xx) refusal is logged first with its cause, like {@link ProblemHandler}, so every 500 the
     * service returns leaves a diagnostic trail even though its body names nothing of the failure. {@code stale_input}
     * is the exception: it is routine and retryable, so it is logged at INFO without the stack trace. A not-found
     * that hides an underlying failure (an unreadable pack answered as {@code pack_not_found}) carries that failure
     * as its cause; the 404 body names nothing of it, so it is logged at WARN here with the cause.
     *
     * @param refusal the domain refusal
     * @return the {@code application/problem+json} response carrying the refusal's code and detail
     */
    @ExceptionHandler(ai.speckitty.charter.kernel.domain.CharterRefusal.class)
    public ResponseEntity<CharterRefusal> refused(ai.speckitty.charter.kernel.domain.CharterRefusal refusal) {
        if (refusal.code() == RefusalCode.STALE_INPUT) {
            // Routine and retryable (an input changed under the read): one line, no stack trace.
            LOGGER.info("Refusal " + refusal.code().token() + ": " + refusal.getMessage());
        } else if (statusOf(refusal.code()).is5xxServerError()) {
            LOGGER.error("Server refusal " + refusal.code().token(), refusal);
        } else if (refusal.getCause() != null) {
            // A client refusal that hides an underlying failure: the body names nothing of it, so keep the trail.
            LOGGER.warn("Refusal " + refusal.code().token() + " hides a cause", refusal);
        }
        return respond(refusal);
    }

    /**
     * Answers an unmet registration precondition: a stale {@code If-Match} is {@code 412 Precondition Failed}, a missing
     * one on a write that requires it {@code 428 Precondition Required}. Both are plain {@code Problem} bodies, not
     * refusal codes (ADR-0020).
     *
     * @param failure the precondition failure
     * @return the {@code application/problem+json} response
     */
    @ExceptionHandler(PreconditionException.class)
    public ResponseEntity<Problem> precondition(PreconditionException failure) {
        HttpStatus status = failure.kind() == PreconditionException.Kind.MISSING
                ? HttpStatus.PRECONDITION_REQUIRED
                : HttpStatus.PRECONDITION_FAILED;
        Problem problem = new Problem(status.getReasonPhrase(), status.value());
        problem.setDetail(failure.getMessage());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * The {@code internal_error} refusal of an unexpected failure. Its detail is the same sentence every time: it
     * names nothing of the failure, which is logged instead.
     *
     * @return the {@code application/problem+json} 500 response
     */
    static ResponseEntity<CharterRefusal> internalError() {
        return respond(new ai.speckitty.charter.kernel.domain.CharterRefusal(
                RefusalCode.INTERNAL_ERROR, INTERNAL_ERROR_DETAIL));
    }

    private static ResponseEntity<CharterRefusal> respond(ai.speckitty.charter.kernel.domain.CharterRefusal refusal) {
        HttpStatus status = statusOf(refusal.code());
        CharterRefusal body = new CharterRefusal(
                title(refusal.code()),
                status.value(),
                CharterRefusal.CodeEnum.fromValue(refusal.code().token()));
        body.setDetail(refusal.getMessage());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /**
     * The HTTP status a refusal code is answered with. A not-found is 404; a validation failure is 422; a conflict or
     * policy refusal is 409; every other code, {@code stale_input} and {@code internal_error} included, is 500. The
     * {@code 412}/{@code 428} preconditions are not refusal codes: {@link #precondition} answers those as plain
     * problem bodies.
     *
     * @param code the refusal code
     * @return its HTTP status
     */
    private static HttpStatus statusOf(RefusalCode code) {
        return switch (code) {
            case MISSION_TYPE_NOT_FOUND,
                    ACTION_NOT_FOUND,
                    PACK_NOT_FOUND,
                    TACTIC_NOT_FOUND,
                    DIRECTIVE_NOT_FOUND,
                    STYLEGUIDE_NOT_FOUND,
                    PARADIGM_NOT_FOUND,
                    TOOLGUIDE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION_FAILED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case REGISTRATION_CONFLICT, GOVERNANCE_POLICY_VIOLATION -> HttpStatus.CONFLICT;
            case PACK_LEVEL_MISMATCH,
                    PACK_UNREADABLE,
                    DRG_SCHEMA_ERROR,
                    UNRESOLVED_EDGE_ENDPOINT,
                    INTERNAL_ERROR,
                    STALE_INPUT -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String title(RefusalCode code) {
        return switch (code) {
            case MISSION_TYPE_NOT_FOUND -> "Mission type not found";
            case ACTION_NOT_FOUND -> "Action not found";
            case PACK_LEVEL_MISMATCH -> "Pack level mismatch";
            case PACK_UNREADABLE -> "Pack unreadable";
            case DRG_SCHEMA_ERROR -> "Invalid DRG document";
            case UNRESOLVED_EDGE_ENDPOINT -> "Unresolved edge endpoint";
            case INTERNAL_ERROR -> "Internal error";
            case GOVERNANCE_POLICY_VIOLATION -> "Governance policy violation";
            case STALE_INPUT -> "Stale input";
            case PACK_NOT_FOUND -> "Pack not found";
            case TACTIC_NOT_FOUND -> "Tactic not found";
            case DIRECTIVE_NOT_FOUND -> "Directive not found";
            case STYLEGUIDE_NOT_FOUND -> "Styleguide not found";
            case PARADIGM_NOT_FOUND -> "Paradigm not found";
            case TOOLGUIDE_NOT_FOUND -> "Toolguide not found";
            case VALIDATION_FAILED -> "Validation failed";
            case REGISTRATION_CONFLICT -> "Registration conflict";
        };
    }
}
