package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackVersionHistory.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The file of the version history (IC-16): {@code <packs>/.history/<packId>.json}, a list of three-field entries. */
class VersionHistoryStoreTest {

    private static final Entry ONE =
            new Entry("1.0.0", "sha256:" + "a".repeat(64), Instant.parse("2026-11-07T10:00:00Z"));
    private static final Entry TWO =
            new Entry("2.0.0", "sha256:" + "b".repeat(64), Instant.parse("2026-11-07T11:30:00Z"));

    private static void publish(VersionHistoryStore store, String packId, List<Entry> entries) {
        store.publish(packId, store.stage(packId, entries));
    }

    @Test
    void aNameNeverRegisteredHasNoHistory(@TempDir Path root) {
        assertThat(new VersionHistoryStore(root).read("pack-x")).isEmpty();
    }

    @Test
    void whatIsPublishedIsReadBackInOrder(@TempDir Path root) {
        VersionHistoryStore store = new VersionHistoryStore(root);

        publish(store, "pack-x", List.of(ONE, TWO));

        assertThat(store.read("pack-x")).containsExactly(ONE, TWO);
        assertThat(store.read("pack-y")).as("another name has its own file").isEmpty();
    }

    @Test
    void theFileIsAJsonListOfPackVersionRevisionAndRegisteredAt(@TempDir Path root) throws IOException {
        VersionHistoryStore store = new VersionHistoryStore(root);

        publish(store, "pack-x", List.of(ONE));

        assertThat(root.resolve(".history").resolve("pack-x.json")).isEqualTo(store.fileOf("pack-x"));
        assertThat(store.fileOf("pack-x")).isRegularFile();
        assertThat(Files.readString(store.fileOf("pack-x")))
                .contains("\"pack_version\"")
                .contains("\"1.0.0\"")
                .contains("\"revision\"")
                .contains(ONE.revision())
                .contains("\"registeredAt\"")
                .contains("2026-11-07T10:00:00Z");
    }

    @Test
    void stagingWritesNothingUnderTheFinalNameAndPublishingLeavesNoTemporaryFile(@TempDir Path root)
            throws IOException {
        VersionHistoryStore store = new VersionHistoryStore(root);
        publish(store, "pack-x", List.of(ONE));

        Path staged = store.stage("pack-x", List.of(ONE, TWO));

        assertThat(store.read("pack-x")).as("staged is not yet published").containsExactly(ONE);
        store.publish("pack-x", staged);
        assertThat(store.read("pack-x")).containsExactly(ONE, TWO);
        assertThat(root.resolve(".history")).isDirectory();
        try (Stream<Path> files = Files.list(root.resolve(".history"))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("pack-x.json");
        }
    }

    @Test
    void aFileThatIsNotAHistoryIsPackUnreadableNotEmpty(@TempDir Path root) throws IOException {
        VersionHistoryStore store = new VersionHistoryStore(root);
        Files.createDirectories(store.fileOf("pack-x").getParent());

        for (String bad : List.of(
                "{ not json",
                "{}",
                "[{\"pack_version\":\"1.0.0\"}]",
                "[{\"pack_version\":\"1.0.0\",\"revision\":\"r\",\"registeredAt\":\"yesterday\"}]")) {
            Files.writeString(store.fileOf("pack-x"), bad);
            assertThatThrownBy(() -> store.read("pack-x"))
                    .as(bad)
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        }
    }

    @Test
    void aPackIdThatWouldLeaveTheHistoryDirectoryIsRefused(@TempDir Path root) {
        VersionHistoryStore store = new VersionHistoryStore(root);

        for (String id : List.of("../x", "a/b", ".", "..", "")) {
            assertThatThrownBy(() -> store.fileOf(id)).as(id).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
