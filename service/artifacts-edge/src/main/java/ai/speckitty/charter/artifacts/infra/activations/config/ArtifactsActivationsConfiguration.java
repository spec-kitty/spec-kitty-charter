package ai.speckitty.charter.artifacts.infra.activations.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** The artifacts activations adapter's configuration: it scans {@code artifacts.infra.activations}, which makes the adapter over the activations application ring a bean. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.artifacts.infra.activations")
public class ArtifactsActivationsConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ArtifactsActivationsConfiguration() {}
}
