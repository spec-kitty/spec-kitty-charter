package ai.speckitty.charter.activations.infra.api.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The activation REST edge's configuration: it scans the {@code infra.api} package, which makes the activation
 * controller and the activation JSON configuration beans. The problem handlers, the Charter headers and the
 * reserved-operation interceptor are served by the packs adapter and the composition root, as for every controller.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.activations.infra.api")
public class ActivationApiConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ActivationApiConfiguration() {}
}
