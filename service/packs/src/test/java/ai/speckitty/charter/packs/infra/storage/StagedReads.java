package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import java.nio.file.Path;

/**
 * Step 2 of a registration over a staged tree, as {@link PackRegistrationService} runs it (#554, WP12): the one read
 * through the reader the store composes, judged, then assembled by the store. The pack is read under its directory's
 * name, as a test stages it.
 */
final class StagedReads {

    private StagedReads() {}

    /**
     * Reads, judges and assembles a staged tree.
     *
     * @param store the store, whose reader reads the tree with its own bindings
     * @param directory the staged tree
     * @return the read, what the tree holds and its diagnostics
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal the registration's refusal
     */
    static PackRegistrationService.ReadUpload read(WholePackStore store, Path directory) {
        return PackRegistrationService.readUpload(
                store.reader(), store, String.valueOf(directory.getFileName()), new StagedPack(directory));
    }

    /**
     * What a staged tree the registration accepts holds.
     *
     * @param store the store
     * @param directory the staged tree
     * @return what the tree holds
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal the registration's refusal
     */
    static WholePackWriteStore.Contents contents(WholePackStore store, Path directory) {
        return read(store, directory).contents();
    }
}
