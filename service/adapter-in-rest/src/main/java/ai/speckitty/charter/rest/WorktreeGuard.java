package ai.speckitty.charter.rest;

import ai.speckitty.charter.api.model.Problem;
import ai.speckitty.charter.read.ServiceInfoQuery;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * The gate in front of every operation (ADR-0011, D9): the service answers one client, the one that holds the
 * worktree token, and never a browser. In this order:
 *
 * <ol>
 *   <li>a host name that is not {@code localhost}, {@code 127.0.0.1} or {@code [::1]} is a 403. This is what stops
 *       DNS rebinding, because a same-origin {@code GET} carries no {@code Origin}. Only the name is judged, never
 *       the port;
 *   <li>an {@code Origin}, {@code Sec-Fetch-Site} or {@code Sec-Fetch-Mode} header is a 403;
 *   <li>when no token is configured (possible only with the development flag, see {@code GuardWiring}) the request
 *       passes;
 *   <li>a missing, malformed or wrong {@code Authorization: Bearer} credential is a 401 that names the scheme in
 *       {@code WWW-Authenticate}.
 * </ol>
 *
 * The browser checks come first and are always on, so a browser is not told whether a token would have been
 * accepted. A refusal is answered here: the filter writes the contract-version and worktree headers and the
 * {@code Problem} body itself, never calls the chain, reads no pack file and sets no {@code Charter-Source-Fingerprint}.
 * It is registered at the highest precedence for every request dispatch, so an unknown path is a 401 before it is a
 * 404.
 *
 * <p>What it does not cover is what the servlet container answers before any filter runs: a malformed request the
 * connector rejects (a bad path encoding, an oversized header, a missing {@code Host}) is a 400 {@code Problem}
 * ({@link ProblemErrorReportValve}) that carries no Charter header. The contract says so.
 *
 * <p>The token is never logged, echoed or put in a header.
 */
public class WorktreeGuard extends OncePerRequestFilter {

    private static final Set<String> LOOPBACK_NAMES = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
    private static final List<String> BROWSER_HEADERS = List.of("Origin", "Sec-Fetch-Site", "Sec-Fetch-Mode");
    private static final String BEARER = "Bearer";
    private static final String BEARER_PREFIX = BEARER + " ";

    private final @Nullable WorktreeToken token;
    private final ServiceInfoQuery info;
    private final Supplier<ObjectMapper> mapper;

    /**
     * A guard for the worktree {@code info} names.
     *
     * @param token the worktree token, or {@code null} to enforce none (the development flag); the browser checks
     *     stay on
     * @param info the use case that knows the worktree id; its pack files are never read here
     * @param mapper the application's JSON mapper, resolved when a refusal is first written, so the problem bodies
     *     serialize as every other problem body does
     */
    public WorktreeGuard(@Nullable WorktreeToken token, ServiceInfoQuery info, Supplier<ObjectMapper> mapper) {
        this.token = token;
        this.info = info;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!fromLoopbackName(request) || sentByBrowser(request)) {
            refuse(response, HttpStatus.FORBIDDEN);
        } else if (token != null && !bearsToken(request, token)) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER);
            refuse(response, HttpStatus.UNAUTHORIZED);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static boolean sentByBrowser(HttpServletRequest request) {
        for (String header : BROWSER_HEADERS) {
            if (request.getHeaders(header).hasMoreElements()) {
                return true;
            }
        }
        return false;
    }

    /** Every name the request gives for the host, the server name and each {@code Host} header, must be loopback. */
    private static boolean fromLoopbackName(HttpServletRequest request) {
        String serverName = request.getServerName();
        if (serverName == null || !isLoopbackName(serverName)) {
            return false;
        }
        return Collections.list(request.getHeaders(HttpHeaders.HOST)).stream().allMatch(WorktreeGuard::isLoopbackName);
    }

    private static boolean isLoopbackName(String hostValue) {
        return LOOPBACK_NAMES.contains(hostName(hostValue.strip()).toLowerCase(Locale.ROOT));
    }

    /**
     * The name of a {@code Host} value without its port. A value that is not {@code name}, {@code name:digits},
     * {@code [ipv6]} or {@code [ipv6]:digits} is returned whole, so that it names no loopback host.
     */
    private static String hostName(String value) {
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end > 0 && isPortSuffix(value.substring(end + 1)) ? value.substring(0, end + 1) : value;
        }
        int colon = value.indexOf(':');
        if (colon < 0 || colon != value.lastIndexOf(':')) {
            return value;
        }
        return isPortSuffix(value.substring(colon)) ? value.substring(0, colon) : value;
    }

    private static boolean isPortSuffix(String rest) {
        return rest.isEmpty() || rest.matches(":\\d*");
    }

    /** A single {@code Authorization: Bearer <token>} header, the scheme matched without regard to case. */
    private static boolean bearsToken(HttpServletRequest request, WorktreeToken expected) {
        List<String> values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (values.size() != 1) {
            return false;
        }
        String value = values.getFirst();
        return value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && expected.matches(value.substring(BEARER_PREFIX.length()));
    }

    private void refuse(HttpServletResponse response, HttpStatus status) throws IOException {
        byte[] body = mapper.get().writeValueAsBytes(new Problem(Problems.title(status), status.value()));
        response.setStatus(status.value());
        response.setHeader(CharterHeaders.CONTRACT_VERSION, ContractVersion.value());
        response.setHeader(CharterHeaders.WORKTREE, info.worktree());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.flushBuffer();
    }
}
