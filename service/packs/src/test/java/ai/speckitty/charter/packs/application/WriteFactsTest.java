package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import ai.speckitty.charter.packs.domain.PromptFacts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The facts a pack will have once a write is stored, laid over the facts it has now (IC-15). */
class WriteFactsTest {

    private static final SlotId REASONS = new SlotId("spdd:reasons-block");

    private static Urn directive(String id) {
        return Urn.parse("directive:" + id).orElseThrow();
    }

    private static Relationship edge(String from, String to, RelationType type, String toPack) {
        return new Relationship(
                directive(from),
                Optional.empty(),
                directive(to),
                Optional.ofNullable(toPack),
                type,
                Optional.empty(),
                Optional.empty());
    }

    private static PackCompositionFacts facts(List<Relationship> edges, List<Preset> presets) {
        return new PackCompositionFacts(
                List.of("base::directive:OLD"),
                List.of("directive:A", "directive:B"),
                List.of("directive:A", "base::directive:OLD"),
                MutabilityPolicySource.empty(),
                presets,
                edges);
    }

    @Test
    void anArtifactIsAddedOnceAndAReplaceChangesNothing() {
        PackCompositionFacts facts = facts(List.of(), List.of());

        PackCompositionFacts added = WriteFacts.withArtifact(facts, directive("C"));

        assertThat(added.defined()).containsExactly("directive:A", "directive:B", "directive:C");
        assertThat(WriteFacts.withArtifact(added, directive("C")))
                .as("a replace")
                .isSameAs(added);
        assertThat(WriteFacts.withArtifact(facts, directive("A")))
                .as("the control: an id the pack holds")
                .isSameAs(facts);
    }

    @Test
    void aRegisteredEdgeIsAddedWithItsOverridesTargetAndItsReferences() {
        PackCompositionFacts facts = facts(List.of(), List.of());
        Relationship overrides = edge("A", "OTHER", RelationType.OVERRIDES, "base");

        PackCompositionFacts after = WriteFacts.withRegisteredEdges(facts, List.of(), List.of(overrides));

        assertThat(after.edges()).containsExactly(overrides);
        assertThat(after.changed()).containsExactly("base::directive:OLD", "base::directive:OTHER");
        assertThat(after.references()).contains("directive:A", "base::directive:OTHER");
    }

    @Test
    void aReplacedEdgeTakesItsOldTargetAndReferencesWithIt() {
        Relationship old = edge("A", "OTHER", RelationType.OVERRIDES, "base");
        PackCompositionFacts facts =
                WriteFacts.withRegisteredEdges(facts(List.of(), List.of()), List.of(), List.of(old));
        Relationship replacing = edge("A", "OTHER", RelationType.SUGGESTS, "base");

        PackCompositionFacts after = WriteFacts.withRegisteredEdges(facts, List.of(old), List.of(replacing));

        assertThat(after.edges()).containsExactly(replacing);
        assertThat(after.changed())
                .as("nothing overrides base::directive:OTHER any longer")
                .containsExactly("base::directive:OLD");
        assertThat(after.references()).contains("base::directive:OTHER");
    }

    @Test
    void aTargetAnotherEdgeStillOverridesStaysChanged() {
        Relationship first = edge("A", "OTHER", RelationType.OVERRIDES, "base");
        Relationship second = edge("B", "OTHER", RelationType.OVERRIDES, "base");
        PackCompositionFacts facts =
                WriteFacts.withRegisteredEdges(facts(List.of(), List.of()), List.of(), List.of(first, second));

        PackCompositionFacts after = WriteFacts.withRegisteredEdges(facts, List.of(first, second), List.of(second));

        assertThat(after.edges()).containsExactly(second);
        assertThat(after.changed()).contains("base::directive:OTHER");
    }

    @Test
    void anArtifactGoesWithItsOutgoingRegisteredEdgesAndItsPresetItems() {
        Relationship outgoing = edge("A", "B", RelationType.SUGGESTS, null);
        Preset team = new Preset(
                "team",
                "The team.",
                Map.of(
                        ArtifactKind.DIRECTIVE,
                        List.of(PresetItem.of("A"), PresetItem.of("B")),
                        ArtifactKind.TACTIC,
                        List.of(PresetItem.of("A"))));
        Preset onlyA = new Preset("only-a", "Only A.", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.of("A"))));
        PackCompositionFacts facts = facts(List.of(outgoing), List.of(team, onlyA));

        PackCompositionFacts after = WriteFacts.withoutArtifact(facts, directive("A"), List.of(outgoing));

        assertThat(after.defined()).containsExactly("directive:B");
        assertThat(after.edges()).isEmpty();
        assertThat(after.presets())
                .as("a directive item goes, the tactic of the same id stays, and a key left empty is left out")
                .containsExactly(
                        new Preset(
                                "team",
                                "The team.",
                                Map.of(
                                        ArtifactKind.DIRECTIVE,
                                        List.of(PresetItem.of("B")),
                                        ArtifactKind.TACTIC,
                                        List.of(PresetItem.of("A")))),
                        new Preset("only-a", "Only A.", Map.of()));
    }

    @Test
    void aPresetNoItemNamesIsLeftAsItIs() {
        Preset team = new Preset("team", "The team.", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.of("B"))));

        assertThat(WriteFacts.withoutItems(team, directive("A"))).isSameAs(team);
    }

    // ---- the prompt facts (R9) -----------------------------------------------------------------------------------

    private static Urn enhancement(String id) {
        return Urn.parse("step_enhancement:" + id).orElseThrow();
    }

    private static PackCompositionFacts withPrompts(List<Relationship> edges) {
        PackCompositionFacts facts = facts(edges, List.of());
        return new PackCompositionFacts(
                facts.changed(),
                facts.defined(),
                facts.references(),
                facts.policy(),
                facts.presets(),
                facts.edges(),
                facts.languages(),
                facts.bindings(),
                new PromptFacts(
                        Map.of(
                                Urn.action("software-dev", "plan"),
                                List.of(new PromptFacts.DeclaredSlot(REASONS, false)),
                                Urn.action("documentation", "write"),
                                List.of()),
                        Map.of(enhancement("reasons"), List.of(new PromptFacts.Fill(REASONS, "Reasons.\n")))));
    }

    @Test
    void anArtifactWriteKeepsThePromptFacts() {
        PackCompositionFacts facts = withPrompts(List.of());

        assertThat(WriteFacts.withArtifact(facts, directive("C")).prompts())
                .as("a per-artifact write writes no step prompt and no enhancement")
                .isEqualTo(facts.prompts());
    }

    @Test
    void aRegisteredEdgeWriteKeepsThePromptFacts() {
        PackCompositionFacts facts = withPrompts(List.of());
        Relationship overrides = edge("A", "OTHER", RelationType.OVERRIDES, "base");

        PackCompositionFacts added = WriteFacts.withRegisteredEdges(facts, List.of(), List.of(overrides));

        assertThat(added.prompts()).isEqualTo(facts.prompts());
        assertThat(WriteFacts.withRegisteredEdges(added, List.of(overrides), List.of())
                        .prompts())
                .isEqualTo(facts.prompts());
    }

    @Test
    void aRemovalOfAnotherKindKeepsThePromptFacts() {
        Relationship outgoing = edge("A", "B", RelationType.SUGGESTS, null);
        PackCompositionFacts facts = withPrompts(List.of(outgoing));

        assertThat(WriteFacts.withoutArtifact(facts, directive("A"), List.of(outgoing))
                        .prompts())
                .isEqualTo(facts.prompts());
    }

    @Test
    void aRemovedMissionTypeTakesItsStepPromptsAndARemovedEnhancementItsFills() {
        PackCompositionFacts facts = withPrompts(List.of());

        PackCompositionFacts typeless = WriteFacts.withoutArtifact(facts, Urn.missionType("software-dev"), List.of());
        PackCompositionFacts unenhanced = WriteFacts.withoutArtifact(facts, enhancement("reasons"), List.of());

        assertThat(typeless.prompts().declared().keySet()).containsExactly(Urn.action("documentation", "write"));
        assertThat(typeless.prompts().fills()).isEqualTo(facts.prompts().fills());
        assertThat(unenhanced.prompts().fills()).isEmpty();
        assertThat(unenhanced.prompts().declared()).isEqualTo(facts.prompts().declared());
    }
}
