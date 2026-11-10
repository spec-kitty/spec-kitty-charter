package ai.speckitty.charter.profiles.infra.activations.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The profiles activations adapter's configuration: it scans {@code profiles.infra.activations}, which makes the adapter
 * over the activations application ring a bean.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.profiles.infra.activations")
public class ProfilesActivationsConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ProfilesActivationsConfiguration() {}
}
