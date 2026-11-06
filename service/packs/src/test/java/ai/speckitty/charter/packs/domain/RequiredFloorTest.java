package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The closure-scoped required floor (FR-023, ADR-0031, planning decision DM-01M6PCWSA65497T9A7J80K6V56): the must-have
 * items of every governing preset of the closure, each with the packs that require it, in closure order.
 */
class RequiredFloorTest {

    private static final Urn D1 = new Urn(NodeKind.DIRECTIVE, "D1");
    private static final Urn D2 = new Urn(NodeKind.DIRECTIVE, "D2");

    /** Every item these tests name is held by some pack of the closure, unless a test says otherwise. */
    private static final Set<Urn> HELD = Set.of(
            D1, D2, new Urn(NodeKind.DIRECTIVE, "D3"), Urn.missionType("software-dev"), Urn.missionType("research"));

    private static RequiredFloor floorOf(List<Map.Entry<String, List<Preset>>> presetsByPackInOrder) {
        return RequiredFloor.of(presetsByPackInOrder, HELD);
    }

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
        RequiredFloor floor = floorOf(List.of(
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
        RequiredFloor floor = floorOf(List.of(
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

        RequiredFloor floor = floorOf(List.of(Map.entry("org", List.of(missions))));

        assertThat(floor.requiringPacks(Urn.missionType("software-dev"))).containsExactly("org");
        assertThat(floor.isRequired(Urn.missionType("research"))).isFalse();
    }

    @Test
    void anEmptyClosureHasAnEmptyFloor() {
        assertThat(floorOf(List.of()).requiredBy()).isEmpty();
        assertThat(floorOf(List.of(Map.entry("org", List.of()))).requiredBy()).isEmpty();
    }

    // A10 (FR-028, DM-01M6RA8MG55V8C2V9PXWDTRNQS, rule C31): Req(C) = Req0(C) ∩ H(C); Unheld(C) is reported.
    @Test
    void a10AMustHaveItemNoPackOfTheClosureHoldsIsDroppedFromTheFloorAndReportedWithItsRequiringPacks() {
        Urn unheld = new Urn(NodeKind.DIRECTIVE, "Z");
        Preset requiresZ = new Preset(
                "z",
                "governs",
                List.of(),
                Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("Z"), PresetItem.mustHave("D1"))),
                List.of(PresetItem.mustHave("unheld-mission")));

        RequiredFloor floor = RequiredFloor.of(
                List.of(Map.entry("org", List.of(requiresZ)), Map.entry("team", List.of(governing("t", "Z")))),
                Set.of(D1));

        assertThat(floor.isRequired(unheld))
                .as("an unheld must-have item is not on the floor")
                .isFalse();
        assertThat(floor.isRequired(Urn.missionType("unheld-mission"))).isFalse();
        assertThat(floor.requiredBy()).containsOnlyKeys(D1);
        assertThat(floor.unheld())
                .as("each unheld item with its requiring packs, in closure order")
                .containsOnlyKeys(unheld, Urn.missionType("unheld-mission"))
                .containsEntry(unheld, List.of("org", "team"))
                .containsEntry(Urn.missionType("unheld-mission"), List.of("org"));
    }

    @Test
    void aFloorWhoseItemsAreAllHeldReportsNothingUnheld() {
        RequiredFloor floor = floorOf(List.of(Map.entry("org", List.of(governing("p", "D1")))));

        assertThat(floor.unheld()).isEmpty();
        assertThat(new RequiredFloor(Map.of(D1, List.of("org"))).unheld())
                .as("a floor built from its items alone reports nothing unheld")
                .isEmpty();
    }

    @Test
    void theFloorIsReadOnly() {
        RequiredFloor floor = floorOf(List.of(Map.entry("org", List.of(governing("p", "D1")))));

        assertThatThrownBy(() -> floor.requiredBy().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> floor.requiringPacks(D1).add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> floor.unheld().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
