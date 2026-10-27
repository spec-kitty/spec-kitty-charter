package ai.speckitty.charter.packs.infra.storage.config;

import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.SerializationStrategy;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The persistence edge's configuration: it scans the storage package, which makes the stereotyped
 * {@code DomainRepositoryService} the single bean realizing the pack ports, and supplies the format that service is
 * injected with and the packs it reads and writes, from {@link StorageProperties} ({@code charter.storage.root}). A
 * database-backed sibling would swap the {@link SerializationStrategy} bean, not the service.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.packs.infra.storage")
@EnableConfigurationProperties(StorageProperties.class)
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

    /**
     * The packs the store reads and writes: every directory under the configured storage root. The check is on the
     * property, so a test that supplies its own {@code @Primary} locations is not held to it.
     *
     * @param properties the bound {@code charter.storage} configuration
     * @return the packs under the root; none when no root is configured (an empty value counts as none, never as the
     *     working directory)
     * @throws IllegalStateException when {@code charter.storage.root} is set and is not an existing directory; the
     *     message names the property, not the path
     */
    @Bean
    DomainRepositoryService.Locations storageLocations(StorageProperties properties) {
        Path root = properties.root();
        if (root == null || root.toString().isEmpty()) {
            return List::of;
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException(
                    "charter.storage.root must name an existing directory; it is configured but is not one");
        }
        return new StorageRootLocations(root);
    }
}
