package ai.speckitty.charter.activations.infra.packs.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** The packs adapter's configuration: it scans {@code activations.infra.packs}, which makes the adapter a bean. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.activations.infra.packs")
public class PacksAvailabilityConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public PacksAvailabilityConfiguration() {}
}
