package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * NFR-001: the {@code content_hash} recipe reproduces every hash the pinned built-in manifest lists. That manifest is
 * not in the sanitized shape (ADR-0043, OP-2), so it is read here with the raw YAML loader, never through
 * {@link PackManifestYaml}; its hashes are data, checked against the pinned bytes.
 */
@Tag("open-packs")
@DisplayName("Pack manifest content_hash parity with the pinned manifest (#195, NFR-001)")
class PackManifestParityTest {

    @Test
    @DisplayName("all 272 constituent and 2 preset hashes of the pinned built-in manifest are reproduced")
    void everyPinnedHashIsReproduced() throws IOException {
        Path builtIn = OpenPacksCorpus.packsDirectory().resolve("built-in");
        Map<?, ?> manifest =
                (Map<?, ?>) Yaml.load(Files.readString(builtIn.resolve("pack-manifest.yaml"), StandardCharsets.UTF_8));
        List<Map<?, ?>> entries = new ArrayList<>();
        for (Object entry : (List<?>) manifest.get("constituents")) {
            entries.add((Map<?, ?>) entry);
        }
        int constituents = entries.size();
        for (Object entry : (List<?>) manifest.get("presets")) {
            entries.add((Map<?, ?>) entry);
        }

        List<String> mismatched = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            String path = String.valueOf(entry.get("path"));
            String expected = String.valueOf(entry.get("content_hash"));
            if (!PackManifests.contentHash(Files.readAllBytes(builtIn.resolve(path)))
                    .equals(expected)) {
                mismatched.add(path);
            }
        }

        assertThat(constituents).isEqualTo(272);
        assertThat(entries).hasSize(274);
        assertThat(mismatched).isEmpty();
    }
}
