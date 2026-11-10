package ai.speckitty.charter.missions.infra.packs.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** The missions packs adapter's configuration: it scans the {@code infra.packs} package, the adapter over the packs application ring. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.missions.infra.packs")
public class MissionsPacksConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public MissionsPacksConfiguration() {}
}
