package ai.speckitty.charter.activations.application.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The activation application ring's configuration: it scans the application package, which makes each stereotyped
 * use-case service a bean. The services depend on the domain and on the {@code AvailablePacks} port only; the
 * adapters realize them.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.activations.application")
public class ActivationApplicationConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ActivationApplicationConfiguration() {}
}
