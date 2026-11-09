package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * OD-9, the one predicate for "pack P holds step M/s" (decision {@code 01M7037V93Y077H8M4W9V8Y6B3}): per pack, P
 * defines {@code mission_type:M} and declares {@code action:M/s}; on the closure, some closure pack holds it per pack.
 * The predicate reads the artifacts a pack defines and nothing else, so compose time and the merged reads call one
 * function.
 */
class StepHoldingTest {

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    @Test
    void aPackHoldsAStepWhenItDefinesTheMissionTypeAndDeclaresTheAction() {
        List<String> defined = List.of("mission_type:M", "action:M/one", "tactic:T");

        assertThat(StepHolding.heldByPack(defined, u("action:M/one"))).isTrue();
        assertThat(StepHolding.heldByPack(defined, u("action:M/two")))
                .as("a step the pack does not declare")
                .isFalse();
    }

    @Test
    void aPackThatDeclaresTheActionWithoutDefiningTheMissionTypeDoesNotHoldTheStep() {
        assertThat(StepHolding.heldByPack(List.of("action:M/one"), u("action:M/one")))
                .isFalse();
    }

    @Test
    void aPackThatDefinesTheMissionTypeWithoutDeclaringTheActionDoesNotHoldTheStep() {
        assertThat(StepHolding.heldByPack(List.of("mission_type:M"), u("action:M/one")))
                .isFalse();
    }

    @Test
    void theMissionTypeOfAnotherNameDoesNotHoldTheStep() {
        assertThat(StepHolding.heldByPack(List.of("mission_type:N", "action:M/one"), u("action:M/one")))
                .isFalse();
    }

    @Test
    void onlyAnActionOfTheFormMissionTypeSlashStepIsAStep() {
        List<String> defined = List.of("mission_type:M", "action:M", "tactic:M/one");

        assertThat(StepHolding.heldByPack(defined, u("tactic:M/one"))).isFalse();
        assertThat(StepHolding.heldByPack(defined, u("action:M"))).isFalse();
        assertThat(StepHolding.missionTypeOf(u("action:M/one"))).contains(u("mission_type:M"));
        assertThat(StepHolding.missionTypeOf(u("action:M"))).isEmpty();
        assertThat(StepHolding.missionTypeOf(u("tactic:M/one"))).isEmpty();
    }

    @Test
    void thePerPackFormListsTheStepsThePackHolds() {
        assertThat(StepHolding.stepsHeldByPack(
                        List.of("action:M/two", "mission_type:M", "action:M/one", "action:N/x", "tactic:T")))
                .containsExactly(u("action:M/one"), u("action:M/two"));
    }

    @Test
    void theClosureHoldsAStepWhenSomePackHoldsItPerPack() {
        List<String> org = List.of("mission_type:M", "action:M/one", "action:M/two");
        List<String> project = List.of("action:M/one");

        assertThat(StepHolding.heldByClosure(List.of(org, project), u("action:M/one")))
                .as("a layer override of a held step is the winning version of a held step")
                .isTrue();
        assertThat(StepHolding.heldByClosure(List.of(org, project), u("action:M/two")))
                .isTrue();
        assertThat(StepHolding.stepsHeldByClosure(List.of(org, project)))
                .containsExactly(u("action:M/one"), u("action:M/two"));
    }

    @Test
    void aWinningMissionTypeDoesNotTakeItsLosersUniqueSteps() {
        // A7, as built and documented in resolution-model.md: the project's mission_type:M wins, and the org pack's
        // step `two` is still held, because step M/two is a URN of its own.
        List<String> org = List.of("mission_type:M", "action:M/one", "action:M/two");
        List<String> project = List.of("mission_type:M", "action:M/one");

        assertThat(StepHolding.stepsHeldByClosure(List.of(org, project)))
                .containsExactly(u("action:M/one"), u("action:M/two"));
    }

    @Test
    void aDeclaredActionWithNoMissionTypeInTheClosureIsNotHeld() {
        List<String> alpha = List.of("action:M/one");
        List<String> beta = List.of("tactic:T");

        assertThat(StepHolding.heldByClosure(List.of(alpha, beta), u("action:M/one")))
                .isFalse();
        assertThat(StepHolding.stepsHeldByClosure(List.of(alpha, beta))).isEmpty();
    }

    @Test
    void theMissionTypeOfOnePackAndTheActionOfAnotherDoNotMakeAHeldStep() {
        List<String> typeOnly = List.of("mission_type:M");
        List<String> actionOnly = List.of("action:M/one");

        assertThat(StepHolding.heldByClosure(List.of(typeOnly, actionOnly), u("action:M/one")))
                .as("per pack: no single pack defines the type and declares the action")
                .isFalse();
    }

    @Test
    void theClosureFormDoesNotDependOnTheOrderOfThePacks() {
        List<List<String>> packs = new ArrayList<>(List.of(
                List.of("mission_type:M", "action:M/one"),
                List.of("action:M/one", "action:M/two"),
                List.of("mission_type:N", "action:N/a")));
        List<Urn> forward = new ArrayList<>(StepHolding.stepsHeldByClosure(packs));
        Collections.reverse(packs);

        assertThat(forward).containsExactly(u("action:M/one"), u("action:N/a"));
        assertThat(StepHolding.stepsHeldByClosure(packs)).containsExactlyElementsOf(forward);
        assertThat(StepHolding.heldByClosure(packs, u("action:M/one"))).isTrue();
    }
}
