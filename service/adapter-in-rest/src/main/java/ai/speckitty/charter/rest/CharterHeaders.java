package ai.speckitty.charter.rest;

import ai.speckitty.charter.read.ServiceInfoQuery;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the {@code Charter-Contract-Version} and {@code Charter-Worktree} headers on every response (ADR-0011). It
 * reads no pack file and digests nothing, so it cannot hide or replace the answer of a read that fails.
 *
 * <p>{@code Charter-Source-Fingerprint} is not set here. Each read sets it, once, on its 200 answer, to the digest it
 * verified ({@link #fingerprint}); a refusal, a bad request or any other failure carries none.
 */
@Component
public class CharterHeaders extends OncePerRequestFilter {

    static final String CONTRACT_VERSION = "Charter-Contract-Version";
    static final String WORKTREE = "Charter-Worktree";
    static final String SOURCE_FINGERPRINT = "Charter-Source-Fingerprint";

    private final ServiceInfoQuery info;

    /**
     * A filter that names the worktree the service serves.
     *
     * @param info the use case that knows the worktree id; its pack files are not read here
     */
    public CharterHeaders(ServiceInfoQuery info) {
        this.info = info;
    }

    /**
     * The headers of a 200 answer of a read: the source fingerprint it verified, set once.
     *
     * @param sourceFingerprint the digest the read verified
     * @return headers holding the one {@code Charter-Source-Fingerprint} value
     */
    static HttpHeaders fingerprint(String sourceFingerprint) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(SOURCE_FINGERPRINT, sourceFingerprint);
        return headers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(CONTRACT_VERSION, ContractVersion.value());
        response.setHeader(WORKTREE, info.worktree());
        chain.doFilter(request, response);
    }
}
