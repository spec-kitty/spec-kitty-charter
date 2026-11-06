package ai.speckitty.charter.deployable;

import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where the activation documents are stored (FR-019): {@code <config-root>/activations/<id>/} when a configuration
 * root is bound, otherwise nowhere on disk, and the composition root keeps them in memory. The activations slice may
 * not name {@link ConfigRoot}, which lives in the packs storage adapter, so the composition root resolves the place and
 * hands it over as a plain {@link Path}. Nothing here creates a directory, as {@link ConfigRoot} requires.
 *
 * @param directory the directory of the activation documents, or empty to hold them in memory
 */
public record ActivationStorageRoot(Optional<Path> directory) {

    /**
     * Requires the directory's optional.
     *
     * @param directory the directory of the activation documents, or empty to hold them in memory
     */
    public ActivationStorageRoot {
        Objects.requireNonNull(directory, "directory");
    }

    /**
     * The place of a service started without a configuration root.
     *
     * @return a root with no directory
     */
    public static ActivationStorageRoot inMemory() {
        return new ActivationStorageRoot(Optional.empty());
    }

    /** Resolves the place from the configuration root, if one is bound. */
    @Configuration(proxyBeanMethods = false)
    public static class Wiring {

        /** For Spring, which finds the configuration by component scan. */
        public Wiring() {}

        /**
         * The activation storage root of this deployment.
         *
         * @param root the configuration root, when {@code charter.config-root} is bound (a test's {@code @Primary} one
         *     first)
         * @return {@code <config-root>/activations}, or {@link #inMemory()} without a root
         */
        @Bean
        ActivationStorageRoot activationStorageRoot(ObjectProvider<ConfigRoot> root) {
            return Optional.ofNullable(root.getIfAvailable())
                    .map(bound -> new ActivationStorageRoot(Optional.of(bound.activations())))
                    .orElseGet(ActivationStorageRoot::inMemory);
        }
    }
}
