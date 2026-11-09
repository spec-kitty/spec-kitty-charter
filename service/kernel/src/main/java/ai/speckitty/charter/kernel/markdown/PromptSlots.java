package ai.speckitty.charter.kernel.markdown;

import java.util.List;
import java.util.Optional;

/** Stub, replaced by the parser in a later commit. */
public final class PromptSlots {

    private PromptSlots() {}

    /** The kind of a fault. */
    public enum Kind {
        /** A marker breaks the grammar. */
        MALFORMED,
        /** A slot holds text. */
        NOT_EMPTY
    }

    /**
     * A declared slot.
     *
     * @param id the id
     * @param position the declaration order
     * @param empty whether it holds only whitespace
     */
    public record Slot(SlotId id, int position, boolean empty) {}

    /**
     * A fault.
     *
     * @param kind the kind
     * @param slot the slot
     * @param line the line
     * @param reason the reason
     */
    public record Fault(Kind kind, Optional<SlotId> slot, int line, String reason) {}

    /**
     * An answer.
     *
     * @param slots the slots
     * @param faults the faults
     */
    public record Parse(List<Slot> slots, List<Fault> faults) {}

    /**
     * Stub.
     *
     * @param text the text
     * @return the answer
     */
    public static Parse parse(String text) {
        return new Parse(List.of(), List.of());
    }

    /**
     * Stub.
     *
     * @param text the text
     * @return whether it holds a marker
     */
    public static boolean containsMarker(String text) {
        return false;
    }
}
