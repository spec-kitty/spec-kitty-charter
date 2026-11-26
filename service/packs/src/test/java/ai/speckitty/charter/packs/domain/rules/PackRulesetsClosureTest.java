package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import ai.speckitty.charter.packs.fixtures.CompositionFacts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pack-set check's one composition (#478, FR-007, S4): {@link PackRulesets#closure(Function)} is {@code CLOSURE}
 * with its preset phase (the contests, then the preset items), so pack-doctor runs one named ruleset and defines no
 * rule of its own.
 */
@DisplayName("PackRulesets.closure(factsOf): CLOSURE with its preset phase (#478, S4)")
class PackRulesetsClosureTest {

    private static final String LEFT_ID = "01KZEFT0000000000000000000";

    private static final String RIGHT_ID = "01RHZGHT000000000000000000";

    private static final String CHILD_ID = "01CHZKD0000000000000000000";

    private static final String ORPHAN_ID = "01ZRPHAN000000000000000000";

    private static final String NONE_ID = "01GNZNE0000000000000000000";
    private static final PackUnderResolution LEFT = pack(LEFT_ID, "left", Optional.empty());

    private static final PackUnderResolution RIGHT = pack(RIGHT_ID, "right", Optional.empty());

    private static final PackUnderResolution CHILD = pack(CHILD_ID, "child", Optional.of(LEFT_ID));

    private static final Function<PackUnderResolution, PackCompositionFacts> EACH_HOLDS_TEAM =
            presetsHeld(Map.of(LEFT_ID, List.of("team"), RIGHT_ID, List.of("team"), CHILD_ID, List.of("team")));

    @Test
    @DisplayName("two unrelated packs of one level holding one preset id refuse with the sibling conflict")
    void twoUnrelatedPacksHoldingOnePresetRefuse() {
        List<Reason> reasons = PackRulesets.closure(EACH_HOLDS_TEAM).apply(List.of(LEFT, RIGHT));

        assertThat(reasons).singleElement().satisfies(reason -> {
            assertThat(reason.code().value()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT.token());
            assertThat(reason.rule()).isEqualTo(CompositionRules.PRESETS_RULE);
            assertThat(reason.detail())
                    .isEqualTo("packs `left` (" + LEFT_ID + ") and `right` (" + RIGHT_ID
                            + ") at org both hold the preset `team`,"
                            + " and neither extends the other by parent_pack");
        });
    }

    @Test
    @DisplayName("a pack that extends the other may hold the same preset id")
    void anExtendingPackDoesNotRefuse() {
        assertThat(PackRulesets.closure(EACH_HOLDS_TEAM).apply(List.of(LEFT, CHILD)))
                .isEmpty();
    }

    @Test
    @DisplayName("the order the packs are given in does not change the answer")
    void theOrderDoesNotChangeTheAnswer() {
        List<Reason> given = PackRulesets.closure(EACH_HOLDS_TEAM).apply(List.of(LEFT, RIGHT));
        List<Reason> reversed = PackRulesets.closure(EACH_HOLDS_TEAM).apply(List.of(RIGHT, LEFT));

        assertThat(given).isNotEmpty();
        assertThat(reversed).isEqualTo(given);
    }

    @Test
    @DisplayName(
            "control: the composition faults alone do not judge the preset contest; the composition adds the phase")
    void closureAloneDoesNotSeeThePresetContest() {
        assertThat(CompositionRules.closure().apply(List.of(LEFT, RIGHT))).isEmpty();
        assertThat(PackRulesets.closure(EACH_HOLDS_TEAM).apply(List.of(LEFT, RIGHT)))
                .isNotEmpty();
    }

    @Test
    @DisplayName("one derivation: closure(factsOf)'s reasons are closureFaults', code, detail and phase, in order")
    void theRulesetsReasonsAreItsFaults() {
        PackUnderResolution orphan = pack(ORPHAN_ID, "orphan", Optional.of(NONE_ID));
        List<PackUnderResolution> set = List.of(LEFT, RIGHT, orphan);

        List<ClosureFault> faults = PackRulesets.closureFaults(set, EACH_HOLDS_TEAM);
        List<Reason> reasons = PackRulesets.closure(EACH_HOLDS_TEAM).apply(set);

        assertThat(faults)
                .extracting(fault -> fault.reason().code().value())
                .containsExactly(RefusalCode.PACK_PARENT_UNRESOLVED.token(), RefusalCode.PACK_SIBLING_CONFLICT.token());
        assertThat(faults).allSatisfy(fault -> assertThat(fault.packs()).isNotEmpty());
        assertThat(reasons)
                .extracting(Reason::code, Reason::detail, Reason::rule)
                .containsExactly(
                        tuple(faults.get(0).reason().code(), faults.get(0).detail(), CompositionRules.RULE),
                        tuple(faults.get(1).reason().code(), faults.get(1).detail(), CompositionRules.PRESETS_RULE));
    }

    @Test
    @DisplayName("the preset phase: the compose, then the contests, then the preset items, each under its phase's rule")
    void thePresetPhaseRunsTheContestsThenThePresetItems() {
        Function<PackUnderResolution, PackCompositionFacts> factsOf =
                pack -> pack.descriptor().packId().equals(LEFT_ID)
                        ? CompositionFacts.of(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                MutabilityPolicySource.empty(),
                                List.of(new Preset(
                                        "team",
                                        "The team.",
                                        Map.of(ArtifactKind.DIRECTIVE, List.of(new PresetItem("DIR-404", true))))))
                        : EACH_HOLDS_TEAM.apply(pack);
        PackUnderResolution orphan = pack(ORPHAN_ID, "orphan", Optional.of(NONE_ID));

        List<ClosureFault> faults = PackRulesets.closureFaults(List.of(LEFT, RIGHT, orphan), factsOf);

        assertThat(faults)
                .extracting(ClosureFault::code, fault -> fault.reason().rule())
                .containsExactly(
                        tuple(RefusalCode.PACK_PARENT_UNRESOLVED, CompositionRules.RULE),
                        tuple(RefusalCode.PACK_SIBLING_CONFLICT, CompositionRules.PRESETS_RULE),
                        tuple(RefusalCode.PRESET_ITEM_NOT_HELD, PresetItemRules.RULE));
        assertThat(PackRulesets.presetItemFaults(List.of(LEFT, RIGHT, orphan), factsOf))
                .as("the phase registration runs is the same one")
                .containsExactly(faults.getLast());
    }

    @Test
    @DisplayName("a set that resolves with no preset contest has no fault")
    void aResolvedSetHasNoFault() {
        assertThat(PackRulesets.closureFaults(List.of(LEFT, CHILD), EACH_HOLDS_TEAM))
                .isEmpty();
    }

    @Test
    @DisplayName(
            "the planted example subjects carry the one reader's Built facts, so a set check over them runs CLOSURE")
    void thePlantedExampleSubjectsAreBuilt() {
        List<PlantedFixtures.Planted> examples = PlantedFixtures.all().stream()
                .filter(planted -> planted.key().startsWith("example-packs/"))
                .toList();

        assertThat(examples).isNotEmpty();
        assertThat(examples)
                .allSatisfy(planted -> assertThat(planted.readout().facts()).isInstanceOf(PackReadout.Built.class));
    }

    /** A pack configured under its name, a pack id, whose descriptor declares the ULID {@code id} (#583). */
    private static PackUnderResolution pack(String id, String name, Optional<String> parent) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(name), PackLevel.ORG),
                new PackDescriptor(id, "1.0.0", parent, name, Optional.of(PackLevel.ORG), Optional.empty()));
    }

    /** Each pack's facts with the presets of the given ids, by the pack's descriptor id; no item, so no item fault. */
    private static Function<PackUnderResolution, PackCompositionFacts> presetsHeld(Map<String, List<String>> presets) {
        return pack -> CompositionFacts.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                MutabilityPolicySource.empty(),
                presets.getOrDefault(pack.descriptor().packId(), List.of()).stream()
                        .map(id -> new Preset(id, "The " + id + " preset.", Map.of()))
                        .toList());
    }
}
