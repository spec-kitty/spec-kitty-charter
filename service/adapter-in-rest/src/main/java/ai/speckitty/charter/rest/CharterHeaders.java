package ai.speckitty.charter.rest;

import ai.speckitty.charter.read.ServiceInfoQuery;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the {@code Charter-Contract-Version}, {@code Charter-Worktree} and {@code Charter-Source-Fingerprint} headers
 * on every response (ADR-0011). The fingerprint is left out when a pack file cannot be read: the read itself then
 * answers why, so the header never hides or replaces that answer.
 */
@Component
public class CharterHeaders extends OncePerRequestFilter {

    static final String CONTRACT_VERSION = "Charter-Contract-Version";
    static final String WORKTREE = "Charter-Worktree";
    static final String SOURCE_FINGERPRINT = "Charter-Source-Fingerprint";

    private final ServiceInfoQuery info;

    /**
     * A filter over the service info.
     *
     * @param info the use case that names the worktree and fingerprints the pack files
     */
    public CharterHeaders(ServiceInfoQuery info) {
        this.info = info;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(CONTRACT_VERSION, ContractVersion.value());
        response.setHeader(WORKTREE, info.worktree());
        try {
            response.setHeader(SOURCE_FINGERPRINT, info.answer().sourceFingerprint());
        } catch (RuntimeException unreadable) {
            // The read reports the failure as its own refusal; the header is only left out.
            logger.debug("No source fingerprint for this response", unreadable);
        }
        chain.doFilter(request, response);
    }
}
