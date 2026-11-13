package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * C38, the slot declared (FR-001, FR-006): every slot an <strong>in-force</strong> step enhancement names is declared
 * by the prompt in force of its step, the prompt at the merged winner's place of {@code action:M/s} (research R8). Each
 * refusal has a failing fixture and a twin that differs only in the faulty part.
 */
class SlotComposeTest {

    private static final String REASONS = "spdd:reasons-block";
    private static final String NOTES = "risk:notes";

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    /** One pack of a fixture: what it holds, the edges it writes and its prompt facts. */
    private record Held(PackUnderResolution pack, List<Relationship> edges, PromptFacts prompts) {}

    /** A closure under construction, packs in closure order. */
    private static final class Fixture {
        private final List<Held> packs = new ArrayList<>();

        Fixture with(Held held) {
            packs.add(held);
            return this;
        }

        MergedClosure merged() {
            return LayerMerge.merge(packs.stream().map(Held::pack).toList(), facts(), new PerPackPolicy(Map.of()));
        }

        Map<String, PackCompositionFacts> facts() {
            Map<String, PackCompositionFacts> facts = new LinkedHashMap<>();
            for (Held held : packs) {
                facts.put(
                        held.pack().configured().id().value(),
                        new PackCompositionFacts(
                                List.of(),
                                held.pack().definedArtifacts(),
                                List.of(),
                                MutabilityPolicySource.empty(),
                                List.of(),
                                held.edges(),
                                Map.of(),
                                Map.of(),
                                held.prompts()));
            }
            return facts;
        }

        List<SlotCompose.SlotViolation> missing(InForceStates states) {
            return SlotCompose.missing(merged(), facts(), states);
        }
    }

    private static PackUnderResolution pack(String name, PackLevel level, String... held) {
        PackDescriptor descriptor = new PackDescriptor(
                "id-" + name,
                "1.0.0",
                Optional.empty(),
                name,
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.of());
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), List.of(held));
    }

    private static Relationship enhances(String from, String to) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return new Relationship(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                RelationType.ENHANCES,
                Optional.empty(),
                Optional.empty());
    }

    private static List<PromptFacts.DeclaredSlot> slots(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new PromptFacts.DeclaredSlot(new SlotId(id), false))
                .toList();
    }

    private static List<PromptFacts.Fill> fills(String... slots) {
        return java.util.Arrays.stream(slots)
                .map(slot -> new PromptFacts.Fill(new SlotId(slot), "Text for " + slot + ".\n"))
                .toList();
    }

    /** The org pack base: mission type M with step s, whose prompt declares the slots, and the enhancement x. */
    private static Held base(List<PromptFacts.DeclaredSlot> declared, List<PromptFacts.Fill> xFills) {
        return new Held(
                pack("base", PackLevel.ORG, "mission_type:M", "action:M/s", "step_enhancement:x"),
                List.of(enhances("step_enhancement:x", "action:M/s")),
                new PromptFacts(Map.of(u("action:M/s"), declared), Map.of(u("step_enhancement:x"), xFills)));
    }

    /** The project pack proj, which replaces M and its step s with a prompt that declares the slots. */
    private static Held override(List<PromptFacts.DeclaredSlot> declared) {
        return new Held(
                pack("proj", PackLevel.PROJECT, "mission_type:M", "action:M/s"),
                List.of(),
                new PromptFacts(Map.of(u("action:M/s"), declared), Map.of()));
    }

    private static InForceStates active(String... urns) {
        Map<Urn, InForceStates.ArtifactState> states = new LinkedHashMap<>();
        for (String urn : urns) {
            states.put(u(urn), state(u(urn), InForceStates.State.ACTIVE, InForceStates.Reason.SELECTED));
        }
        return states(states);
    }

    private static InForceStates.ArtifactState state(Urn urn, InForceStates.State state, InForceStates.Reason reason) {
        return new InForceStates.ArtifactState(urn, state, reason, false, List.of(), List.of(), Optional.empty());
    }

    private static InForceStates states(Map<Urn, InForceStates.ArtifactState> states) {
        return new InForceStates(states, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    @Test
    void aSlotTheBasePromptDeclaresIsComposed() {
        Fixture fixture = new Fixture().with(base(slots(REASONS), fills(REASONS)));

        assertThat(fixture.missing(active("mission_type:M", "step_enhancement:x")))
                .isEmpty();
    }

    @Test
    void aSlotAnOverrideDropsIsMissingNamingTheEnhancementItsPackTheSlotAndThePromptsPack() {
        Fixture fixture =
                new Fixture().with(base(slots(REASONS), fills(REASONS))).with(override(slots()));

        assertThat(fixture.missing(active("mission_type:M", "step_enhancement:x")))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.enhancement()).isEqualTo(u("step_enhancement:x"));
                    assertThat(found.enhancingPack()).isEqualTo(new PackIdentifier("base"));
                    assertThat(found.step()).isEqualTo(u("action:M/s"));
                    assertThat(found.slot()).isEqualTo(new SlotId(REASONS));
                    assertThat(found.promptPack()).isEqualTo(new PackIdentifier("proj"));
                    assertThat(found.promptHeld()).isTrue();
                    assertThat(found.packs()).containsExactly(new PackIdentifier("base"), new PackIdentifier("proj"));
                    assertThat(found.detail(PackIdentifier::value))
                            .contains("step_enhancement:x")
                            .contains("`base`")
                            .contains(REASONS)
                            .contains("action:M/s")
                            .contains("`proj`");
                });
    }

    @Test
    void theTwinAnOverrideThatDeclaresTheSlotComposes() {
        Fixture fixture =
                new Fixture().with(base(slots(REASONS), fills(REASONS))).with(override(slots(REASONS)));

        assertThat(fixture.missing(active("mission_type:M", "step_enhancement:x")))
                .isEmpty();
    }

    @Test
    void aStepWithNoPromptInForceDeclaresNoSlot() {
        Held base = new Held(
                pack("base", PackLevel.ORG, "mission_type:M", "action:M/s", "step_enhancement:x"),
                List.of(enhances("step_enhancement:x", "action:M/s")),
                new PromptFacts(Map.of(), Map.of(u("step_enhancement:x"), fills(REASONS))));

        assertThat(new Fixture().with(base).missing(active("mission_type:M", "step_enhancement:x")))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.promptHeld()).isFalse();
                    assertThat(found.promptPack()).isEqualTo(new PackIdentifier("base"));
                    assertThat(found.detail(PackIdentifier::value)).contains("no prompt");
                });
    }

    @Test
    void ofTwoEnhancementsOnlyTheOneWhoseSlotIsMissingRefuses() {
        Held base = new Held(
                pack("base", PackLevel.ORG, "mission_type:M", "action:M/s", "step_enhancement:x", "step_enhancement:y"),
                List.of(enhances("step_enhancement:x", "action:M/s"), enhances("step_enhancement:y", "action:M/s")),
                new PromptFacts(
                        Map.of(u("action:M/s"), slots(REASONS)),
                        Map.of(u("step_enhancement:x"), fills(REASONS), u("step_enhancement:y"), fills(NOTES))));

        assertThat(new Fixture()
                        .with(base)
                        .missing(active("mission_type:M", "step_enhancement:x", "step_enhancement:y")))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.enhancement()).isEqualTo(u("step_enhancement:y"));
                    assertThat(found.slot()).isEqualTo(new SlotId(NOTES));
                });
    }

    @Test
    void anEnhancementThatIsNotInForceNeverRefuses() {
        Fixture fixture =
                new Fixture().with(base(slots(REASONS), fills(REASONS))).with(override(slots()));
        Map<Urn, InForceStates.ArtifactState> held = new LinkedHashMap<>();
        held.put(
                u("mission_type:M"),
                state(u("mission_type:M"), InForceStates.State.ACTIVE, InForceStates.Reason.SELECTED));
        held.put(
                u("step_enhancement:x"),
                state(u("step_enhancement:x"), InForceStates.State.INACTIVE, InForceStates.Reason.NOT_SELECTED));

        assertThat(fixture.missing(states(held))).isEmpty();
        assertThat(fixture.missing(active("mission_type:M"))).isEmpty();
    }

    @Test
    void onlyTheWinningCopyOfASameIdEnhancementIsJudged() {
        // base's copy of x fills a slot no prompt declares; proj's copy, which wins across levels, fills the declared
        // one. The losing copy refuses nothing.
        Held base = base(slots(REASONS), fills(NOTES));
        Held winner = new Held(
                pack("proj", PackLevel.PROJECT, "step_enhancement:x"),
                List.of(enhances("step_enhancement:x", "base::action:M/s")),
                new PromptFacts(Map.of(), Map.of(u("step_enhancement:x"), fills(REASONS))));

        assertThat(new Fixture().with(base).with(winner).missing(active("mission_type:M", "step_enhancement:x")))
                .isEmpty();
    }

    @Test
    void theTwinTheWinningCopyThatNamesAnUndeclaredSlotRefuses() {
        Held base = base(slots(REASONS), fills(REASONS));
        Held winner = new Held(
                pack("proj", PackLevel.PROJECT, "step_enhancement:x"),
                List.of(enhances("step_enhancement:x", "base::action:M/s")),
                new PromptFacts(Map.of(), Map.of(u("step_enhancement:x"), fills(NOTES))));

        assertThat(new Fixture().with(base).with(winner).missing(active("mission_type:M", "step_enhancement:x")))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.enhancingPack()).isEqualTo(new PackIdentifier("proj"));
                    assertThat(found.slot()).isEqualTo(new SlotId(NOTES));
                    assertThat(found.promptPack()).isEqualTo(new PackIdentifier("base"));
                });
    }

    @Test
    void thePromptInForceIsAtTheWinnersPlaceOfTheStepNotTheWinningMissionTypes() {
        // proj replaces M but does not declare s: the step's place is base's version, which keeps it (R8, A7), so the
        // prompt in force is base's, which declares the slot.
        Held replacingType = new Held(
                pack("proj", PackLevel.PROJECT, "mission_type:M"), List.of(), new PromptFacts(Map.of(), Map.of()));

        Fixture fixture =
                new Fixture().with(base(slots(REASONS), fills(REASONS))).with(replacingType);

        assertThat(fixture.missing(active("mission_type:M", "step_enhancement:x")))
                .isEmpty();
    }

    @Test
    void eachEnhancementIsVisitedOnceAndEachMissingSlotIsNamedOnce() {
        Held base = new Held(
                pack("base", PackLevel.ORG, "mission_type:M", "action:M/s", "step_enhancement:x"),
                List.of(enhances("step_enhancement:x", "action:M/s")),
                new PromptFacts(
                        Map.of(u("action:M/s"), slots()), Map.of(u("step_enhancement:x"), fills(REASONS, NOTES))));

        assertThat(new Fixture().with(base).missing(active("mission_type:M", "step_enhancement:x")))
                .extracting(found -> found.slot().value())
                .containsExactly(REASONS, NOTES);
    }
}
