package ai.speckitty.charter.profiles.infra.packs.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** The profiles packs adapter's configuration: it scans {@code profiles.infra.packs}, which makes the adapter a bean. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.profiles.infra.packs")
public class ProfilesPacksConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ProfilesPacksConfiguration() {}
}
