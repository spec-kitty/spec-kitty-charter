package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * NFR-001: the {@code content_hash} recipe reproduces every hash an example pack's manifest lists (ADR-0070), and its
 * {@code manifest_hash}. The example manifests are in the sanitized shape (ADR-0042), so each is read through
 * {@link PackManifestYaml} and checked with the gate's own verification, {@link PackManifests#stale}. The parity runs
 * both ways: every file the manifest lists is on disk with its hash, and every file of a kind the manifest lists (and
 * every preset) is listed, so a pack file left out of the manifest is caught too. After an intended
 * change to an example pack, regenerate its manifest with {@link PackManifests#recompute} (a throwaway test; there is no
 * task for it).
 */
@DisplayName("Pack manifest content_hash parity with the example manifests (#195, NFR-001)")
class PackManifestParityTest {

    @TempDir
    private Path scratch;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"built-in", "project"})
    @DisplayName("every constituent and preset hash an example manifest lists is reproduced")
    void everyHashIsReproduced(String name) {
        Path pack = ExamplePacks.pack(name);
        PackManifestDso manifest = manifest(pack);

        assertThat(manifest.constituents())
                .as("constituents the manifest lists")
                .isNotEmpty();
        assertThat(manifest.presets()).as("presets the manifest lists").isNotEmpty();
        assertThat(PackManifests.stale(manifest, bytesIn(pack))).isEmpty();
        assertThat(unlisted(pack, manifest))
                .as("pack files the manifest does not list")
                .isEmpty();
    }

    @Test
    @DisplayName("failing control: a directive file the manifest does not list is caught")
    void anUnlistedFileIsCaught() throws IOException {
        Path copy = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, scratch);
        PackManifestDso manifest = manifest(copy);
        String listed = manifest.constituents().stream()
                .filter(c -> c.kind().equals("directive"))
                .findFirst()
                .orElseThrow()
                .path();
        Path extra = copy.resolve("directives/zzz-extra.directive.yaml");
        Files.writeString(
                extra,
                Files.readString(copy.resolve(listed), StandardCharsets.UTF_8)
                        .replaceFirst("(?m)^id: .*$", "id: zzz-extra"),
                StandardCharsets.UTF_8);

        assertThat(PackManifests.stale(manifest, bytesIn(copy))).isEmpty();
        assertThat(unlisted(copy, manifest)).containsExactly("directives/zzz-extra.directive.yaml");
    }

    /**
     * The files of the pack that the manifest should list but does not: every file of each constituent kind the
     * manifest lists (as the production file table classifies it), and every file under {@code presets/}.
     */
    private static List<String> unlisted(Path pack, PackManifestDso manifest) {
        Set<String> listed = new TreeSet<>();
        manifest.constituents().forEach(c -> listed.add(c.path()));
        manifest.presets().forEach(p -> listed.add(p.path()));
        Set<String> expected = new TreeSet<>();
        for (PackManifestDso.Constituent constituent : manifest.constituents()) {
            ArtifactKind kind = ArtifactKind.fromToken(constituent.kind()).orElseThrow();
            expected.addAll(PackFileSet.files(pack, kind));
        }
        Path presets = pack.resolve("presets");
        if (Files.isDirectory(presets)) {
            try (Stream<Path> walk = Files.walk(presets)) {
                walk.filter(Files::isRegularFile)
                        .map(file -> pack.relativize(file).toString().replace('\\', '/'))
                        .forEach(expected::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        List<String> missing = new ArrayList<>(expected);
        missing.removeAll(listed);
        return missing;
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
