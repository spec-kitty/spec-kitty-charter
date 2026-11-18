package ai.speckitty.charter.routing.infra.activations.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The routing activations adapter's configuration: it scans {@code routing.infra.activations}, which makes the adapter
 * over the activations application ring a bean.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.routing.infra.activations")
public class RoutingActivationsConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public RoutingActivationsConfiguration() {}
}
