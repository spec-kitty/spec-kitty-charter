package ai.speckitty.charter.profiles.application.config;

import org.springframework.context.annotation.Configuration;

/** The profiles application ring's configuration. It holds no bean yet: the use cases join it with the read endpoints. */
@Configuration(proxyBeanMethods = false)
public class ProfilesApplicationConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ProfilesApplicationConfiguration() {}
}
