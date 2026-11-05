package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.StoredActivation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Ids that differ only in case never alias on disk (T106): their directories have names that differ when lower-cased,
 * so a case-insensitive file system (the macOS and Windows defaults) keeps them apart, and each reads back its own
 * document. The layout is {@code <root>/<encoded id>/activation.json}.
 */
class FileActivationRepositoryCaseFoldTest {

    private static final ActivationId UPPER = new ActivationId("ABC");
    private static final ActivationId LOWER = new ActivationId("abc");

    @TempDir
    Path root;

    @Test
    void caseVariantsAreStoredApartAndReadBackDistinct() {
        FileActivationRepository store = new FileActivationRepository(root);

        assertThat(store.create(UPPER, ActivationRepositoryContract.document(1)))
                .isPresent();
        assertThat(store.create(LOWER, ActivationRepositoryContract.document(2)))
                .as("the lower-case id is free, not aliased to the upper-case one")
                .isPresent();

        assertThat(store.find(UPPER)).contains(StoredActivation.of(UPPER, ActivationRepositoryContract.document(1)));
        assertThat(store.find(LOWER)).contains(StoredActivation.of(LOWER, ActivationRepositoryContract.document(2)));
    }

    @Test
    void theDirectoryNamesOnDiskDifferWhenLowerCased() throws IOException {
        FileActivationRepository store = new FileActivationRepository(root);
        store.create(UPPER, ActivationRepositoryContract.document(1));
        store.create(LOWER, ActivationRepositoryContract.document(2));

        List<String> names;
        try (Stream<Path> listed = Files.list(root)) {
            names = listed.map(path -> path.getFileName().toString()).sorted().toList();
        }

        assertThat(names).containsExactly("abc-0", "abc-7");
        assertThat(names.stream().map(name -> name.toLowerCase(Locale.ROOT)).distinct())
                .hasSize(2);
        assertThat(root.resolve("abc-7").resolve(FileActivationRepository.FILE)).isRegularFile();
    }

    @Test
    void aDirectoryWhoseNameDoesNotDecodeToTheIdIsAStoreFault() {
        assertThatThrownBy(() -> FileActivationRepository.checkedName(LOWER, "abc-7"))
                .isInstanceOf(ActivationStorageException.class)
                .hasMessageContaining("abc");
    }
}
