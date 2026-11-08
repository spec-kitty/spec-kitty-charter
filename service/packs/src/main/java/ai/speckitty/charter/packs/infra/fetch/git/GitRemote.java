package ai.speckitty.charter.packs.infra.fetch.git;

import java.net.InetAddress;

/**
 * Where git is sent for a repository the service has checked: the location it is given after {@code --} and what the run
 * adds to the fixed pins and environment. This is the seam of the git source: production is {@link #https()}, which gives
 * the canonical URL and pins the checked address; the contract scenarios and the git-host tests map the repository to a
 * local one, with the transport that repository needs, and so exercise the rest of the source against real git without
 * the network.
 */
@FunctionalInterface
public interface GitRemote {

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
     * The production remote: the canonical https URL, with the checked address pinned.
     *
     * @return the remote
     */
    static GitRemote https() {
        return (repository, address) -> new Target(repository.url(), GitTransport.NONE);
    }
}
