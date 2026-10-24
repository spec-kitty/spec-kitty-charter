package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileSystemPackSourcesTest {

    @Test
    void theSourceFingerprintFollowsTheFilesAndNotWhereThePacksAre(@TempDir Path dir) throws IOException {
        Path first = Files.createDirectories(dir.resolve("one/qa"));
        Path second = Files.createDirectories(dir.resolve("two/qa"));
        for (Path root : List.of(first, second)) {
            Files.writeString(root.resolve("a.graph.yaml"), "nodes: []\n");
            Files.createDirectories(root.resolve("drg"));
            Files.writeString(root.resolve("drg/fragment.yaml"), "nodes: []\n");
        }
        String fingerprint = sources(first).fingerprint();
        assertThat(fingerprint).matches("sha256:[0-9a-f]{64}");
        assertThat(sources(second).fingerprint()).as("same files elsewhere").isEqualTo(fingerprint);

        Files.writeString(second.resolve("a.graph.yaml"), "nodes: []\nedges: []\n");
        assertThat(sources(second).fingerprint()).as("a changed file").isNotEqualTo(fingerprint);
    }

    @Test
    void aPackThatIsNoDirectoryHasNoSourceFingerprint(@TempDir Path dir) {
        assertThatThrownBy(() -> sources(dir.resolve("missing")).fingerprint())
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tactic", "directive", "styleguide", "paradigm"})
    void aLoadedFileOfEachKindChangesTheFingerprintWhileADecoyDoesNot(String kind, @TempDir Path dir)
            throws IOException {
        Path root = Files.createDirectories(dir.resolve("qa"));
        Files.writeString(root.resolve("pack.yaml"), "name: qa\n");
        Files.writeString(root.resolve("a.graph.yaml"), "nodes: []\n");
        Path loaded = Files.createDirectories(root.resolve("nested/deeper")).resolve("e." + kind + ".yaml");
        Files.writeString(loaded, "id: e\n");
        Path decoy = root.resolve("nested/e." + kind + ".yml");
        Files.writeString(decoy, "id: e\n");
        String baseline = sources(root).fingerprint();

        Files.writeString(decoy, "id: changed\n");
        assertThat(sources(root).fingerprint()).as("a decoy").isEqualTo(baseline);

        Files.writeString(loaded, "id: changed\n");
        assertThat(sources(root).fingerprint()).as("the %s file", kind).isNotEqualTo(baseline);
    }

    @Test
    void aRootWrittenWithADotDigestsTheSameAsTheNormalizedRoot(@TempDir Path dir) throws IOException {
        Path root = Files.createDirectories(dir.resolve("qa"));
        Files.writeString(root.resolve("pack.yaml"), "name: qa\n");
        Files.writeString(root.resolve("presets-free.tactic.yaml"), "id: t\n");
        Files.createDirectories(root.resolve("presets"));
        Files.writeString(root.resolve("presets/p.tactic.yaml"), "id: p\n");

        assertThat(sources(root.resolve(".")).fingerprint())
                .isEqualTo(sources(root).fingerprint());
    }

    /** The order and labels of a pack without loaded-kind files are what the fingerprint always digested. */
    @Test
    void aPackWithoutLoadedKindFilesDigestsAsItAlwaysDid(@TempDir Path dir) throws IOException {
        Path root = Files.createDirectories(dir.resolve("qa"));
        Files.createDirectories(root.resolve("drg"));
        Files.writeString(root.resolve("drg/fragment.yaml"), "frag\n");
        Files.writeString(root.resolve("b.graph.yaml"), "b\n");
        Files.writeString(root.resolve("a.graph.yaml"), "a\n");
        Files.writeString(root.resolve("pack.yaml"), "pack\n");
        Files.writeString(root.resolve("notes.yaml"), "ignored\n");

        Fingerprint expected = Fingerprint.sha256();
        for (String file : List.of("pack.yaml", "a.graph.yaml", "b.graph.yaml", "drg/fragment.yaml")) {
            expected.add("qa/" + file).add(Files.readAllBytes(root.resolve(file)));
        }

        assertThat(sources(root).fingerprint()).isEqualTo(expected.value());
    }

    @Test
    void aLoadedFileThatCannotBeReadRefusesTheFingerprint(@TempDir Path dir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")), "permissions do not bite for root");
        Path root = Files.createDirectories(dir.resolve("qa"));
        Path unreadable = Files.createDirectories(root.resolve("deep/er")).resolve("e.tactic.yaml");
        Files.writeString(unreadable, "id: e\n");
        assumeTrue(unreadable.toFile().setReadable(false), "the file system cannot revoke read access");

        assertThatThrownBy(() -> sources(root).fingerprint()).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(r.getMessage()).isEqualTo("qa/deep/er/e.tactic.yaml cannot be read");
        });
    }

    @Test
    void aNestedDirectoryThatCannotBeListedRefusesTheFingerprint(@TempDir Path dir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")), "permissions do not bite for root");
        Path root = Files.createDirectories(dir.resolve("qa"));
        Path nested = Files.createDirectories(root.resolve("deep/er"));
        assumeTrue(nested.toFile().setReadable(false), "the file system cannot revoke read access");

        try {
            assertThatThrownBy(() -> sources(root).fingerprint())
                    .isInstanceOfSatisfying(
                            CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        } finally {
            assertThat(nested.toFile().setReadable(true)).isTrue();
        }
    }

    /** Where permissions do not bite (root), a loaded file whose read fails with an I/O error still refuses. */
    @Test
    void aLoadedFileWhoseReadFailsRefusesTheFingerprintEvenForRoot(@TempDir Path dir) throws IOException {
        Path unreadable = Path.of("/proc/self/mem");
        assumeTrue(Files.isRegularFile(unreadable), "no /proc/self/mem on this system");
        Path root = Files.createDirectories(dir.resolve("qa"));
        Files.createSymbolicLink(root.resolve("e.tactic.yaml"), unreadable);

        assertThatThrownBy(() -> sources(root).fingerprint()).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(r.getMessage()).isEqualTo("qa/e.tactic.yaml cannot be read");
        });
    }

    private static FileSystemPackSources sources(Path root) {
        return new FileSystemPackSources(() -> List.of(new PackLocation("qa", PackLevel.ORG, root)));
    }
}
