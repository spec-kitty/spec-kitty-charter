package ai.speckitty.charter.packs.infra.storage.config;

import ai.speckitty.charter.packs.infra.storage.SerializationStrategy;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The persistence edge's configuration: it scans the storage package, which makes the stereotyped
 * {@code DomainRepositoryService} the single bean realizing the pack ports, and supplies the format that service is
 * injected with. A database-backed sibling would swap the {@link SerializationStrategy} bean, not the service.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.packs.infra.storage")
public class StorageConfiguration {

    /** For Spring, which instantiates the configuration class. */
    public StorageConfiguration() {}

    /**
     * The serialization format of pack files.
     *
     * @return the YAML strategy
     */
    @Bean
    SerializationStrategy serializationStrategy() {
        return new YamlSerializationStrategy();
    }
}
