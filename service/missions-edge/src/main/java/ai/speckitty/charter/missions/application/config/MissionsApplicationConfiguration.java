package ai.speckitty.charter.missions.application.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** The missions application ring's configuration: it scans the {@code application} package, which makes the mission-type reads a bean. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.missions.application")
public class MissionsApplicationConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public MissionsApplicationConfiguration() {}
}
