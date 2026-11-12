package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.markdown.SlotId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The prompt facts of a pack (research R9, squad A1): the slots each held step prompt declares and the fills each held
 * step enhancement writes, built from the facts the verified read already holds.
 */
class PromptFactsTest {

    private static final SlotId REASONS = new SlotId("spdd:reasons-block");
    private static final SlotId NOTES = new SlotId("team:notes");

    private static Urn action(String missionType, String step) {
        return Urn.action(missionType, step);
    }

    private static Urn enhancement(String id) {
        return Urn.parse("step_enhancement:" + id).orElseThrow();
    }

    private static PackContents.PromptSlotFacts prompt(String file, String text) {
        return new PackContents.PromptSlotFacts(file, PromptSlots.parse(text));
    }

    private static StepEnhancementFacts.Enhancement enhancementFile(
            String type, String id, StepEnhancementFacts.Entry... entries) {
        return new StepEnhancementFacts.Enhancement(
                "missions/" + type + "/enhancements/" + id + ".step-enhancement.yaml", type, id, List.of(entries));
    }

    private static StepEnhancementFacts.Companion companion(String type, String name, String text) {
        return StepEnhancementFacts.Companion.read("missions/" + type + "/enhancements/" + name, false, false, text);
    }

    private static StepEnhancementFacts enhancements(
            List<StepEnhancementFacts.Enhancement> enhancements, List<StepEnhancementFacts.Companion> companions) {
        return new StepEnhancementFacts(enhancements, companions, Set.of(), Set.of(), List.of());
    }

    // ---- the record --------------------------------------------------------------------------------------------

    @Test
    void noneDeclaresAndFillsNothing() {
        assertThat(PromptFacts.NONE.declared()).isEmpty();
        assertThat(PromptFacts.NONE.fills()).isEmpty();
    }

    @Test
    void theCompositionFactsCarryThemAndEveryShorterFormMeansNone() {
        PackCompositionFacts shortest = new PackCompositionFacts(List.of(), List.of(), List.of());
        PromptFacts prompts = new PromptFacts(
                Map.of(action("software-dev", "plan"), List.of(new PromptFacts.DeclaredSlot(REASONS, false))),
                Map.of());

        PackCompositionFacts full = new PackCompositionFacts(
                List.of(),
                List.of(),
                List.of(),
                MutabilityPolicySource.empty(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                prompts);

        assertThat(PackCompositionFacts.NONE.prompts()).isSameAs(PromptFacts.NONE);
        assertThat(shortest.prompts()).isSameAs(PromptFacts.NONE);
        assertThat(new PackCompositionFacts(
                                List.of(),
                                List.of(),
                                List.of(),
                                MutabilityPolicySource.empty(),
                                List.of(),
                                List.of(),
                                Map.of(),
                                Map.of())
                        .prompts())
                .isSameAs(PromptFacts.NONE);
        assertThat(full.prompts()).isEqualTo(prompts);
        assertThat(full).as("the member takes part in equality").isNotEqualTo(shortest);
    }

    @Test
    void theCopiesAreDefensive() {
        List<PromptFacts.DeclaredSlot> slots = new ArrayList<>(List.of(new PromptFacts.DeclaredSlot(REASONS, false)));
        Map<Urn, List<PromptFacts.DeclaredSlot>> declared = new LinkedHashMap<>();
        declared.put(action("software-dev", "plan"), slots);
        List<PromptFacts.Fill> entries = new ArrayList<>(List.of(new PromptFacts.Fill(REASONS, "Reason.\n")));
        Map<Urn, List<PromptFacts.Fill>> fills = new LinkedHashMap<>();
        fills.put(enhancement("reasons"), entries);

        PromptFacts facts = new PromptFacts(declared, fills);
        slots.add(new PromptFacts.DeclaredSlot(NOTES, false));
        declared.put(action("software-dev", "review"), List.of());
        entries.clear();
        fills.clear();

        assertThat(facts.declared())
                .containsExactly(Map.entry(
                        action("software-dev", "plan"), List.of(new PromptFacts.DeclaredSlot(REASONS, false))));
        assertThat(facts.fills())
                .containsExactly(
                        Map.entry(enhancement("reasons"), List.of(new PromptFacts.Fill(REASONS, "Reason.\n"))));
        assertThatThrownBy(() -> facts.declared().put(action("software-dev", "tasks"), List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facts.declared()
                        .get(action("software-dev", "plan"))
                        .add(new PromptFacts.DeclaredSlot(NOTES, false)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facts.fills().get(enhancement("reasons")).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theKeysAreInTheirSortedOrderWhateverTheInputOrder() {
        Map<Urn, List<PromptFacts.DeclaredSlot>> declared = new LinkedHashMap<>();
        declared.put(action("software-dev", "tasks"), List.of());
        declared.put(action("documentation", "write"), List.of());
        declared.put(action("software-dev", "plan"), List.of());
        Map<Urn, List<PromptFacts.Fill>> fills = new LinkedHashMap<>();
        fills.put(enhancement("zeta"), List.of());
        fills.put(enhancement("alpha"), List.of());

        PromptFacts facts = new PromptFacts(declared, fills);

        assertThat(facts.declared().keySet())
                .containsExactly(
                        action("documentation", "write"),
                        action("software-dev", "plan"),
                        action("software-dev", "tasks"));
        assertThat(facts.fills().keySet()).containsExactly(enhancement("alpha"), enhancement("zeta"));
        Map<Urn, List<PromptFacts.DeclaredSlot>> reversed = new LinkedHashMap<>();
        List.copyOf(declared.keySet()).reversed().forEach(urn -> reversed.put(urn, List.of()));
        assertThat(new PromptFacts(reversed, fills)).isEqualTo(facts);
    }

    @Test
    void aDeclaredKeyIsAnActionAndAFillKeyIsAStepEnhancement() {
        assertThatThrownBy(() -> new PromptFacts(Map.of(enhancement("reasons"), List.of()), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step_enhancement:reasons");
        assertThatThrownBy(() -> new PromptFacts(Map.of(), Map.of(action("software-dev", "plan"), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action:software-dev/plan");
    }

    // ---- the factory -------------------------------------------------------------------------------------------

    @Test
    void eachStepPromptDeclaresItsSlotsInDeclarationOrderUnderItsAction() {
        PromptFacts facts = PromptFacts.of(
                List.of(
                        prompt("missions/software-dev/steps/plan.prompt.md", """
                                # Plan

                                <!-- team:notes:start -->
                                <!-- team:notes:end -->

                                <!-- spdd:reasons-block:start -->
                                <!-- spdd:reasons-block:end -->
                                """),
                        prompt("missions/software-dev/steps/specify.prompt.md", "# Specify\n")),
                StepEnhancementFacts.none());

        assertThat(facts.declared())
                .containsExactly(
                        Map.entry(
                                action("software-dev", "plan"),
                                List.of(
                                        new PromptFacts.DeclaredSlot(NOTES, false),
                                        new PromptFacts.DeclaredSlot(REASONS, false))),
                        Map.entry(action("software-dev", "specify"), List.of()));
        assertThat(facts.fills()).isEmpty();
    }

    @Test
    void aSlotThatHoldsTextIsDeclaredAndSaysSo() {
        PromptFacts facts = PromptFacts.of(
                List.of(prompt(
                        "missions/software-dev/steps/plan.prompt.md",
                        "<!-- spdd:reasons-block:start -->\nlegacy text\n<!-- spdd:reasons-block:end -->\n")),
                StepEnhancementFacts.none());

        assertThat(facts.declared().get(action("software-dev", "plan")))
                .containsExactly(new PromptFacts.DeclaredSlot(REASONS, true));
    }

    @Test
    void anOnDemandStepsPromptIsMappedLikeAnyOtherStep() {
        PromptFacts facts = PromptFacts.of(
                List.of(prompt(
                        "missions/software-dev/steps/hotfix.prompt.md",
                        "<!-- spdd:reasons-block:start -->\n<!-- spdd:reasons-block:end -->\n")),
                StepEnhancementFacts.none());

        assertThat(facts.declared())
                .containsExactly(Map.entry(
                        action("software-dev", "hotfix"), List.of(new PromptFacts.DeclaredSlot(REASONS, false))));
    }

    @Test
    void aPromptOutsideAMissionTypesStepsIsNoStepPrompt() {
        String slotted = "<!-- spdd:reasons-block:start -->\n<!-- spdd:reasons-block:end -->\n";

        PromptFacts facts = PromptFacts.of(
                List.of(
                        prompt("prompts/plan.prompt.md", slotted),
                        prompt("missions/software-dev/plan.prompt.md", slotted),
                        prompt("missions/software-dev/steps/nested/plan.prompt.md", slotted),
                        prompt("missions/software-dev/steps/plan.guidelines.md", slotted),
                        prompt("missions/software-dev/steps/.prompt.md", slotted),
                        prompt("missions/software-dev/steps/plan.prompt.md", slotted)),
                StepEnhancementFacts.none());

        assertThat(facts.declared().keySet())
                .as("only missions/<type>/steps/<step>.prompt.md is a step prompt; the control is held")
                .containsExactly(action("software-dev", "plan"));
    }

    @Test
    void eachEnhancementFillsItsSlotsWithItsCompanionsCanonicalTextInAuthoredOrder() {
        StepEnhancementFacts read = enhancements(
                List.of(
                        enhancementFile(
                                "software-dev",
                                "reasons-plan",
                                new StepEnhancementFacts.Entry(REASONS.value(), "reasons-plan.prompt-fragment.md"),
                                new StepEnhancementFacts.Entry(NOTES.value(), "notes-plan.prompt-fragment.md")),
                        enhancementFile(
                                "documentation",
                                "reasons-write",
                                new StepEnhancementFacts.Entry(REASONS.value(), "reasons-write.prompt-fragment.md"))),
                List.of(
                        companion("software-dev", "notes-plan.prompt-fragment.md", "Notes.\n"),
                        companion("software-dev", "reasons-plan.prompt-fragment.md", "Reasons for the plan.\n"),
                        companion("documentation", "reasons-write.prompt-fragment.md", "Reasons for the docs.\n")));

        PromptFacts facts = PromptFacts.of(List.of(), read);

        assertThat(facts.declared()).isEmpty();
        assertThat(facts.fills())
                .containsExactly(
                        Map.entry(
                                enhancement("reasons-plan"),
                                List.of(
                                        new PromptFacts.Fill(REASONS, "Reasons for the plan.\n"),
                                        new PromptFacts.Fill(NOTES, "Notes.\n"))),
                        Map.entry(
                                enhancement("reasons-write"),
                                List.of(new PromptFacts.Fill(REASONS, "Reasons for the docs.\n"))));
    }

    @Test
    void anEnhancementWithNoEntryFillsNothingButIsListed() {
        PromptFacts facts =
                PromptFacts.of(List.of(), enhancements(List.of(enhancementFile("software-dev", "empty")), List.of()));

        assertThat(facts.fills()).containsExactly(Map.entry(enhancement("empty"), List.of()));
    }

    @Test
    void anEntryTheLoadRefusesFirstGivesNoFill() {
        StepEnhancementFacts read = enhancements(
                List.of(enhancementFile(
                        "software-dev",
                        "faulty",
                        new StepEnhancementFacts.Entry("Not A Slot", "a.prompt-fragment.md"),
                        new StepEnhancementFacts.Entry(REASONS.value(), "missing.prompt-fragment.md"),
                        new StepEnhancementFacts.Entry(REASONS.value(), "unreadable.prompt-fragment.md"),
                        new StepEnhancementFacts.Entry(NOTES.value(), "a.prompt-fragment.md"))),
                List.of(
                        companion("software-dev", "a.prompt-fragment.md", "A.\n"),
                        StepEnhancementFacts.Companion.unreadable(
                                "missions/software-dev/enhancements/unreadable.prompt-fragment.md")));

        PromptFacts facts = PromptFacts.of(List.of(), read);

        assertThat(facts.fills())
                .as("a bad slot id, a missing and an unreadable companion are the load's refusals; the control fills")
                .containsExactly(Map.entry(enhancement("faulty"), List.of(new PromptFacts.Fill(NOTES, "A.\n"))));
    }

    // ---- a removal ---------------------------------------------------------------------------------------------

    @Test
    void aRemovedMissionTypeTakesItsStepsPromptsWithIt() {
        PromptFacts facts = new PromptFacts(
                Map.of(
                        action("software-dev", "plan"), List.of(new PromptFacts.DeclaredSlot(REASONS, false)),
                        action("software-dev-extra", "plan"), List.of(),
                        action("documentation", "write"), List.of()),
                Map.of(enhancement("reasons"), List.of(new PromptFacts.Fill(REASONS, "R.\n"))));

        PromptFacts after = facts.without(Urn.missionType("software-dev"));

        assertThat(after.declared().keySet())
                .as("a type whose id only starts with the removed one keeps its steps")
                .containsExactly(action("documentation", "write"), action("software-dev-extra", "plan"));
        assertThat(after.fills()).isEqualTo(facts.fills());
    }

    @Test
    void aRemovedStepEnhancementTakesItsFillsWithIt() {
        PromptFacts facts = new PromptFacts(
                Map.of(action("software-dev", "plan"), List.of(new PromptFacts.DeclaredSlot(REASONS, false))),
                Map.of(
                        enhancement("reasons"), List.of(new PromptFacts.Fill(REASONS, "R.\n")),
                        enhancement("notes"), List.of(new PromptFacts.Fill(NOTES, "N.\n"))));

        PromptFacts after = facts.without(enhancement("reasons"));

        assertThat(after.fills().keySet()).containsExactly(enhancement("notes"));
        assertThat(after.declared()).isEqualTo(facts.declared());
    }

    @Test
    void aRemovalOfAnyOtherArtifactChangesNothing() {
        PromptFacts facts = new PromptFacts(
                Map.of(action("software-dev", "plan"), List.of(new PromptFacts.DeclaredSlot(REASONS, false))),
                Map.of(enhancement("reasons"), List.of(new PromptFacts.Fill(REASONS, "R.\n"))));

        assertThat(facts.without(Urn.parse("directive:DIRECTIVE_001").orElseThrow()))
                .isSameAs(facts);
    }
}
