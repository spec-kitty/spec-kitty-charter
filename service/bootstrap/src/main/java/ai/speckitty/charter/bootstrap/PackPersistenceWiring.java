package ai.speckitty.charter.bootstrap;

import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the pack persistence ports live: the edge {@link DomainRepositoryService} as a bean, fed the same configured
 * pack locations the rest of the service reads. The domain ports carry no Spring type; the wiring stays here.
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

    /** One service realizing the tactic, descriptor, pack and inspection ports over the configured packs. */
    @Bean
    DomainRepositoryService domainRepositoryService(DomainRepositoryService.Locations locations) {
        return new DomainRepositoryService(locations);
    }
}
