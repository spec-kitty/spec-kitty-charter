package ai.speckitty.charter.deployable;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Binds and validates {@link ContainerProperties} under the {@code container} profile only (ADR-0041 decision 1).
 * Without the profile nothing at {@code charter.container.*} is bound, so the local start is unchanged.
 */
@Configuration(proxyBeanMethods = false)
@Profile(ContainerProperties.PROFILE)
@EnableConfigurationProperties(ContainerProperties.class)
public class ContainerWiring {

    /** For Spring, which instantiates the configuration class. */
    public ContainerWiring() {}
}
