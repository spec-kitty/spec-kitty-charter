package ai.speckitty.charter.missions.infra.activations.config;

import org.springframework.context.annotation.Configuration;

/** The missions activations adapter's configuration. It holds no bean yet: the adapter over the activations application ring joins it. */
@Configuration(proxyBeanMethods = false)
public class MissionsActivationsConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public MissionsActivationsConfiguration() {}
}
