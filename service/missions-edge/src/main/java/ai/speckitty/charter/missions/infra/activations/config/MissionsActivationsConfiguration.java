package ai.speckitty.charter.missions.infra.activations.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The missions activations adapter's configuration: it scans {@code missions.infra.activations}, which makes the adapter
 * over the activations application ring a bean.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.missions.infra.activations")
public class MissionsActivationsConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public MissionsActivationsConfiguration() {}
}
