package ai.speckitty.charter.deployable;

import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feeds the storage edge {@link DomainRepositoryService} the same configured pack locations the rest of the service
 * reads; the service itself is scan-driven by the storage layer's configuration. The domain ports carry no Spring type; the wiring stays
 * here.
 */
@Configuration(proxyBeanMethods = false)
public class PackPersistenceWiring {

    /** For Spring, which instantiates the configuration class. */
    public PackPersistenceWiring() {}

    /** The configured pack locations, read on every call so the service sees what {@code charter.packs} says. */
    @Bean
    DomainRepositoryService.Locations domainRepositoryLocations(CharterWiring.PackLocations packLocations) {
        return packLocations::get;
    }
}
