package ai.speckitty.charter.routing.application.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The routing application ring's configuration: it scans the application package, which makes the recommendation's
 * use-case service a bean. The service depends on the ring's port only; the adapter realizes it.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.routing.application")
public class RoutingApplicationConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public RoutingApplicationConfiguration() {}
}
