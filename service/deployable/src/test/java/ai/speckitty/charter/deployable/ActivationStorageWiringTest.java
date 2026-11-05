package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.infra.storage.ActivationDirectoryName;
import ai.speckitty.charter.activations.infra.storage.FileActivationRepository;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** How the composition root picks the activation repository from the {@link ActivationStorageRoot} (FR-019). */
class ActivationStorageWiringTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ActivationStorageWiring.class);

    @Test
    void withADirectoryTheRepositoryIsTheFileAdapterOverIt(@TempDir Path temp) {
        Path directory = temp.resolve("activations");
        runner.withBean(ActivationStorageRoot.class, () -> new ActivationStorageRoot(Optional.of(directory)))
                .run(context -> {
                    ActivationRepository repository = context.getBean(ActivationRepository.class);
                    assertThat(repository).isInstanceOf(FileActivationRepository.class);
                    assertThat(directory).as("nothing is created at startup").doesNotExist();

                    repository.create(
                            new ActivationId("wired"),
                            ActivationDocument.builder().build().document());
                    assertThat(directory
                                    .resolve(ActivationDirectoryName.encode(new ActivationId("wired")))
                                    .resolve("activation.json"))
                            .isRegularFile();
                });
    }

    @Test
    void withoutADirectoryTheRepositoryIsInMemory() {
        runner.withBean(ActivationStorageRoot.class, ActivationStorageRoot::inMemory)
                .run(context -> assertThat(context.getBean(ActivationRepository.class))
                        .isInstanceOf(InMemoryActivationRepository.class));
    }
}
