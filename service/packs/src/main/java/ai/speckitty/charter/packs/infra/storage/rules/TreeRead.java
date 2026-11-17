package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackContents;
import java.util.List;
import java.util.Objects;

/**
 * The one read of a pack tree (#554, S1, FR-016): what the storage rules judge. The one reader in the storage edge
 * produces it; nothing outside {@code packs.infra.storage} and the composition root names it, and registration and the
 * validation edge see it only as the {@code PackReadout} of the domain port {@code PackReading} (S6).
 *
 * @param contents the pack's storage-agnostic facts; a file the read refused yields no fact
 * @param census the tree's files, held and not held, the manifest and the reserved shard
 * @param readFaults the faults the read met, each a kernel reason with the code its owner builds, the file as its
 *     location and the reader's wording, in the reader's order: the descriptor, the root shards, the fragment, every
 *     other file, then the format prechecks (S2)
 */
public record TreeRead(PackContents contents, TreeCensus census, List<Reason> readFaults) {
    /**
     * Checks the fields and copies the faults.
     *
     * @param contents the pack's facts
     * @param census the census
     * @param readFaults the read faults
     */
    public TreeRead {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(census, "census");
        readFaults = List.copyOf(readFaults);
    }
}
