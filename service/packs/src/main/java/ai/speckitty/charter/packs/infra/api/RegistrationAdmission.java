package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The synchronous checks of {@code PUT /packs/{packId}} that come before its body is read (IC-14, IC-18), in the order the
 * contract lists them, each its own answer: registration switched off ({@code 503 pack_source_unavailable}), the
 * {@code packId} grammar ({@code 400}, decision DM-01M6TV6VDNHNCVWRHHC74WATV0), the conditional headers ({@code 400} for
 * a malformed one or both together), a declared length over 32 MiB ({@code 413}), and then the registration's slot: a
 * stale {@code If-Match} or an {@code If-None-Match: *} over a stored pack is {@code 412}, a full queue is {@code 503}
 * with {@code Retry-After}. It runs in {@code preHandle}, before the body is bound, so a refused request's body is never
 * read; the slot it takes is handed to the controller, and given back at the end of a request that did not start its job.
 * The {@code 401} and {@code 403} are the guard's, before routing.
 */
@Component
public class RegistrationAdmission implements HandlerInterceptor, WebMvcConfigurer {

    /** The request attribute that holds the slot the admission took. */
    static final String RESERVATION = RegistrationAdmission.class.getName() + ".reservation";

    private static final String UPLOAD = "registerPackFromArchive";
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
        if (!(handler instanceof HandlerMethod method)
                || !UPLOAD.equals(method.getMethod().getName())) {
            return true;
        }
        AuditContext audit = AuditContext.of(request);
        if (!registration.enabled()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_SOURCE_UNAVAILABLE,
                    "Registration is switched off: charter.registration.enabled is not true.");
        }
        String packId = packIdOf(request);
        audit.packId(packId);
        audit.source("archive", "");
        Preconditions preconditions = preconditionsOf(request);
        ArchiveBodyAdvice.requireDeclaredLengthWithinLimit(headersOf(request));
        RegistrationJobs.Origin origin =
                new RegistrationJobs.Origin(info.worktree(), AuditContext.credentialOf(request), UPLOAD, "archive", "");
        RegistrationJobs.Reservation reservation = jobs.reserve(packId, preconditions, origin);
        request.setAttribute(RESERVATION, reservation);
        audit.revisionBefore(reservation.revisionBefore());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception failure) {
        if (request.getAttribute(RESERVATION) instanceof RegistrationJobs.Reservation reservation) {
            // A reservation whose job started is not touched by this: abandon only gives back a slot never started.
            reservation.abandon();
        }
    }

    /**
     * The slot the admission took for this request.
     *
     * @param request the request
     * @return the reservation
     * @throws IllegalStateException when the admission did not run
     */
    static RegistrationJobs.Reservation reservationOf(HttpServletRequest request) {
        if (request.getAttribute(RESERVATION) instanceof RegistrationJobs.Reservation reservation) {
            return reservation;
        }
        throw new IllegalStateException("the registration was not admitted");
    }

    private static String packIdOf(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Object value = variables instanceof Map<?, ?> map ? map.get("packId") : null;
        if (!(value instanceof String packId) || !PACK_ID.matcher(packId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The pack id must match " + PACK_ID.pattern() + ".");
        }
        return packId;
    }

    private static Preconditions preconditionsOf(HttpServletRequest request) {
        String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (ifMatch != null && ifNoneMatch != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "If-Match and If-None-Match: * exclude each other.");
        }
        if (ifNoneMatch != null && !ifNoneMatch.equals("*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "If-None-Match takes only *.");
        }
        if (ifMatch != null && !IF_MATCH.matcher(ifMatch).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "If-Match is a quoted sha256 revision, or *.");
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
