package ai.speckitty.charter.missions.application.config;

import org.springframework.context.annotation.Configuration;

/** The missions application ring's configuration. It holds no bean yet: the use cases join it with the mission-type reads. */
@Configuration(proxyBeanMethods = false)
public class MissionsApplicationConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public MissionsApplicationConfiguration() {}
}
