package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
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
 * Maps domain refusals to the contract's {@code CharterRefusal} problem bodies. It runs before
 * {@link ProblemHandler}, whose catch-all would otherwise answer a refusal as an unexpected failure.
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
        } else if (!refusal.code().notFound()) {
            LOGGER.error("Server refusal " + refusal.code().token(), refusal);
        } else if (refusal.getCause() != null) {
            // A not-found that hides an underlying failure: the 404 body names nothing of it, so keep the trail.
            LOGGER.warn("Refusal " + refusal.code().token() + " hides a cause", refusal);
        }
        return respond(refusal);
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
        HttpStatus status = refusal.code().notFound() ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
        CharterRefusal body = new CharterRefusal(
                title(refusal.code()),
                status.value(),
                CharterRefusal.CodeEnum.fromValue(refusal.code().token()));
        body.setDetail(refusal.getMessage());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
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
        };
    }
}
