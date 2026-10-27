package ai.speckitty.charter.packs.infra.storage.config;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the pack store keeps its packs, for reading and writing: {@code charter.storage.root}, set in
 * {@code application.yaml}, an external config file, or on the command line
 * ({@code --charter.storage.root=/path/to/packs}). Every directory directly under the root is a pack; see
 * {@link ai.speckitty.charter.packs.infra.storage.StorageRootLocations}.
 *
 * @param root the storage root; {@code null} when none is configured, and the service then serves no packs
 */
@ConfigurationProperties("charter.storage")
public record StorageProperties(@Nullable Path root) {}
