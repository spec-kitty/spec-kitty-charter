package ai.speckitty.charter.profiles.infra.api.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The profiles inbound adapter's configuration: it scans the {@code infra.api} package, which makes the profile
 * controller, its wire mapper and its JSON configuration beans. The shared problem and refusal handlers are the web
 * transport's; the Charter headers and the reserved-operation interceptor are served by the packs adapter and the
 * composition root, as for every controller.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.profiles.infra.api")
public class ProfilesApiConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ProfilesApiConfiguration() {}
}
