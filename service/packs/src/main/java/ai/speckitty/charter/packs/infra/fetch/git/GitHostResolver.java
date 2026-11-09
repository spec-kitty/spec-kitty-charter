package ai.speckitty.charter.packs.infra.fetch.git;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Name resolution as the git source's check sees it: the addresses of a host, all of which must be global unicast before
 * one is pinned for git. Production uses the JVM resolver, which {@code GlobalAddressResolverProvider} guards for every
 * allowlisted host; a test supplies the answer.
 */
@FunctionalInterface
public interface GitHostResolver {

    /**
     * The addresses of a host.
     *
     * @param host a normalised host name that is on the allowlist
     * @return the addresses
     * @throws UnknownHostException when the host does not resolve
     */
    List<InetAddress> resolve(String host) throws UnknownHostException;

    /**
     * The JVM resolver.
     *
     * @return the resolver
     */
    static GitHostResolver system() {
        return host -> List.of(InetAddress.getAllByName(host));
    }
}
