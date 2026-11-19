package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
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
 * @param facts the pack's composition facts from the same read, or the refusal that stopped them (#478, S1): what the
 *     pack-set check ({@code CLOSURE}) judges of the pack
 */
public record PackReadout(
        PackContents contents,
        List<ClassifiedFile> files,
        List<NotHeldFile> notHeld,
        Rationale rationale,
        Facts facts) {
    /**
     * Checks the fields and copies the lists.
     *
     * @param contents the pack's facts
     * @param files the classified files
     * @param notHeld the files not held
     * @param rationale the verdict of the read
     * @param facts the composition facts, or why there are none
     */
    public PackReadout {
        Objects.requireNonNull(contents, "contents");
        files = List.copyOf(files);
        notHeld = List.copyOf(notHeld);
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(facts, "facts");
    }

    /**
     * The composition facts of the one read (#478, S1, S10): {@link Built} when the composing read would answer the pack,
     * {@link Unreadable} when it would refuse it. Built from the store half of the same read, never by a second read.
     */
    public sealed interface Facts permits Built, Unreadable {}

    /**
     * The facts the composing read answers for the pack: what {@code CLOSURE} judges of it.
     *
     * @param descriptor the pack's descriptor, as the load assembled it
     * @param facts the pack's composition facts
     */
    public record Built(PackDescriptor descriptor, PackCompositionFacts facts) implements Facts {
        /**
         * Checks the fields.
         *
         * @param descriptor the descriptor
         * @param facts the facts
         */
        public Built {
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(facts, "facts");
        }
    }

    /**
     * The refusal the composing read gives the pack instead of its facts: {@code pack_unreadable} for a graph document,
     * a file or a descriptor that cannot be read, or the load's own code (such as {@code validation_failed}).
     *
     * @param code the refusal's code
     * @param detail the refusal's detail, worded as the composing read words it
     */
    public record Unreadable(RefusalCode code, String detail) implements Facts {
        /**
         * Checks the fields.
         *
         * @param code the code
         * @param detail the detail
         */
        public Unreadable {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
        }

        /**
         * The facts of a read with no assembly input ({@code PackContents.Assembly.None}, facts written by hand): the
         * pack cannot be composed, {@code pack_unreadable}. The one wording of that answer, for the reader and for a
         * test's hand-written readout.
         *
         * @param pack the pack's configured name
         * @return the facts
         */
        public static Unreadable noAssembly(String pack) {
            return new Unreadable(
                    RefusalCode.PACK_UNREADABLE, "the pack " + pack + " cannot be read: it has no assembly");
        }
    }
}
