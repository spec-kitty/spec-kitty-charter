package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The effective prompt of one step under an activation (FR-003, FR-005; ADR-0066 decisions 4, 5 and 9): the prompt in
 * force as it is stored, every slot it declares in declaration order, each with the fragments of the step enhancements
 * in force that fill it, and a diagnostic per slot that still holds legacy text. The packs slice decides it; this is the
 * activations slice's value of it, which the read answers and the tag never digests.
 *
 * <p>A step with no prompt companion has no prompt: no text, no pack and no slot ({@link #NONE}, squad A11). The
 * fragments of a slot are in the order the packs slice gives them (level, then pack name, then enhancement id, each by
 * code point); this value keeps that order and judges nothing.
 *
 * @param prompt the stored text of the prompt in force, unchanged; empty when the step has no prompt
 * @param promptPackId the configured identifier of the pack that holds the prompt in force; empty when the step has no
 *     prompt
 * @param slots every slot the prompt declares, in declaration order, filled or not
 * @param diagnostics a {@link Diagnostic.Code#PROMPT_SLOT_NOT_EMPTY} per declared slot that holds legacy text (OD-7)
 */
public record EffectivePrompt(
        String prompt, Optional<String> promptPackId, List<Slot> slots, List<Diagnostic> diagnostics) {

    /** The answer for a step with no prompt companion: no text, no pack, no slot and no diagnostic. */
    public static final EffectivePrompt NONE = new EffectivePrompt("", Optional.empty(), List.of(), List.of());

    /**
     * Checks and copies the fields.
     *
     * @param prompt the prompt's text
     * @param promptPackId the pack of the prompt
     * @param slots the declared slots
     * @param diagnostics the diagnostics
     * @throws IllegalArgumentException when no pack is named and there is text, a slot or a diagnostic: only a prompt a
     *     pack holds has any
     */
    public EffectivePrompt {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(promptPackId, "promptPackId");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (promptPackId.isEmpty() && (!prompt.isEmpty() || !slots.isEmpty() || !diagnostics.isEmpty())) {
            throw new IllegalArgumentException(
                    "A step with no prompt has no text, slot or diagnostic; the pack of the prompt is not named.");
        }
    }

    /**
     * One slot the prompt in force declares, with the fragments that fill it.
     *
     * @param slot the slot's id, {@code <namespace>:<name>}, as the prompt declares it
     * @param fragments its fragments, in order; empty when no enhancement in force fills it
     */
    public record Slot(String slot, List<Fragment> fragments) {

        /**
         * Checks and copies the fields.
         *
         * @param slot the slot's id
         * @param fragments its fragments
         */
        public Slot {
            Objects.requireNonNull(slot, "slot");
            fragments = List.copyOf(Objects.requireNonNull(fragments, "fragments"));
        }
    }

    /**
     * One fragment of a slot, with where it came from.
     *
     * @param packId the configured identifier of the pack that holds the winning copy of the enhancement
     * @param packName the descriptor {@code name} of that pack
     * @param level the level of that pack
     * @param enhancement the enhancement's id, without its kind
     * @param text the fragment's text
     */
    public record Fragment(String packId, String packName, PackLevel level, String enhancement, String text) {

        /**
         * Checks the fields.
         *
         * @param packId the enhancing pack
         * @param packName its descriptor name
         * @param level its level
         * @param enhancement the enhancement's id
         * @param text the fragment's text
         */
        public Fragment {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(packName, "packName");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(enhancement, "enhancement");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A finding about the prompt that does not stop the read (the contract's {@code Diagnostic}).
     *
     * @param code what was found
     * @param detail what was found and what the load did about it, naming pack-relative paths only
     */
    public record Diagnostic(Code code, String detail) {

        /** The findings the prompt read lists (the contract's {@code DiagnosticCode}, in part). */
        public enum Code {
            /** A declared slot holds legacy text under an unversioned pack descriptor; the text is kept. */
            PROMPT_SLOT_NOT_EMPTY;

            /**
             * The contract's spelling.
             *
             * @return the snake_case token
             */
            public String token() {
                return name().toLowerCase(Locale.ROOT);
            }
        }

        /**
         * Checks the fields.
         *
         * @param code what was found
         * @param detail the detail
         */
        public Diagnostic {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
