package ai.speckitty.charter.packs.infra.fetch.config;

import ai.speckitty.charter.packs.application.GuideFetcher;
import ai.speckitty.charter.packs.infra.fetch.GlobalAddressResolverProvider;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import ai.speckitty.charter.packs.infra.fetch.GuideFetchProperties;
import ai.speckitty.charter.packs.infra.fetch.HttpsGuideFetcher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The guide fetcher's configuration: binds {@code charter.guide-fetch.*}, installs the allowlist into the JVM-wide
 * resolver guard ({@link GlobalAddressResolverProvider}, which the JDK instantiates and Spring cannot inject) and
 * supplies the {@link GuideFetcher} port.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GuideFetchProperties.class)
public class GuideFetchConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public GuideFetchConfiguration() {}

    /**
     * The allowlist, installed into the resolver guard for as long as the context lives; closing the context
     * installs the empty allowlist again.
     *
     * @param properties the bound configuration
     * @return the installed allowlist
     * @throws IllegalStateException when the allowlist names a host and the JVM did not install the resolver guard
     */
    @Bean
    InstalledAllowlist guideFetchAllowlist(GuideFetchProperties properties) {
        GuideAllowlist allowlist = properties.allowlist();
        GlobalAddressResolverProvider.installRequiringGuard(allowlist);
        return new InstalledAllowlist(allowlist);
    }

    /**
     * The guarded HTTPS fetcher.
     *
     * @param properties the bound configuration
     * @param installed the allowlist the resolver guard acts on, the same the fetcher checks
     * @return the fetcher; Spring closes it (and its client) with the context
     */
    @Bean
    HttpsGuideFetcher guideFetcher(GuideFetchProperties properties, InstalledAllowlist installed) {
        return HttpsGuideFetcher.create(
                installed.allowlist(),
                properties.timeout(),
                properties.maxSize().toBytes(),
                properties.maxRedirects());
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
