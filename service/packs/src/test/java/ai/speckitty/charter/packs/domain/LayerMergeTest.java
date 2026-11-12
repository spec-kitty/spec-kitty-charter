package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The layer merge as a table, one row per rule of the composition and activation rules page that the merge decides
 * (C15, C17, C18, C21, C22, C29, C31; the edge rules C19, C20 and C36 are the merged {@code EdgeIndex}'s, in its own
 * test). Each row composes a closure with the resolver, as a read does, and merges it.
 */
class LayerMergeTest {

    /** A closure under construction: the packs, what their facts carry and the policies they declare. */
    static final class Fixture {

        private final List<PackUnderResolution> packs = new ArrayList<>();
        private final Map<String, List<Preset>> presets = new HashMap<>();
        private final Map<String, Map<String, List<String>>> languages = new HashMap<>();
        private final Map<String, MutabilityPolicySource> policies = new HashMap<>();

        Fixture pack(String name, PackLevel level, String... held) {
            return pack(name, level, List.of(), held);
        }

        Fixture pack(String name, PackLevel level, List<PackOverride> overrides, String... held) {
            PackDescriptor descriptor = new PackDescriptor(
                    packId(name),
                    "1.0.0",
                    Optional.empty(),
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    PackPaths.none(),
                    List.of(),
                    overrides);
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), Arrays.asList(held)));
            return this;
        }

        Fixture languages(String name, String urn, String... declared) {
            languages.computeIfAbsent(name, ignored -> new HashMap<>()).put(urn, Arrays.asList(declared));
            return this;
        }

        Fixture preset(String name, Preset preset) {
            presets.computeIfAbsent(name, ignored -> new ArrayList<>()).add(preset);
            return this;
        }

        Fixture policy(String name, MutabilityPolicySource policy) {
            policies.put(packId(name), policy);
            return this;
        }

        List<PackUnderResolution> closure() {
            return PackCompositionResolver.resolve(packs);
        }

        MergedClosure merge() {
            return LayerMerge.merge(closure(), facts(), new PerPackPolicy(policies));
        }

        /** The floor {@link LayerMerge#floorOf} answers, which the writes read beside {@link #merge()}'s. */
        RequiredFloor floorOf() {
            return LayerMerge.floorOf(closure(), facts());
        }

        private Map<String, PackCompositionFacts> facts() {
            Map<String, PackCompositionFacts> facts = new HashMap<>();
            for (PackUnderResolution pack : packs) {
                String name = pack.configured().id().value();
                facts.put(
                        name,
                        new PackCompositionFacts(
                                List.of(),
                                pack.definedArtifacts(),
                                List.of(),
                                MutabilityPolicySource.empty(),
                                presets.getOrDefault(name, List.of()),
                                List.of(),
                                languages.getOrDefault(name, Map.of())));
            }
            return facts;
        }
    }

    static String packId(String name) {
        return "id-" + name;
    }

    static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    static MergedClosure.Winner winner(MergedClosure merged, Urn urn) {
        Optional<MergedClosure.Winner> winner = merged.winnerOf(urn);
        assertThat(winner).as("the merge names a winner of " + urn).isPresent();
        return winner.orElseThrow();
    }

    static PackIdentifier configured(String name) {
        return new PackIdentifier(name);
    }

    /** One row of the table: the rule it pins and the check. */
    record Row(String rule, String scenario, Runnable check) {
        @Override
        public String toString() {
            return rule + " " + scenario;
        }
    }

    static Stream<Row> rows() {
        return Stream.of(
                        levelAndSiblingRows(),
                        mutabilityRows(),
                        stepContestAndLanguageOrderRows(),
                        presetAndMustHaveRows())
                .flatMap(rows -> rows);
    }

    private static Stream<Row> levelAndSiblingRows() {
        return Stream.of(
                new Row("C17", "the level that applies wins, built-in then org then project", LayerMergeTest::c17),
                new Row("C17", "an id held once is its own winner, and replaces nothing", LayerMergeTest::c17Single),
                new Row("C18", "the winner replaces the others whole, per artifact", LayerMergeTest::c18),
                new Row("C18", "the winner's languages are read from its own pack alone", LayerMergeTest::c18Languages),
                new Row(
                        "C18",
                        "a winner that declares no language inherits none from the version it replaced",
                        LayerMergeTest::c18NoLanguagesInherited),
                new Row("C15", "a settled sibling contest puts the named member in force", LayerMergeTest::c15),
                new Row(
                        "C15",
                        "a settled sibling loser is no layer override, the level winner is",
                        LayerMergeTest::c15BelowALayerWinner),
                new Row("C15", "an unsettled sibling contest still refuses in the compose", LayerMergeTest::c15Open));
    }

    private static Stream<Row> mutabilityRows() {
        return Stream.of(
                new Row(
                        "C21",
                        "a locked artifact refuses its override and names both packs",
                        LayerMergeTest::c21Locked),
                new Row("C21", "an extendable artifact refuses its override too", LayerMergeTest::c21Extendable),
                new Row("C21", "an open artifact is overridden", LayerMergeTest::c21Open),
                new Row(
                        "C21",
                        "the winning pack's own lock never blocks its override",
                        LayerMergeTest::c21WinnerExcluded),
                new Row(
                        "C21",
                        "a third pack that raised the level refuses the override",
                        LayerMergeTest::c21RaisedByAnotherPack),
                new Row("C22", "the policy of the closure is each winner's policy", LayerMergeTest::c22));
    }

    private static Stream<Row> stepContestAndLanguageOrderRows() {
        return Stream.of(
                new Row(
                        "C15",
                        "an unsettled step contest the compose lets through refuses the merge, never last loaded wins",
                        LayerMergeTest::c15UnsettledStep),
                new Row(
                        "C15",
                        "a step contest whose overrides entries name different siblings refuses in either closure order",
                        LayerMergeTest::c15StepEntriesNameDifferentSiblings),
                new Row(
                        "C15",
                        "a slash-less action contest whose entries name different siblings refuses in either order",
                        LayerMergeTest::c15ActionEntriesNameDifferentSiblings),
                new Row(
                        "C15",
                        "a step contest settled by one entry puts the named sibling in force in either order",
                        LayerMergeTest::c15StepSettledByOneEntry),
                new Row(
                        "C15",
                        "an action that names no step (action:M/) shares no contest with its mission type",
                        LayerMergeTest::c15ActionWithoutAStepNameIsNoStep),
                new Row("C18", "a winner's languages keep the order it declares", LayerMergeTest::c18LanguageOrder));
    }

    private static Stream<Row> presetAndMustHaveRows() {
        return Stream.of(
                new Row(
                        "C29",
                        "the copy of a preset id held last in merge order is the one applied",
                        LayerMergeTest::c29),
                new Row(
                        "C31",
                        "a project copy without the org's must-have items does not drop them (A1)",
                        LayerMergeTest::c31Union),
                new Row(
                        "C31",
                        "a requirement only tightens: a convenience copy adds none and drops none",
                        LayerMergeTest::c31Tighten),
                new Row(
                        "C31",
                        "a must-have item no pack holds is reported, never required (A10)",
                        LayerMergeTest::c31Unheld),
                new Row("C31", "a mission type is a must-have item like any other", LayerMergeTest::c31MissionType),
                new Row(
                        "C31",
                        "within one level the child's copy replaces its parent's, and the replaced copy requires nothing",
                        LayerMergeTest::c31SameLevel),
                new Row(
                        "C31",
                        "the union is across levels: a same-level replaced copy drops out, the other levels' stay",
                        LayerMergeTest::c31SameLevelBesideOtherLevels));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void everyRuleTheMergeDecidesHasItsRow(Row row) {
        row.check().run();
    }

    private static void c17() {
        MergedClosure merged = new Fixture()
                .pack("builtin", PackLevel.BUILT_IN, "directive:D", "directive:B")
                .pack("org", PackLevel.ORG, "directive:D", "directive:O")
                .pack("project", PackLevel.PROJECT, "directive:D")
                .merge();

        MergedClosure.Winner winner = winner(merged, urn("directive:D"));
        assertThat(winner.packId()).isEqualTo(configured("project"));
        assertThat(winner.descriptorPackId()).isEqualTo(packId("project"));
        assertThat(winner.level()).isEqualTo(PackLevel.PROJECT);
        assertThat(winner(merged, urn("directive:O")).level()).isEqualTo(PackLevel.ORG);
        assertThat(winner(merged, urn("directive:B")).level()).isEqualTo(PackLevel.BUILT_IN);
        assertThat(merged.layerOverrides())
                .as("one record per losing version, in merge order")
                .containsExactly(
                        new LayerOverride(
                                urn("directive:D"),
                                new LayerOverride.Side(configured("project"), PackLevel.PROJECT),
                                new LayerOverride.Side(configured("builtin"), PackLevel.BUILT_IN)),
                        new LayerOverride(
                                urn("directive:D"),
                                new LayerOverride.Side(configured("project"), PackLevel.PROJECT),
                                new LayerOverride.Side(configured("org"), PackLevel.ORG)));
    }

    private static void c17Single() {
        MergedClosure merged = new Fixture()
                .pack("builtin", PackLevel.BUILT_IN, "directive:B")
                .pack("org", PackLevel.ORG, "directive:O")
                .merge();

        assertThat(merged.winners()).containsOnlyKeys(urn("directive:B"), urn("directive:O"));
        assertThat(merged.layerOverrides()).isEmpty();
    }

    private static void c18() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "directive:D", "directive:E")
                .pack("project", PackLevel.PROJECT, "directive:D")
                .merge();

        assertThat(winner(merged, urn("directive:D")).packId()).isEqualTo(configured("project"));
        assertThat(winner(merged, urn("directive:E")).packId())
                .as("the merge is per artifact: what the project does not hold stays the org's")
                .isEqualTo(configured("org"));
        assertThat(merged.layerOverrides()).extracting(LayerOverride::urn).containsExactly(urn("directive:D"));
    }

    private static void c15() {
        MergedClosure merged = new Fixture()
                .pack("org-a", PackLevel.ORG, "directive:D")
                .pack("org-b", PackLevel.ORG, "directive:D")
                .pack("org-c", PackLevel.ORG, List.of(new PackOverride("directive:D", packId("org-a"))), "directive:C")
                .merge();

        MergedClosure.Winner winner = winner(merged, urn("directive:D"));
        assertThat(winner.packId())
                .as("the named member's version is in force, not the last one loaded")
                .isEqualTo(configured("org-a"));
        assertThat(merged.layerOverrides())
                .as("a settled sibling contest is no layer override")
                .isEmpty();
    }

    private static void c15BelowALayerWinner() {
        MergedClosure merged = new Fixture()
                .pack("org-a", PackLevel.ORG, "directive:D")
                .pack("org-b", PackLevel.ORG, List.of(new PackOverride("directive:D", packId("org-b"))), "directive:D")
                .pack("project", PackLevel.PROJECT, "directive:D")
                .merge();

        assertThat(winner(merged, urn("directive:D")).packId()).isEqualTo(configured("project"));
        assertThat(merged.layerOverrides())
                .containsExactly(new LayerOverride(
                        urn("directive:D"),
                        new LayerOverride.Side(configured("project"), PackLevel.PROJECT),
                        new LayerOverride.Side(configured("org-b"), PackLevel.ORG)));
    }

    private static void c15Open() {
        Fixture contested =
                new Fixture().pack("org-a", PackLevel.ORG, "directive:D").pack("org-b", PackLevel.ORG, "directive:D");

        assertThatThrownBy(contested::closure)
                .isInstanceOfSatisfying(
                        PackCompositionResolver.Refusal.class,
                        refusal -> assertThat(refusal.toCharterRefusal().code())
                                .isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT));
    }

    private static void c18Languages() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "tactic:T", "tactic:U")
                .pack("project", PackLevel.PROJECT, "tactic:T")
                .languages("org", "tactic:T", "java", "kotlin")
                .languages("org", "tactic:U", "python")
                .languages("project", "tactic:T", "rust")
                .merge();

        assertThat(merged.languages().get(urn("tactic:T"))).containsExactlyInAnyOrder("rust");
        assertThat(merged.languages().get(urn("tactic:U"))).containsExactlyInAnyOrder("python");
        assertThat(merged.languages()).containsOnlyKeys(urn("tactic:T"), urn("tactic:U"));
    }

    private static void c15UnsettledStep() {
        Fixture contested =
                new Fixture().pack("org-a", PackLevel.ORG, "action:M/s").pack("org-b", PackLevel.ORG, "action:M/s");

        assertThat(contested.closure())
                .as("the compose leaves a step out of the sibling contest")
                .hasSize(2);
        assertThatThrownBy(contested::merge).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
            assertThat(refusal.getMessage()).contains("action:M/s", "org-a", "org-b", "org");
            assertThat(refusal.packs()).extracting(RefusalPack::name).containsExactlyInAnyOrder("org-a", "org-b");
        });
    }

    private static Fixture twoDefiners(String urn, String key, String xNames, String yNames, boolean xFirst) {
        PackOverride byX = new PackOverride(key, packId(xNames));
        PackOverride byY = new PackOverride(key, packId(yNames));
        Fixture fixture = new Fixture();
        if (xFirst) {
            fixture.pack("org-x", PackLevel.ORG, List.of(byX), urn).pack("org-y", PackLevel.ORG, List.of(byY), urn);
        } else {
            fixture.pack("org-y", PackLevel.ORG, List.of(byY), urn).pack("org-x", PackLevel.ORG, List.of(byX), urn);
        }
        return fixture;
    }

    private static void refusesAsConflict(Fixture fixture, String urn) {
        assertThat(fixture.closure())
                .as("the compose leaves an action out of the sibling contest")
                .hasSize(2);
        assertThatThrownBy(fixture::merge).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
            assertThat(refusal.getMessage()).contains(urn, "org-x", "org-y", "different winners");
            assertThat(refusal.packs()).extracting(RefusalPack::name).containsExactlyInAnyOrder("org-x", "org-y");
        });
    }

    private static void c15StepEntriesNameDifferentSiblings() {
        for (boolean xFirst : new boolean[] {true, false}) {
            refusesAsConflict(twoDefiners("action:M/s", "mission_type:M", "org-x", "org-y", xFirst), "action:M/s");
        }
    }

    private static void c15ActionEntriesNameDifferentSiblings() {
        for (boolean xFirst : new boolean[] {true, false}) {
            refusesAsConflict(twoDefiners("action:A", "action:A", "org-x", "org-y", xFirst), "action:A");
        }
    }

    private static void c15StepSettledByOneEntry() {
        for (boolean xFirst : new boolean[] {true, false}) {
            MergedClosure merged = twoDefiners("action:M/s", "mission_type:M", "org-y", "org-y", xFirst)
                    .merge();
            assertThat(winner(merged, urn("action:M/s")).packId())
                    .as("the named sibling is in force whatever the closure order")
                    .isEqualTo(configured("org-y"));
        }
    }

    private static void c15ActionWithoutAStepNameIsNoStep() {
        for (boolean xFirst : new boolean[] {true, false}) {
            Fixture contested = twoDefiners("action:M/", "mission_type:M", "org-y", "org-y", xFirst);

            assertThatThrownBy(contested::merge).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
                assertThat(refusal.getMessage())
                        .as("the entry names the mission type, and an action that names no step is not its step")
                        .contains("action:M/", "no overrides entry names");
            });
        }
    }

    private static void c18LanguageOrder() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "tactic:T")
                .languages("org", "tactic:T", "zulu", "alpha", "mike", "bravo", "kilo", "delta")
                .merge();

        assertThat(merged.languages().get(urn("tactic:T")))
                .containsExactly("zulu", "alpha", "mike", "bravo", "kilo", "delta");
    }

    private static void c18NoLanguagesInherited() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "tactic:T")
                .pack("project", PackLevel.PROJECT, "tactic:T")
                .languages("org", "tactic:T", "java")
                .merge();

        assertThat(merged.languages())
                .as("the project's tactic declares none, and nothing is inherited from the org's")
                .isEmpty();
    }

    private static MutabilityPolicySource level(String urn, MutabilityLevel level) {
        return MutabilityPolicySource.builder().entry(urn(urn), level).build();
    }

    private static void refuses(Fixture fixture, String... mentions) {
        assertThatThrownBy(fixture::merge).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).contains(mentions);
            assertThat(refusal.packs())
                    .as("the winning pack first, then the pack it replaces")
                    .extracting(RefusalPack::name)
                    .containsExactly("project", "org");
        });
    }

    private static void c21Locked() {
        refuses(
                new Fixture()
                        .pack("org", PackLevel.ORG, "directive:D")
                        .pack("project", PackLevel.PROJECT, "directive:D")
                        .policy("org", level("directive:D", MutabilityLevel.LOCKED)),
                "directive:D",
                "locked",
                "project",
                "org");
    }

    private static void c21Extendable() {
        refuses(
                new Fixture()
                        .pack("org", PackLevel.ORG, "directive:D")
                        .pack("project", PackLevel.PROJECT, "directive:D")
                        .policy("org", level("directive:D", MutabilityLevel.EXTENDABLE)),
                "directive:D",
                "extendable");
    }

    private static void c21Open() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "directive:D")
                .pack("project", PackLevel.PROJECT, "directive:D")
                .policy("org", level("directive:D", MutabilityLevel.OPEN))
                .merge();

        assertThat(winner(merged, urn("directive:D")).packId()).isEqualTo(configured("project"));
    }

    private static void c21WinnerExcluded() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "directive:D")
                .pack("project", PackLevel.PROJECT, "directive:D")
                .policy("project", level("directive:D", MutabilityLevel.LOCKED))
                .merge();

        MergedClosure.Winner winner = winner(merged, urn("directive:D"));
        assertThat(winner.packId()).isEqualTo(configured("project"));
        assertThat(winner.policy().level())
                .as("the winner's own lock still shows as its level along the closure")
                .isEqualTo(MutabilityLevel.LOCKED);
    }

    private static void c21RaisedByAnotherPack() {
        refuses(
                new Fixture()
                        .pack("org", PackLevel.ORG, "directive:D")
                        .pack("org-guard", PackLevel.ORG)
                        .pack("project", PackLevel.PROJECT, "directive:D")
                        .policy("org-guard", level("directive:D", MutabilityLevel.LOCKED)),
                "directive:D",
                "locked",
                "org-guard");
    }

    private static void c22() {
        MergedClosure merged = new Fixture()
                .pack("builtin", PackLevel.BUILT_IN, "directive:D", "directive:E")
                .pack("org", PackLevel.ORG, "directive:D")
                .policy("builtin", level("directive:E", MutabilityLevel.LOCKED))
                .merge();

        assertThat(winner(merged, urn("directive:E")).policy())
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.LOCKED, Optional.of(packId("builtin"))));
        assertThat(winner(merged, urn("directive:D")).policy()).isEqualTo(PerPackPolicy.Level.OPEN);
    }

    private static Preset preset(String id, String... mustHave) {
        return new Preset(
                id,
                "a preset",
                Map.of(
                        ArtifactKind.DIRECTIVE,
                        Arrays.stream(mustHave).map(PresetItem::mustHave).toList()));
    }

    private static Preset convenience(String id, String directive) {
        return new Preset(id, "a preset", Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.of(directive))));
    }

    private static void c29() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "directive:A")
                .pack("project", PackLevel.PROJECT, "directive:B")
                .preset("org", preset("team", "A"))
                .preset("project", preset("team", "B"))
                .merge();

        assertThat(merged.presets()).containsOnlyKeys("team");
        assertThat(merged.presets().get("team").descriptorPackId()).isEqualTo(packId("project"));
        assertThat(merged.presets().get("team").preset()).isEqualTo(preset("team", "B"));
    }

    private static void c31Union() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "directive:A", "directive:B")
                .pack("project", PackLevel.PROJECT, "directive:C")
                .preset("org", preset("team", "A", "B"))
                .preset("project", preset("team", "C"))
                .merge();

        assertThat(merged.presets().get("team").mustHave())
                .containsExactly(urn("directive:A"), urn("directive:B"), urn("directive:C"));
        assertThat(merged.floor().requiredBy())
                .containsOnlyKeys(urn("directive:A"), urn("directive:B"), urn("directive:C"));
        assertThat(merged.floor().requiringPacks(urn("directive:A"))).containsExactly(packId("org"));
        assertThat(merged.floor().requiringPacks(urn("directive:C"))).containsExactly(packId("project"));
    }

    private static void c31Tighten() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "directive:A", "directive:D")
                .pack("project", PackLevel.PROJECT, "directive:B")
                .preset("org", preset("team", "A"))
                .preset("project", convenience("team", "D"))
                .merge();

        assertThat(merged.presets().get("team").preset()).isEqualTo(convenience("team", "D"));
        assertThat(merged.floor().requiredBy())
                .as("the winning copy requires nothing, and the org's requirement stays")
                .containsOnlyKeys(urn("directive:A"));
    }

    private static void c31Unheld() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "directive:A")
                .preset("org", preset("team", "A", "GONE"))
                .merge();

        assertThat(merged.floor().requiredBy()).containsOnlyKeys(urn("directive:A"));
        assertThat(merged.floor().unheld()).containsOnlyKeys(urn("directive:GONE"));
        assertThat(merged.presets().get("team").mustHave()).containsExactly(urn("directive:A"), urn("directive:GONE"));
    }

    private static void c31MissionType() {
        MergedClosure merged = new Fixture()
                .pack("org", PackLevel.ORG, "mission_type:M")
                .preset(
                        "org",
                        new Preset(
                                "team",
                                "a preset",
                                Map.of(ArtifactKind.MISSION_TYPE, List.of(PresetItem.mustHave("M")))))
                .merge();

        assertThat(merged.floor().requiredBy()).containsOnlyKeys(urn("mission_type:M"));
    }

    private static void c31SameLevel() {
        Fixture fixture = new Fixture()
                .pack("a-parent", PackLevel.ORG, "directive:A")
                .pack("b-child", PackLevel.ORG, "directive:B")
                .preset("a-parent", preset("team", "A"))
                .preset("b-child", preset("team", "B"));
        MergedClosure merged = fixture.merge();

        assertThat(merged.presets().get("team").preset()).isEqualTo(preset("team", "B"));
        assertThat(merged.presets().get("team").mustHave())
                .as("the replaced parent copy contributes no must-have item")
                .containsExactly(urn("directive:B"));
        assertThat(merged.floor().requiredBy()).containsOnlyKeys(urn("directive:B"));
        assertThat(fixture.floorOf().requiredBy())
                .as("floorOf feeds the writes and answers as the merge does")
                .containsOnlyKeys(urn("directive:B"));
    }

    private static void c31SameLevelBesideOtherLevels() {
        Fixture fixture = new Fixture()
                .pack("core", PackLevel.BUILT_IN, "directive:K")
                .pack("a-parent", PackLevel.ORG, "directive:A")
                .pack("b-child", PackLevel.ORG, "directive:B")
                .pack("project", PackLevel.PROJECT, "directive:C")
                .preset("core", preset("team", "K"))
                .preset("a-parent", preset("team", "A"))
                .preset("b-child", preset("team", "B"))
                .preset("project", preset("team", "C"));
        MergedClosure merged = fixture.merge();

        assertThat(merged.presets().get("team").mustHave())
                .containsExactly(urn("directive:K"), urn("directive:B"), urn("directive:C"));
        assertThat(merged.floor().requiredBy())
                .containsOnlyKeys(urn("directive:K"), urn("directive:B"), urn("directive:C"));
        assertThat(fixture.floorOf().requiredBy())
                .containsOnlyKeys(urn("directive:K"), urn("directive:B"), urn("directive:C"));
    }

    @Test
    void anEmptyClosureMergesToNothing() {
        assertThat(new Fixture().merge()).isEqualTo(MergedClosure.EMPTY);
    }
}
