package ai.speckitty.charter.packs.infra.storage.config;

import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.SerializationStrategy;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The persistence edge's configuration: it scans the storage package, which makes the stereotyped
 * {@code DomainRepositoryService} the single bean realizing the pack ports, and supplies the format that service is
 * injected with, the {@link ConfigRoot} ({@code charter.config-root}) and the packs it reads and writes there. A
 * database-backed sibling would swap the {@link SerializationStrategy} bean, not the service.
 *
 * <p>A test replaces the root with a {@code @Primary} {@link ConfigRoot} of its own (the deployable's
 * {@code ConfigRootTestConfiguration}); the pack locations follow it. No bean here creates a directory.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("ai.speckitty.charter.packs.infra.storage")
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    /** The properties the configuration root replaced; a stale one fails the start rather than serving nothing. */
    private static final List<ConfigurationPropertyName> RETIRED =
            List.of(ConfigurationPropertyName.of("charter.packs"), ConfigurationPropertyName.of("charter.storage"));

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
     * The configured root, made absolute against the working directory at startup.
     *
     * @param properties the bound configuration
     * @return the configuration root
     * @throws IllegalStateException when {@code charter.config-root} is empty, or names something that exists and is
     *     not a directory; the message names the property, not the path
     */
    @Bean
    @ConditionalOnProperty("charter.config-root")
    ConfigRoot configRoot(StorageProperties properties) {
        Path root = properties.configRoot();
        if (root == null || root.toString().isBlank()) {
            throw new IllegalStateException("charter.config-root is configured but empty; name a directory");
        }
        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new IllegalStateException(
                    "charter.config-root must name a directory; it names something else that exists");
        }
        return new ConfigRoot(root);
    }

    /**
     * The packs the store reads and writes: every directory under {@code <config-root>/packs}. Without a configured
     * root the service serves no packs.
     *
     * @param root the configuration root, the {@code @Primary} one when a test supplies its own
     * @param environment where a retired property would be found
     * @return the packs of the pack domain
     * @throws IllegalStateException when a retired property ({@code charter.packs}, {@code charter.storage.*}) is set
     */
    @Bean
    DomainRepositoryService.Locations storageLocations(ObjectProvider<ConfigRoot> root, Environment environment) {
        rejectRetired(environment);
        ConfigRoot configured = root.getIfAvailable();
        return configured == null ? List::of : new StorageRootLocations(configured.packs());
    }

    private static void rejectRetired(Environment environment) {
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (source instanceof IterableConfigurationPropertySource iterable) {
                iterable.stream()
                        .filter(name -> RETIRED.stream()
                                .anyMatch(retired -> retired.equals(name) || retired.isAncestorOf(name)))
                        .findFirst()
                        .ifPresent(name -> {
                            throw new IllegalStateException(Objects.toString(name)
                                    + " is no longer read: packs live under charter.config-root (<root>/packs/<pack>);"
                                    + " pass --configRootLocation <path>");
                        });
            }
        }
    }
}
