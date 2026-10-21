package ai.speckitty.charter.packs.application.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The application ring's configuration: it scans the application package, which makes each stereotyped use-case
 * service a bean. The services depend on the domain ports only; the persistence edge realizes them.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.packs.application")
public class ApplicationConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ApplicationConfiguration() {}
}
