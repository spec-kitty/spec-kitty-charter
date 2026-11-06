package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.kernel.domain.RefusalViolation;
import java.util.List;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The faults of a sub-resource write's body ({@code SelectionRequest}, {@code ExclusionRequest},
 * {@code PresetApplication}), carried from the body read to the {@code 422} step of the write (FR-010, ADR-0020 D6).
 * {@link ActivationBodyAdvice} records them, as a request attribute, instead of refusing the body while it is read,
 * which would answer {@code 422} before {@code 428}, {@code 404 activation_not_found} and {@code 412}; the controller
 * hands them to the use case, which refuses them once the preconditions hold.
 */
final class DeferredBody {

    /** The request attribute that holds the faults. */
    static final String ATTRIBUTE = DeferredBody.class.getName() + ".violations";

    private DeferredBody() {}

    /**
     * Records the faults of the request's body; a call outside a request records nothing.
     *
     * @param violations the faults, in the order they are reported
     */
    static void record(List<RefusalViolation> violations) {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            request.setAttribute(ATTRIBUTE, List.copyOf(violations), RequestAttributes.SCOPE_REQUEST);
        }
    }

    /**
     * The faults recorded for the request's body.
     *
     * @return the faults; empty when the body was well formed, or outside a request
     */
    static List<RefusalViolation> recorded() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        Object recorded = request == null ? null : request.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (recorded instanceof List<?> violations) {
            return violations.stream().map(RefusalViolation.class::cast).toList();
        }
        return List.of();
    }
}
