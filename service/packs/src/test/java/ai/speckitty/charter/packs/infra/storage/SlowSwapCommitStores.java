package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Test support: a {@link PackCommitStore} whose directory swap pauses between its two renames, so a reader can run in
 * the window where the live directory is missing. It is the real swap ({@link PackCommitStore#moveAside}) with its
 * {@link PackCommitStore.SwapPhases#afterAside} pause, so the aside copy is named, hidden and deleted as in production
 * (#490). It lives here because the commit store's swap seam is package-private.
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
            Locations locations, SerializationStrategy strategy, Consumer<Path> midSwap) {
        return pausingMidSwap(locations, strategy, midSwap, RegistryWriteLock.shared(), PackWriteGeneration.shared());
    }

    /**
     * As {@link #pausingMidSwap(Locations, SerializationStrategy, Consumer)}, over a registry lock
     * and a write generation of the test's own.
     *
     * @param locations the configured packs
     * @param strategy the serialization format
     * @param midSwap what runs inside the window; it may block
     * @param lock the registry lock a whole-pack registration takes
     * @param generation the write generation the swap is bracketed with
     * @return the commit store
     */
    public static PackCommitStore pausingMidSwap(
            Locations locations,
            SerializationStrategy strategy,
            Consumer<Path> midSwap,
            RegistryWriteLock lock,
            PackWriteGeneration generation) {
        return new PackCommitStore(
                locations,
                strategy,
                PackCommitStore.moveAside(new PackCommitStore.SwapPhases() {
                    @Override
                    public void afterAside(Path live, Path aside) {
                        midSwap.accept(live);
                    }
                }),
                lock,
                generation);
    }
}
