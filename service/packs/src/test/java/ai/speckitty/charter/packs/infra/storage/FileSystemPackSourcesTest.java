package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    private static FileSystemPackSources sources(Path root) {
        return new FileSystemPackSources(() -> List.of(new PackLocation("qa", PackLevel.ORG, root)));
    }
}
