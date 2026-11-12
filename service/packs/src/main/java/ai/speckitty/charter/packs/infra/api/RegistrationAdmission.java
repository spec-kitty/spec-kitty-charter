package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.web.InvalidParameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The synchronous checks of {@code PUT /packs/{packId}} that come before its body is read (IC-14, IC-18), and of
 * {@code POST /packs} that need no body (registration on, the conditional headers), and the registration switch of the two
 * {@code DELETE}s of a pack ({@code deletePackArtifact}, {@code deletePackRelationship}), in the order the
 * contract lists them, each its own answer: registration switched off ({@code 503 pack_source_unavailable}), the
 * {@code packId} grammar ({@code 400}, decision DM-01M6TV6VDNHNCVWRHHC74WATV0), the conditional headers ({@code 400} for
 * a malformed one or both together), a declared length over 32 MiB ({@code 413}), the upload's place and room (ten
 * uploads open already is {@code 503} with {@code Retry-After}; less room than the declared bytes and what the open
 * uploads will write is {@code 503 pack_storage_full}), and then the registration's slot: a stale {@code If-Match} or an {@code If-None-Match: *} over a stored pack is {@code 412}, a full queue is {@code 503}
 * with {@code Retry-After}. It runs in {@code preHandle}, before the body is bound, so a refused request's body is never
 * read. A body that is still arriving holds no slot: the queue and the preconditions are checked now and again when the
 * controller has spooled the body and takes the slot.
 * The {@code 401} and {@code 403} are the guard's, before routing.
 */
@Component
public class RegistrationAdmission implements HandlerInterceptor, WebMvcConfigurer {

    /** The request attribute that holds the origin of an upload, for the audit entry of its job. */
    static final String ORIGIN = RegistrationAdmission.class.getName() + ".origin";

    /** The request attribute that holds the upload the admission opened. */
    static final String UPLOAD_OPEN = RegistrationAdmission.class.getName() + ".upload";

    /** The request attribute that holds the preconditions the admission judged. */
    static final String PRECONDITIONS = RegistrationAdmission.class.getName() + ".preconditions";

    private static final String UPLOAD = "registerPackFromArchive";
    private static final String SOURCE = "registerPackFromSource";
    private static final String DELETE_ARTIFACT = "deletePackArtifact";
    private static final String DELETE_RELATIONSHIP = "deletePackRelationship";
    private static final Pattern PACK_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}$");
    private static final Pattern IF_MATCH = Pattern.compile("^(\\*|\"sha256:[0-9a-f]{64}\")$");

    private final RegistrationSwitch registration;
    private final RegistrationJobs jobs;
    private final ServiceInfoQuery info;

    /**
     * An admission over the switch and the jobs.
     *
     * @param registration whether registration is on
     * @param jobs the registration jobs
     * @param info the use case that knows the worktree id
     */
    @Autowired
    public RegistrationAdmission(RegistrationSwitch registration, RegistrationJobs jobs, ServiceInfoQuery info) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.info = Objects.requireNonNull(info, "info");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        String name = method.getMethod().getName();
        if (SOURCE.equals(name)) {
            admitSource(request);
        } else if (UPLOAD.equals(name)) {
            admitUpload(request);
        } else if (DELETE_ARTIFACT.equals(name) || DELETE_RELATIONSHIP.equals(name)) {
            // ADR-0065 D13, FR-037: the pack's writes of this PR are one family under one switch. A removal reads no
            // body
            // and takes no slot, so the switch is the whole of its admission; the revision rules are the services'.
            requireRegistrationOn();
        }
        return true;
    }

    private void admitUpload(HttpServletRequest request) {
        AuditContext audit = AuditContext.of(request);
        requireRegistrationOn();
        String packId = packIdOf(request);
        audit.packId(packId);
        audit.source("archive", "");
        Preconditions preconditions = preconditionsOf(request);
        HttpHeaders headers = headersOf(request);
        ArchiveBodyAdvice.requireDeclaredLengthWithinLimit(headers);
        // The upload takes a place among the ten that may be open (a refusal is a 503 with Retry-After) and the room
        // the
        // volumes have beyond what the open ones will write (#418, F1). It holds no queue slot (#418, A4): a body that
        // is
        // still arriving holds none. The queue and the preconditions are judged now, so that a refusal comes before the
        // body is read, and again when the body is spooled.
        RegistrationJobs.Upload upload = jobs.openUpload(
                headers.getContentLength() >= 0
                        ? headers.getContentLength()
                        : RegistrationJobs.StorageRoom.UNKNOWN_BYTES);
        try {
            jobs.precheck(packId, preconditions);
        } catch (RuntimeException | Error refused) {
            upload.discard();
            throw refused;
        }
        request.setAttribute(UPLOAD_OPEN, upload);
        request.setAttribute(PRECONDITIONS, preconditions);
        request.setAttribute(
                ORIGIN,
                new RegistrationJobs.Origin(
                        info.worktree(), AuditContext.credentialOf(request), UPLOAD, "archive", ""));
    }

    /**
     * Gives the upload's place back when the request ends without the controller having spooled and adopted it (a body the
     * guard refused, a media type it did not take). Idempotent: the controller discards it too, and an adopted one stays.
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception failure) {
        if (request.getAttribute(UPLOAD_OPEN) instanceof RegistrationJobs.Upload upload) {
            upload.discard();
        }
    }

    /**
     * The checks of {@code POST /packs} that need no body: registration on, and the conditional headers. The target pack,
     * the kind and the slot come with the body, which the controller reads, so they are the controller's.
     */
    private void admitSource(HttpServletRequest request) {
        requireRegistrationOn();
        request.setAttribute(PRECONDITIONS, preconditionsOf(request));
    }

    private void requireRegistrationOn() {
        if (!registration.enabled()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_SOURCE_UNAVAILABLE,
                    "Registration is switched off: charter.registration.enabled is not true.");
        }
    }

    /**
     * The conditional headers the admission judged for this request.
     *
     * @param request the request
     * @return the preconditions
     * @throws IllegalStateException when the admission did not run
     */
    static Preconditions admittedPreconditions(HttpServletRequest request) {
        if (request.getAttribute(PRECONDITIONS) instanceof Preconditions preconditions) {
            return preconditions;
        }
        throw new IllegalStateException("the registration was not admitted");
    }

    /**
     * The upload the admission opened for this request: it holds a place among the open uploads and the directory the body
     * is spooled in.
     *
     * @param request the request
     * @return the upload
     * @throws IllegalStateException when the admission did not run
     */
    static RegistrationJobs.Upload admittedUpload(HttpServletRequest request) {
        if (request.getAttribute(UPLOAD_OPEN) instanceof RegistrationJobs.Upload upload) {
            return upload;
        }
        throw new IllegalStateException("the registration was not admitted");
    }

    /**
     * The origin the admission recorded for an upload.
     *
     * @param request the request
     * @return the origin
     * @throws IllegalStateException when the admission did not run
     */
    static RegistrationJobs.Origin originOf(HttpServletRequest request) {
        if (request.getAttribute(ORIGIN) instanceof RegistrationJobs.Origin origin) {
            return origin;
        }
        throw new IllegalStateException("the registration was not admitted");
    }

    private static String packIdOf(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Object value = variables instanceof Map<?, ?> map ? map.get("packId") : null;
        if (!(value instanceof String packId) || !PACK_ID.matcher(packId).matches()) {
            throw new InvalidParameter(
                    InvalidParameter.In.PATH,
                    "packId",
                    String.valueOf(value),
                    "which is not in the form this parameter accepts");
        }
        return packId;
    }

    private static Preconditions preconditionsOf(HttpServletRequest request) {
        String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (ifMatch != null && ifNoneMatch != null) {
            throw InvalidParameter.excluding(
                    InvalidParameter.In.HEADER, HttpHeaders.IF_MATCH, HttpHeaders.IF_NONE_MATCH);
        }
        if (ifNoneMatch != null && !ifNoneMatch.equals("*")) {
            throw new InvalidParameter(
                    InvalidParameter.In.HEADER, HttpHeaders.IF_NONE_MATCH, ifNoneMatch, "which is not *");
        }
        if (ifMatch != null && !IF_MATCH.matcher(ifMatch).matches()) {
            throw new InvalidParameter(
                    InvalidParameter.In.HEADER,
                    HttpHeaders.IF_MATCH,
                    ifMatch,
                    "which is neither * nor one quoted sha256 revision");
        }
        return ifMatch != null
                ? Preconditions.ifMatch(ifMatch)
                : ifNoneMatch != null ? Preconditions.createOnly() : Preconditions.none();
    }

    private static HttpHeaders headersOf(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        long length = request.getContentLengthLong();
        if (length >= 0) {
            headers.setContentLength(length);
        }
        return headers;
    }
}
