package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.fixtures.CompositionFacts;
import ai.speckitty.charter.packs.infra.storage.TreeReads;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The preset-item rule (#629, #632 item 1; rulings R-a, R-k, R-l, R-m; plan D3): a preset item that the declaring pack's
 * own closure ({@code depends_on} and {@code parent_pack}, never a dependent) lacks is refused. A {@code must_have} item
 * no closure pack holds as a file answers {@code preset_item_not_held}; any item no closure pack declares as its kind
 * answers {@code preset_item_undeclared}. One fault per item. An item that names an artifact of the own closure only by
 * case gets no fault here: its answer is the case-only one, which {@code CLOSURE}'s case-only phase gives
 * ({@link CaseOnlyRules}, ruling R-z).
 */
@DisplayName("PresetItemRules: a preset item the declaring pack's own closure lacks is refused (#629, #632.1)")
class PresetItemRulesTest {

    private static final String BASE_ID = "01BASE00000000000000000000";
    private static final String ORG_ID = "01ZRG000000000000000000000";
    private static final String CHILD_ID = "01CHZLD0000000000000000000";
    private static final String DEPENDENT_ID = "01DEPENDENT000000000000000";

    /** The base: holds a directive file, and declares a tactic by a graph node only. */
    private static final PackUnderResolution BASE = pack(BASE_ID, "base", Optional.empty(), List.of());

    /** An org pack that depends on the base. */
    private static final PackUnderResolution ORG = pack(ORG_ID, "org", Optional.empty(), List.of(BASE_ID));

    private final Map<String, PackCompositionFacts> facts = new HashMap<>();

    private PackCompositionFacts factsOf(PackUnderResolution pack) {
        return facts.getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE);
    }

    /** As the readout builds a pack under resolution: what a reference can name is the facts' referable list. */
    private List<PackUnderResolution> read(PackUnderResolution... packs) {
        return Arrays.stream(packs)
                .map(pack -> new PackUnderResolution(
                        pack.configured(),
                        pack.descriptor(),
                        List.of(),
                        factsOf(pack).referable(),
                        Set.of()))
                .toList();
    }

    private List<ClosureFault> judge(PackUnderResolution... packs) {
        return PresetItemRules.phase(this::factsOf).apply(read(packs));
    }

    private void base() {
        facts.put("base", facts(List.of("directive:DIR-1"), List.of("tactic:only-a-node"), List.of()));
    }

    @Test
    @DisplayName("a must_have item no pack of the own closure holds answers preset_item_not_held at the item")
    void anUnheldMustHaveIsNotHeld() {
        base();
        facts.put(
                "org",
                facts(List.of(), List.of(), List.of(preset("team", ArtifactKind.DIRECTIVE, required("DIR-404")))));

        List<ClosureFault> faults = judge(BASE, ORG);

        assertThat(faults).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.PRESET_ITEM_NOT_HELD);
            assertThat(fault.reason().rule()).isEqualTo(PresetItemRules.RULE);
            assertThat(fault.origin()).isEqualTo(new Origin("org::directive:DIR-404"));
            assertThat(fault.detail())
                    .isEqualTo("the preset `team` of pack `org` requires directive:DIR-404 (must_have), but no pack"
                            + " of its own closure (`org`, `base`) holds it as an artifact file");
            assertThat(fault.packs()).extracting(RefusalPack::name).containsExactly("org");
            assertThat(fault.violations())
                    .containsExactly(new RefusalViolation(
                            "presets/team.yaml#/activated_directives/0", "preset_item_not_held", fault.detail()));
        });
    }

    @Test
    @DisplayName("a must_have held only by a dependent, outside the own closure, is not held")
    void heldOnlyByADependentIsNotHeld() {
        PackUnderResolution dependent = pack(DEPENDENT_ID, "dependent", Optional.empty(), List.of(BASE_ID));
        facts.put(
                "base",
                facts(List.of(), List.of(), List.of(preset("team", ArtifactKind.DIRECTIVE, required("DIR-DEP")))));
        facts.put("dependent", facts(List.of("directive:DIR-DEP"), List.of(), List.of()));

        assertThat(judge(BASE, dependent))
                .extracting(
                        ClosureFault::code, fault -> fault.packs().getFirst().name())
                .containsExactly(tuple(RefusalCode.PRESET_ITEM_NOT_HELD, "base"));
    }

    @Test
    @DisplayName("a must_have held by the parent_pack is held: the own closure takes the parent line")
    void heldByTheParentIsAccepted() {
        PackUnderResolution child = pack(CHILD_ID, "child", Optional.of(BASE_ID), List.of());
        base();
        facts.put(
                "child",
                facts(List.of(), List.of(), List.of(preset("team", ArtifactKind.DIRECTIVE, required("DIR-1")))));

        assertThat(judge(BASE, child)).isEmpty();
    }

    @Test
    @DisplayName("an item that is declared but not held, and not must_have, is accepted")
    void declaredButNotRequiredIsAccepted() {
        base();
        facts.put(
                "org",
                facts(
                        List.of(),
                        List.of(),
                        List.of(preset("team", ArtifactKind.TACTIC, PresetItem.of("only-a-node")))));

        assertThat(judge(BASE, ORG)).isEmpty();
    }

    @Test
    @DisplayName("a must_have that is only declared, by a graph node, is not held")
    void declaredOnlyMustHaveIsNotHeld() {
        base();
        facts.put(
                "org",
                facts(List.of(), List.of(), List.of(preset("team", ArtifactKind.TACTIC, required("only-a-node")))));

        assertThat(judge(BASE, ORG)).extracting(ClosureFault::code).containsExactly(RefusalCode.PRESET_ITEM_NOT_HELD);
    }

    @Test
    @DisplayName("a pack that a duplicate pack_id shadows is not judged against the other pack's closure")
    void aShadowedDuplicateIsNotJudgedAgainstTheOtherPack() {
        PackUnderResolution twin = pack(BASE_ID, "twin", Optional.empty(), List.of());
        facts.put("base", facts(List.of(), List.of(), List.of()));
        facts.put(
                "twin",
                facts(
                        List.of("directive:DIR-TWIN"),
                        List.of(),
                        List.of(preset("team", ArtifactKind.DIRECTIVE, required("DIR-TWIN")))));

        assertThat(judge(BASE, twin))
                .as("DUPLICATE_ID is CLOSURE's answer, not a false item fault")
                .isEmpty();
    }

    @Test
    @DisplayName("an item no pack of the own closure declares answers preset_item_undeclared")
    void anUndeclaredItemIsUndeclared() {
        base();
        facts.put(
                "org",
                facts(List.of(), List.of(), List.of(preset("team", ArtifactKind.PARADIGM, PresetItem.of("ghost")))));

        assertThat(judge(BASE, ORG)).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.PRESET_ITEM_UNDECLARED);
            assertThat(fault.detail())
                    .isEqualTo("the preset `team` of pack `org` activates paradigm:ghost (not must_have), but no"
                            + " pack of its own closure (`org`, `base`) declares it as a paradigm");
            assertThat(fault.violations())
                    .extracting(RefusalViolation::pointer, RefusalViolation::code)
                    .containsExactly(tuple("presets/team.yaml#/activated_paradigms/0", "preset_item_undeclared"));
        });
    }

    @Test
    @DisplayName("an item of another kind never satisfies the entry: a tactic DIR-1 is not the directive DIR-1")
    void anotherKindNeverSatisfiesTheEntry() {
        base();
        facts.put(
                "org",
                facts(List.of(), List.of(), List.of(preset("team", ArtifactKind.TACTIC, PresetItem.of("DIR-1")))));

        assertThat(judge(BASE, ORG)).extracting(ClosureFault::code).containsExactly(RefusalCode.PRESET_ITEM_UNDECLARED);
    }

    @Test
    @DisplayName("a mission type is an item too: a required mission type no pack holds is not held")
    void aMissionTypeIsAnItem() {
        base();
        facts.put(
                "org",
                facts(List.of(), List.of(), List.of(preset("team", ArtifactKind.MISSION_TYPE, required("research")))));

        assertThat(judge(BASE, ORG))
                .extracting(
                        ClosureFault::code,
                        fault -> fault.violations().getFirst().pointer())
                .containsExactly(
                        tuple(RefusalCode.PRESET_ITEM_NOT_HELD, "presets/team.yaml#/activated_mission_types/0"));
    }

    @Test
    @DisplayName("a case-only mismatch gets no item fault: CLOSURE answers the case-only fault,"
            + " pack_reference_artifact_missing, alone")
    void aCaseOnlyMismatchGetsTheCaseOnlyFaultOnly() {
        base();
        facts.put(
                "org", facts(List.of(), List.of(), List.of(preset("team", ArtifactKind.DIRECTIVE, required("dir-1")))));

        assertThat(judge(BASE, ORG)).isEmpty();
        assertThat(PackRulesets.closureFaults(read(BASE, ORG), this::factsOf))
                .singleElement()
                .satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                    assertThat(fault.reason().rule()).isEqualTo(CaseOnlyRules.RULE);
                    assertThat(fault.origin()).isEqualTo(new Origin("org"));
                    assertThat(fault.packs()).extracting(RefusalPack::name).containsExactly("org", "base");
                    assertThat(fault.detail())
                            .contains("`directive:dir-1`", "`directive:DIR-1`", "differ only by case");
                    // The existing case-only answer stands: registration's, which carries no violation of its own.
                    assertThat(fault.violations()).isEmpty();
                });
    }

    @Test
    @DisplayName("two faults in one preset come in item order, and packs by pack id, then presets by path")
    void faultsComeInPackPresetAndItemOrder() {
        base();
        Map<ArtifactKind, List<PresetItem>> items = new LinkedHashMap<>();
        items.put(ArtifactKind.DIRECTIVE, List.of(PresetItem.of("DIR-1"), required("DIR-2"), PresetItem.of("DIR-3")));
        items.put(ArtifactKind.TACTIC, List.of(PresetItem.of("missing")));
        facts.put(
                "org",
                facts(
                        List.of(),
                        List.of(),
                        List.of(
                                new Preset("zeta", "Zeta.", Map.of(ArtifactKind.PARADIGM, List.of(required("p")))),
                                new Preset("alpha", "Alpha.", items))));
        facts.put(
                "base",
                facts(
                        List.of("directive:DIR-1"),
                        List.of("tactic:only-a-node"),
                        List.of(new Preset("own", "Own.", Map.of(ArtifactKind.DIRECTIVE, List.of(required("X")))))));

        assertThat(judge(ORG, BASE))
                .extracting(
                        ClosureFault::code,
                        fault -> fault.violations().getFirst().pointer())
                .containsExactly(
                        tuple(RefusalCode.PRESET_ITEM_NOT_HELD, "presets/own.yaml#/activated_directives/0"),
                        tuple(RefusalCode.PRESET_ITEM_NOT_HELD, "presets/alpha.yaml#/activated_directives/1"),
                        tuple(RefusalCode.PRESET_ITEM_UNDECLARED, "presets/alpha.yaml#/activated_directives/2"),
                        tuple(RefusalCode.PRESET_ITEM_UNDECLARED, "presets/alpha.yaml#/activated_tactics/0"),
                        tuple(RefusalCode.PRESET_ITEM_NOT_HELD, "presets/zeta.yaml#/activated_paradigms/0"));
    }

    @Test
    @DisplayName("a pack whose own closure misses a pack is not judged: the dependency fault speaks for it")
    void anIncompleteClosureIsNotJudged() {
        facts.put(
                "org", facts(List.of(), List.of(), List.of(preset("team", ArtifactKind.DIRECTIVE, required("DIR-1")))));

        assertThat(judge(ORG)).isEmpty();
    }

    /**
     * The positive controls (T015): every preset item of every example pack is held or declared as it must be within
     * its own closure, and a planted variant of each example preset, in the test and never in the example packs,
     * answers the exact code.
     */
    @Nested
    @DisplayName("over the example packs")
    class ExamplePacksControl {

        private static final Map<String, PackReadout.Built> READ = readExamples();

        private static Map<String, PackReadout.Built> readExamples() {
            Map<String, PackReadout.Built> read = new LinkedHashMap<>();
            for (String name : ExamplePacks.names()) {
                PackRoot root = new PackRoot(
                        name,
                        Optional.of(ExamplePacks.level(name)),
                        ExamplePacks.pack(name).toString());
                read.put(name, (PackReadout.Built)
                        TreeReads.withReadout(root).readout().facts());
            }
            return read;
        }

        private static List<PackUnderResolution> set() {
            return READ.entrySet().stream()
                    .map(entry -> entry.getValue()
                            .underResolution(new ConfiguredPack(
                                    new PackIdentifier(entry.getKey()), ExamplePacks.level(entry.getKey()))))
                    .toList();
        }

        private static Function<PackUnderResolution, PackCompositionFacts> plantedIn(String pack, Preset planted) {
            return under -> {
                PackCompositionFacts facts =
                        READ.get(under.configured().id().value()).facts();
                return under.configured().id().value().equals(pack) ? withPreset(facts, planted) : facts;
            };
        }

        private static List<ClosureFault> judgeWith(String pack, Preset planted) {
            return PresetItemRules.phase(plantedIn(pack, planted)).apply(set());
        }

        private static PackCompositionFacts withPreset(PackCompositionFacts facts, Preset planted) {
            List<Preset> presets = new ArrayList<>(facts.presets().stream()
                    .filter(preset -> !preset.id().equals(planted.id()))
                    .toList());
            presets.add(planted);
            return new PackCompositionFacts(
                    facts.changed(),
                    facts.held(),
                    facts.declared(),
                    facts.references(),
                    facts.policy(),
                    presets,
                    facts.edges(),
                    facts.languages(),
                    facts.prompts(),
                    facts.terms(),
                    facts.delegations());
        }

        private static Preset planting(Preset preset, ArtifactKind kind, PresetItem item) {
            Map<ArtifactKind, List<PresetItem>> items = new LinkedHashMap<>(preset.artifacts());
            List<PresetItem> list = new ArrayList<>(items.getOrDefault(kind, List.of()));
            list.add(item);
            items.put(kind, list);
            return new Preset(preset.id(), preset.description(), items);
        }

        static List<Arguments> examplePresets() {
            List<Arguments> presets = new ArrayList<>();
            READ.forEach((pack, built) ->
                    built.facts().presets().forEach(preset -> presets.add(Arguments.of(pack, preset.id()))));
            return presets;
        }

        private static Preset presetOf(String pack, String id) {
            return READ.get(pack).facts().presets().stream()
                    .filter(preset -> preset.id().equals(id))
                    .findFirst()
                    .orElseThrow();
        }

        @Test
        @DisplayName("every preset item of every example pack is held or declared within its own closure")
        void everyExamplePresetItemIsInItsOwnClosure() {
            long items = READ.values().stream()
                    .flatMap(built -> built.facts().presets().stream())
                    .mapToLong(preset -> preset.artifacts().values().stream()
                            .mapToLong(List::size)
                            .sum())
                    .sum();

            assertThat(examplePresets()).as("the control is not vacuous").hasSizeGreaterThanOrEqualTo(6);
            assertThat(items).isPositive();
            assertThat(PresetItemRules.phase(under ->
                                    READ.get(under.configured().id().value()).facts())
                            .apply(set()))
                    .isEmpty();
        }

        @ParameterizedTest(name = "{0} {1}")
        @MethodSource("examplePresets")
        @DisplayName("planted: a must_have nobody holds answers preset_item_not_held at its item")
        void aPlantedUnheldMustHaveIsNotHeld(String pack, String presetId) {
            Preset preset = presetOf(pack, presetId);
            int index = preset.artifacts()
                    .getOrDefault(ArtifactKind.DIRECTIVE, List.of())
                    .size();

            assertThat(judgeWith(pack, planting(preset, ArtifactKind.DIRECTIVE, required("PLANTED_UNHELD"))))
                    .singleElement()
                    .satisfies(fault -> {
                        assertThat(fault.code()).isEqualTo(RefusalCode.PRESET_ITEM_NOT_HELD);
                        assertThat(fault.origin()).isEqualTo(new Origin(pack + "::directive:PLANTED_UNHELD"));
                        assertThat(fault.violations().getFirst().pointer())
                                .isEqualTo("presets/" + presetId + ".yaml#/activated_directives/" + index);
                    });
        }

        @ParameterizedTest(name = "{0} {1}")
        @MethodSource("examplePresets")
        @DisplayName("planted: an item nobody declares answers preset_item_undeclared")
        void aPlantedUndeclaredItemIsUndeclared(String pack, String presetId) {
            assertThat(judgeWith(
                            pack,
                            planting(presetOf(pack, presetId), ArtifactKind.PARADIGM, PresetItem.of("planted-ghost"))))
                    .extracting(ClosureFault::code)
                    .containsExactly(RefusalCode.PRESET_ITEM_UNDECLARED);
        }

        @ParameterizedTest(name = "{0} {1}")
        @MethodSource("examplePresets")
        @DisplayName("planted: built-in's tactic acceptance-test-first named by case only keeps the case-only code")
        void aPlantedCaseOnlyItemKeepsItsCode(String pack, String presetId) {
            Preset planted =
                    planting(presetOf(pack, presetId), ArtifactKind.TACTIC, PresetItem.of("ACCEPTANCE-TEST-FIRST"));

            assertThat(judgeWith(pack, planted)).as("no item fault of its own").isEmpty();
            assertThat(PackRulesets.closureFaults(set(), plantedIn(pack, planted)))
                    .extracting(ClosureFault::code)
                    .containsExactly(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
        }
    }

    private static PresetItem required(String id) {
        return new PresetItem(id, true);
    }

    private static Preset preset(String id, ArtifactKind kind, PresetItem item) {
        return new Preset(id, "The " + id + " preset.", Map.of(kind, List.of(item)));
    }

    private static PackCompositionFacts facts(List<String> held, List<String> declared, List<Preset> presets) {
        return CompositionFacts.of(List.of(), held, declared, List.of(), MutabilityPolicySource.empty(), presets);
    }

    /** A pack configured under its name, whose descriptor declares the ULID {@code id}. */
    private static PackUnderResolution pack(String id, String name, Optional<String> parent, List<String> dependsOn) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(name), PackLevel.ORG),
                new PackDescriptor(
                        id,
                        "1.0.0",
                        parent,
                        name,
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        dependsOn.stream()
                                .map(dep -> new PackDependency(dep, ">=1.0,<2"))
                                .toList(),
                        List.of()),
                List.of(),
                List.of());
    }
}
