package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Activation preset (#195, ADR-0042)")
class PresetTest {

    private static Map<ArtifactKind, List<PresetItem>> lists(ArtifactKind kind, PresetItem... items) {
        Map<ArtifactKind, List<PresetItem>> out = new LinkedHashMap<>();
        out.put(kind, List.of(items));
        return out;
    }

    private static Preset preset(String id, Map<ArtifactKind, List<PresetItem>> artifacts) {
        return new Preset(id, "The house rules.", artifacts);
    }

    private static Map<ArtifactKind, List<PresetItem>> withMissionTypes(Preset preset, PresetItem... missionTypes) {
        Map<ArtifactKind, List<PresetItem>> out = new LinkedHashMap<>(preset.artifacts());
        out.put(ArtifactKind.MISSION_TYPE, List.of(missionTypes));
        return out;
    }

    private static Preset houseRules() {
        Map<ArtifactKind, List<PresetItem>> artifacts = new LinkedHashMap<>();
        artifacts.put(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("GUARD"), PresetItem.of("STYLE")));
        artifacts.put(ArtifactKind.TACTIC, List.of(PresetItem.of("tdd")));
        artifacts.put(ArtifactKind.SKILL, List.of(PresetItem.of("pr-review")));
        artifacts.put(
                ArtifactKind.MISSION_TYPE, List.of(PresetItem.of("research"), PresetItem.mustHave("software-dev")));
        return new Preset("house-rules", "The house rules.", artifacts);
    }

    @Test
    @DisplayName("the must-have items are the required set, in authored order, and make the preset governing")
    void mustHaveItemsAreTheRequiredSet() {
        Preset preset = houseRules();

        assertThat(preset.required())
                .containsExactly(
                        Map.entry(ArtifactKind.DIRECTIVE, List.of("GUARD")),
                        Map.entry(ArtifactKind.MISSION_TYPE, List.of("software-dev")));
        assertThat(preset.governing()).isTrue();
    }

    @Test
    @DisplayName("a preset with no must-have item is a convenience preset with an empty required set")
    void withoutMarksItIsAConveniencePreset() {
        Preset preset = preset("defaults", lists(ArtifactKind.DIRECTIVE, PresetItem.of("A"), PresetItem.of("B")));

        assertThat(preset.required()).isEmpty();
        assertThat(preset.governing()).isFalse();
    }

    @Test
    @DisplayName("a must-have mission type alone makes a preset governing")
    void aMustHaveMissionTypeGoverns() {
        Preset preset = preset("p", lists(ArtifactKind.MISSION_TYPE, PresetItem.mustHave("plan")));

        assertThat(preset.required()).containsExactly(Map.entry(ArtifactKind.MISSION_TYPE, List.of("plan")));
        assertThat(preset.governing()).isTrue();
    }

    @Test
    @DisplayName("must-have items of every kind feed the required floor, a mission type and a skill included (T028)")
    void mustHaveItemsOfEveryKindFeedTheFloor() {
        Map<ArtifactKind, List<PresetItem>> artifacts =
                new LinkedHashMap<>(houseRules().artifacts());
        artifacts.put(ArtifactKind.SKILL, List.of(PresetItem.mustHave("pr-review")));
        artifacts.put(ArtifactKind.GLOSSARY_PACK, List.of(PresetItem.of("core-terms")));

        RequiredFloor floor = RequiredFloor.of(List.of(Map.entry("org", List.of(preset("house-rules", artifacts)))));

        assertThat(floor.requiredBy())
                .containsOnlyKeys(
                        new Urn(NodeKind.DIRECTIVE, "GUARD"),
                        new Urn(NodeKind.SKILL, "pr-review"),
                        Urn.missionType("software-dev"));
        assertThat(floor.requiringPacks(Urn.missionType("software-dev"))).containsExactly("org");
        assertThat(floor.isRequired(Urn.missionType("research"))).isFalse();
        assertThat(floor.isRequired(new Urn(NodeKind.GLOSSARY_PACK, "core-terms")))
                .isFalse();
    }

    @Test
    @DisplayName("items keep their authored order and the preset is equal by value")
    void orderIsKeptAndEqualityIsByValue() {
        Preset preset = houseRules();

        assertThat(preset.artifacts().get(ArtifactKind.DIRECTIVE))
                .extracting(PresetItem::id)
                .containsExactly("GUARD", "STYLE");
        assertThat(preset).isEqualTo(houseRules());
        assertThat(preset).isNotEqualTo(preset("house-rules", withMissionTypes(preset, PresetItem.of("research"))));
    }

    @Test
    @DisplayName("every collection is copied and read-only, so neither the caller nor a reader can change the preset")
    void collectionsAreCopiedAndReadOnly() {
        List<PresetItem> items = new ArrayList<>(List.of(PresetItem.of("A")));
        Map<ArtifactKind, List<PresetItem>> artifacts = new LinkedHashMap<>();
        artifacts.put(ArtifactKind.DIRECTIVE, items);
        List<PresetItem> missionTypes = new ArrayList<>(List.of(PresetItem.mustHave("plan")));
        artifacts.put(ArtifactKind.MISSION_TYPE, missionTypes);
        Preset preset = new Preset("p", "d", artifacts);

        artifacts.put(ArtifactKind.TACTIC, List.of(PresetItem.of("t")));
        items.add(PresetItem.of("B"));
        missionTypes.add(PresetItem.of("research"));

        assertThat(preset.artifacts()).containsOnlyKeys(ArtifactKind.DIRECTIVE, ArtifactKind.MISSION_TYPE);
        assertThat(preset.artifacts().get(ArtifactKind.DIRECTIVE)).containsExactly(PresetItem.of("A"));
        assertThat(preset.artifacts().get(ArtifactKind.MISSION_TYPE)).containsExactly(PresetItem.mustHave("plan"));
        assertThatThrownBy(() -> preset.artifacts().put(ArtifactKind.TACTIC, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> preset.required().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(
                        () -> preset.required().get(ArtifactKind.MISSION_TYPE).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"house.rules", "House", "1st", "house--rules", "house-", "", "-house"})
    @DisplayName("an id outside the grammar breaches, naming it, where a valid id in the same shape does not")
    void idOutsideTheGrammarBreaches(String id) {
        assertThatCode(() -> preset("house-rules", Map.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> preset(id, Map.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("Preset '" + id + "': the id must be lowercase words joined by single hyphens");
    }

    @Test
    @DisplayName("an id of 64 characters is the longest")
    void sixtyFourCharactersIsTheLongestId() {
        assertThatCode(() -> preset("a".repeat(64), Map.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> preset("a".repeat(65), Map.of())).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a blank description breaches, naming the preset; a written one does not")
    void blankDescriptionBreaches() {
        assertThatCode(() -> new Preset("p", "d", Map.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> new Preset("p", " ", Map.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("Preset 'p': the description must not be blank.");
    }

    @ParameterizedTest
    @EnumSource(
            value = ArtifactKind.class,
            names = {"ASSET", "TEMPLATE", "GUARD", "LIFECYCLE"})
    @DisplayName("a list of a kind that is never activated breaches, naming the kind")
    void neverActivatedKindsBreach(ArtifactKind kind) {
        assertThatCode(() -> preset("p", lists(ArtifactKind.DIRECTIVE, PresetItem.of("x"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> preset("p", lists(kind, PresetItem.of("x"))))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining(
                        "Preset 'p': " + kind.plural() + " are never activated, so a preset carries no list of them.");
    }

    @Test
    @DisplayName("a preset may name any activatable kind, skills, glossary packs and mission types included (FR-041)")
    void everyActivatableKindIsAccepted() {
        assertThat(Preset.ACTIVATION_KINDS)
                .isEqualTo(EnumSet.complementOf(EnumSet.of(
                        ArtifactKind.ASSET, ArtifactKind.TEMPLATE, ArtifactKind.GUARD, ArtifactKind.LIFECYCLE)))
                .contains(ArtifactKind.SKILL, ArtifactKind.GLOSSARY_PACK, ArtifactKind.MISSION_TYPE)
                .allMatch(ArtifactKind::activatable);
        for (ArtifactKind kind : Preset.ACTIVATION_KINDS) {
            assertThatCode(() -> preset("p", lists(kind, PresetItem.of("x")))).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("an empty activation list breaches, naming the kind; a one-item list does not")
    void emptyListBreaches() {
        assertThatCode(() -> preset("p", lists(ArtifactKind.TACTIC, PresetItem.of("t"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> preset("p", lists(ArtifactKind.TACTIC)))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining(
                        "Preset 'p': the activation list of tactics must not be empty; leave the kind out instead.");
    }

    @Test
    @DisplayName("an id listed twice in one list breaches, naming the list and the id; distinct ids do not")
    void anIdTwiceInOneListBreaches() {
        assertThatCode(() -> preset("p", lists(ArtifactKind.DIRECTIVE, PresetItem.of("A"), PresetItem.of("B"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () -> preset("p", lists(ArtifactKind.DIRECTIVE, PresetItem.of("A"), PresetItem.mustHave("A"))))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining(
                        "Preset 'p': the activation list of directives must name each id once; 'A' is listed twice.");
        assertThatCode(() ->
                        preset("p", lists(ArtifactKind.MISSION_TYPE, PresetItem.of("plan"), PresetItem.of("research"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() ->
                        preset("p", lists(ArtifactKind.MISSION_TYPE, PresetItem.of("plan"), PresetItem.of("plan"))))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining(
                        "Preset 'p': the activation list of mission_types must name each id once; 'plan' is listed"
                                + " twice.");
    }

    @Test
    @DisplayName("one id may appear in two different lists")
    void anIdMayRepeatAcrossLists() {
        Map<ArtifactKind, List<PresetItem>> artifacts = new LinkedHashMap<>();
        artifacts.put(ArtifactKind.DIRECTIVE, List.of(PresetItem.of("X")));
        artifacts.put(ArtifactKind.TACTIC, List.of(PresetItem.of("X")));

        assertThatCode(() -> preset("p", artifacts)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a blank item id breaches; a written one does not")
    void blankItemIdBreaches() {
        assertThatCode(() -> PresetItem.of("A")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PresetItem.of(" "))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A preset item's id must not be blank.");
        assertThat(PresetItem.mustHave("A")).isEqualTo(new PresetItem("A", true));
    }
}
