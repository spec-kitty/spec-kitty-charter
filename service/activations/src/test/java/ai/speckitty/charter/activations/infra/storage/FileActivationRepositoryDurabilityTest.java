package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository.DeleteResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a write and a delete force to the device (ADR-0061): a new directory's parent chain, the id's directory after
 * the move, and after a delete the id's directory and the root. A directory that cannot be opened for forcing is
 * tolerated (Windows); a force that fails is not.
 */
class FileActivationRepositoryDurabilityTest {

    private static final ActivationId ID = new ActivationId("durable");

    @TempDir
    Path host;

    /** Records every directory forced, in order, and forces it for real unless told to fail. */
    private static final class Recording implements FileActivationRepository.Durability {
        private final List<Path> forced = new ArrayList<>();
        private boolean failing;

        @Override
        public void move(Path temp, Path target) throws IOException {
            FileActivationRepository.DISK.move(temp, target);
        }

        @Override
        public void forceDirectory(Path directory) throws IOException {
            forced.add(directory);
            if (failing) {
                throw new IOException("injected: the device refused the force of " + directory);
            }
            FileActivationRepository.Durability.super.forceDirectory(directory);
        }
    }

    private static ActivationDocument document(int n) {
        return ActivationRepositoryContract.document(n);
    }

    private Path idDirectory(Path root) {
        return root.resolve(ActivationDirectoryName.encode(ID));
    }

    @Test
    void aFirstWriteUnderNewDirectoriesForcesTheParentOfEachAndThenTheIdsDirectory() {
        Path root = host.resolve("a").resolve("b");
        Recording disk = new Recording();

        new FileActivationRepository(root, disk).create(ID, document(1));

        assertThat(disk.forced)
                .as("the parent of every directory the write created, outermost first, then the id's directory")
                .containsExactly(host, host.resolve("a"), root, idDirectory(root));
    }

    @Test
    void aReplaceForcesTheIdsDirectoryAlone() {
        Path root = host.resolve("store");
        FileActivationRepository store = new FileActivationRepository(root);
        store.create(ID, document(1));
        Recording disk = new Recording();

        new FileActivationRepository(root, disk).replace(ID, Optional.empty(), document(2));

        assertThat(disk.forced).containsExactly(idDirectory(root));
    }

    @Test
    void aSecondIdUnderAnExistingRootForcesTheRootAndItsOwnDirectory() {
        Path root = host.resolve("store");
        new FileActivationRepository(root).create(new ActivationId("first"), document(1));
        Recording disk = new Recording();

        new FileActivationRepository(root, disk).create(ID, document(2));

        assertThat(disk.forced).containsExactly(root, idDirectory(root));
    }

    @Test
    void aDeleteForcesTheIdsDirectoryAfterTheFileGoesAndThenTheRootAfterTheDirectoryGoes() {
        Path root = host.resolve("store");
        new FileActivationRepository(root).create(ID, document(1));
        Recording disk = new Recording();

        new FileActivationRepository(root, disk).delete(ID, Optional.empty());

        assertThat(disk.forced).containsExactly(idDirectory(root), root);
        assertThat(idDirectory(root)).doesNotExist();
    }

    @Test
    void aForceThatFailsAfterTheMoveIsAStorageFailureThoughTheNewDocumentIsCommittedAndNoTempFileStays()
            throws IOException {
        Path root = host.resolve("store");
        new FileActivationRepository(root).create(ID, document(1));
        Recording disk = new Recording();
        disk.failing = true;

        assertThatThrownBy(() -> new FileActivationRepository(root, disk).replace(ID, Optional.empty(), document(2)))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining(ID.value());

        assertThat(disk.forced).as("only the force after the move ran").containsExactly(idDirectory(root));
        assertThat(new FileActivationRepository(root).find(ID))
                .hasValueSatisfying(stored -> assertThat(stored.document()).isEqualTo(document(2)));
        try (var listed = Files.list(idDirectory(root))) {
            assertThat(listed.map(path -> path.getFileName().toString())).noneMatch(name -> name.contains(".tmp-"));
        }
    }

    @Test
    void aForceThatFailsOnADeleteIsAStorageFailureThoughTheDocumentIsGone() {
        Path root = host.resolve("store");
        new FileActivationRepository(root).create(ID, document(1));
        Recording disk = new Recording();
        disk.failing = true;

        assertThatThrownBy(() -> new FileActivationRepository(root, disk).delete(ID, Optional.empty()))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining(ID.value());

        assertThat(new FileActivationRepository(root).find(ID)).isEmpty();
    }

    @Test
    void aDirectoryThatCannotBeOpenedForAnotherReasonIsNotTolerated() throws IOException {
        Path file = Files.writeString(host.resolve("a-file"), "not a directory");

        assertThatThrownBy(() -> FileActivationRepository.DISK.forceDirectory(file.resolve("below")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void aDirectoryThatCannotBeOpenedIsToleratedAndOneThatCanIsForced() {
        FileActivationRepository.Durability disk = FileActivationRepository.DISK;

        assertThatCode(() -> disk.forceDirectory(host.resolve("not-there"))).doesNotThrowAnyException();
        assertThatCode(() -> disk.forceDirectory(host)).doesNotThrowAnyException();
    }

    @Test
    void aTemporaryFileThatCannotBeCleanedAfterADeleteIsNoFailure() throws IOException {
        Path root = host.resolve("store");
        new FileActivationRepository(root).create(ID, document(1));
        Path stuck = idDirectory(root).resolve(FileActivationRepository.TEMP_PREFIX + "stuck");
        Files.createDirectories(stuck);
        Files.writeString(stuck.resolve("inner"), "a directory a file delete cannot remove");

        assertThat(new FileActivationRepository(root).delete(ID, Optional.empty()))
                .isInstanceOf(DeleteResult.Deleted.class);

        assertThat(idDirectory(root).resolve(FileActivationRepository.FILE)).doesNotExist();
    }
}
