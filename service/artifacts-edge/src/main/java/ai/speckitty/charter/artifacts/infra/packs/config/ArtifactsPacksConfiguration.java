package ai.speckitty.charter.artifacts.infra.packs.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** The artifacts packs adapter's configuration: it scans the {@code infra.packs} package, the adapter over the packs application ring. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.artifacts.infra.packs")
public class ArtifactsPacksConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ArtifactsPacksConfiguration() {}
}
