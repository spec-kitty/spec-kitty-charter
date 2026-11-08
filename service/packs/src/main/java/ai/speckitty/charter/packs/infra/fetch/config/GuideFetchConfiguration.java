package ai.speckitty.charter.packs.infra.fetch.config;

import ai.speckitty.charter.packs.application.GuideFetcher;
import ai.speckitty.charter.packs.infra.fetch.GlobalAddressResolverProvider;
import ai.speckitty.charter.packs.infra.fetch.GuardedHttpsExchange;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import ai.speckitty.charter.packs.infra.fetch.GuideFetchProperties;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange;
import ai.speckitty.charter.packs.infra.fetch.HttpsGuideFetcher;
import ai.speckitty.charter.packs.infra.fetch.PackSourceProperties;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The outbound fetchers' configuration: binds {@code charter.guide-fetch.*} and {@code charter.pack-sources.*},
 * installs the union of every allowlist they name into the JVM-wide resolver guard ({@link
 * GlobalAddressResolverProvider}, which the JDK instantiates and Spring cannot inject), and supplies the
 * {@link GuideFetcher} port and the guarded exchange of the archive source. Each fetcher checks only its own allowlist;
 * the guard acts on the union, so a host that only the archive list names cannot escape the rebinding guard (squad 1).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GuideFetchProperties.class, PackSourceProperties.class})
public class GuideFetchConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public GuideFetchConfiguration() {}

    /**
     * The allowlist, installed into the resolver guard for as long as the context lives; closing the context
     * installs the empty allowlist again.
     *
     * @param guides the bound guide configuration
     * @param sources the bound pack-source configuration
     * @return the installed allowlist: the union of the guide, archive and git allowlists
     * @throws IllegalStateException when an allowlist names a host and the JVM did not install the resolver guard
     */
    @Bean
    InstalledAllowlist guideFetchAllowlist(GuideFetchProperties guides, PackSourceProperties sources) {
        GuideAllowlist allowlist = guides.allowlist().union(sources.allowlist());
        GlobalAddressResolverProvider.installRequiringGuard(allowlist);
        return new InstalledAllowlist(allowlist);
    }

    /**
     * The guarded HTTPS fetcher.
     *
     * @param properties the bound configuration
     * @param installed the allowlist the resolver guard acts on, installed before the fetcher can fetch
     * @return the fetcher, which checks the guide allowlist alone; Spring closes it with the context
     */
    @Bean
    HttpsGuideFetcher guideFetcher(GuideFetchProperties properties, InstalledAllowlist installed) {
        Objects.requireNonNull(installed, "installed");
        return HttpsGuideFetcher.create(
                properties.allowlist(),
                properties.timeout(),
                properties.maxSize().toBytes(),
                properties.maxRedirects());
    }

    /**
     * The guarded exchange of the https archive source: its own allowlist, deadline and redirect limit.
     *
     * @param sources the bound pack-source configuration
     * @param installed the allowlist the resolver guard acts on, installed before the exchange can fetch
     * @return the exchange
     */
    @Bean
    HttpsExchange archiveExchange(PackSourceProperties sources, InstalledAllowlist installed) {
        Objects.requireNonNull(installed, "installed");
        PackSourceProperties.Https https = sources.https();
        return GuardedHttpsExchange.create(https.allowlist(), https.timeout(), https.maxRedirects());
    }

    /**
     * The allowlist installed into the resolver guard; uninstalled when the context closes.
     *
     * @param allowlist the installed allowlist
     */
    record InstalledAllowlist(GuideAllowlist allowlist) implements AutoCloseable {
        @Override
        public void close() {
            if (GlobalAddressResolverProvider.installed().equals(allowlist)) {
                GlobalAddressResolverProvider.install(GuideAllowlist.EMPTY);
            }
        }
    }
}
