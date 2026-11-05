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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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

    private Path file(ActivationId id) {
        return root().resolve(id.value()).resolve("activation.json");
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
        Files.write(file(ID), "{\"schemaVersion\":1,".getBytes(StandardCharsets.UTF_8));
        FileActivationRepository store = new FileActivationRepository(root());

        assertThatThrownBy(() -> store.find(ID))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining(ID.value());
        assertThatThrownBy(() -> store.replace(ID, Optional.empty(), document(2)))
                .isInstanceOf(ActivationStorageException.class);
    }

    @Test
    void aWriteRefusedByThePlatformIsAStoreFaultNamingTheId() throws IOException {
        Files.createDirectories(root());
        Files.writeString(root().resolve(ID.value()), "a file where the directory belongs");
        FileActivationRepository store = new FileActivationRepository(root());

        assertThatThrownBy(() -> store.create(ID, document(1)))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining(ID.value());
    }
}
