package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackVersionHistory;
import ai.speckitty.charter.packs.fixtures.FixtureUlids;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The store's removal of a pack whole (ADR-0081 D8, D9; review R4-2): one rename to a hidden discard name, the history
 * healed and kept, a failed rename leaving the pack whole, and nothing a listing's crash repair (#281) would restore.
 */
class PackRemoveWholeTest {

    private static Path installed(Path host) throws IOException {
        Path packs = Files.createDirectories(host.resolve("packs"));
        Path copy = PackFixtures.copyOf("pack-a", host.resolve("source"));
        Path live = packs.resolve("pack-a");
        Files.move(copy, live);
        Files.delete(live.resolve("broken-tactic.tactic.yaml"));
        return live;
    }

    private static List<String> namesBeside(Path live) throws IOException {
        try (Stream<Path> siblings = Files.list(live.getParent())) {
            return siblings.map(sibling -> sibling.getFileName().toString()).toList();
        }
    }

    @Test
    void thePackIsRemovedWholeAndItsHistoryIsHealedAndKept(@TempDir Path host) throws IOException {
        Path live = installed(host);
        StorageRootLocations locations = new StorageRootLocations(live.getParent());
        PackCommitStore store = new PackCommitStore(locations, new YamlSerializationStrategy());
        assertThat(store.versionHistory("pack-a"))
                .as("a pack placed by hand has no history")
                .isEmpty();

        store.removeWhole(live, "pack-a");

        assertThat(live).doesNotExist();
        assertThat(namesBeside(live)).as("no aside or discard copy is left").noneMatch(name -> name.contains(".pack-"));
        assertThat(store.versionHistory("pack-a"))
                .as("the stored pair is healed into the history, so its label stays taken (D9)")
                .extracting(PackVersionHistory.Entry::packVersion)
                .hasSize(1);
        assertThat(locations.all()).noneMatch(location -> location.name().equals("pack-a"));
    }

    @Test
    void aRenameThatFailsLeavesThePackWhole(@TempDir Path host) throws IOException {
        Path live = installed(host);
        PackCommitStore store =
                new PackCommitStore(new StorageRootLocations(live.getParent()), new YamlSerializationStrategy());
        List<String> files;
        try (Stream<Path> walk = Files.walk(live)) {
            files = walk.map(live::relativize).map(Path::toString).sorted().toList();
        }

        CharterRefusal refused = catchThrowableOfType(
                CharterRefusal.class,
                () -> store.removeWhole(live, "pack-a", (from, to) -> {
                    throw new IOException("injected: the rename fails");
                }));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
        try (Stream<Path> walk = Files.walk(live)) {
            assertThat(walk.map(live::relativize).map(Path::toString).sorted().toList())
                    .as("the pack is whole")
                    .isEqualTo(files);
        }
    }

    @Test
    void aCrashBeforeTheDiscardedTreeIsDeletedLeavesNothingAListingRestores(@TempDir Path host) throws IOException {
        Path live = installed(host);
        StorageRootLocations locations = new StorageRootLocations(live.getParent());
        PackCommitStore store = new PackCommitStore(locations, new YamlSerializationStrategy());
        Path[] discarded = new Path[1];

        // The process dies after the rename and before the delete: the rename is done and the tree is left.
        store.removeWhole(live, "pack-a", (from, to) -> {
            Files.move(from, to);
            discarded[0] = to;
        });
        Files.createDirectories(discarded[0]);
        Files.writeString(discarded[0].resolve("pack.yaml"), "pack_id: " + FixtureUlids.of("pack-a") + "\n");

        assertThat(locations.all())
                .as("a hidden discard copy is never restored as the pack (#281)")
                .noneMatch(location -> location.name().equals("pack-a"));
        assertThat(live).doesNotExist();
    }
}
