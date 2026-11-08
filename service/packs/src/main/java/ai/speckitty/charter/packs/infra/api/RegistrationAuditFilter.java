package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes the audit entry of a registration or {@code DELETE} attempt that ends at the web edge (NFR-010, IC-18): every
 * answer but the {@code 202} of a registration, whichever rule refused it and wherever it was refused (the packId
 * grammar, the body size, the media type, a stale precondition, a full queue, a framework refusal before any handler),
 * because the filter looks at the answer and not at the code that gave it. A registration the executor took over
 * ({@link AuditContext#handedToJob}) is entered by its job when it ends, so an attempt is entered once. The guard, which
 * runs before this filter, enters the {@code 401} and {@code 403} it answers itself.
 */
@Component
public class RegistrationAuditFilter extends OncePerRequestFilter {

    private final AuditLog audit;
    private final ServiceInfoQuery info;
    private final RegistrationPaths paths;
    private final Clock clock;

    /**
     * A filter over the audit log.
     *
     * @param audit the audit log
     * @param info the use case that knows the worktree id
     * @param basePath the API base path
     */
    @Autowired
    RegistrationAuditFilter(
            AuditLog audit, ServiceInfoQuery info, @Value("${charter.api.base-path:/api/v1}") String basePath) {
        this(audit, info, basePath, Clock.systemUTC());
    }

    RegistrationAuditFilter(AuditLog audit, ServiceInfoQuery info, String basePath, Clock clock) {
        this.audit = audit;
        this.info = info;
        this.paths = new RegistrationPaths(basePath);
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<RegistrationPaths.Attempt> attempt = paths.classify(request.getMethod(), request.getRequestURI());
        if (attempt.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        AuditContext context = AuditContext.of(request);
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException failure) {
            record(request, attempt.get(), context, 500);
            throw failure;
        }
        if (!context.isHandedToJob()) {
            record(request, attempt.get(), context, response.getStatus());
        }
    }

    private void record(
            HttpServletRequest request, RegistrationPaths.Attempt attempt, AuditContext context, int status) {
        String outcome = status >= 200 && status < 300 ? "succeeded:" + status : "refused:" + status;
        Instant now = clock.instant();
        audit.record(new AuditLog.Entry(
                now,
                info.worktree(),
                AuditContext.credentialOf(request),
                attempt.operation(),
                context.packIdOr(attempt.packId()),
                context.sourceKind(attempt.sourceKind()),
                context.locator(),
                outcome,
                context.revisionBefore(),
                context.revisionAfter()));
    }
}
