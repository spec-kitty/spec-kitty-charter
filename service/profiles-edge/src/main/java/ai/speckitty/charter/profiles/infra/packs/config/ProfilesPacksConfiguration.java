package ai.speckitty.charter.profiles.infra.packs.config;

import org.springframework.context.annotation.Configuration;

/** The profiles packs adapter's configuration. It holds no bean yet: the adapter over the packs application ring joins it. */
@Configuration(proxyBeanMethods = false)
public class ProfilesPacksConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ProfilesPacksConfiguration() {}
}
