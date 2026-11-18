package ai.speckitty.charter.routing.infra.api.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The routing inbound adapter's configuration: it scans the {@code infra.api} package, which makes the recommendation
 * controller, its wire mapper and its JSON configuration beans. The shared problem and refusal handlers are the web
 * transport's; the Charter headers and the reserved-operation interceptor are served as for every controller.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.routing.infra.api")
public class RoutingApiConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public RoutingApiConfiguration() {}
}
