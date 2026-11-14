package ai.speckitty.charter.web;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.PackRegistrationProblem;
import ai.speckitty.charter.api.model.Problem;
import ai.speckitty.charter.api.model.RefusalPack;
import ai.speckitty.charter.api.model.RefusalViolation;
import ai.speckitty.charter.api.model.RequiringPack;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.kernel.transport.RetryLaterException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Maps domain refusals to the contract's {@code CharterRefusal} problem bodies, and a registration's unmet
 * {@code If-Match} precondition to a plain {@code Problem}. It runs before {@link ProblemHandler}, whose catch-all would
 * otherwise answer either as an unexpected failure.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RefusalHandler {

    private static final Log LOGGER = LogFactory.getLog(RefusalHandler.class);

    /** The operation whose 200 body carries the problem of a refused registration (ADR-0056). */
    static final String REGISTRATION_STATUS = "getPackRegistration";

    /** The eleven pack composition codes, which a refused registration carries as 422 where a read answers 500. */
    private static Map<RefusalCode, HttpStatus> registrationStatusCodes() {
        return java.util.stream.Stream.of(
                        RefusalCode.PACK_DEPENDENCY_CYCLE,
                        RefusalCode.PACK_DEPENDENCY_UNKNOWN,
                        RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE,
                        RefusalCode.PACK_PARENT_UNRESOLVED,
                        RefusalCode.PACK_LEVEL_VIOLATION,
                        RefusalCode.PACK_NAME_CONFLICT,
                        RefusalCode.PACK_DUPLICATE_ID,
                        RefusalCode.PACK_SIBLING_CONFLICT,
                        RefusalCode.PACK_REFERENCE_UNQUALIFIED,
                        RefusalCode.PACK_REFERENCE_PACK_MISSING,
                        RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        code -> code, code -> HttpStatus.UNPROCESSABLE_CONTENT));
    }

    /**
     * The operations that judge a write over the closures a pack joins and refuse it synchronously: the per-artifact
     * writes (a directive, a paradigm, a styleguide and a toolguide, each created and put), the relationship write and
     * the two {@code DELETE}s (IC-15; operator ruling DM-01M6VG6GC40AKYYFRR35SV8HVW).
     */
    private static final List<String> IMPACT_CHECKED_WRITES = List.of(
            "registerPackDirective",
            "putPackDirective",
            "registerPackParadigm",
            "putPackParadigm",
            "registerPackStyleguide",
            "putPackStyleguide",
            "registerPackToolguide",
            "putPackToolguide",
            "putPackRelationship",
            "deletePackArtifact",
            "deletePackRelationship");

    /**
     * The activation writes, which store a document only once its closure composes with it (C38, squad A3): the create,
     * the replace, the select, the unselect, the deactivate, the re-activate and the preset application.
     */
    private static final List<String> ACTIVATION_WRITES = List.of(
            "createActivation",
            "replaceActivation",
            "selectActivationArtifact",
            "unselectActivationArtifact",
            "deactivateActivationArtifact",
            "reactivateActivationArtifact",
            "applyActivationPreset");

    /**
     * The refusal codes an operation answers with a status other than the code's own, by the operation's
     * {@code operationId} (the name of its controller method): the {@code operations} map of the code's entry in the
     * contract's {@code x-refusal}, which the contract scenarios hold this table to. A refused registration, and each
     * write that is judged over the closures its pack joins, answers the eleven composition codes as 422 where a read
     * is refused by packs that do not compose (500): the request is what leaves them unable to. Each of them, and each
     * activation write, answers {@code prompt_slot_missing} as 422 (OD-4 (i), squad R4): the request is what would leave
     * a step enhancement in force without its slot, where a composing read is refused 500.
     */
    private static final Map<String, Map<RefusalCode, HttpStatus>> STATUS_BY_OPERATION = statusByOperation();

    private static Map<String, Map<RefusalCode, HttpStatus>> statusByOperation() {
        Map<String, Map<RefusalCode, HttpStatus>> byOperation = new java.util.HashMap<>();
        byOperation.put(REGISTRATION_STATUS, impactCheckedStatusCodes());
        IMPACT_CHECKED_WRITES.forEach(operation -> byOperation.put(operation, impactCheckedStatusCodes()));
        // The relationship write is the one operation that carries prompt_fragment_invalid as the problem's own code:
        // an edge that would break a held step enhancement's edge rules is the request's fault (R-REL). It gets a copy
        // of the map of the others, so every other operation's map stays as it was.
        Map<RefusalCode, HttpStatus> relationshipWrite = new java.util.EnumMap<>(RefusalCode.class);
        relationshipWrite.putAll(impactCheckedStatusCodes());
        relationshipWrite.put(RefusalCode.PROMPT_FRAGMENT_INVALID, HttpStatus.UNPROCESSABLE_CONTENT);
        byOperation.put("putPackRelationship", Map.copyOf(relationshipWrite));
        // An activation write keeps the composition codes' own status (500): only the slot check judges the document.
        ACTIVATION_WRITES.forEach(operation ->
                byOperation.put(operation, Map.of(RefusalCode.PROMPT_SLOT_MISSING, HttpStatus.UNPROCESSABLE_CONTENT)));
        return Map.copyOf(byOperation);
    }

    /** A registration's and each impact-checked write's: the eleven composition codes and the slot check, all 422. */
    private static Map<RefusalCode, HttpStatus> impactCheckedStatusCodes() {
        Map<RefusalCode, HttpStatus> codes = new java.util.EnumMap<>(RefusalCode.class);
        codes.putAll(registrationStatusCodes());
        codes.put(RefusalCode.PROMPT_SLOT_MISSING, HttpStatus.UNPROCESSABLE_CONTENT);
        return Map.copyOf(codes);
    }

    /**
     * The most violations a problem body lists (the contract's {@code maxItems}). A 1 MiB body of nulls or unknown ids
     * holds about 200,000 refused parts, and listing every one would answer tens of megabytes; the detail counts the
     * ones left out instead.
     */
    static final int MAX_VIOLATIONS = 100;

    /** The most packs a problem body lists, as {@code artifact_referenced} promises: a pack names at most 100 referrers. */
    static final int MAX_PACKS = 100;

    private static final String INTERNAL_ERROR_DETAIL =
            "The charter service failed unexpectedly; the failure is logged.";

    /** For Spring, which finds the advice by component scan. */
    public RefusalHandler() {}

    /**
     * Answers a refusal with its problem body: 404 when the refusal is about what was asked, 500 otherwise. A
     * server-error (5xx) refusal is logged first with its cause, like {@link ProblemHandler}, so every 500 the
     * service returns leaves a diagnostic trail even though its body names nothing of the failure. {@code stale_input}
     * is routine and retryable, so it is logged at INFO without the stack trace. A refusal that says the configured
     * packs are broken ({@code pack_unreadable}, the composition codes) is no bug of the service: it is logged at WARN
     * with its code, detail and packs and no stack trace, which only {@code internal_error} keeps. A not-found
     * that hides an underlying failure (an unreadable pack answered as {@code pack_not_found}) carries that failure
     * as its cause; the 404 body names nothing of it, so it is logged at WARN here with the cause.
     *
     * @param refusal the domain refusal
     * @param request the request, whose handler names the operation that raised the refusal
     * @return the {@code application/problem+json} response carrying the refusal's code and detail
     */
    @ExceptionHandler(ai.speckitty.charter.kernel.domain.CharterRefusal.class)
    public ResponseEntity<CharterRefusal> refused(
            ai.speckitty.charter.kernel.domain.CharterRefusal refusal, WebRequest request) {
        return refused(refusal, operationOf(request));
    }

    /**
     * Answers a refusal raised outside any operation, or one whose operation is not named: the status of its code.
     *
     * @param refusal the domain refusal
     * @return the {@code application/problem+json} response carrying the refusal's code and detail
     */
    public ResponseEntity<CharterRefusal> refused(ai.speckitty.charter.kernel.domain.CharterRefusal refusal) {
        return refused(refusal, (String) null);
    }

    private ResponseEntity<CharterRefusal> refused(
            ai.speckitty.charter.kernel.domain.CharterRefusal refusal, @Nullable String operation) {
        if (refusal.code() == RefusalCode.STALE_INPUT) {
            // Routine and retryable (an input changed under the read): one line, no stack trace.
            LOGGER.info("Refusal " + refusal.code().token() + ": " + refusal.getMessage());
        } else if (refusal.code() == RefusalCode.INTERNAL_ERROR) {
            // A fault of the service itself: the trail keeps the stack trace.
            LOGGER.error("Server refusal " + refusal.code().token(), refusal);
        } else if (statusOf(refusal.code(), operation).is5xxServerError()) {
            // The configured packs are broken, not the service: an unreadable pack, a graph that cannot be read or the
            // pack composition. The code, the detail and the packs say what to fix; a stack trace would say nothing.
            LOGGER.warn("Refusal " + refusal.code().token() + ": " + refusal.getMessage() + packsOf(refusal));
        } else if (refusal.getCause() != null) {
            // A client refusal that hides an underlying failure: the body names nothing of it, so keep the trail.
            LOGGER.warn("Refusal " + refusal.code().token() + " hides a cause", refusal);
        }
        return respond(refusal, operation);
    }

    /** The {@code operationId} of the operation that handled the request: its controller method's name; none outside one. */
    private static @Nullable String operationOf(WebRequest request) {
        return request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
                        instanceof HandlerMethod handler
                ? handler.getMethod().getName()
                : null;
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
     * The problem a refused registration ends with, as the status resource answers it: the refusal the synchronous
     * answer would have been, with the status its {@code x-refusal} entry gives {@code getPackRegistration} (the eleven
     * composition codes are {@code 422} there, not {@code 500}).
     *
     * @param refusal the domain refusal the job ended with
     * @return the problem, with its code, packs and violations
     */
    public PackRegistrationProblem registrationProblem(ai.speckitty.charter.kernel.domain.CharterRefusal refusal) {
        HttpStatus status = statusOf(refusal.code(), REGISTRATION_STATUS);
        PackRegistrationProblem problem = new PackRegistrationProblem(title(refusal.code()), status.value());
        problem.setCode(refusal.code().token());
        problem.setDetail(refusal.getMessage() + leftOut(refusal.violations().size()));
        if (!refusal.packs().isEmpty()) {
            problem.setPacks(packDtos(refusal.packs()));
        }
        if (!refusal.violations().isEmpty()) {
            problem.setViolations(violationDtos(refusal.violations()));
        }
        return problem;
    }

    private static List<RefusalPack> packDtos(List<ai.speckitty.charter.kernel.domain.RefusalPack> packs) {
        return packs.stream()
                .limit(MAX_PACKS)
                .map(pack -> new RefusalPack(pack.descriptorPackId(), pack.packId(), pack.name()))
                .toList();
    }

    private static List<RefusalViolation> violationDtos(
            List<ai.speckitty.charter.kernel.domain.RefusalViolation> violations) {
        return violations.stream()
                .limit(MAX_VIOLATIONS)
                .map(violation -> new RefusalViolation(violation.pointer(), violation.code(), violation.detail()))
                .toList();
    }

    /**
     * Answers a request the service cannot take now.
     *
     * @param busy the refusal
     * @return the {@code application/problem+json} response
     */
    @ExceptionHandler(RetryLaterException.class)
    public ResponseEntity<Problem> retryLater(RetryLaterException busy) {
        LOGGER.info("Retry later (" + busy.retryAfterSeconds() + " s): " + busy.getMessage());
        Problem problem =
                new Problem(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(), HttpStatus.SERVICE_UNAVAILABLE.value());
        problem.setDetail(busy.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Integer.toString(busy.retryAfterSeconds()))
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
        return respond(
                new ai.speckitty.charter.kernel.domain.CharterRefusal(
                        RefusalCode.INTERNAL_ERROR, INTERNAL_ERROR_DETAIL),
                null);
    }

    /** The packs a refusal names, as {@code [packs: name (pack_id), ...]}; nothing when it names none. */
    private static String packsOf(ai.speckitty.charter.kernel.domain.CharterRefusal refusal) {
        return refusal.packs().isEmpty()
                ? ""
                : refusal.packs().stream()
                        .map(pack -> pack.name() + " (" + pack.descriptorPackId() + ")")
                        .collect(java.util.stream.Collectors.joining(", ", " [packs: ", "]"));
    }

    /** The sentence the detail gains when violations are left out of the body; nothing when every one is listed. */
    private static String leftOut(int violations) {
        int more = violations - MAX_VIOLATIONS;
        return more > 0 ? " " + more + " more violations are not listed." : "";
    }

    private static ResponseEntity<CharterRefusal> respond(
            ai.speckitty.charter.kernel.domain.CharterRefusal refusal, @Nullable String operation) {
        HttpStatus status = statusOf(refusal.code(), operation);
        CharterRefusal body = new CharterRefusal(
                title(refusal.code()),
                status.value(),
                CharterRefusal.CodeEnum.fromValue(refusal.code().token()));
        body.setDetail(refusal.getMessage() + leftOut(refusal.violations().size()));
        if (!refusal.packs().isEmpty()) {
            body.setPacks(packDtos(refusal.packs()));
        }
        if (!refusal.violations().isEmpty()) {
            body.setViolations(violationDtos(refusal.violations()));
        }
        if (!refusal.requiredBy().isEmpty()) {
            body.setRequiredBy(refusal.requiredBy().stream()
                    .map(pack -> new RequiringPack(pack.packId(), pack.via()))
                    .toList());
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /**
     * The HTTP status a refusal code is answered with. A not-found is 404; a validation failure is 422; a conflict or
     * policy refusal is 409; the pack registration codes keep the status of their {@code x-refusal} entry (a refused
     * source or relation rule is 422, an unavailable source or a full disk 503, a timed-out registration 504); every
     * other code is 500, {@code stale_input} and {@code internal_error} and the codes of the pack composition
     * included: the request is fine and the configured packs are not. An operation that declares
     * another status for a code in the per-operation statuses of the contract's x-refusal ({@link #STATUS_BY_OPERATION})
     * answers that one. The {@code 412}/{@code 428} preconditions are not refusal codes: {@link #precondition} answers
     * those as plain problem bodies.
     *
     * @param code the refusal code
     * @param operation the id of the operation that raised it, when known
     * @return its HTTP status
     */
    private static HttpStatus statusOf(RefusalCode code, @Nullable String operation) {
        HttpStatus declared = operation == null
                ? null
                : STATUS_BY_OPERATION.getOrDefault(operation, Map.of()).get(code);
        return declared != null ? declared : statusOf(code);
    }

    /**
     * Each refusal code's status when its operation declares none; every code has exactly one, checked when the class
     * loads. A new code without a status no longer fails the compile, as the exhaustive switch did:
     * {@code RefusalHandlerTest.everyCodeHasAMapping} is the guard that replaced the compiler. A class of its own, as a
     * mapping: the reserved-code guard leaves the handler's mappings out.
     */
    private static final class StatusByCode {

        private static final Set<RefusalCode> NOT_FOUND_CODES = EnumSet.of(
                RefusalCode.MISSION_TYPE_NOT_FOUND,
                RefusalCode.ACTION_NOT_FOUND,
                RefusalCode.PACK_NOT_FOUND,
                RefusalCode.PACK_DOCUMENT_NOT_FOUND,
                RefusalCode.TACTIC_NOT_FOUND,
                RefusalCode.DIRECTIVE_NOT_FOUND,
                RefusalCode.STYLEGUIDE_NOT_FOUND,
                RefusalCode.PARADIGM_NOT_FOUND,
                RefusalCode.TOOLGUIDE_NOT_FOUND,
                RefusalCode.KIND_NOT_FOUND,
                RefusalCode.ACTIVATION_NOT_FOUND,
                RefusalCode.ARTIFACT_NOT_FOUND,
                RefusalCode.PRESET_NOT_FOUND,
                RefusalCode.RELATIONSHIP_NOT_FOUND,
                RefusalCode.REGISTRATION_NOT_FOUND,
                RefusalCode.MISSION_STEP_CONTRACT_NOT_FOUND,
                RefusalCode.SKELETON_NOT_FOUND,
                RefusalCode.NO_PROFILE_MATCHED);

        private static final Set<RefusalCode> UNPROCESSABLE_CODES = EnumSet.of(
                RefusalCode.VALIDATION_FAILED,
                RefusalCode.ACTIVATION_INVALID,
                RefusalCode.PACK_SOURCE_REFUSED,
                RefusalCode.OVERRIDE_NOT_PERMITTED,
                RefusalCode.ENHANCES_CYCLE,
                RefusalCode.CONFLICTING_RELATIONS,
                RefusalCode.SELF_OVERRIDE,
                RefusalCode.OVERRIDE_CYCLE,
                RefusalCode.FALLBACK_PROFILE_UNKNOWN,
                RefusalCode.FALLBACK_PROFILE_NOT_LOCKED,
                RefusalCode.FALLBACK_PROFILE_NOT_ROLE_NEUTRAL,
                RefusalCode.FALLBACK_PROFILE_MISSING,
                RefusalCode.FALLBACK_PROFILE_CONFLICT);

        private static final Set<RefusalCode> CONFLICT_CODES = EnumSet.of(
                RefusalCode.REGISTRATION_CONFLICT,
                RefusalCode.GOVERNANCE_POLICY_VIOLATION,
                RefusalCode.ARTIFACT_REQUIRED,
                RefusalCode.ARTIFACT_LOCKED,
                RefusalCode.PACK_VERSION_CONFLICT,
                RefusalCode.ARTIFACT_REFERENCED,
                RefusalCode.RELATIONSHIP_NOT_REGISTERED,
                RefusalCode.MISSION_TYPE_NOT_IN_FORCE);

        private static final Set<RefusalCode> UNAVAILABLE_CODES =
                EnumSet.of(RefusalCode.PACK_SOURCE_UNAVAILABLE, RefusalCode.PACK_STORAGE_FULL);

        private static final Set<RefusalCode> TIMED_OUT_CODES = EnumSet.of(RefusalCode.REGISTRATION_TIMED_OUT);

        private static final Set<RefusalCode> SERVER_ERROR_CODES = EnumSet.of(
                RefusalCode.PACK_LEVEL_MISMATCH,
                RefusalCode.PACK_UNREADABLE,
                RefusalCode.DRG_SCHEMA_ERROR,
                RefusalCode.UNRESOLVED_EDGE_ENDPOINT,
                RefusalCode.PACK_DEPENDENCY_CYCLE,
                RefusalCode.PACK_DEPENDENCY_UNKNOWN,
                RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE,
                RefusalCode.PACK_PARENT_UNRESOLVED,
                RefusalCode.PACK_LEVEL_VIOLATION,
                RefusalCode.PACK_NAME_CONFLICT,
                RefusalCode.PACK_DUPLICATE_ID,
                RefusalCode.PACK_SIBLING_CONFLICT,
                RefusalCode.PACK_REFERENCE_UNQUALIFIED,
                RefusalCode.PACK_REFERENCE_PACK_MISSING,
                RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING,
                RefusalCode.PROMPT_SLOT_MALFORMED,
                RefusalCode.PROMPT_SLOT_NOT_EMPTY,
                RefusalCode.PROMPT_FRAGMENT_INVALID,
                RefusalCode.PROMPT_SLOT_MISSING,
                RefusalCode.INTERNAL_ERROR,
                RefusalCode.STALE_INPUT);

        /** Each refusal code's status when its operation declares none; every code has exactly one, checked at load. */
        private static final Map<RefusalCode, HttpStatus> STATUS_BY_CODE = statusByCode();

        private static Map<RefusalCode, HttpStatus> statusByCode() {
            Map<RefusalCode, HttpStatus> statuses = new EnumMap<>(RefusalCode.class);
            family(statuses, HttpStatus.NOT_FOUND, NOT_FOUND_CODES);
            family(statuses, HttpStatus.UNPROCESSABLE_CONTENT, UNPROCESSABLE_CODES);
            family(statuses, HttpStatus.CONFLICT, CONFLICT_CODES);
            family(statuses, HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_CODES);
            family(statuses, HttpStatus.GATEWAY_TIMEOUT, TIMED_OUT_CODES);
            family(statuses, HttpStatus.INTERNAL_SERVER_ERROR, SERVER_ERROR_CODES);
            Set<RefusalCode> unmapped = EnumSet.allOf(RefusalCode.class);
            unmapped.removeAll(statuses.keySet());
            if (!unmapped.isEmpty()) {
                throw new IllegalStateException("refusal codes with no HTTP status: " + unmapped);
            }
            return Collections.unmodifiableMap(statuses);
        }

        private static void family(Map<RefusalCode, HttpStatus> statuses, HttpStatus status, Set<RefusalCode> codes) {
            for (RefusalCode code : codes) {
                HttpStatus earlier = statuses.put(code, status);
                if (earlier != null) {
                    throw new IllegalStateException(code + " has two HTTP statuses: " + earlier + " and " + status);
                }
            }
        }

        static HttpStatus of(RefusalCode code) {
            return Objects.requireNonNull(STATUS_BY_CODE.get(code), code::name);
        }
    }

    private static HttpStatus statusOf(RefusalCode code) {
        return StatusByCode.of(code);
    }

    /**
     * The title of a refusal: its token as a sentence ({@code kind_not_found} is "Kind not found"), so a new code is
     * titled without a new branch. {@code drg_schema_error} is the one exception, titled "Invalid DRG document".
     *
     * @param code the refusal code
     * @return the human-readable title; clients never branch on it
     */
    private static String title(RefusalCode code) {
        if (code == RefusalCode.DRG_SCHEMA_ERROR) {
            return "Invalid DRG document";
        }
        String words = code.token().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
