package ai.speckitty.charter.packs.infra.fetch.git;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * Where git is sent for a repository the service has checked: the location it is given after {@code --} and what the run
 * adds to the fixed pins and environment. This is the seam of the git source: production is {@link #https()}, which gives
 * the canonical URL and pins the checked address; the contract scenarios and the git-host tests map the repository to a
 * local one, with the transport that repository needs, and so exercise the rest of the source against real git without
 * the network.
 */
@FunctionalInterface
public interface GitRemote {

    /** The only port git is sent to, and the one the pin names. */
    int HTTPS_PORT = 443;

    /**
     * What git is given for a repository.
     *
     * @param location the argument git fetches from, after {@code --}
     * @param transport the pins and variables added to the run
     */
    record Target(String location, GitTransport transport) {}

    /**
     * The target of a repository whose host the service resolved and checked.
     *
     * @param repository the repository, rebuilt from the parsed URL
     * @param address the one checked address git may connect to
     * @return what git is given
     */
    Target target(GitRepository repository, InetAddress address);

    /**
     * The production remote: the canonical https URL, with the checked address pinned for git's own connection
     * ({@code http.curloptResolve=<host>:443:<address>}), so git cannot resolve the host again and be answered another
     * address (rebinding). The pin is built from the canonical host, never from the URL as sent.
     *
     * @return the remote
     */
    static GitRemote https() {
        return (repository, address) -> {
            String literal = address instanceof Inet6Address
                    ? "[" + address.getHostAddress() + "]"
                    : address.getHostAddress();
            String pin = "http.curloptResolve=" + repository.host() + ":" + HTTPS_PORT + ":" + literal;
            return new Target(repository.url(), new GitTransport(List.of(pin), Map.of()));
        };
    }
}
