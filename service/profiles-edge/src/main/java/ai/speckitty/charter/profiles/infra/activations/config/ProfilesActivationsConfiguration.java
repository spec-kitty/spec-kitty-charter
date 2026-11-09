package ai.speckitty.charter.profiles.infra.activations.config;

import org.springframework.context.annotation.Configuration;

/** The profiles activations adapter's configuration. It holds no bean yet: the adapter over the activations application ring joins it. */
@Configuration(proxyBeanMethods = false)
public class ProfilesActivationsConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ProfilesActivationsConfiguration() {}
}
