package ai.speckitty.charter.missions.infra.packs.config;

import org.springframework.context.annotation.Configuration;

/** The missions packs adapter's configuration. It holds no bean yet: the adapter over the packs application ring joins it. */
@Configuration(proxyBeanMethods = false)
public class MissionsPacksConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public MissionsPacksConfiguration() {}
}
