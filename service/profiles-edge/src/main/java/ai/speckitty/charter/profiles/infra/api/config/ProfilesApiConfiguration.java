package ai.speckitty.charter.profiles.infra.api.config;

import org.springframework.context.annotation.Configuration;

/** The profiles inbound adapter's configuration. It holds no bean yet: the controller joins it with the read endpoints. */
@Configuration(proxyBeanMethods = false)
public class ProfilesApiConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ProfilesApiConfiguration() {}
}
