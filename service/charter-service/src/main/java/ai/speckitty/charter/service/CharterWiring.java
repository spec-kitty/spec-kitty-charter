package ai.speckitty.charter.service;

import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.Fingerprint;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.PackSources;
import ai.speckitty.charter.read.ActionContextQuery;
import ai.speckitty.charter.read.PackListingQuery;
import ai.speckitty.charter.read.ServiceInfoQuery;
import ai.speckitty.charter.yaml.FileSystemCharterPackRepository;
import ai.speckitty.charter.yaml.PackLocation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Connects the use cases to the file-based adapter. */
@Configuration(proxyBeanMethods = false)
public class CharterWiring {

    /** For Spring, which instantiates the configuration class. */
    public CharterWiring() {}

    /** Where the packs are; a test may supply a {@code @Primary} one. */
    public interface PackLocations extends Supplier<List<PackLocation>> {}

    @Bean
    PackLocations packLocations(CharterProperties properties) {
        List<PackLocation> locations = properties.packs().stream()
                .map(p -> new PackLocation(
                        p.name(),
                        PackLevel.fromToken(p.level())
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "charter.packs: unknown level '" + p.level() + "' for " + p.name())),
                        p.path()))
                .toList();
        return () -> locations;
    }

    @Bean
    FileSystemCharterPackRepository charterPackRepository(PackLocations locations) {
        return new FileSystemCharterPackRepository(locations);
    }

    @Bean
    ServiceInfoQuery serviceInfoQuery(CharterProperties properties, PackSources sources) {
        String configured = properties.worktreeId();
        return new ServiceInfoQuery(
                configured == null || configured.isBlank() ? derivedWorktreeId(properties) : configured, sources);
    }

    /**
     * The worktree id of a service started without {@code charter.worktree-id}: the first 16 hex digits of a digest
     * of the configured pack directories, resolved to their real paths where they exist. It is stable for one
     * configuration and names no host path.
     *
     * @param properties the bound configuration
     * @return 16 lower-case hex digits
     */
    static String derivedWorktreeId(CharterProperties properties) {
        Fingerprint fingerprint = Fingerprint.sha256();
        for (CharterProperties.Pack pack : properties.packs()) {
            fingerprint.add(realPath(pack.path()).toString());
        }
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

    @Bean
    ActionContextQuery actionContextQuery(CharterPackRepository packs) {
        return new ActionContextQuery(packs);
    }

    @Bean
    PackListingQuery packListingQuery(CharterPackRepository packs) {
        return new PackListingQuery(packs);
    }
}
