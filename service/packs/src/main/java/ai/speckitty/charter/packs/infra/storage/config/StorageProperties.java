package ai.speckitty.charter.packs.infra.storage.config;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the service keeps its files: {@code charter.config-root}, the one directory with a subdirectory per domain
 * ({@link ai.speckitty.charter.packs.infra.storage.ConfigRoot}). {@code application.yaml} defaults it to
 * {@code ${user.home}/.spec-kitty/charter} and lets {@code --configRootLocation <path>} override it; any other Spring
 * property source can set it too.
 *
 * @param configRoot the configuration root; {@code null} or empty when none is configured, and the service then
 *     serves no packs
 */
@ConfigurationProperties("charter")
public record StorageProperties(@Nullable Path configRoot) {}
