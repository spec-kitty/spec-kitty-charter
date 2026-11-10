package ai.speckitty.charter.missions.infra.api.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The missions inbound adapter's configuration: it scans the {@code infra.api} package, which makes the mission-type
 * controller, its link builder and its JSON configuration beans. The shared problem and refusal handlers are the web
 * transport's; the Charter headers are served by the packs adapter, as for every controller.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.missions.infra.api")
public class MissionsApiConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public MissionsApiConfiguration() {}
}
