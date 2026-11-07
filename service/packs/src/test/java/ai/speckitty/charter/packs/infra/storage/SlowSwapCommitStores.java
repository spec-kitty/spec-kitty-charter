package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Test support: a {@link PackCommitStore} whose directory swap pauses between its two renames, so a reader can run in
 * the window where the live directory is missing. It lives here because the commit store's swap seam is package-private.
 */
public final class SlowSwapCommitStores {

    private SlowSwapCommitStores() {}

    /**
     * A commit store whose swap runs {@code midSwap} after the live directory is moved aside and before the staging
     * directory takes its place.
     *
     * @param locations the configured packs
     * @param strategy the serialization format
     * @param midSwap what runs inside the window; it may block
     * @return the commit store
     */
    public static PackCommitStore pausingMidSwap(
            DomainRepositoryService.Locations locations, SerializationStrategy strategy, Consumer<Path> midSwap) {
        return new PackCommitStore(locations, strategy, (staging, live) -> {
            Path aside = live.resolveSibling(live.getFileName() + ".pack-old-slow");
            PackCommitStore.moveDirectory(live, aside);
            try {
                midSwap.accept(live);
                PackCommitStore.moveDirectory(staging, live);
            } catch (IOException | RuntimeException failed) {
                PackCommitStore.moveDirectory(aside, live);
                throw failed;
            }
            PackCommitStore.deleteTree(aside);
        });
    }
}
