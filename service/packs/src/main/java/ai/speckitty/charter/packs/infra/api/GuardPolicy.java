package ai.speckitty.charter.packs.infra.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * What the {@link WorktreeGuard} accepts besides the token: the host names a request may give, whether it must give
 * one in {@code Host}, and the paths a {@code GET} may reach without the token. The guard knows these values, never
 * where they come from. The deployable chooses them by profile (ADR-0041 decisions 2, 6 and 7), so this module never
 * learns the profile.
 *
 * <ul>
 *   <li>{@link #loopback()} is the local daemon's policy (ADR-0018 decision 9): the loopback names, no required
 *       {@code Host}, no path without the token;
 *   <li>{@link #named(Collection)} is a configured list: a request must carry {@code Host}, and every name it gives
 *       must be on the list.
 * </ul>
 *
 * Host names are compared without regard to case, and the port is never judged. A path is exempt only for a
 * {@code GET} whose raw request URI is exactly that path, with no context path: nothing is decoded or normalized, so
 * a variant of the path ({@code /actuator/health/}, {@code /actuator/%68ealth}, {@code ;params}) still needs the
 * token.
 */
public final class GuardPolicy {

    private static final Set<String> LOOPBACK_NAMES = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private final Set<String> hosts;
    private final boolean hostHeaderRequired;
    private final Set<String> tokenExemptGets;

    private GuardPolicy(Set<String> hosts, boolean hostHeaderRequired, Set<String> tokenExemptGets) {
        this.hosts = Set.copyOf(hosts);
        this.hostHeaderRequired = hostHeaderRequired;
        this.tokenExemptGets = Set.copyOf(tokenExemptGets);
    }

    /**
     * The local daemon's policy: {@code localhost}, {@code 127.0.0.1} and {@code [::1]} (the server name may also be
     * the bare {@code ::1}), no required {@code Host}, every path behind the token.
     *
     * @return the loopback policy
     */
    public static GuardPolicy loopback() {
        return new GuardPolicy(LOOPBACK_NAMES, false, Set.of());
    }

    /**
     * A configured list of host names. A request must carry {@code Host}: without one, the servlet container judges
     * its own address, which no list should have to name.
     *
     * @param hosts the accepted host names, without a port; compared without regard to case
     * @return the policy, with every path behind the token
     * @throws IllegalArgumentException when the list is empty or holds a blank name
     */
    public static GuardPolicy named(Collection<String> hosts) {
        if (hosts.isEmpty()) {
            throw new IllegalArgumentException("a guard policy needs at least one host name");
        }
        Set<String> names = new LinkedHashSet<>();
        for (String host : hosts) {
            if (host.isBlank()) {
                throw new IllegalArgumentException("a guard policy's host name must not be blank");
            }
            names.add(lowerCase(host.strip()));
        }
        return new GuardPolicy(names, true, Set.of());
    }

    /**
     * This policy, with one more path a {@code GET} may reach without the token. The host and browser checks still
     * apply to it.
     *
     * @param path the exact raw request URI, starting with {@code /}
     * @return a new policy
     * @throws IllegalArgumentException when the path does not start with {@code /}
     */
    public GuardPolicy withTokenExemptGet(String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("a token-exempt path must start with /: " + path);
        }
        Set<String> paths = new LinkedHashSet<>(tokenExemptGets);
        paths.add(path);
        return new GuardPolicy(hosts, hostHeaderRequired, paths);
    }

    /**
     * Whether a host name, its port already removed, is accepted.
     *
     * @param name the host name a request gives
     * @return {@code true} when the name, ignoring case, is accepted
     */
    public boolean acceptsHost(String name) {
        return hosts.contains(lowerCase(name));
    }

    /**
     * Whether a request without a {@code Host} header is refused.
     *
     * @return {@code true} for a configured list
     */
    public boolean hostHeaderRequired() {
        return hostHeaderRequired;
    }

    /**
     * Whether a request may skip the token check: a {@code GET}, with no context path, whose raw request URI is
     * exactly an exempt path.
     *
     * @param request the request
     * @return {@code true} when the token is not asked for
     */
    public boolean exemptsFromToken(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
                && request.getContextPath().isEmpty()
                && tokenExemptGets.contains(request.getRequestURI());
    }

    /** Only case is folded on the request side: a name with inner whitespace stays unaccepted, as it always was. */
    private static String lowerCase(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "GuardPolicy[hosts=" + hosts + ", hostHeaderRequired=" + hostHeaderRequired + ", tokenExemptGets="
                + tokenExemptGets + "]";
    }
}
