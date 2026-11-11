package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusalOf;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The registration impact check of IC-15 (FR-025, FR-027) and the hand-off rulings WP14's review left to it. Every refusal
 * is paired with a positive control on the same fixture, and a refusal is shown to change nothing on disk.
 */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class RegistrationImpactTest {

    static final String BASE_ID = PackRegistrationServiceTest.ulid('E');
    static final String DERIVED_ID = PackRegistrationServiceTest.ulid('F');
    private static final String DEPENDS_ON_BASE = "depends_on:\n  - pack_id: " + BASE_ID + "\n    range: '>=1.0'\n";
    private static final String POLICY = "schema_version: 1\nartifacts:\n  directive:LOCKED_ONE: locked\n";

    /** The pack the others build on: it locks one of its directives. */
    static Packs base(String version) {
        return Packs.of(BASE_ID, "base", version, "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", POLICY)
                .directive("LOCKED_ONE", "Locked")
                .directive("OPEN_ONE", "Open");
    }

    static Packs derived(String version) {
        return Packs.of(DERIVED_ID, "derived", version, "project", DEPENDS_ON_BASE)
                .directive("DERIVED_ONE", "Own");
    }

    /** As {@link #base}, with the policy the gate scenarios need: one locked directive and one extendable. */
    static Packs basePolicy(String version) {
        return Packs.of(BASE_ID, "base", version, "org", "mutability_policy: policy.yaml\n")
                .file(
                        "policy.yaml",
                        "schema_version: 1\nartifacts:\n  directive:LOCKED_ONE: locked\n  directive:EXT_ONE: extendable\n")
                .directive("LOCKED_ONE", "Locked")
                .directive("EXT_ONE", "Extendable")
                .directive("OPEN_ONE", "Open");
    }

    // ---- q4b: a twin-declared id with one unreadable copy (WP14 review cycle 4) ------------------------------------

    @Test
    void anIdDeclaredByTwoFilesOneOfWhichCannotBeReadIsNeverKeptByteIdentical(@TempDir Path host) {
        Rig rig = new Rig(host.resolve("locked"));
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        String unreadableTwin = Packs.directiveText("LOCKED_ONE", "Mine") + "references:\n  - directive:X\n";
        rig.install(
                "derived",
                derived("1.0.0")
                        .file("directives/TWIN.directive.yaml", unreadableTwin)
                        .directive("LOCKED_ONE", "Mine"));
        Map<String, String> before = rig.filesOf(rig.packs.resolve("derived"));

        assertThat(refusalOf(() -> rig.register(
                        "derived", rig.stage(derived("2.0.0").directive("LOCKED_ONE", "Mine")), Preconditions.none())))
                .as("one copy of LOCKED_ONE cannot be read, so the readable copy is no proof the id is unchanged")
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.filesOf(rig.packs.resolve("derived")))
                .as("nothing changed")
                .isEqualTo(before);

        Rig control = new Rig(host.resolve("open"));
        control.register("base", control.stage(base("1.0.0")), Preconditions.none());
        control.install(
                "derived",
                derived("1.0.0")
                        .file(
                                "directives/TWIN.directive.yaml",
                                Packs.directiveText("OPEN_ONE", "Mine") + "references:\n  - directive:X\n")
                        .directive("OPEN_ONE", "Mine"));
        assertThat(control.register(
                                "derived",
                                control.stage(derived("2.0.0").directive("OPEN_ONE", "Mine")),
                                Preconditions.none())
                        .outcome())
                .as("the control: the same twins over an open id")
                .isEqualTo(Outcome.REPLACED);
    }

    // ---- the closures the pack joins (T070, FR-025) ----------------------------------------------------------------

    private static final String LEFT_ID = PackRegistrationServiceTest.ulid('G');
    private static final String RIGHT_ID = PackRegistrationServiceTest.ulid('H');
    private static final String TOP_ID = PackRegistrationServiceTest.ulid('J');
    private static final String LONE_ID = PackRegistrationServiceTest.ulid('K');
    private static final String RANGE_1 = "    range: '>=1.0,<2'\n";

    private static String dependsOn(String... ids) {
        StringBuilder text = new StringBuilder("depends_on:\n");
        for (String id : ids) {
            text.append("  - pack_id: ").append(id).append('\n').append(RANGE_1);
        }
        return text.toString();
    }

    private static Packs org(String id, String name, String version, String extra) {
        return Packs.of(id, name, version, "org", extra);
    }

    private static Packs orgBase(String version) {
        return org(BASE_ID, "base", version, "").directive("BASE_ONE", "Base");
    }

    /** Base under two packs that a top pack builds on: a diamond. */
    private static Rig diamond(Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none());
        rig.register("left", rig.stage(org(LEFT_ID, "left", "1.0.0", dependsOn(BASE_ID))), Preconditions.none());
        rig.register("right", rig.stage(org(RIGHT_ID, "right", "1.0.0", dependsOn(BASE_ID))), Preconditions.none());
        rig.register("top", rig.stage(org(TOP_ID, "top", "1.0.0", dependsOn(LEFT_ID, RIGHT_ID))), Preconditions.none());
        return rig;
    }

    @Test
    void everyClosureThatHoldsThePackIsComposedAndAnUnrelatedPackIsNot(@TempDir Path host) {
        Rig rig = diamond(host);
        rig.install("lone", org(LONE_ID, "lone", "1.0.0", dependsOn(PackRegistrationServiceTest.ulid('M'))));
        Map<String, String> before = rig.filesOf(rig.packs.resolve("base"));

        CharterRefusal refused = refusal(() -> rig.register("base", rig.stage(orgBase("2.0.0")), Preconditions.none()));

        assertThat(refused.code())
                .as("left, right and top each hold base, and 2.0.0 is outside their range")
                .isEqualTo(RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE);
        assertThat(refused.packs()).extracting(RefusalPack::packId).contains("base");
        assertThat(refused.getMessage()).contains("closure rooted at");
        assertThat(rig.filesOf(rig.packs.resolve("base"))).as("nothing changed").isEqualTo(before);

        assertThat(rig.register("base", rig.stage(orgBase("1.5.0")), Preconditions.none())
                        .outcome())
                .as("the control: in range. The lone pack depends on a pack nobody holds and is not composed")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aClosureThatDoesNotHoldThePackIsNotJudgedEvenWhenItIsBroken(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("lone", org(LONE_ID, "lone", "1.0.0", dependsOn(PackRegistrationServiceTest.ulid('M'))));

        assertThat(rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
    }

    private static final String SIBLING_A = PackRegistrationServiceTest.ulid('N');
    private static final String SIBLING_B = PackRegistrationServiceTest.ulid('P');

    private static Packs sibling(String id, String name, String title) {
        return org(id, name, "1.0.0", "").directive("SHARED_ONE", title);
    }

    @Test
    void siblingsThatOnlyConflictWhenOneActivationListsThemTogetherAreRefused(@TempDir Path host) {
        Rig rig = new Rig(host.resolve("together"));
        rig.register("sib-a", rig.stage(sibling(SIBLING_A, "sib-a", "Mine")), Preconditions.none());
        rig.activations.add(Optional.of(List.of("sib-a", "sib-b")));

        CharterRefusal refused = refusal(
                () -> rig.register("sib-b", rig.stage(sibling(SIBLING_B, "sib-b", "Yours")), Preconditions.none()));

        assertThat(refused.code())
                .as("each composes alone, and together they define one id with no overrides edge")
                .isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
        assertThat(rig.packs.resolve("sib-b")).as("nothing was created").doesNotExist();

        Rig apart = new Rig(host.resolve("apart"));
        apart.register("sib-a", apart.stage(sibling(SIBLING_A, "sib-a", "Mine")), Preconditions.none());
        apart.activations.add(Optional.of(List.of("sib-a")));
        apart.activations.add(Optional.of(List.of("sib-b")));
        assertThat(apart.register("sib-b", apart.stage(sibling(SIBLING_B, "sib-b", "Yours")), Preconditions.none())
                        .outcome())
                .as("the control: the same two packs listed apart")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void anActivationThatListsNoPacksRootsAtEveryConfiguredPack(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("sib-a", rig.stage(sibling(SIBLING_A, "sib-a", "Mine")), Preconditions.none());
        rig.activations.add(Optional.empty());

        assertThat(refusalOf(() ->
                        rig.register("sib-b", rig.stage(sibling(SIBLING_B, "sib-b", "Yours")), Preconditions.none())))
                .isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
    }

    @Test
    void anActivationThatNamesAPackNobodyConfiguresIsLeftOutNotRefusedForIt(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.activations.add(Optional.of(List.of("ghost")));

        assertThat(rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none())
                        .outcome())
                .as("that activation is already unresolved; this registration does not make it worse")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void eachPacksFactsAreReadOnceHoweverManyClosuresHoldTheReplacedPack(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register(
                "chain-0",
                rig.stage(org(ulid(0), "chain-0", "1.0.0", "").directive("D0", "Zero")),
                Preconditions.none());
        for (int i = 1; i < 10; i++) {
            rig.register(
                    "chain-" + i,
                    rig.stage(org(ulid(i), "chain-" + i, "1.0.0", dependsOn(ulid(i - 1)))
                            .directive("D" + i, "N" + i)),
                    Preconditions.none());
        }
        Map<String, Integer> factReads = new java.util.concurrent.ConcurrentHashMap<>();
        ai.speckitty.charter.packs.domain.ConfiguredPack first = rig.repository.configured().stream()
                .filter(pack -> pack.id().value().equals("chain-0"))
                .findFirst()
                .orElseThrow();
        RegistrationImpact.Replacement same = new RegistrationImpact.Replacement(
                first, rig.repository.get(first.id()), rig.repository.compositionFacts(first.id()));

        new RegistrationImpact(
                        new ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory(
                                rig.repository, rig.repository, pack -> {
                                    factReads.merge(pack.value(), 1, Integer::sum);
                                    return rig.repository.compositionFacts(pack);
                                }),
                        ActivationImpact.NONE)
                .check(same);

        assertThat(factReads)
                .as("ten closures hold chain-0, and no pack's files are read twice for them")
                .hasSize(10)
                .allSatisfy((pack, reads) -> assertThat(reads).as(pack).isEqualTo(1));
    }

    /** Crockford base32 has no I, L, O or U. */
    private static final String LETTERS = "ABCDEFGHJKMNPQRSTVWXYZ";

    private static String ulid(int index) {
        return PackRegistrationServiceTest.ulid(LETTERS.charAt(index));
    }

    // ---- compose failures name the closure root and the packs (T071) -----------------------------------------------

    @Test
    void aCompositionRefusalNamesTheClosureRootAndThePacksInvolved(@TempDir Path host) {
        Rig rig = diamond(host);

        CharterRefusal refused = refusal(() -> rig.register("base", rig.stage(orgBase("2.0.0")), Preconditions.none()));

        assertThat(refused.packs()).as("the packs of the refusal").isNotEmpty();
        assertThat(refused.packs().stream().map(RefusalPack::packId))
                .as("the dependent that no longer composes, and base")
                .contains("base")
                .anyMatch(id -> List.of("left", "right", "top").contains(id));
    }

    // ---- hand-off: a staged pack whose ancestor is not stored or cannot be read (WP14 review, p5b) ------------------

    @Test
    void aDependsOnThatNamesAPackThatIsNotStoredIsRefusedAndOneThatIsStoredIsNot(@TempDir Path host) {
        Rig rig = new Rig(host);

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(derived("1.0.0")), Preconditions.none())))
                .isEqualTo(RefusalCode.PACK_DEPENDENCY_UNKNOWN);
        assertThat(rig.packs.resolve("derived")).as("nothing was created").doesNotExist();

        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        assertThat(rig.register("derived", rig.stage(derived("1.0.0")), Preconditions.none())
                        .outcome())
                .as("the control: base is stored now")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aParentPackThatNamesAPackThatIsNotStoredIsRefusedAndOneThatIsStoredIsNot(@TempDir Path host) {
        Rig rig = new Rig(host);
        Packs child = Packs.of(DERIVED_ID, "derived", "1.0.0", "project", "parent_pack: " + BASE_ID + "\n");

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(child), Preconditions.none())))
                .isEqualTo(RefusalCode.PACK_PARENT_UNRESOLVED);

        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        assertThat(rig.register("derived", rig.stage(child), Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void anAncestorWhoseDescriptorCannotBeReadRefusesTheStagedPack(@TempDir Path host) {
        Rig rig = new Rig(host.resolve("broken"));
        rig.install("base", base("1.0.0").with("pack.yaml", "pack_id: [unclosed\n"));

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(derived("1.0.0")), Preconditions.none())))
                .as("base may be the pack that declares the id derived depends on")
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(rig.packs.resolve("derived")).doesNotExist();

        Rig control = new Rig(host.resolve("sound"));
        control.register("base", control.stage(base("1.0.0")), Preconditions.none());
        assertThat(control.register("derived", control.stage(derived("1.0.0")), Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void anAncestorWhoseFilesCannotBeReadRefusesTheStagedPack(@TempDir Path host) {
        Rig rig = new Rig(host.resolve("broken"));
        rig.install(
                "base",
                base("1.0.0")
                        .file(
                                "directives/OPEN_ONE.directive.yaml",
                                Packs.directiveText("OPEN_ONE", "Open") + "references:\n  - directive:X\n"));

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(derived("1.0.0")), Preconditions.none())))
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(rig.packs.resolve("derived")).doesNotExist();
    }

    @Test
    void droppingTheDependencyFreesThePackFromTheAncestorsLock(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        rig.register("derived", rig.stage(derived("1.0.0")), Preconditions.none());
        Packs claimsLocked = Packs.of(DERIVED_ID, "derived", "2.0.0", "project", DEPENDS_ON_BASE)
                .directive("LOCKED_ONE", "Mine");

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(claimsLocked), Preconditions.none())))
                .as("while derived builds on base, base's lock covers LOCKED_ONE")
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);

        Packs freed = Packs.of(DERIVED_ID, "derived", "2.0.0", "project").directive("LOCKED_ONE", "Mine");
        assertThat(rig.register("derived", rig.stage(freed), Preconditions.none())
                        .outcome())
                .as("judged along the new closure (ADR-0063 D3), which no longer holds base")
                .isEqualTo(Outcome.REPLACED);
    }

    // ---- relation refusals, each with a passing near-miss (T072: A6, A8, A11, A12, C28)
    // ------------------------------

    private static final String ACME_ID = PackRegistrationServiceTest.ulid('Q');

    static String graph(String... edges) {
        return "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n"
                + String.join(
                        "",
                        java.util.Arrays.stream(edges)
                                .map(edge -> "- " + edge + "\n")
                                .toList());
    }

    static String edge(String source, String relation, String target) {
        return "{source: 'directive:" + source + "', target: 'directive:" + target + "', relation: " + relation + "}";
    }

    static Packs acme(String... edges) {
        return Packs.of(ACME_ID, "acme", "1.0.0", "org")
                .directive("A", "A")
                .directive("B", "B")
                .directive("C", "C")
                .file("relations.graph.yaml", graph(edges));
    }

    record Relation(String name, RefusalCode code, List<String> refused, List<String> accepted) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Relation> relations() {
        return Stream.of(
                new Relation(
                        "an enhances cycle",
                        RefusalCode.ENHANCES_CYCLE,
                        List.of(edge("A", "enhances", "B"), edge("B", "enhances", "A")),
                        List.of(edge("B", "enhances", "A"))),
                new Relation(
                        "an enhancer with no route to a non-enhancer",
                        RefusalCode.ENHANCES_CYCLE,
                        List.of(edge("A", "enhances", "MISSING_ONE")),
                        List.of(edge("A", "enhances", "B"))),
                new Relation(
                        "an enhances chain that ends in an artifact that enhances nothing",
                        RefusalCode.ENHANCES_CYCLE,
                        List.of(edge("C", "enhances", "B"), edge("B", "enhances", "MISSING_ONE")),
                        List.of(edge("C", "enhances", "B"), edge("B", "enhances", "A"))),
                new Relation(
                        "an artifact that enhances and overrides one target",
                        RefusalCode.CONFLICTING_RELATIONS,
                        List.of(edge("B", "enhances", "A"), edge("B", "overrides", "A")),
                        List.of(edge("B", "enhances", "A"), edge("B", "overrides", "C"))),
                new Relation(
                        "an overrides cycle",
                        RefusalCode.OVERRIDE_CYCLE,
                        List.of(edge("A", "overrides", "B"), edge("B", "overrides", "C"), edge("C", "overrides", "A")),
                        List.of(edge("A", "overrides", "B"), edge("B", "overrides", "C"))));
    }

    @ParameterizedTest
    @MethodSource("relations")
    void aRelationThatBreaksARuleIsRefusedWithItsCodeAndTheNearMissComposes(Relation relation, @TempDir Path host) {
        Rig rig = new Rig(host);

        CharterRefusal refused = refusal(() ->
                rig.register("acme", rig.stage(acme(relation.refused().toArray(String[]::new))), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(relation.code());
        assertThat(refused.packs()).extracting(RefusalPack::packId).contains("acme");
        assertThat(refused.getMessage()).contains("acme");
        assertThat(rig.packs.resolve("acme")).as("nothing was created").doesNotExist();

        assertThat(rig.register(
                                "acme",
                                rig.stage(acme(relation.accepted().toArray(String[]::new))),
                                Preconditions.none())
                        .outcome())
                .as("the control: the near miss")
                .isEqualTo(Outcome.CREATED);
    }

    @ParameterizedTest
    @CsvSource({"overrides,SELF_OVERRIDE", "enhances,ENHANCES_CYCLE"})
    void anEdgeWhoseTargetResolvesToItsOwnSourceIsRefusedAndAnotherTargetIsNot(
            String relation, RefusalCode code, @TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        String shadow =
                "{source: 'base::directive:OPEN_ONE', target: 'base::directive:OPEN_ONE', relation: " + relation + "}";
        String other =
                "{source: 'base::directive:OPEN_ONE', target: 'directive:DERIVED_ONE', relation: " + relation + "}";

        assertThat(refusalOf(() -> rig.register(
                        "derived",
                        rig.stage(derived("1.0.0").file("relations.graph.yaml", graph(shadow))),
                        Preconditions.none())))
                .as("both ends name base's OPEN_ONE, so the target is the source, though derived writes the edge")
                .isEqualTo(code);
        assertThat(rig.packs.resolve("derived")).doesNotExist();

        assertThat(rig.register(
                                "derived",
                                rig.stage(derived("1.0.0").file("relations.graph.yaml", graph(other))),
                                Preconditions.none())
                        .outcome())
                .as("the control: the target is derived's own artifact")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aRelationOfAReplacedPackThatBreaksARuleIsRefusedAndChangesNothing(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("acme", rig.stage(acme(edge("B", "enhances", "A"))), Preconditions.none());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("acme"));
        Packs cycle = Packs.of(ACME_ID, "acme", "2.0.0", "org")
                .directive("A", "A")
                .directive("B", "B")
                .directive("C", "C")
                .file("relations.graph.yaml", graph(edge("B", "enhances", "A"), edge("A", "enhances", "B")));

        assertThat(refusalOf(() -> rig.register("acme", rig.stage(cycle), Preconditions.none())))
                .isEqualTo(RefusalCode.ENHANCES_CYCLE);
        assertThat(rig.filesOf(rig.packs.resolve("acme"))).isEqualTo(before);
    }

    @Test
    void anOverridesEdgeOntoAnArtifactOfALayerAboveIsNotASelfOverride(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        Packs overrides = derived("1.0.0")
                .directive("OPEN_ONE", "Mine")
                .file(
                        "relations.graph.yaml",
                        graph(
                                "{source: 'directive:OPEN_ONE', target: 'base::directive:OPEN_ONE', relation: overrides}"));

        assertThat(rig.register("derived", rig.stage(overrides), Preconditions.none())
                        .outcome())
                .as("the target is base's OPEN_ONE, a place other than the source's")
                .isEqualTo(Outcome.CREATED);
    }

    private static String overridesFrom(String target) {
        return graph(
                "{source: 'directive:DERIVED_ONE', target: 'base::directive:" + target + "', relation: overrides}");
    }

    @Test
    void anOverridesEdgeOntoALockedOrExtendableTargetIsRefusedNamingBothPacksAndAnOpenOneIsNot(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(basePolicy("1.0.0")), Preconditions.none());

        for (String target : List.of("LOCKED_ONE", "EXT_ONE")) {
            CharterRefusal refused = refusal(() -> rig.register(
                    "derived",
                    rig.stage(derived("1.0.0").file("relations.graph.yaml", overridesFrom(target))),
                    Preconditions.none()));
            assertThat(refused.code()).as(target).isEqualTo(RefusalCode.OVERRIDE_NOT_PERMITTED);
            assertThat(refused.packs())
                    .as("the pack that breaks the rule first, then the pack that holds the level")
                    .extracting(RefusalPack::packId)
                    .containsExactly("derived", "base");
        }
        assertThat(rig.packs.resolve("derived")).doesNotExist();

        assertThat(rig.register(
                                "derived",
                                rig.stage(derived("1.0.0").file("relations.graph.yaml", overridesFrom("OPEN_ONE"))),
                                Preconditions.none())
                        .outcome())
                .as("the control: the target is open")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aReplacedAncestorThatLocksATargetRefusesTheOverridesEdgeOfAStoredDescendant(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        rig.register(
                "derived",
                rig.stage(derived("1.0.0").file("relations.graph.yaml", overridesFrom("OPEN_ONE"))),
                Preconditions.none());
        Packs locksIt = org(BASE_ID, "base", "2.0.0", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", "schema_version: 1\nartifacts:\n  directive:OPEN_ONE: locked\n")
                .directive("LOCKED_ONE", "Locked")
                .directive("OPEN_ONE", "Open");
        Map<String, String> before = rig.filesOf(rig.packs.resolve("base"));

        CharterRefusal refused = refusal(() -> rig.register("base", rig.stage(locksIt), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.OVERRIDE_NOT_PERMITTED);
        assertThat(refused.packs()).extracting(RefusalPack::packId).contains("derived", "base");
        assertThat(rig.filesOf(rig.packs.resolve("base"))).as("nothing changed").isEqualTo(before);
    }

    @Test
    void aLockOfAPackThatDoesNotBuildOnTheHolderIsOutOfScope(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        Packs bystander = Packs.of(LONE_ID, "bystander", "1.0.0", "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", "schema_version: 1\nartifacts:\n  directive:OPEN_ONE: locked\n")
                .directive("OTHER_ONE", "Other");
        rig.register("bystander", rig.stage(bystander), Preconditions.none());
        rig.activations.add(Optional.of(List.of("derived", "bystander")));

        assertThat(rig.register(
                                "derived",
                                rig.stage(derived("1.0.0").file("relations.graph.yaml", overridesFrom("OPEN_ONE"))),
                                Preconditions.none())
                        .outcome())
                .as(
                        "bystander shares the activation's closure but neither holds OPEN_ONE nor builds on a pack that does")
                .isEqualTo(Outcome.CREATED);
    }

    // ---- notices (T073, FR-027, B4) ---------------------------------------------------------------------------------

    private static final String LOCK_X = "schema_version: 1\nartifacts:\n  directive:X_ONE: locked\n";
    private static final String REQUIRE_X = """
            schema_version: "1.0"
            id: starter
            description: A starter.
            activated_directives:
              - id: X_ONE
                must_have: true
            """;

    private static Packs noticing(String version, boolean locked, boolean required) {
        Packs pack = Packs.of(ACME_ID, "acme", version, "org", locked ? "mutability_policy: policy.yaml\n" : "")
                .directive("X_ONE", "X");
        if (locked) {
            pack.file("policy.yaml", LOCK_X);
        }
        if (required) {
            pack.file("presets/starter.yaml", REQUIRE_X);
        }
        return pack;
    }

    @Test
    void theNoticesListTheFloorAndLocksAPackAddedAndRemoved(@TempDir Path host) {
        Rig rig = new Rig(host);

        PackRegistrationService.RegistrationOutcome created =
                rig.register("acme", rig.stage(noticing("1.0.0", true, true)), Preconditions.none());
        assertThat(created.notices().locksAdded()).containsExactly("directive:X_ONE");
        assertThat(created.notices().floorAdded()).containsExactly("directive:X_ONE");
        assertThat(created.notices().locksRemoved()).isEmpty();
        assertThat(created.notices().floorRemoved()).isEmpty();

        PackRegistrationService.RegistrationOutcome relaxed =
                rig.register("acme", rig.stage(noticing("2.0.0", false, false)), Preconditions.none());
        assertThat(relaxed.notices().locksRemoved()).containsExactly("directive:X_ONE");
        assertThat(relaxed.notices().floorRemoved()).containsExactly("directive:X_ONE");
        assertThat(relaxed.notices().locksAdded()).isEmpty();
        assertThat(relaxed.notices().floorAdded()).isEmpty();
    }

    @Test
    void aRegistrationThatChangesNeitherReportsNothing(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("acme", rig.stage(noticing("1.0.0", true, true)), Preconditions.none());

        PackRegistrationService.RegistrationOutcome same =
                rig.register("acme", rig.stage(noticing("1.0.0", true, true)), Preconditions.none());
        PackRegistrationService.RegistrationOutcome renamed =
                rig.register("acme", rig.stage(noticing("1.0.1", true, true)), Preconditions.none());

        assertThat(same.notices()).isEqualTo(RegistrationImpact.Notices.NONE);
        assertThat(renamed.notices()).isEqualTo(RegistrationImpact.Notices.NONE);
    }

    private static final String GOVERNING_X = governing("X_ONE");
    private static final String GOVERNING_Y = governing("Y_ONE");

    private static String governing(String id) {
        return "schema_version: \"1.0\"\nid: governing\ndescription: The governing preset.\n"
                + "activated_directives:\n  - id: " + id + "\n    must_have: true\n";
    }

    private static Packs floorBase() {
        return Packs.of(BASE_ID, "base", "1.0.0", "org")
                .directive("X_ONE", "X")
                .file("presets/governing.yaml", GOVERNING_X);
    }

    private static Packs floorTop(String version, boolean governs) {
        Packs top =
                Packs.of(DERIVED_ID, "top", version, "project", DEPENDS_ON_BASE).directive("Y_ONE", "Y");
        return governs ? top.file("presets/governing.yaml", GOVERNING_Y) : top;
    }

    @Test
    void aLaterCopyOfAGoverningPresetAddsToTheFloorAndRemovesNothingOfTheEarlierOne(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(floorBase()), Preconditions.none());
        rig.register("top", rig.stage(floorTop("1.0.0", false)), Preconditions.none());

        PackRegistrationService.RegistrationOutcome governed =
                rig.register("top", rig.stage(floorTop("2.0.0", true)), Preconditions.none());

        assertThat(governed.notices().floorAdded())
                .as("the union of the copies is required (A1), so Y_ONE joins the floor")
                .containsExactly("directive:Y_ONE");
        assertThat(governed.notices().floorRemoved())
                .as("X_ONE stays required by the built-in copy, whichever copy wins")
                .isEmpty();
    }

    @Test
    void droppingTheLaterCopyOfAGoverningPresetRemovesOnlyItsOwnItems(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(floorBase()), Preconditions.none());
        rig.register("top", rig.stage(floorTop("1.0.0", true)), Preconditions.none());

        PackRegistrationService.RegistrationOutcome dropped =
                rig.register("top", rig.stage(floorTop("2.0.0", false)), Preconditions.none());

        assertThat(dropped.notices().floorRemoved()).containsExactly("directive:Y_ONE");
        assertThat(dropped.notices().floorAdded()).isEmpty();
    }

    @Test
    void theActivationsSliceIsToldOfAStoredRegistrationAndOfNoOtherOne(@TempDir Path host) {
        Rig rig = new Rig(host);

        rig.register("acme", rig.stage(noticing("1.0.0", true, true)), Preconditions.none());
        rig.register("acme", rig.stage(noticing("1.0.0", true, true)), Preconditions.none());
        refusalOf(() -> rig.register("acme", rig.stage(noticing("1.0.0", false, false)), Preconditions.none()));

        assertThat(rig.registered).containsExactly("acme");
    }

    // ---- the census of stored packs (T075)
    // ---------------------------------------------------------------------------

    private static final String CYCLE = graph(edge("A", "enhances", "B"), edge("B", "enhances", "A"));

    private static Packs storedAcme(String relations) {
        return Packs.of(ACME_ID, "acme", "1.0.0", "org")
                .directive("A", "A")
                .directive("B", "B")
                .file("relations.graph.yaml", relations);
    }

    @Test
    void theCensusNamesEachStoredPackThatWouldNowBeRefusedAndNoOther(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("acme", storedAcme(CYCLE));
        rig.install("base", base("1.0.0"));

        List<RegistrationImpact.Finding> findings = rig.impact.census();

        assertThat(findings)
                .extracting(RegistrationImpact.Finding::pack, RegistrationImpact.Finding::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("acme", RefusalCode.ENHANCES_CYCLE));
        assertThat(findings.getFirst().detail()).contains("acme");
        assertThat(rig.filesOf(rig.packs.resolve("acme")))
                .as("the census reads and writes nothing")
                .containsKey("relations.graph.yaml");
    }

    @Test
    void theCensusFindsNothingInSoundPacks(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("acme", storedAcme(graph(edge("B", "enhances", "A"))));

        assertThat(rig.impact.census()).isEmpty();
    }

    @Test
    void aPackThatCannotBeComposedIsAFindingToo(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("derived", derived("1.0.0"));

        assertThat(rig.impact.census())
                .extracting(RegistrationImpact.Finding::pack, RegistrationImpact.Finding::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("derived", RefusalCode.PACK_DEPENDENCY_UNKNOWN));
    }

    @Test
    void theStartupLogWarnsOnceForEachFindingAndRefusesNothing(
            @TempDir Path host, org.springframework.boot.test.system.CapturedOutput output) {
        Rig rig = new Rig(host);
        rig.install("acme", storedAcme(CYCLE));

        rig.impact.logCensus();

        assertThat(output.getAll()).contains("WARN").contains("acme").contains("enhances_cycle");
        assertThat(rig.filesOf(rig.packs.resolve("acme"))).containsKey("relations.graph.yaml");
    }

    // ---- C21: an id an ancestor holds is a replace of the ancestor's artifact (decision 01M6VEDYX8TQ4AKCP7C19WAP75)
    // ----

    @Test
    void creatingAPackThatHoldsAnExtendableIdOfItsAncestorIsRefusedAndAnOpenIdIsNot(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(basePolicy("1.0.0")), Preconditions.none());

        assertThat(refusalOf(() -> rig.register(
                        "derived", rig.stage(derived("1.0.0").directive("EXT_ONE", "Mine")), Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(refusalOf(() -> rig.register(
                        "derived", rig.stage(derived("1.0.0").directive("LOCKED_ONE", "Mine")), Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.packs.resolve("derived")).doesNotExist();

        assertThat(rig.register(
                                "derived",
                                rig.stage(derived("1.0.0").directive("OPEN_ONE", "Mine")),
                                Preconditions.none())
                        .outcome())
                .as("the control: an open id may be replaced by a pack built on it")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aReplaceThatAddsAnExtendableIdOfTheAncestorIsRefusedAndAnOpenIdIsNot(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(basePolicy("1.0.0")), Preconditions.none());
        rig.register("derived", rig.stage(derived("1.0.0")), Preconditions.none());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("derived"));

        assertThat(refusalOf(() -> rig.register(
                        "derived", rig.stage(derived("2.0.0").directive("EXT_ONE", "Mine")), Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.filesOf(rig.packs.resolve("derived")))
                .as("nothing changed")
                .isEqualTo(before);

        assertThat(rig.register(
                                "derived",
                                rig.stage(derived("2.0.0").directive("OPEN_ONE", "Mine")),
                                Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void thePerArtifactPutOfAnExtendableIdOfTheAncestorIsRefusedAndOfAnOpenIdIsNot(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(basePolicy("1.0.0")), Preconditions.none());
        rig.register("derived", rig.stage(derived("1.0.0")), Preconditions.none());
        ConsistentRead read = new ConsistentRead(
                new ai.speckitty.charter.packs.infra.storage.FileSystemPackSources(rig.locations::all), rig.generation);
        PackMutabilityPolicy gate = new PackMutabilityPolicy(
                new OfferingReadService(rig.repository, rig.repository, rig.repository, read),
                rig.repository,
                rig.repository,
                read);
        DirectiveRegistrationService puts = new DirectiveRegistrationService(rig.repository, rig.writeStore, gate);
        ai.speckitty.charter.kernel.domain.PackIdentifier derivedPack =
                new ai.speckitty.charter.kernel.domain.PackIdentifier("derived");

        assertThat(refusalOf(() -> puts.registerDirective(derivedPack, directiveOf("EXT_ONE"), false, null)))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.filesOf(rig.packs.resolve("derived"))).doesNotContainKey("EXT_ONE.directive.yaml");

        puts.registerDirective(derivedPack, directiveOf("OPEN_ONE"), false, null);
        assertThat(rig.filesOf(rig.packs.resolve("derived"))).containsKey("OPEN_ONE.directive.yaml");
    }

    private static ai.speckitty.charter.packs.domain.Directive directiveOf(String id) {
        return ai.speckitty.charter.packs.domain.Directive.builder()
                .id(new ai.speckitty.charter.packs.domain.DirectiveIdentifier(id))
                .packId(new ai.speckitty.charter.kernel.domain.PackIdentifier("derived"))
                .schemaVersion("1.0")
                .title(id)
                .intent("Intent of " + id)
                .enforcement(ai.speckitty.charter.packs.domain.Enforcement.REQUIRED)
                .build();
    }

    // ---- the cost of the check (cycle 2, issue 3)
    // --------------------------------------------------------------------

    /** Counts the descriptors a compose asks the views for, which is what each compose judges again. */
    private static final class CountingViews implements StagedPackViews {
        private final StagedPackViews real;
        final java.util.concurrent.atomic.AtomicInteger descriptorAsks =
                new java.util.concurrent.atomic.AtomicInteger();

        CountingViews(StagedPackViews real) {
            this.real = real;
        }

        @Override
        public Session session() {
            Session session = real.session();
            return new Session() {
                @Override
                public View stored() {
                    return counted(session.stored());
                }

                @Override
                public View with(RegistrationImpact.Replacement written) {
                    return counted(session.with(written));
                }
            };
        }

        private View counted(View view) {
            ai.speckitty.charter.packs.domain.PackDescriptorRepository counting =
                    new ai.speckitty.charter.packs.domain.PackDescriptorRepository() {
                        @Override
                        public ai.speckitty.charter.kernel.domain.PackDescriptor get(
                                ai.speckitty.charter.kernel.domain.PackIdentifier pack) {
                            return view.descriptors().get(pack);
                        }

                        @Override
                        public Optional<ai.speckitty.charter.kernel.domain.PackDescriptor> find(
                                ai.speckitty.charter.kernel.domain.PackIdentifier pack) {
                            descriptorAsks.incrementAndGet();
                            return view.descriptors().find(pack);
                        }

                        @Override
                        public void save(
                                ai.speckitty.charter.kernel.domain.PackIdentifier pack,
                                ai.speckitty.charter.kernel.domain.PackDescriptor descriptor) {
                            view.descriptors().save(pack, descriptor);
                        }
                    };
            return new View(view.catalog(), counting, view.artifacts());
        }
    }

    /** Asks the check about a base pack with {@code dependents} packs built on it and answers how often descriptors were asked for. */
    private static int descriptorAsks(Path host, int dependents) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none());
        for (int i = 1; i <= dependents; i++) {
            rig.install(
                    "dep-" + i,
                    org(
                                    ulid(i % 22).substring(0, 16) + String.format("%010d", i),
                                    "dep-" + i,
                                    "1.0.0",
                                    dependsOn(BASE_ID))
                            .directive("DEP_" + i, "Own"));
        }
        CountingViews views = new CountingViews(new ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory(
                rig.repository, rig.repository, rig.repository));
        ai.speckitty.charter.packs.domain.ConfiguredPack configured = rig.repository.configured().stream()
                .filter(pack -> pack.id().value().equals("base"))
                .findFirst()
                .orElseThrow();
        RegistrationImpact.Replacement same = new RegistrationImpact.Replacement(
                configured, rig.repository.get(configured.id()), rig.repository.compositionFacts(configured.id()));

        new RegistrationImpact(views, ActivationImpact.NONE).check(same);

        return views.descriptorAsks.get();
    }

    @Test
    void theDescriptorsAreAskedForOncePerViewHoweverManyClosuresHoldThePack(@TempDir Path host) {
        int packs = 51;
        int asks = descriptorAsks(host, 50);

        assertThat(asks)
                .as(
                        "51 closures hold base; each pack is read and judged once per view (roots, before, after), not once per closure")
                .isLessThanOrEqualTo(4 * packs);
    }

    @Test
    void theConfiguredLocationsAreListedOnceHoweverManyPacksTheCheckReads(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none());
        for (int i = 1; i <= 50; i++) {
            rig.install(
                    "dep-" + i,
                    org(
                                    ulid(i % 22).substring(0, 16) + String.format("%010d", i),
                                    "dep-" + i,
                                    "1.0.0",
                                    dependsOn(BASE_ID))
                            .directive("DEP_" + i, "Own"));
        }
        java.util.concurrent.atomic.AtomicInteger listings = new java.util.concurrent.atomic.AtomicInteger();
        ai.speckitty.charter.packs.infra.storage.Locations counting = () -> {
            listings.incrementAndGet();
            return rig.locations.all();
        };
        ai.speckitty.charter.packs.domain.ConfiguredPack base = rig.repository.configured().stream()
                .filter(pack -> pack.id().value().equals("base"))
                .findFirst()
                .orElseThrow();
        RegistrationImpact.Replacement same = new RegistrationImpact.Replacement(
                base, rig.repository.get(base.id()), rig.repository.compositionFacts(base.id()));
        RegistrationImpact impact = new RegistrationImpact(
                new ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory(
                        counting, new ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy()),
                ActivationImpact.NONE);

        impact.check(same);

        assertThat(listings.get())
                .as("each listing parses every pack.yaml, so one per pack read is a square; the session lists once")
                .isLessThanOrEqualTo(2);
    }
}
