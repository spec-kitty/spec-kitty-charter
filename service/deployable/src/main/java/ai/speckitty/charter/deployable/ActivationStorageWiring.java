package ai.speckitty.charter.deployable;

import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.infra.storage.FileActivationRepository;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The activation repository of this deployment (FR-019): the composition root is the only place outside the
 * activations slice that names its storage adapters, and it picks one from the {@link ActivationStorageRoot}. Nothing
 * here creates a directory; the first write does, as {@code ConfigRoot} requires.
 */
@Configuration(proxyBeanMethods = false)
public class ActivationStorageWiring {

    /** For Spring, which finds the configuration by component scan. */
    public ActivationStorageWiring() {}

    /**
     * The activation repository.
     *
     * @param root where the activation documents are stored
     * @return a {@link FileActivationRepository} on {@link ActivationStorageRoot#directory()}, or an
     *     {@link InMemoryActivationRepository} when there is no directory
     */
    @Bean
    ActivationRepository activationRepository(ActivationStorageRoot root) {
        return root.directory()
                .<ActivationRepository>map(FileActivationRepository::new)
                .orElseGet(InMemoryActivationRepository::new);
    }
}
