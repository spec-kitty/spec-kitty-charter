package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A listing of the store that the platform refuses is a fault of the store itself (issue 418, R6), answered as the
 * store answers a document it cannot read back: an {@link ActivationStorageException}, which the inbound adapter and
 * the pack write path both treat as the service's fault ({@code 500 internal_error}), never a bare
 * {@link java.io.UncheckedIOException} of no particular meaning.
 */
class FileActivationRepositoryListingFailureTest {

    @TempDir
    Path root;

    private static final class Unlistable implements FileActivationRepository.Durability {
        @Override
        public void move(Path temp, Path target) throws IOException {
            Files.move(temp, target);
        }

        @Override
        public List<Path> list(Path directory) throws IOException {
            throw new IOException("injected: the root cannot be read");
        }
    }

    @Test
    void aListingThePlatformRefusesIsAStorageFaultThatNamesTheListingAndKeepsTheCause() throws IOException {
        Files.createDirectories(root.resolve("some-activation"));
        FileActivationRepository repository = new FileActivationRepository(root, new Unlistable());

        assertThatThrownBy(repository::list).isInstanceOfSatisfying(ActivationStorageException.class, fault -> {
            assertThat(fault).hasMessageContaining("activation store cannot be listed");
            assertThat(fault).hasCauseInstanceOf(IOException.class);
        });
    }
}
