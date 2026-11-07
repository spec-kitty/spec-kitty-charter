package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The closure-scoped required floor (FR-023, ADR-0031, planning decision DM-01M6PCWSA65497T9A7J80K6V56): the must-have
 * items of every governing preset of the closure, each with the packs that require it, in closure order.
 */
class RequiredFloorTest {

    private static final Urn D1 = new Urn(NodeKind.DIRECTIVE, "D1");
    private static final Urn D2 = new Urn(NodeKind.DIRECTIVE, "D2");

    private static Preset governing(String id, String... mustHaveDirectives) {
        List<PresetItem> items = new ArrayList<>();
        for (String directive : mustHaveDirectives) {
            items.add(PresetItem.mustHave(directive));
        }
        items.add(PresetItem.of("D2"));
        return new Preset(id, "governs", Map.of(ArtifactKind.DIRECTIVE, items));
    }

    private static Preset convenience(String id) {
        return new Preset(
                id,
                "conveniences",
                Map.of(
                        ArtifactKind.DIRECTIVE,
                        List.of(PresetItem.of("D1"), PresetItem.of("D3")),
                        ArtifactKind.MISSION_TYPE,
                        List.of(PresetItem.of("research"))));
    }

    @Test
    void aGoverningPresetsMustHaveItemIsRequiredByItsPackAndAConvenienceItemIsNot() {
        RequiredFloor floor = RequiredFloor.of(List.of(
                Map.entry("builtin", List.of(convenience("c"))), Map.entry("org", List.of(governing("p", "D1")))));

        assertThat(floor.requiredBy()).containsOnlyKeys(D1);
        assertThat(floor.requiringPacks(D1)).containsExactly("org");
        assertThat(floor.isRequired(D1)).isTrue();
        assertThat(floor.isRequired(D2))
                .as("a convenience item of a governing preset")
                .isFalse();
        assertThat(floor.requiringPacks(D2)).isEmpty();
        assertThat(floor.isRequired(new Urn(NodeKind.DIRECTIVE, "D3")))
                .as("an item of a convenience preset")
                .isFalse();
    }

    @Test
    void twoGoverningPresetsInTwoPacksRequiringOneItemListBothPacksInClosureOrder() {
        RequiredFloor floor = RequiredFloor.of(List.of(
                Map.entry("team", List.of(governing("t", "D1"))),
                Map.entry("org", List.of(governing("o", "D1"), governing("o2", "D1")))));

        assertThat(floor.requiringPacks(D1))
                .as("each pack once, in closure order")
                .containsExactly("team", "org");
    }

    @Test
    void aMustHaveMissionTypeIsRequiredAsAMissionTypeUrn() {
        Preset missions = new Preset(
                "missions",
                "mission types",
                Map.of(
                        ArtifactKind.MISSION_TYPE,
                        List.of(PresetItem.mustHave("software-dev"), PresetItem.of("research"))));

        RequiredFloor floor = RequiredFloor.of(List.of(Map.entry("org", List.of(missions))));

        assertThat(floor.requiringPacks(Urn.missionType("software-dev"))).containsExactly("org");
        assertThat(floor.isRequired(Urn.missionType("research"))).isFalse();
    }

    @Test
    void anEmptyClosureHasAnEmptyFloor() {
        assertThat(RequiredFloor.of(List.of()).requiredBy()).isEmpty();
        assertThat(RequiredFloor.of(List.of(Map.entry("org", List.of()))).requiredBy())
                .isEmpty();
    }

    @Test
    void theFloorIsReadOnly() {
        RequiredFloor floor = RequiredFloor.of(List.of(Map.entry("org", List.of(governing("p", "D1")))));

        assertThatThrownBy(() -> floor.requiredBy().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> floor.requiringPacks(D1).add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
