package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.PromptFacts;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EffectivePromptQuery} over hand-built merged closures and prompt facts (FR-003, FR-005, NFR-002; ADR-0066
 * decisions 4, 5 and 9): the prompt in force at the merged winner's place of the step, served as stored, its declared
 * slots in order, and per slot the fragments of the winning copies of the enhancements in force, ordered by level, then
 * pack name, then enhancement id.
 */
@DisplayName("The effective prompt of a step (FR-003, FR-005)")
class EffectivePromptQueryTest {

    private static final Urn STEP = Urn.action("software-dev", "plan");
    private static final String REASONS = "spdd:reasons-block";
    private static final String NOTES = "spdd:notes";

    private final Map<Urn, MergedClosure.Winner> winners = new LinkedHashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, List<Relationship>> edges = new HashMap<>();
    private final Map<String, Map<Urn, List<PromptFacts.DeclaredSlot>>> declared = new HashMap<>();
    private final Map<String, Map<Urn, List<PromptFacts.Fill>>> fills = new HashMap<>();
    private final Map<String, Payload> prompts = new HashMap<>();
    private final Set<Urn> inForce = new HashSet<>();

    /** A pack of the closure: its configured id, its descriptor name and its level. */
    private void pack(String id, String name) {
        names.put(id, name);
    }

    private void win(Urn urn, String pack, PackLevel level) {
        winners.put(urn, new MergedClosure.Winner(urn, new PackIdentifier(pack), "id-" + pack, level, levelOpen()));
    }

    private static PerPackPolicy.Level levelOpen() {
        return PerPackPolicy.Level.OPEN;
    }

    /** The pack holds the step's prompt, stored as {@code text}, declaring the slots. */
    private void prompt(String pack, String text, PromptFacts.DeclaredSlot... slots) {
        prompts.put(pack, Payload.of(text.getBytes(StandardCharsets.UTF_8)));
        declared.computeIfAbsent(pack, ignored -> new HashMap<>()).put(STEP, List.of(slots));
    }

    private static PromptFacts.DeclaredSlot slot(String id) {
        return new PromptFacts.DeclaredSlot(new SlotId(id), false);
    }

    private static PromptFacts.DeclaredSlot slotHoldingText(String id) {
        return new PromptFacts.DeclaredSlot(new SlotId(id), true);
    }

    private static Urn enhancement(String id) {
        return Urn.parse("step_enhancement:" + id).orElseThrow();
    }

    /** The pack holds a copy of enhancement {@code id} of {@code target}, filling {@code slot} with {@code text}. */
    private void enhancer(String pack, String id, Urn target, String slot, String text) {
        edges.computeIfAbsent(pack, ignored -> new ArrayList<>())
                .add(new Relationship(
                        enhancement(id), target, RelationType.ENHANCES, Optional.empty(), Optional.empty()));
        fills.computeIfAbsent(pack, ignored -> new HashMap<>())
                .computeIfAbsent(enhancement(id), ignored -> new ArrayList<>())
                .add(new PromptFacts.Fill(new SlotId(slot), text));
    }

    /** An enhancement held, won by {@code pack} and in force. */
    private void enhancementInForce(String pack, PackLevel level, String id, String slot, String text) {
        enhancer(pack, id, STEP, slot, text);
        win(enhancement(id), pack, level);
        inForce.add(enhancement(id));
    }

    private Map<String, PackCompositionFacts> facts() {
        Set<String> packs = new HashSet<>(names.keySet());
        packs.addAll(edges.keySet());
        packs.addAll(declared.keySet());
        Map<String, PackCompositionFacts> facts = new HashMap<>();
        for (String pack : packs) {
            facts.put(
                    pack,
                    new PackCompositionFacts(
                            List.of(),
                            List.of(),
                            List.of(),
                            MutabilityPolicySource.empty(),
                            List.of(),
                            edges.getOrDefault(pack, List.of()),
                            Map.of(),
                            Map.of(),
                            new PromptFacts(
                                    declared.getOrDefault(pack, Map.of()), fills.getOrDefault(pack, Map.of()))));
        }
        return facts;
    }

    private MergedClosure merged() {
        return new MergedClosure(winners, List.of(), Map.of(), Map.of(), new RequiredFloor(Map.of()));
    }

    private EffectivePromptQuery.EffectivePrompt answer() {
        return answer(EffectivePromptQuery.Visits.NONE);
    }

    private EffectivePromptQuery.EffectivePrompt answer(EffectivePromptQuery.Visits visits) {
        return EffectivePromptQuery.answer(
                STEP,
                merged(),
                facts(),
                inForce::contains,
                pack -> names.getOrDefault(pack.value(), pack.value()),
                pack -> Optional.ofNullable(prompts.get(pack.value())),
                visits);
    }

    /** The base pack holds the mission type, the step and its prompt, which declares {@code spdd:reasons-block}. */
    private void aBuiltInStepDeclaringTheReasonsBlock() {
        pack("built-in", "built-in");
        win(Urn.missionType("software-dev"), "built-in", PackLevel.BUILT_IN);
        win(STEP, "built-in", PackLevel.BUILT_IN);
        prompt(
                "built-in",
                "# Plan\n\n<!-- spdd:reasons-block:start -->\n<!-- spdd:reasons-block:end -->\n",
                slot(REASONS));
    }

    @Test
    @DisplayName("fragments are ordered by level, then pack name, then enhancement id, each by code point (R1)")
    void fragmentsAreOrderedByLevelThenPackNameThenEnhancementId() {
        aBuiltInStepDeclaringTheReasonsBlock();
        pack("org-a", "zeta");
        pack("org-z", "alpha");
        pack("proj", "aardvark");
        // The closure first holds them in an order that is none of the sorts.
        enhancementInForce("proj", PackLevel.PROJECT, "b-project", REASONS, "Project.\n");
        enhancementInForce("org-a", PackLevel.ORG, "a-notes", REASONS, "Zeta.\n");
        enhancementInForce("built-in", PackLevel.BUILT_IN, "zz-builtin", REASONS, "Built-in.\n");
        enhancementInForce("org-z", PackLevel.ORG, "m-notes", REASONS, "Alpha.\n");

        EffectivePromptQuery.EffectivePrompt prompt = answer();

        assertThat(prompt.slots()).singleElement().satisfies(slot -> {
            assertThat(slot.slot()).isEqualTo(new SlotId(REASONS));
            assertThat(slot.fragments())
                    .as("built-in, then org by name (alpha before zeta, the reverse of their ids), then project;"
                            + " a sort by id alone, by name alone or by pack id gives another order")
                    .extracting(
                            fragment -> fragment.packId().value(),
                            EffectivePromptQuery.Fragment::packName,
                            EffectivePromptQuery.Fragment::level,
                            EffectivePromptQuery.Fragment::enhancement,
                            EffectivePromptQuery.Fragment::text)
                    .containsExactly(
                            tuple("built-in", "built-in", PackLevel.BUILT_IN, "zz-builtin", "Built-in.\n"),
                            tuple("org-z", "alpha", PackLevel.ORG, "m-notes", "Alpha.\n"),
                            tuple("org-a", "zeta", PackLevel.ORG, "a-notes", "Zeta.\n"),
                            tuple("proj", "aardvark", PackLevel.PROJECT, "b-project", "Project.\n"));
        });
    }

    @Test
    @DisplayName("within one pack, enhancement ids are compared by code point")
    void enhancementIdsOfOnePackAreComparedByCodePoint() {
        aBuiltInStepDeclaringTheReasonsBlock();
        pack("org", "org");
        enhancementInForce("org", PackLevel.ORG, "b", REASONS, "B.\n");
        enhancementInForce("org", PackLevel.ORG, "a-b", REASONS, "AB.\n");
        enhancementInForce("org", PackLevel.ORG, "a", REASONS, "A.\n");

        assertThat(answer().slots().getFirst().fragments())
                .extracting(EffectivePromptQuery.Fragment::enhancement)
                .containsExactly("a", "a-b", "b");
    }

    @Test
    @DisplayName("an enhancement held but not in force contributes nothing (ADR-0066 d4)")
    void anEnhancementHeldButNotInForceIsAbsent() {
        aBuiltInStepDeclaringTheReasonsBlock();
        pack("org", "org");
        enhancementInForce("org", PackLevel.ORG, "active", REASONS, "Active.\n");
        enhancer("org", "held-only", STEP, REASONS, "Held only.\n");
        win(enhancement("held-only"), "org", PackLevel.ORG);

        assertThat(answer().slots().getFirst().fragments())
                .extracting(EffectivePromptQuery.Fragment::enhancement)
                .containsExactly("active");
    }

    @Test
    @DisplayName("an enhancement of another step, or with a second target, contributes nothing here")
    void anEnhancementOfAnotherStepOrWithTwoTargetsIsAbsent() {
        aBuiltInStepDeclaringTheReasonsBlock();
        pack("org", "org");
        enhancementInForce("org", PackLevel.ORG, "here", REASONS, "Here.\n");
        enhancer("org", "elsewhere", Urn.action("software-dev", "tasks"), REASONS, "Elsewhere.\n");
        win(enhancement("elsewhere"), "org", PackLevel.ORG);
        inForce.add(enhancement("elsewhere"));
        enhancer("org", "two-targets", STEP, REASONS, "Two targets.\n");
        edges.get("org")
                .add(new Relationship(
                        enhancement("two-targets"),
                        Urn.action("software-dev", "tasks"),
                        RelationType.ENHANCES,
                        Optional.empty(),
                        Optional.empty()));
        win(enhancement("two-targets"), "org", PackLevel.ORG);
        inForce.add(enhancement("two-targets"));

        assertThat(answer().slots().getFirst().fragments())
                .extracting(EffectivePromptQuery.Fragment::enhancement)
                .containsExactly("here");
    }

    @Test
    @DisplayName("a declared slot no enhancement in force fills is listed, in declaration order, with no fragment")
    void aSlotNoInForceEnhancementFillsIsListedEmpty() {
        pack("built-in", "built-in");
        win(STEP, "built-in", PackLevel.BUILT_IN);
        prompt("built-in", "# Plan\n", slot(NOTES), slot(REASONS));
        enhancementInForce("built-in", PackLevel.BUILT_IN, "fills-reasons", REASONS, "Reasons.\n");

        EffectivePromptQuery.EffectivePrompt prompt = answer();

        assertThat(prompt.slots())
                .extracting(
                        slot -> slot.slot().value(), slot -> slot.fragments().size())
                .containsExactly(tuple(NOTES, 0), tuple(REASONS, 1));
        assertThat(prompt.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("a step whose prompt declares no slot answers its prompt and no slot")
    void aStepWithNoSlotsAnswersItsPromptAndNoSlot() {
        pack("built-in", "built-in");
        win(STEP, "built-in", PackLevel.BUILT_IN);
        prompt("built-in", "# Plan\n\nNo slot here.\n");

        EffectivePromptQuery.EffectivePrompt prompt = answer();

        assertThat(new String(prompt.prompt().bytes(), StandardCharsets.UTF_8)).isEqualTo("# Plan\n\nNo slot here.\n");
        assertThat(prompt.promptPackId()).contains(new PackIdentifier("built-in"));
        assertThat(prompt.slots()).isEmpty();
        assertThat(prompt.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("a step with no prompt companion answers an empty prompt, no slot and no pack")
    void aStepWithNoPromptCompanionAnswersAnEmptyPromptAndNoPack() {
        pack("built-in", "built-in");
        win(STEP, "built-in", PackLevel.BUILT_IN);

        EffectivePromptQuery.EffectivePrompt prompt = answer();

        assertThat(prompt.prompt().size()).isZero();
        assertThat(prompt.promptPackId()).isEmpty();
        assertThat(prompt.slots()).isEmpty();
        assertThat(prompt.diagnostics()).isEmpty();
        assertThat(prompt).isEqualTo(EffectivePromptQuery.EffectivePrompt.NONE);
    }

    @Test
    @DisplayName("the prompt is served as stored, byte for byte (ADR-0066 d9)")
    void thePromptIsServedAsStored() {
        pack("built-in", "built-in");
        win(STEP, "built-in", PackLevel.BUILT_IN);
        String stored = "---\ntitle: Plan\n---\r\n# Plan  \r\n\r\n<!-- spdd:reasons-block:start --><!-- "
                + "spdd:reasons-block:end -->";
        prompt("built-in", stored, slot(REASONS));

        assertThat(answer().prompt()).isEqualTo(Payload.of(stored.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("a slot that holds legacy text is listed with its fragments, unflagged, and a diagnostic (OD-7, OD-8)")
    void aSlotHoldingLegacyTextIsListedWithItsFragmentsAndADiagnostic() {
        pack("built-in", "built-in");
        win(STEP, "built-in", PackLevel.BUILT_IN);
        prompt("built-in", "# Plan\n", slot(NOTES), slotHoldingText(REASONS));
        enhancementInForce("built-in", PackLevel.BUILT_IN, "fills-reasons", REASONS, "Reasons.\n");

        EffectivePromptQuery.EffectivePrompt prompt = answer();

        assertThat(prompt.slots())
                .extracting(
                        slot -> slot.slot().value(), slot -> slot.fragments().size())
                .containsExactly(tuple(NOTES, 0), tuple(REASONS, 1));
        assertThat(prompt.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY);
            assertThat(diagnostic.detail())
                    .contains(REASONS)
                    .contains("missions/software-dev/steps/plan.prompt.md")
                    .doesNotContain(NOTES);
        });
    }

    @Test
    @DisplayName("only the winning copy of an enhancement is served, with the winner's pack")
    void onlyTheWinningCopyOfAnEnhancementIsServed() {
        aBuiltInStepDeclaringTheReasonsBlock();
        pack("org", "org");
        pack("proj", "proj");
        enhancer("org", "notes", STEP, REASONS, "The replaced copy.\n");
        enhancementInForce("proj", PackLevel.PROJECT, "notes", REASONS, "The winning copy.\n");

        assertThat(answer().slots().getFirst().fragments())
                .extracting(
                        fragment -> fragment.packId().value(),
                        EffectivePromptQuery.Fragment::level,
                        EffectivePromptQuery.Fragment::text)
                .containsExactly(tuple("proj", PackLevel.PROJECT, "The winning copy.\n"));
    }

    @Test
    @DisplayName("the prompt is read at the merged winner's place of the step, not the mission type's (A7)")
    void thePromptIsReadAtTheWinnersPlaceOfTheStep() {
        pack("base", "base");
        pack("proj", "proj");
        // proj replaces the mission type and declares no plan step; only base's replaced version keeps it.
        win(Urn.missionType("software-dev"), "proj", PackLevel.PROJECT);
        win(STEP, "base", PackLevel.ORG);
        prompt("base", "# Base plan\n", slot(REASONS));
        prompts.put("proj", Payload.of("# Not this one\n".getBytes(StandardCharsets.UTF_8)));
        declared.computeIfAbsent("proj", ignored -> new HashMap<>()).put(STEP, List.of(slot(NOTES)));

        EffectivePromptQuery.EffectivePrompt prompt = answer();

        assertThat(new String(prompt.prompt().bytes(), StandardCharsets.UTF_8)).isEqualTo("# Base plan\n");
        assertThat(prompt.promptPackId()).contains(new PackIdentifier("base"));
        assertThat(prompt.slots()).extracting(slot -> slot.slot().value()).containsExactly(REASONS);
    }

    // ---- NFR-002: the work grows at most linearly with the step enhancements -----------------------------------

    /** The visits of one answer over {@code size} enhancements in force, spread over {@code steps} steps. */
    private static long visits(int size, int steps) {
        EffectivePromptQueryTest fixture = new EffectivePromptQueryTest();
        fixture.aBuiltInStepDeclaringTheReasonsBlock();
        fixture.pack("org", "org");
        for (int i = 0; i < size; i++) {
            Urn target = i % steps == 0 ? STEP : Urn.action("software-dev", "step-" + (i % steps));
            fixture.enhancer("org", "e-" + i, target, REASONS, "Fragment " + i + ".\n");
            fixture.win(enhancement("e-" + i), "org", PackLevel.ORG);
            fixture.inForce.add(enhancement("e-" + i));
        }
        AtomicLong count = new AtomicLong();
        EffectivePromptQuery.EffectivePrompt prompt = fixture.answer(count::incrementAndGet);
        assertThat(prompt.slots().getFirst().fragments()).hasSize((size + steps - 1) / steps);
        return count.get();
    }

    @Test
    @DisplayName("NFR-002: twice the enhancements on one step cost at most 2.5 times the visits")
    void theCountGrowsLinearlyWithTheEnhancementsOfOneStep() {
        long thousand = visits(1_000, 1);
        long twoThousand = visits(2_000, 1);

        assertThat(thousand).isPositive();
        assertThat((double) twoThousand / thousand)
                .as("visits at 2,000 (%d) over visits at 1,000 (%d)", twoThousand, thousand)
                .isLessThanOrEqualTo(2.5);
    }

    @Test
    @DisplayName("NFR-002: twice the enhancements spread over steps cost at most 2.5 times the visits")
    void theCountGrowsLinearlyWithTheEnhancementsSpreadOverSteps() {
        long thousand = visits(1_000, 50);
        long twoThousand = visits(2_000, 50);

        assertThat(thousand).isPositive();
        assertThat((double) twoThousand / thousand)
                .as("visits at 2,000 (%d) over visits at 1,000 (%d)", twoThousand, thousand)
                .isLessThanOrEqualTo(2.5);
    }
}
