package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackReadout;
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

    private static final PackUnderResolution LEFT = pack("01LEFT", "left", Optional.empty());

    private static final PackUnderResolution RIGHT = pack("01RIGHT", "right", Optional.empty());

    private static final PackUnderResolution CHILD = pack("01CHILD", "child", Optional.of("01LEFT"));

    private static final Function<PackUnderResolution, Collection<String>> EACH_HOLDS_TEAM =
            holding(Map.of("01LEFT", List.of("team"), "01RIGHT", List.of("team"), "01CHILD", List.of("team")));

    @Test
    @DisplayName("two unrelated packs of one level holding one preset id refuse with the sibling conflict")
    void twoUnrelatedPacksHoldingOnePresetRefuse() {
        List<Reason> reasons = PackRulesets.closure(EACH_HOLDS_TEAM).apply(List.of(LEFT, RIGHT));

        assertThat(reasons).singleElement().satisfies(reason -> {
            assertThat(reason.code().value()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT.token());
            assertThat(reason.rule()).isEqualTo(CompositionRules.PRESETS_RULE);
            assertThat(reason.detail())
                    .isEqualTo("packs `left` (01LEFT) and `right` (01RIGHT) at org both hold the preset `team`,"
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

    private static PackUnderResolution pack(String id, String name, Optional<String> parent) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), PackLevel.ORG),
                new PackDescriptor(id, "1.0.0", parent, name, Optional.of(PackLevel.ORG), Optional.empty()));
    }
}
