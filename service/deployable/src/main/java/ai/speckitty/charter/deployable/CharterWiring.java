package ai.speckitty.charter.deployable;

import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.domain.PackValidation;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import ai.speckitty.charter.packs.infra.storage.ProjectConfigSources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Connects the use cases to the file-based adapter. */
@Configuration(proxyBeanMethods = false)
public class CharterWiring {

    /** For Spring, which instantiates the configuration class. */
    public CharterWiring() {}

    /** Where the packs are; a test may supply a {@code @Primary} one. */
    public interface PackLocations extends Supplier<List<PackLocation>> {}

    /** Where the project configuration directory is, if one is configured; a test may supply a {@code @Primary} one. */
    public interface ProjectConfigLocation extends Supplier<Optional<Path>> {}

    /**
     * The configured project configuration directory. The check is on the property, not on the supplier, so a test
     * that overrides the supplier is not held to it.
     *
     * @throws IllegalStateException when {@code charter.project-config-dir} is set and is not an existing directory;
     *     the message names the property, not the path
     */
    @Bean
    ProjectConfigLocation projectConfigLocation(CharterProperties properties) {
        Path directory = properties.projectConfigDir();
        if (directory != null && !Files.isDirectory(directory)) {
            throw new IllegalStateException(
                    "charter.project-config-dir must name an existing directory; it is configured but is not one");
        }
        Optional<Path> configured = Optional.ofNullable(directory);
        return () -> configured;
    }

    /**
     * The packs the read path fingerprints and validates: the same ones the storage layer reads and writes, from its
     * {@code <config-root>/packs}, so the two can never disagree.
     */
    @Bean
    PackLocations packLocations(DomainRepositoryService.Locations storage) {
        return storage::all;
    }

    @Bean
    FileSystemPackSources packSources(PackLocations locations) {
        return new FileSystemPackSources(locations);
    }

    /** Validate mode (FR-010) over the file-based inspector: the seam the pack doctor (#115) composes. */
    @Bean
    PackValidation packValidation() {
        return new PackValidation(new FileSystemPackInspector());
    }

    /** The pack files and the project configuration files, as one fingerprint; injected by its own type. */
    @Bean
    ProjectConfigSources projectConfigSources(FileSystemPackSources packs, ProjectConfigLocation projectConfig) {
        return new ProjectConfigSources(packs, projectConfig);
    }

    /**
     * The one verifier of the freshness rule, over the pack and project configuration files. It has no stereotype:
     * the deployment holds two {@code PackSources}, so use cases receive this bean, never the port.
     */
    @Bean
    ConsistentRead consistentRead(ProjectConfigSources sources) {
        return new ConsistentRead(sources);
    }

    @Bean
    ServiceInfoQuery serviceInfoQuery(
            CharterProperties properties, ObjectProvider<ConfigRoot> root, ConsistentRead read) {
        String configured = properties.worktreeId();
        return new ServiceInfoQuery(
                configured == null || configured.isBlank() ? derivedWorktreeId(root.getIfAvailable()) : configured,
                read);
    }

    /**
     * The worktree id of a service started without {@code charter.worktree-id}: the first 16 hex digits of a digest
     * of the configuration root, resolved to its real path where it exists. It depends on the configuration only, not
     * on which packs the root holds at startup, and names no host path.
     *
     * @param root the configuration root, or null when none is configured
     * @return 16 lower-case hex digits
     */
    static String derivedWorktreeId(@Nullable ConfigRoot root) {
        Fingerprint fingerprint = Fingerprint.sha256();
        fingerprint.add(root == null ? "" : realPath(root.root()).toString());
        String digest = fingerprint.value();
        return digest.substring(digest.indexOf(':') + 1, digest.indexOf(':') + 1 + 16);
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException missing) {
            // A pack that does not exist yet is named as configured; loading it refuses later.
            return path.toAbsolutePath().normalize();
        }
    }
}
