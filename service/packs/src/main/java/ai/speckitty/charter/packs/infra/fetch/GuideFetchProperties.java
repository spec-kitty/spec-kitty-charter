package ai.speckitty.charter.packs.infra.fetch;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * How guides are fetched from a URI: {@code charter.guide-fetch.*} under the one configuration root (ADR-0038).
 *
 * @param allowedHosts the hosts a guide may be fetched from, matched exactly after lower-casing and stripping a
 *     trailing dot; empty by default, and every URI is then refused ({@code HOST_NOT_ALLOWED})
 * @param timeout the deadline over the whole exchange: resolution, every hop and the body (10 s)
 * @param maxSize the largest guide accepted (1 MiB)
 * @param maxRedirects the most redirects followed (3)
 */
@ConfigurationProperties("charter.guide-fetch")
public record GuideFetchProperties(
        @DefaultValue List<String> allowedHosts,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("1MB") DataSize maxSize,
        @DefaultValue("3") int maxRedirects) {

    /**
     * The bound configuration.
     *
     * @param allowedHosts the allowed hosts; copied
     * @param timeout the deadline over the whole exchange
     * @param maxSize the largest guide accepted
     * @param maxRedirects the most redirects followed
     */
    public GuideFetchProperties {
        allowedHosts = List.copyOf(allowedHosts);
    }

    /**
     * The allowlist the properties configure.
     *
     * @return the normalised allowlist
     */
    public GuideAllowlist allowlist() {
        return GuideAllowlist.of(allowedHosts);
    }
}
