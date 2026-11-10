package ai.speckitty.charter.profiles.application.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The profiles application ring's configuration: it scans the application package, which makes each stereotyped use-case
 * service a bean. The services depend on the ring's ports only; the adapters realize them.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.profiles.application")
public class ProfilesApplicationConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public ProfilesApplicationConfiguration() {}
}
