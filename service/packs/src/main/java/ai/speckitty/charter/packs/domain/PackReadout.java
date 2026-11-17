package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Rationale;
import java.util.List;
import java.util.Objects;

/**
 * What {@link PackReading} returns for one read of a pack (#554, S6): kernel and domain values only.
 *
 * @param contents the pack's storage-agnostic facts
 * @param files every regular file of the pack the read classified, with its kind or its exclusion: what validate mode's
 *     verdict shows
 * @param notHeld every file of the pack the store does not hold, path-sorted, each with its kind and reason: what a
 *     registration removes before it stores the pack
 * @param rationale the storage edge's verdict of the read: its read faults, in the reader's order
 */
public record PackReadout(
        PackContents contents, List<ClassifiedFile> files, List<NotHeldFile> notHeld, Rationale rationale) {
    /**
     * Checks the fields and copies the lists.
     *
     * @param contents the pack's facts
     * @param files the classified files
     * @param notHeld the files not held
     * @param rationale the verdict of the read
     */
    public PackReadout {
        Objects.requireNonNull(contents, "contents");
        files = List.copyOf(files);
        notHeld = List.copyOf(notHeld);
        Objects.requireNonNull(rationale, "rationale");
    }
}
