package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a removal reads as authored in a pack's graph documents ({@link CommitStorePackWriteStore#authored}): it fails
 * closed on a document it cannot read, so a removal never passes over what such a document declares or joins (#254).
 */
class CommitStorePackWriteStoreAuthoredTest {

    private static final PackIdentifier PACK = new PackIdentifier("qa");

    private static CommitStorePackWriteStore storeOver(Path host, String bodyPath) throws IOException {
        Path pack = host.resolve(PACK.value());
        write(pack, "pack.yaml", """
                pack_id: 01M4P01D5TWB5FKZGNNQG3943R
                pack_version: 1.0.0
                name: qa
                pack_level: org
                pack_drg: drg/fragment.yaml
                """);
        write(pack, "drg/fragment.yaml", """
            nodes:
            - id: D1
              kind: directives
              title: D1
              body_path: %s
            - id: D2
              kind: directives
              title: D2
            edges:
            - {source: 'directive:D1', target: 'directive:D2', relation: suggests}
            """.formatted(bodyPath));
        StorageRootLocations locations = new StorageRootLocations(host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        return new CommitStorePackWriteStore(locations, strategy, new PackCommitStore(locations, strategy));
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    void aFragmentWhoseBodyPathStaysInsideThePackIsReadAsAuthored(@TempDir Path host) throws IOException {
        CommitStorePackWriteStore store = storeOver(host, "directives/D1.directive.yaml");

        assertThat(store.authored(PACK).declared())
                .containsExactly(new Urn(NodeKind.DIRECTIVE, "D1"), new Urn(NodeKind.DIRECTIVE, "D2"));
        assertThat(store.authored(PACK).edges()).hasSize(1);
    }

    @Test
    void aFragmentWhoseBodyPathLeavesThePackRefusesThePackRatherThanReadingNothingAuthored(@TempDir Path host)
            throws IOException {
        CommitStorePackWriteStore store = storeOver(host, "../outside.directive.yaml");

        assertThatThrownBy(() -> store.authored(PACK))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage("a graph document of qa cannot be read");
    }
}
