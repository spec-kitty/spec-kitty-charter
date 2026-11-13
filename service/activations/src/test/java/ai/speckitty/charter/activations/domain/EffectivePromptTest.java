package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The activations slice's value of an effective prompt (FR-003, FR-005): a step with no prompt has nothing else (squad
 * A11), the lists are copies, and the diagnostic code spells the contract's token.
 */
class EffectivePromptTest {

    private static final EffectivePrompt.Fragment FRAGMENT =
            new EffectivePrompt.Fragment("base", "base", PackLevel.ORG, "spdd-plan", "Fill the canvas.\n");

    @Test
    void theAnswerForAStepWithNoPromptHasNoTextNoPackAndNoSlot() {
        assertThat(EffectivePrompt.NONE.prompt()).isEmpty();
        assertThat(EffectivePrompt.NONE.promptPackId()).isEmpty();
        assertThat(EffectivePrompt.NONE.slots()).isEmpty();
        assertThat(EffectivePrompt.NONE.diagnostics()).isEmpty();
    }

    @Test
    void aPromptWithoutAPackIsRefusedAndItsTwinWithThePackIsKept() {
        List<EffectivePrompt.Slot> slots = List.of(new EffectivePrompt.Slot("spdd:reasons-block", List.of(FRAGMENT)));

        assertThatThrownBy(() -> new EffectivePrompt("# Plan\n", Optional.empty(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EffectivePrompt("", Optional.empty(), slots, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new EffectivePrompt("# Plan\n", Optional.of("base"), slots, List.of()).slots())
                .containsExactly(slots.getFirst());
    }

    @Test
    void anEmptyPromptAPackHoldsIsAPrompt() {
        EffectivePrompt empty = new EffectivePrompt("", Optional.of("base"), List.of(), List.of());

        assertThat(empty.promptPackId()).contains("base");
    }

    @Test
    void theListsAreCopiesInTheOrderGiven() {
        EffectivePrompt.Fragment second =
                new EffectivePrompt.Fragment("arch", "architecture-notes", PackLevel.ORG, "arch-plan", "ADR.\n");
        List<EffectivePrompt.Fragment> fragments = new ArrayList<>(List.of(FRAGMENT, second));
        EffectivePrompt.Slot slot = new EffectivePrompt.Slot("spdd:reasons-block", fragments);
        List<EffectivePrompt.Slot> slots = new ArrayList<>(List.of(slot));

        EffectivePrompt prompt = new EffectivePrompt("# Plan\n", Optional.of("base"), slots, List.of());
        fragments.clear();
        slots.clear();

        assertThat(prompt.slots())
                .singleElement()
                .satisfies(kept -> assertThat(kept.fragments()).containsExactly(FRAGMENT, second));
    }

    @Test
    void theDiagnosticCodeIsTheContractToken() {
        EffectivePrompt.Diagnostic diagnostic = new EffectivePrompt.Diagnostic(
                EffectivePrompt.Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY, "slot spdd:reasons-block holds text");

        assertThat(diagnostic.code().token()).isEqualTo("prompt_slot_not_empty");
    }
}
