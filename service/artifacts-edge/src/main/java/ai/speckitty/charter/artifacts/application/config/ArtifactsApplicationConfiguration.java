package ai.speckitty.charter.artifacts.application.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** The artifacts application ring's configuration: it scans the {@code application} package, which makes the asset, skill and references reads beans. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.artifacts.application")
public class ArtifactsApplicationConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ArtifactsApplicationConfiguration() {}
}
