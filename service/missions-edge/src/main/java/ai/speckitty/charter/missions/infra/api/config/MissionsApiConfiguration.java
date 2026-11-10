package ai.speckitty.charter.missions.infra.api.config;

import org.springframework.context.annotation.Configuration;

/** The missions inbound adapter's configuration. It holds no bean yet: the controller joins it with the mission-type reads. */
@Configuration(proxyBeanMethods = false)
public class MissionsApiConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public MissionsApiConfiguration() {}
}
