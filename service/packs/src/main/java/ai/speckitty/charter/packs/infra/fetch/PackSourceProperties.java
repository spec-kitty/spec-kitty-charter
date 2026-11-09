package ai.speckitty.charter.packs.infra.fetch;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * The sources a pack registration may fetch from: {@code charter.pack-sources.*} under the one configuration root
 * (ADR-0038, IC-05, IC-18). Every kind is off until its own {@code enabled} flag is on, and every allowlist and root is
 * empty by default, so a service that was never configured for a source refuses it.
 *
 * @param https the https archive source ({@code charter.pack-sources.https})
 * @param git the git source ({@code charter.pack-sources.git})
 * @param filesystem the local path source ({@code charter.pack-sources.filesystem})
 */
@ConfigurationProperties("charter.pack-sources")
public record PackSourceProperties(
        @DefaultValue Https https,
        @DefaultValue Git git,
        @DefaultValue Filesystem filesystem) {

    /** The archive bound of a registration, 32 MiB (NFR-004), which the free-space check is sized from. */
    private static final long ARCHIVE_BOUND_BYTES = 32L * 1024 * 1024;

    private static final String HTTPS_HOSTS = "charter.pack-sources.https.allowed-hosts";
    private static final String GIT_HOSTS = "charter.pack-sources.git.allowed-hosts";

    /**
     * The https archive source.
     *
     * @param enabled whether the kind is switched on; off by default
     * @param allowedHosts the hosts an archive may be fetched from, matched exactly after lower-casing and stripping a
     *     trailing dot; empty by default, and every URL is then refused ({@code host_not_allowed})
     * @param timeout the deadline over the whole fetch: resolution, every hop and the body (60 s)
     * @param maxSize the largest archive accepted (32 MiB; a larger value fails the start, because the free-space check
     *     is sized from that bound)
     * @param maxRedirects the most redirects followed (3)
     */
    public record Https(
            @DefaultValue("false") boolean enabled,
            @DefaultValue List<String> allowedHosts,
            @DefaultValue("60s") Duration timeout,
            @DefaultValue("32MB") DataSize maxSize,
            @DefaultValue("3") int maxRedirects) {

        /**
         * The bound configuration.
         *
         * @param enabled whether the kind is switched on
         * @param allowedHosts the allowed hosts; copied
         * @param timeout the deadline over the whole fetch
         * @param maxSize the largest archive accepted
         * @param maxRedirects the most redirects followed
         * @throws IllegalArgumentException when {@code maxSize} is over 32 MiB, the archive bound the free-space check of a
         *     registration is sized from
         */
        public Https {
            if (maxSize.toBytes() > ARCHIVE_BOUND_BYTES) {
                throw new IllegalArgumentException("charter.pack-sources.https.max-size is " + maxSize.toBytes()
                        + " bytes; it is at most 32 MiB, the archive bound the free-space check is sized from.");
            }
            allowedHosts = List.copyOf(allowedHosts);
        }

        /**
         * The allowlist the properties configure.
         *
         * @return the normalised allowlist
         */
        public GuideAllowlist allowlist() {
            return GuideAllowlist.of(allowedHosts, HTTPS_HOSTS);
        }
    }

    /**
     * The git source.
     *
     * @param enabled whether the kind is switched on; off by default
     * @param allowedHosts the hosts a repository may be fetched from; empty by default
     * @param timeout the deadline over the whole fetch: lookup, wait for a slot and every git command (60 s)
     * @param executable the absolute path of the git binary, or unset to look for {@code git} on the {@code PATH}; the
     *     binary must be 2.50.1 or later, or the git source answers {@code 503 pack_source_unavailable} (NFR-011)
     */
    public record Git(
            @DefaultValue("false") boolean enabled,
            @DefaultValue List<String> allowedHosts,
            @DefaultValue("60s") Duration timeout,
            @Nullable Path executable) {

        /**
         * The bound configuration.
         *
         * @param enabled whether the kind is switched on
         * @param allowedHosts the allowed hosts; copied
         * @param timeout the deadline over the whole fetch
         * @param executable the git binary, or null to look on the {@code PATH}
         */
        public Git {
            allowedHosts = List.copyOf(allowedHosts);
        }

        /**
         * The allowlist the properties configure.
         *
         * @return the normalised allowlist
         */
        public GuideAllowlist allowlist() {
            return GuideAllowlist.of(allowedHosts, GIT_HOSTS);
        }
    }

    /**
     * The local path source.
     *
     * @param enabled whether the kind is switched on; off by default
     * @param root the directory a path is read under; unset by default, and the source is then refused
     */
    public record Filesystem(
            @DefaultValue("false") boolean enabled,
            @Nullable Path root) {}

    /**
     * Every host an archive or a git fetch may go to: the set the resolver guard must act on besides the guides'.
     *
     * @return the union of the https and git allowlists
     */
    public GuideAllowlist allowlist() {
        return https.allowlist().union(git.allowlist());
    }
}
