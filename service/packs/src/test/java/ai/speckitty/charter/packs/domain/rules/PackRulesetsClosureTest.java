package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.rules.CompositionRules.CompositionFault;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pack-set check's one composition (#478, FR-007, S4): {@link PackRulesets#closure(Function)} is {@code CLOSURE}
 * with its preset phase, so pack-check runs one named ruleset and defines no rule of its own.
 */
@DisplayName("PackRulesets.closure(presetIdsOf): CLOSURE with its preset phase (#478, S4)")
class PackRulesetsClosureTest {

    private static final String LEFT_ID = "01KZEFT0000000000000000000";

    private static final String RIGHT_ID = "01RHZGHT000000000000000000";

    private static final String CHILD_ID = "01CHZKD0000000000000000000";

    private static final String ORPHAN_ID = "01ZRPHAN000000000000000000";

    private static final String NONE_ID = "01GNZNE0000000000000000000";
    private static final PackUnderResolution LEFT = pack(LEFT_ID, "left", Optional.empty());

    private static final PackUnderResolution RIGHT = pack(RIGHT_ID, "right", Optional.empty());

    private static final PackUnderResolution CHILD = pack(CHILD_ID, "child", Optional.of(LEFT_ID));

    private static final Function<PackUnderResolution, Collection<String>> EACH_HOLDS_TEAM =
            holding(Map.of(LEFT_ID, List.of("team"), RIGHT_ID, List.of("team"), CHILD_ID, List.of("team")));

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
    @DisplayName("control: closure() alone does not judge the preset contest; the new composition adds the phase")
    void closureAloneDoesNotSeeThePresetContest() {
        assertThat(PackRulesets.closure().apply(List.of(LEFT, RIGHT))).isEmpty();
        assertThat(PackRulesets.closure(EACH_HOLDS_TEAM).apply(List.of(LEFT, RIGHT)))
                .isNotEmpty();
    }

    @Test
    @DisplayName("one derivation: closure(presetIdsOf)'s reasons are closureFaults', code, detail and phase, in order")
    void theRulesetsReasonsAreItsFaults() {
        PackUnderResolution orphan = pack(ORPHAN_ID, "orphan", Optional.of(NONE_ID));
        List<PackUnderResolution> set = List.of(LEFT, RIGHT, orphan);

        List<CompositionFault> faults = PackRulesets.closureFaults(set, EACH_HOLDS_TEAM);
        List<Reason> reasons = PackRulesets.closure(EACH_HOLDS_TEAM).apply(set);

        assertThat(faults)
                .extracting(fault -> fault.code().value())
                .containsExactly(RefusalCode.PACK_PARENT_UNRESOLVED.token(), RefusalCode.PACK_SIBLING_CONFLICT.token());
        assertThat(faults).allSatisfy(fault -> assertThat(fault.packs()).isNotEmpty());
        assertThat(reasons)
                .extracting(Reason::code, Reason::detail, Reason::rule)
                .containsExactly(
                        tuple(faults.get(0).code(), faults.get(0).detail(), CompositionRules.RULE),
                        tuple(faults.get(1).code(), faults.get(1).detail(), CompositionRules.PRESETS_RULE));
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

    private static Function<PackUnderResolution, Collection<String>> holding(Map<String, List<String>> presets) {
        return pack -> presets.getOrDefault(pack.descriptor().packId(), List.of());
    }

    /** A pack configured under its name, a pack id, whose descriptor declares the ULID {@code id} (#583). */
    private static PackUnderResolution pack(String id, String name, Optional<String> parent) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(name), PackLevel.ORG),
                new PackDescriptor(id, "1.0.0", parent, name, Optional.of(PackLevel.ORG), Optional.empty()));
    }
}
