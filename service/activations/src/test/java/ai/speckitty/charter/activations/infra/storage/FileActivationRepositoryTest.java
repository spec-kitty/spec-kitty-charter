package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.Jcs;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileActivationRepositoryTest extends ActivationRepositoryContract {

    private static final ActivationId ID = new ActivationId("file-id");

    @TempDir
    Path temp;

    /** The store's root, not created up front: the first write creates it. */
    private Path root() {
        return temp.resolve("activations");
    }

    @Override
    ActivationRepository repository() {
        return new FileActivationRepository(root());
    }

    @Override
    void storeBytes(ActivationRepository repository, ActivationId id, byte[] bytes) {
        try {
            Files.createDirectories(file(id).getParent());
            Files.write(file(id), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    byte[] storedBytes(ActivationRepository repository, ActivationId id) {
        try {
            return Files.readAllBytes(file(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(ActivationId id) {
        return root().resolve(ActivationDirectoryName.encode(id)).resolve("activation.json");
    }

    @Test
    void nothingIsCreatedUntilTheFirstWrite() {
        FileActivationRepository store = new FileActivationRepository(root());

        assertThat(store.find(ID)).isEmpty();
        assertThat(root()).doesNotExist();

        store.create(ID, document(1));
        assertThat(file(ID)).isRegularFile();
    }

    @Test
    void theFileHoldsExactlyTheJcsBytesTheRevisionHashes() throws IOException {
        ActivationDocument document = document(1);
        new FileActivationRepository(root()).create(ID, document);

        byte[] bytes = Files.readAllBytes(file(ID));

        assertThat(bytes).isEqualTo(Jcs.canonical(document.toCanonicalTree()));
        assertThat(Revision.of(bytes)).isEqualTo(document.revision());
    }

    @Test
    void aNewInstanceOverTheSameRootReadsEveryDocumentBack() {
        new FileActivationRepository(root()).create(ID, document(1));

        assertThat(new FileActivationRepository(root()).find(ID)).contains(StoredActivation.of(ID, document(1)));
    }

    @Test
    void aStrayTempFileIsIgnoredByFindAndRemovedByTheNextWrite() throws IOException {
        FileActivationRepository store = new FileActivationRepository(root());
        store.create(ID, document(1));
        Path stray = file(ID).resolveSibling("activation.json.tmp-left-by-a-crash");
        Files.writeString(stray, "{\"schemaVer");

        assertThat(store.find(ID)).map(StoredActivation::document).contains(document(1));

        store.replace(ID, Optional.empty(), document(2));
        assertThat(stray).doesNotExist();
        assertThat(store.find(ID)).map(StoredActivation::document).contains(document(2));
    }

    @Test
    void deleteRemovesTheFileAndItsDirectory() {
        FileActivationRepository store = new FileActivationRepository(root());
        store.create(ID, document(1));

        store.delete(ID, Optional.empty());

        assertThat(file(ID).getParent()).doesNotExist();
    }

    @Test
    void aCorruptFileIsAStoreFaultNamingTheId() throws IOException {
        Files.createDirectories(file(ID).getParent());
        Files.write(file(ID), "{\"schemaVersion\":2,".getBytes(StandardCharsets.UTF_8));
        FileActivationRepository store = new FileActivationRepository(root());

        assertThatThrownBy(() -> store.find(ID))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining(ID.value());
        assertThatThrownBy(() -> store.replace(ID, Optional.empty(), document(2)))
                .isInstanceOf(ActivationStorageException.class);
    }

    @Test
    void readingAVersionOneFileLeavesItsBytesAndModificationTimeAlone() throws IOException {
        byte[] versionOne = "{\"missionTypes\":[\"software-dev\"],\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8);
        storeBytes(repository(), ID, versionOne);
        FileTime written = FileTime.fromMillis(1_000_000_000L);
        Files.setLastModifiedTime(file(ID), written);
        FileActivationRepository store = new FileActivationRepository(root());

        store.find(ID);
        store.find(ID);

        assertThat(Files.readAllBytes(file(ID))).isEqualTo(versionOne);
        assertThat(Files.getLastModifiedTime(file(ID))).isEqualTo(written);
        try (Stream<Path> files = Files.list(file(ID).getParent())) {
            assertThat(files).containsExactly(file(ID));
        }
    }

    @Test
    void aWriteRefusedByThePlatformIsAStoreFaultNamingTheId() throws IOException {
        Files.createDirectories(root());
        Files.writeString(root().resolve(ActivationDirectoryName.encode(ID)), "a file where the directory belongs");
        FileActivationRepository store = new FileActivationRepository(root());

        assertThatThrownBy(() -> store.create(ID, document(1)))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining(ID.value());
    }
}
