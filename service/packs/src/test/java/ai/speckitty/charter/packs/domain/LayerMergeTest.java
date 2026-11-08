package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        private final Map<String, PackCompositionFacts> facts = new HashMap<>();
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
            facts.put(name, new PackCompositionFacts(List.of(), Arrays.asList(held), List.of()));
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
            return LayerMerge.merge(closure(), facts, new PerPackPolicy(policies));
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
                new Row("C17", "the level that applies wins, built-in then org then project", LayerMergeTest::c17),
                new Row("C17", "an id held once is its own winner, and replaces nothing", LayerMergeTest::c17Single),
                new Row("C18", "the winner replaces the others whole, per artifact", LayerMergeTest::c18),
                new Row("C15", "a settled sibling contest puts the named member in force", LayerMergeTest::c15),
                new Row(
                        "C15",
                        "a settled sibling loser is no layer override, the level winner is",
                        LayerMergeTest::c15BelowALayerWinner),
                new Row("C15", "an unsettled sibling contest still refuses in the compose", LayerMergeTest::c15Open),
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

    @Test
    void anEmptyClosureMergesToNothing() {
        assertThat(new Fixture().merge()).isEqualTo(MergedClosure.EMPTY);
    }
}
