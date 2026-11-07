package ai.speckitty.charter.packs.infra.fetch;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * The sources of a pack registration (skeleton: the shape the tests bind, built next): {@code charter.pack-sources.*}.
 *
 * @param https the https archive source
 * @param git the git source
 * @param filesystem the local path source
 */
@ConfigurationProperties("charter.pack-sources")
public record PackSourceProperties(
        @DefaultValue Https https,
        @DefaultValue Git git,
        @DefaultValue Filesystem filesystem) {

    /**
     * The https archive source.
     *
     * @param enabled whether the kind is switched on
     * @param allowedHosts the hosts an archive may be fetched from
     * @param timeout the deadline over the whole exchange
     * @param maxSize the largest archive accepted
     * @param maxRedirects the most redirects followed
     */
    public record Https(
            boolean enabled, List<String> allowedHosts, Duration timeout, DataSize maxSize, int maxRedirects) {}

    /**
     * The git source.
     *
     * @param enabled whether the kind is switched on
     * @param allowedHosts the hosts a repository may be fetched from
     */
    public record Git(boolean enabled, List<String> allowedHosts) {}

    /**
     * The local path source.
     *
     * @param enabled whether the kind is switched on
     * @param root the directory a path is read under
     */
    public record Filesystem(boolean enabled, @Nullable Path root) {}
}
