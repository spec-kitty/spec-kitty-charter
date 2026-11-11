package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * NFR-001: the {@code content_hash} recipe reproduces every hash an example pack's manifest lists (ADR-0070), and its
 * {@code manifest_hash}. The example manifests are in the sanitized shape (ADR-0042), so each is read through
 * {@link PackManifestYaml} and checked with the gate's own verification, {@link PackManifests#stale}. After an intended
 * change to an example pack, regenerate its manifest with {@link PackManifests#recompute} (a throwaway test; there is no
 * task for it).
 */
@DisplayName("Pack manifest content_hash parity with the example manifests (#195, NFR-001)")
class PackManifestParityTest {

    @TempDir
    private Path scratch;

    @ParameterizedTest(name = "{0}: {1} constituent and {2} preset hashes are reproduced")
    @CsvSource({"built-in, 21, 2", "project, 4, 1"})
    @DisplayName("every hash of an example manifest is reproduced")
    void everyHashIsReproduced(String name, int constituents, int presets) {
        Path pack = ExamplePacks.pack(name);
        PackManifestDso manifest = manifest(pack);

        assertThat(manifest.constituents()).hasSize(constituents);
        assertThat(manifest.presets()).hasSize(presets);
        assertThat(PackManifests.stale(manifest, bytesIn(pack))).isEmpty();
    }

    @Test
    @DisplayName("failing control: a deleted artifact file and a changed one are both caught")
    void aDeletedAndAChangedFileAreCaught() throws IOException {
        Path copy = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, scratch);
        PackManifestDso manifest = manifest(copy);
        String deleted = manifest.constituents().get(0).path();
        String changed = manifest.constituents().get(1).path();
        Files.delete(copy.resolve(deleted));
        Path edited = copy.resolve(changed);
        Files.writeString(
                edited, Files.readString(edited, StandardCharsets.UTF_8) + "# edited\n", StandardCharsets.UTF_8);

        assertThat(PackManifests.stale(manifest, bytesIn(copy)))
                .containsExactly("constituents{" + changed + "}.content_hash", "missing: " + deleted);
    }

    private static PackManifestDso manifest(Path pack) {
        try {
            String text = Files.readString(pack.resolve("pack-manifest.yaml"), StandardCharsets.UTF_8);
            return PackManifestYaml.read((Map<?, ?>) Yaml.load(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static java.util.function.Function<String, Optional<byte[]>> bytesIn(Path pack) {
        return path -> {
            Path file = pack.resolve(path);
            try {
                return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
