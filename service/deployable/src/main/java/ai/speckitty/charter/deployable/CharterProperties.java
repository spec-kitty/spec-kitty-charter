package ai.speckitty.charter.deployable;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The worktree the service serves. Where the packs are is the storage layer's own configuration,
 * {@code charter.config-root} ({@code StorageProperties}).
 *
 * @param worktreeId the opaque id of the worktree the service serves, {@code charter.worktree-id}, which the launcher
 *     sets; {@code null} when none is configured, and the wiring then derives one from where the packs are
 * @param projectConfigDir the worktree's project configuration directory ({@code .kittify}), {@code charter.project-config-dir},
 *     whose {@code config.yaml} and {@code charter.yaml} join the source fingerprint; {@code null} when none is
 *     configured, and the fingerprint then covers the packs only
 */
@ConfigurationProperties("charter")
public record CharterProperties(
        @Nullable String worktreeId, @Nullable Path projectConfigDir) {}
