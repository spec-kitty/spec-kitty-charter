package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Where the activation documents are stored (FR-019): under {@code <config-root>/activations} when a configuration root
 * is bound, and in memory when none is. Resolving the place creates nothing.
 */
class ActivationStorageRootTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ActivationStorageRoot.Wiring.class);

    @Test
    void withAConfigurationRootTheActivationsAreStoredUnderItsActivationsDirectory(@TempDir Path root) {
        ConfigRoot configRoot = new ConfigRoot(root);

        runner.withBean(ConfigRoot.class, () -> configRoot).run(context -> {
            assertThat(context.getBean(ActivationStorageRoot.class).directory())
                    .contains(root.toAbsolutePath().normalize().resolve("activations"));
            assertThat(configRoot.activations())
                    .as("nothing creates the directory")
                    .doesNotExist();
        });
    }

    @Test
    void withoutAConfigurationRootTheActivationsAreHeldInMemory() {
        runner.run(context -> {
            assertThat(context.getBean(ActivationStorageRoot.class)).isEqualTo(ActivationStorageRoot.inMemory());
            assertThat(context.getBean(ActivationStorageRoot.class).directory()).isEmpty();
        });
    }

    @Test
    void theInMemoryRootHasNoDirectory() {
        assertThat(ActivationStorageRoot.inMemory().directory()).isEqualTo(Optional.empty());
    }
}
