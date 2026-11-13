package ai.speckitty.charter.artifacts.infra.api.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The artifacts inbound adapter's configuration: it scans the {@code infra.api} package, which makes the asset, skill
 * and references controllers and their link builders beans. The shared problem and refusal handlers are the web
 * transport's; the Charter headers are served by the packs adapter, as for every controller.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.artifacts.infra.api")
public class ArtifactsApiConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ArtifactsApiConfiguration() {}
}
