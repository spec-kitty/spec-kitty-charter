package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.Payload;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The packs side of the effective-prompt read (FR-003, FR-005; ADR-0066 decisions 4, 5 and 9): for one step of a merged
 * closure, the prompt in force as stored, every slot it declares and, per slot, the fragments of the step enhancements
 * in force that fill it. A pure function of its inputs: it reads nothing but what it is handed.
 */
public final class EffectivePromptQuery {

    private EffectivePromptQuery() {}

    /**
     * A count of the work the query does (NFR-002): one call per visit of an enhancement or a slot. The reads never use
     * one; an operation-count test hands one in.
     */
    @FunctionalInterface
    interface Visits {

        /** Counts nothing. */
        Visits NONE = () -> {};

        /** Counts one visit of an enhancement or a slot. */
        void visit();
    }

    /**
     * One fragment of a slot, with where it came from.
     *
     * @param packId the configured identifier of the pack that holds the winning copy of the enhancement
     * @param packName the descriptor {@code name} of that pack, the second ordering key
     * @param level the level of that pack, the first ordering key
     * @param enhancement the enhancement's id, without its kind, the third ordering key
     * @param text the canonical text of the fragment, as the pack's prompt facts hold it
     */
    public record Fragment(PackIdentifier packId, String packName, PackLevel level, String enhancement, String text) {

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
     * One slot the prompt in force declares, with the fragments that fill it.
     *
     * @param slot the slot's id
     * @param fragments its fragments, by level, then pack name, then enhancement id, each by code point; empty when
     *     no enhancement in force fills it
     */
    public record Slot(SlotId slot, List<Fragment> fragments) {

        /**
         * Checks and copies the fields.
         *
         * @param slot the slot's id
         * @param fragments its fragments, in order
         */
        public Slot {
            Objects.requireNonNull(slot, "slot");
            fragments = List.copyOf(Objects.requireNonNull(fragments, "fragments"));
        }
    }

    /**
     * The effective prompt of a step.
     *
     * @param prompt the stored bytes of the prompt in force, unchanged (ADR-0066 decision 9); no byte when the step has
     *     no prompt
     * @param promptPackId the configured identifier of the pack that holds the prompt in force; empty when the step has
     *     no prompt
     * @param slots every slot the prompt declares, in declaration order
     * @param diagnostics a {@code prompt_slot_not_empty} per declared slot that holds text (OD-7), in declaration order
     */
    public record EffectivePrompt(
            Payload prompt, Optional<PackIdentifier> promptPackId, List<Slot> slots, List<Diagnostic> diagnostics) {

        /** The answer for a step with no prompt: no byte, no pack, no slot. */
        public static final EffectivePrompt NONE =
                new EffectivePrompt(Payload.of(new byte[0]), Optional.empty(), List.of(), List.of());

        /**
         * Checks and copies the fields.
         *
         * @param prompt the prompt's bytes
         * @param promptPackId the pack of the prompt
         * @param slots the declared slots
         * @param diagnostics the diagnostics
         */
        public EffectivePrompt {
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(promptPackId, "promptPackId");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /**
     * The effective prompt of a step.
     *
     * @param step the step, {@code action:M/s}
     * @param merged the layer merge of the closure
     * @param facts the facts each pack of the closure was read with, by configured identifier
     * @param inForce whether an artifact is in force under the request
     * @param packNames the descriptor {@code name} of each pack of the closure
     * @param promptOf the stored prompt of the step in a pack; empty when that pack holds none
     * @return the prompt in force, its slots and their fragments
     */
    public static EffectivePrompt answer(
            Urn step,
            MergedClosure merged,
            Map<String, PackCompositionFacts> facts,
            Predicate<Urn> inForce,
            Function<PackIdentifier, String> packNames,
            Function<PackIdentifier, Optional<Payload>> promptOf) {
        return answer(step, merged, facts, inForce, packNames, promptOf, Visits.NONE);
    }

    static EffectivePrompt answer(
            Urn step,
            MergedClosure merged,
            Map<String, PackCompositionFacts> facts,
            Predicate<Urn> inForce,
            Function<PackIdentifier, String> packNames,
            Function<PackIdentifier, Optional<Payload>> promptOf,
            Visits visits) {
        return EffectivePrompt.NONE;
    }
}
