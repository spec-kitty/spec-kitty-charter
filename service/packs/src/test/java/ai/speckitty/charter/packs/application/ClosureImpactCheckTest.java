package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusalOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ImpactCheck;
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
class ClosureImpactCheckTest {

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
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).contains("base");
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

    // ---- #358: only the distribution pack settles a sibling contest, on the whole-pack write ---------------------

    private static final String DIST_ID = PackRegistrationServiceTest.ulid('Q');
    private static final String DIST_TWO_ID = PackRegistrationServiceTest.ulid('R');
    private static final String MEMBER_ID = PackRegistrationServiceTest.ulid('S');

    private static String settles(String member) {
        return "overrides:\n  - artifact: directive:SHARED_ONE\n    member: " + member + "\n";
    }

    /** The two siblings installed, contesting {@code directive:SHARED_ONE}, and one activation over {@code roots}. */
    private static Rig contested(Path host, String... roots) {
        Rig rig = new Rig(host);
        rig.install("sib-a", sibling(SIBLING_A, "sib-a", "Mine"));
        rig.install("sib-b", sibling(SIBLING_B, "sib-b", "Yours"));
        rig.activations.add(Optional.of(List.of(roots)));
        return rig;
    }

    @Test
    void theDistributionPackThatDependsOnBothSiblingsSettlesTheContestOnWrite(@TempDir Path host) {
        Rig rig = contested(host, "dist");

        assertThat(rig.register(
                                "dist",
                                rig.stage(org(
                                        DIST_ID,
                                        "dist",
                                        "1.0.0",
                                        dependsOn(SIBLING_A, SIBLING_B) + settles(SIBLING_A))),
                                Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aSiblingDoesNotSettleItsOwnContestOnWrite(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("sib-a", sibling(SIBLING_A, "sib-a", "Mine"));
        rig.activations.add(Optional.of(List.of("sib-a", "sib-b")));

        CharterRefusal refused = refusal(() -> rig.register(
                "sib-b",
                rig.stage(org(SIBLING_B, "sib-b", "1.0.0", settles(SIBLING_B)).directive("SHARED_ONE", "Yours")),
                Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
        assertThat(refused.getMessage()).contains("declared by pack `sib-b`", "which is not the distribution pack");
        assertThat(rig.packs.resolve("sib-b")).as("nothing was created").doesNotExist();
    }

    @Test
    void aMemberOfTheDistributionDoesNotSettleTheContestOnWrite(@TempDir Path host) {
        Rig rig = contested(host, "dist");
        rig.install("dist", org(DIST_ID, "dist", "1.0.0", dependsOn(SIBLING_A, SIBLING_B, MEMBER_ID)));

        CharterRefusal refused = refusal(() -> rig.register(
                "member", rig.stage(org(MEMBER_ID, "member", "1.0.0", settles(SIBLING_A))), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
        assertThat(refused.getMessage()).contains("declared by pack `member`", "which is not the distribution pack");
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).contains("member");
        assertThat(rig.packs.resolve("member")).as("nothing was created").doesNotExist();
    }

    @Test
    void twoRootsSettlingOneArtifactAreRefusedOnWrite(@TempDir Path host) {
        Rig rig = contested(host, "dist", "dist-two");
        rig.install("dist", org(DIST_ID, "dist", "1.0.0", dependsOn(SIBLING_A, SIBLING_B) + settles(SIBLING_A)));

        CharterRefusal refused = refusal(() -> rig.register(
                "dist-two",
                rig.stage(org(DIST_TWO_ID, "dist-two", "1.0.0", dependsOn(SIBLING_A, SIBLING_B) + settles(SIBLING_A))),
                Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
        assertThat(refused.getMessage()).contains("more than one distribution pack");
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).contains("dist", "dist-two");
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
        ImpactCheck.Replacement same = new ImpactCheck.Replacement(
                first, rig.repository.get(first.id()), rig.repository.compositionFacts(first.id()));

        new ClosureImpactCheck(
                        new ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory(
                                rig.repository, rig.repository, pack -> {
                                    factReads.merge(pack.value(), 1, Integer::sum);
                                    return rig.repository.compositionFacts(pack);
                                }),
                        ActivationImpact.NONE)
                .judge(new ImpactCheck.Registration(same));

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
        assertThat(refused.packs().stream().map(pack -> pack.packId().value()))
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
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).contains("acme");
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

    // ---- the edge-index rules on the write path (#639, ADR-0082 slice 4, rulings D2, Q-B, Q-C; #667) --------------

    /** Derived holds base's OPEN_ONE, a layer override, and writes an overrides edge from it onto base's copy. */
    private static Packs overridingItsOwnLayerOverride(String extraDescriptor) {
        return Packs.of(DERIVED_ID, "derived", "1.0.0", "project", DEPENDS_ON_BASE + extraDescriptor)
                .directive("DERIVED_ONE", "Own")
                .directive("OPEN_ONE", "Mine")
                .file(
                        "relations.graph.yaml",
                        graph(
                                "{source: 'directive:OPEN_ONE', target: 'base::directive:OPEN_ONE', relation: overrides}"));
    }

    @Test
    void anOverridesEdgeOntoTheCopyItsSourceReplacesIsASelfOverrideOnceTheMergeRePointsIt(@TempDir Path host) {
        // Ruling D2 (C36, A12): the merge re-points the edge to derived's own OPEN_ONE, the version in force, so the
        // edge ends at its own source. The read refuses it self_override, and so does the registration now.
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());

        CharterRefusal refused = refusal(
                () -> rig.register("derived", rig.stage(overridingItsOwnLayerOverride("")), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.SELF_OVERRIDE);
        assertThat(refused.getMessage())
                .isEqualTo("In the closure rooted at derived: pack `derived` writes the overrides edge"
                        + " `directive:OPEN_ONE` -> `derived::directive:OPEN_ONE`, which resolves to its own source"
                        + " in the merged closure");
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).containsExactly("derived");
        assertThat(rig.packs.resolve("derived")).as("nothing was created").doesNotExist();

        assertThat(rig.register(
                                "derived",
                                rig.stage(derived("1.0.0").file("relations.graph.yaml", overridesFrom("OPEN_ONE"))),
                                Preconditions.none())
                        .outcome())
                .as("the control: the edge starts at derived's own DERIVED_ONE, which replaces nothing")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aRegistrationWhoseClosureDeclaresAFallbackAndARePointedSelfOverrideAnswersTheSelfOverride(@TempDir Path host) {
        // #667: the fallback rule reads the winner of its profile, which runs the merge. The edge-index rules judge the
        // merge first, so the registration answers the fault's code, never the merge's pack_unreadable.
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        Packs fallingBack = overridingItsOwnLayerOverride(
                        "mutability_policy: policy.yaml\nfallback_profile: generic-agent\n")
                .file("policy.yaml", "schema_version: 1\nartifacts:\n  agent_profile:generic-agent: locked\n")
                .file("agent_profiles/generic-agent.agent.yaml", """
                        profile-id: generic-agent
                        name: Generic agent
                        role: generic
                        purpose: Serve when no profile fits.
                        specialization:
                          primary-focus: Any work
                        """);

        CharterRefusal refused = refusal(() -> rig.register("derived", rig.stage(fallingBack), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.SELF_OVERRIDE);
        assertThat(rig.packs.resolve("derived")).as("nothing was created").doesNotExist();
    }

    private static final String RX_BASE_ID = PackRegistrationServiceTest.ulid('0');
    private static final String RX_EXT_ID = PackRegistrationServiceTest.ulid('1');

    /** A pack whose mission type rx has the one step a, and whose graph declares the action rx/c besides. */
    private static Packs rxBase() {
        return Packs.of(RX_BASE_ID, "rxbase", "1.0.0", "org")
                .file("missions/rx/rx.mission-type.yaml", """
                        schema_version: '1.0'
                        id: rx
                        title: Rx
                        description: A mission type.
                        steps:
                        - id: a
                          title: A
                        """)
                .file("mission_type.graph.yaml", """
                        schema_version: '1.0'
                        generated_at: STATIC
                        nodes:
                        - {urn: 'action:rx/a', kind: action, label: a}
                        - {urn: 'action:rx/c', kind: action, label: c}
                        edges:
                        - {source: 'mission_type:rx', target: 'action:rx/a', relation: requires}
                        """);
    }

    /** A pack that links rxbase's mission type to one of its actions. */
    private static Packs rxExt(String action) {
        return Packs.of(
                        RX_EXT_ID,
                        "rxext",
                        "1.0.0",
                        "org",
                        "depends_on:\n  - pack_id: " + RX_BASE_ID + "\n    range: '>=1.0'\n")
                .file(
                        "extra.graph.yaml",
                        graph("{source: 'rxbase::mission_type:rx', target: 'rxbase::action:rx/" + action
                                + "', relation: requires}"));
    }

    @Test
    void anotherPacksStepLinkToAnActionThatIsNoStepOfTheTypeIsRefusedAsTheReadRefusesIt(@TempDir Path host) {
        // Ruling Q-C: the stray link is the reference's fault, with the violation mission_step_unlinked.
        Rig rig = new Rig(host);
        rig.register("rxbase", rig.stage(rxBase()), Preconditions.none());

        CharterRefusal refused = refusal(() -> rig.register("rxext", rig.stage(rxExt("c")), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
        assertThat(refused.getMessage())
                .startsWith("In the closure rooted at rxext: ")
                .contains("mission_type:rx", "action:rx/c", "names no step of the type");
        assertThat(refused.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.code()).isEqualTo("mission_step_unlinked");
            assertThat(violation.detail()).contains("mission_type:rx", "action:rx/c");
        });
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).containsExactly("rxbase", "rxext");
        assertThat(rig.packs.resolve("rxext")).as("nothing was created").doesNotExist();

        assertThat(rig.register("rxext", rig.stage(rxExt("a")), Preconditions.none())
                        .outcome())
                .as("the control: the link names a step of the type")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aReplacedPackThatLocksAnArtifactALaterLevelReplacesIsRefusedForTheReplacingPacksClosure(@TempDir Path host) {
        // Ruling Q-B (C21): only an open artifact may be replaced by a later level, on the write as on the read.
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        rig.register("derived", rig.stage(derived("1.0.0").directive("OPEN_ONE", "Mine")), Preconditions.none());
        Packs locking = Packs.of(BASE_ID, "base", "2.0.0", "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", "schema_version: 1\nartifacts:\n  directive:OPEN_ONE: locked\n")
                .directive("LOCKED_ONE", "Locked")
                .directive("OPEN_ONE", "Open");
        Map<String, String> before = rig.filesOf(rig.packs.resolve("base"));

        CharterRefusal refused = refusal(() -> rig.register("base", rig.stage(locking), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.OVERRIDE_NOT_PERMITTED);
        assertThat(refused.getMessage())
                .startsWith("In the closure rooted at derived: ")
                .contains("replaces `directive:OPEN_ONE`", "locked", "only an open artifact may be replaced");
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).containsExactly("derived", "base");
        assertThat(rig.filesOf(rig.packs.resolve("base"))).as("nothing changed").isEqualTo(before);

        assertThat(rig.register(
                                "base",
                                rig.stage(Packs.of(BASE_ID, "base", "2.0.0", "org", "mutability_policy: policy.yaml\n")
                                        .file("policy.yaml", POLICY)
                                        .directive("LOCKED_ONE", "Locked")
                                        .directive("OPEN_ONE", "Open")),
                                Preconditions.none())
                        .outcome())
                .as("the control: OPEN_ONE stays open")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aClosureThatBreaksARelationRuleAndAnEdgeIndexRuleIsRefusedForTheRelationFaultAlone(@TempDir Path host) {
        // FR-009: the relations gate the edge index, so a client reads the root cause and not its knock-on fault.
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        Packs mixed = Packs.of(DERIVED_ID, "derived", "1.0.0", "project", DEPENDS_ON_BASE)
                .directive("DERIVED_ONE", "Own")
                .directive("SECOND_ONE", "Second")
                .directive("OPEN_ONE", "Mine")
                .file(
                        "relations.graph.yaml",
                        graph(
                                "{source: 'directive:OPEN_ONE', target: 'base::directive:OPEN_ONE', relation: overrides}",
                                edge("DERIVED_ONE", "enhances", "SECOND_ONE"),
                                edge("SECOND_ONE", "enhances", "DERIVED_ONE")));

        CharterRefusal refused = refusal(() -> rig.register("derived", rig.stage(mixed), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.ENHANCES_CYCLE);
        assertThat(refused.violations()).as("one fault, the relation rule's").isEmpty();
        assertThat(refused.getMessage())
                .startsWith("In the closure rooted at derived: ")
                .doesNotContain("self");
    }

    @Test
    void theCensusReportsTheRelationFaultAloneOfAClosureThatBreaksBoth(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("base", base("1.0.0"));
        rig.install(
                "derived",
                overridingItsOwnLayerOverride("")
                        .directive("SECOND_ONE", "Second")
                        .file(
                                "cycle.graph.yaml",
                                graph(
                                        edge("DERIVED_ONE", "enhances", "SECOND_ONE"),
                                        edge("SECOND_ONE", "enhances", "DERIVED_ONE"))));

        assertThat(rig.impact.census()).singleElement().satisfies(finding -> {
            assertThat(finding.pack()).isEqualTo("derived");
            assertThat(finding.code()).isEqualTo(RefusalCode.ENHANCES_CYCLE);
            assertThat(finding.refusal().violations())
                    .as("one fault, the relation rule's")
                    .isEmpty();
            assertThat(finding.detail()).endsWith(" (packs derived)");
        });
    }

    @Test
    void theCensusNamesAnEdgeIndexFaultWithItsOwnCodeAndThePacksItNames(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("rxbase", rxBase());
        rig.install("rxext", rxExt("c"));

        assertThat(rig.impact.census()).singleElement().satisfies(finding -> {
            assertThat(finding.pack()).isEqualTo("rxext");
            assertThat(finding.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
            assertThat(finding.detail()).contains("names no step of the type").endsWith(" (packs rxbase)");
            assertThat(finding.refusal().violations())
                    .extracting(RefusalViolation::code)
                    .containsExactly("mission_step_unlinked");
        });
    }

    @Test
    void aQualifiedEndpointOutsideTheWritersClosureIsTheComposesReferenceFaultBeforeAnyEdgeIndex(@TempDir Path host) {
        // T020: every endpoint an edge writes is a reference of its pack's facts, so the compose's references check
        // refuses a qualified endpoint outside the writer's depends_on closure, and the edge index never meets one.
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        Packs outside = Packs.of(DERIVED_ID, "derived", "1.0.0", "project")
                .directive("DERIVED_ONE", "Own")
                .file("relations.graph.yaml", overridesFrom("OPEN_ONE"));

        CharterRefusal refused = refusal(() -> rig.register("derived", rig.stage(outside), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_PACK_MISSING);
        assertThat(refused.getMessage()).contains("base::directive:OPEN_ONE");
        assertThat(rig.register(
                                "derived",
                                rig.stage(derived("1.0.0").file("relations.graph.yaml", overridesFrom("OPEN_ONE"))),
                                Preconditions.none())
                        .outcome())
                .as("the control: derived depends on base")
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
                    .extracting(pack -> pack.packId().value())
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
    void aReplacementThatBreaksTwoClosuresIsOneRefusalNamingTheFaultsOfBoth(@TempDir Path host) {
        // #556, FR-003, FR-009, US1.3: every closure a write joins is judged, and the refusal names every fault.
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        rig.register(
                "derived",
                rig.stage(derived("1.0.0").file("relations.graph.yaml", overridesFrom("OPEN_ONE"))),
                Preconditions.none());
        rig.register(
                "second",
                rig.stage(Packs.of(PackRegistrationServiceTest.ulid('W'), "second", "1.0.0", "project", DEPENDS_ON_BASE)
                        .directive("SECOND_ONE", "Second")
                        .file(
                                "relations.graph.yaml",
                                graph("{source: 'directive:SECOND_ONE', target: 'base::directive:OPEN_ONE',"
                                        + " relation: overrides}"))),
                Preconditions.none());
        Packs locking = Packs.of(BASE_ID, "base", "2.0.0", "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", "schema_version: 1\nartifacts:\n  directive:OPEN_ONE: locked\n")
                .directive("LOCKED_ONE", "Locked")
                .directive("OPEN_ONE", "Open");

        CharterRefusal refused = refusal(() -> rig.register("base", rig.stage(locking), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.OVERRIDE_NOT_PERMITTED);
        assertThat(refused.getMessage()).endsWith("(2 fault(s); every one is in violations)");
        assertThat(refused.violations())
                .extracting(
                        violation -> violation.code(),
                        violation -> violation.detail().startsWith("In the closure rooted at "))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("override_not_permitted", true),
                        org.assertj.core.groups.Tuple.tuple("override_not_permitted", true));
        assertThat(refused.violations())
                .extracting(violation -> violation.detail())
                .anySatisfy(detail -> assertThat(detail).contains("rooted at derived"))
                .anySatisfy(detail -> assertThat(detail).contains("rooted at second"));
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).contains("derived", "second", "base");
    }

    @Test
    void aLaterClosureThatCannotBeReadNeverDisplacesTheFaultOfAnEarlierOne(@TempDir Path host) {
        // #556, D1 (PR #618 squad, correctness lens): a write that one closure refuses answers that refusal, as it did
        // when the first faulting closure threw, even when a stored pack of a later closure cannot be read.
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        rig.register(
                "derived",
                rig.stage(derived("1.0.0").file("relations.graph.yaml", overridesFrom("OPEN_ONE"))),
                Preconditions.none());
        rig.install(
                "later",
                Packs.of(PackRegistrationServiceTest.ulid('W'), "later", "1.0.0", "project", DEPENDS_ON_BASE)
                        .directive("LATER_ONE", "Later")
                        .file(
                                "directives/LATER_ONE.directive.yaml",
                                Packs.directiveText("LATER_ONE", "Later") + "references:\n  - directive:X\n"));
        Packs locking = Packs.of(BASE_ID, "base", "2.0.0", "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", "schema_version: 1\nartifacts:\n  directive:OPEN_ONE: locked\n")
                .directive("LOCKED_ONE", "Locked")
                .directive("OPEN_ONE", "Open");

        CharterRefusal refused = refusal(() -> rig.register("base", rig.stage(locking), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.OVERRIDE_NOT_PERMITTED);
        assertThat(refused.getMessage()).contains("rooted at derived");
    }

    @Test
    void closuresWithSeveralComposeFaultsEachAreOneFlatRefusalNamingEveryFaultInItsClosure(@TempDir Path host) {
        // #556, FR-008, US1.3 (PR #618 review B1): a compose phase's faults are placed in their closure one by one,
        // never nested as one refusal inside another, so the count is every fault's and each one names its closure.
        String belowTwo = "depends_on:\n  - pack_id: " + BASE_ID + "\n    range: '<2.0'\n";
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none());
        rig.register("derived", rig.stage(org(DERIVED_ID, "derived", "1.0.0", belowTwo)), Preconditions.none());
        rig.register(
                "second",
                rig.stage(org(PackRegistrationServiceTest.ulid('W'), "second", "1.0.0", belowTwo)),
                Preconditions.none());
        Packs dangling = org(
                        BASE_ID,
                        "base",
                        "2.0.0",
                        "depends_on:\n  - pack_id: " + PackRegistrationServiceTest.ulid('X') + "\n    range: '>=1.0'\n")
                .directive("BASE_ONE", "Base");

        CharterRefusal refused = refusal(() -> rig.register("base", rig.stage(dangling), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_DEPENDENCY_UNKNOWN);
        assertThat(refused.getMessage()).endsWith("(5 fault(s); every one is in violations)");
        assertThat(refused.violations())
                .extracting(violation -> violation.code())
                .containsExactly(
                        "pack_dependency_unknown",
                        "pack_dependency_unknown",
                        "pack_dependency_out_of_range",
                        "pack_dependency_unknown",
                        "pack_dependency_out_of_range");
        assertThat(refused.violations())
                .extracting(violation -> violation.detail())
                .as("no row is itself a refusal of several faults")
                .noneSatisfy(detail -> assertThat(detail).contains("fault(s)"))
                .as("every fault names its closure")
                .allSatisfy(detail -> assertThat(detail).startsWith("In the closure rooted at "));
        assertThat(refused.violations())
                .extracting(violation -> violation.detail())
                .filteredOn(detail -> detail.contains("out of range") || detail.contains("in range"))
                .satisfiesExactly(
                        first -> assertThat(first).startsWith("In the closure rooted at derived"),
                        second -> assertThat(second).startsWith("In the closure rooted at second"));
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).contains("base", "derived", "second");
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
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).contains("derived", "base");
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

        assertThat(same.notices()).isEqualTo(ImpactCheck.Notices.NONE);
        assertThat(renamed.notices()).isEqualTo(ImpactCheck.Notices.NONE);
    }

    // ---- the preset items (#629, #632 item 1; WP03 T014) ---------------------------------------------------------

    @Test
    void aMustHaveTheOwnClosureDoesNotHoldIsRefusedAtTheItemAndTheHeldControlRegisters(@TempDir Path host) {
        Rig rig = new Rig(host);
        Packs unheld = Packs.of(ACME_ID, "acme", "1.0.0", "org").file("presets/starter.yaml", REQUIRE_X);

        CharterRefusal refused = refusal(() -> rig.register("acme", rig.stage(unheld), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.PRESET_ITEM_NOT_HELD);
        assertThat(refused.getMessage())
                .isEqualTo("the preset `starter` of pack `acme` requires directive:X_ONE (must_have), but no pack of"
                        + " its own closure (`acme`) holds it as an artifact file");
        assertThat(refused.violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("presets/starter.yaml#/activated_directives/0", "preset_item_not_held"));
        assertThat(rig.packs.resolve("acme")).as("nothing was created").doesNotExist();
        assertThat(rig.register("acme", rig.stage(noticing("1.0.0", false, true)), Preconditions.none())
                        .outcome())
                .as("the control: the same preset once the pack holds X_ONE")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aMustHaveHeldByADependencyIsHeldAndTheDependentRegisters(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(floorBase()), Preconditions.none());
        Packs top = Packs.of(DERIVED_ID, "top", "1.0.0", "project", DEPENDS_ON_BASE)
                .file("presets/team.yaml", governing("X_ONE").replace("id: governing", "id: team"));

        assertThat(rig.register("top", rig.stage(top), Preconditions.none()).outcome())
                .as("base, on top's depends_on, holds X_ONE as a file")
                .isEqualTo(Outcome.CREATED);
    }

    private static final String MID_ID = PackRegistrationServiceTest.ulid('V');

    /**
     * A write to a pack the declaring pack builds on is judged for the declaring pack's preset (FR-003, R-a: every
     * write), modelled on {@code FallbackProfileImpactTest}'s write to the pack holding the fallback. Dropping X_ONE from
     * base itself is the inbound-reference check's 409 (the preset item refers to it), so the write takes base out of
     * top's own closure instead: mid stops depending on base, and top's must_have X_ONE is held by no pack of it.
     */
    @Test
    void aWriteToAPackTheDeclaringPackBuildsOnIsJudgedForTheDeclaringPacksPreset(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(floorBase()), Preconditions.none());
        String dependsOnMid = "depends_on:\n  - pack_id: " + MID_ID + "\n    range: '>=1.0'\n";
        rig.register("mid", rig.stage(Packs.of(MID_ID, "mid", "1.0.0", "org", DEPENDS_ON_BASE)), Preconditions.none());
        rig.register(
                "top",
                rig.stage(Packs.of(DERIVED_ID, "top", "1.0.0", "project", dependsOnMid)
                        .file("presets/team.yaml", governing("X_ONE").replace("id: governing", "id: team"))),
                Preconditions.none());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("mid"));

        CharterRefusal refused = refusal(
                () -> rig.register("mid", rig.stage(Packs.of(MID_ID, "mid", "2.0.0", "org")), Preconditions.none()));

        assertThat(refused.code())
                .as("mid declares no preset; top, which builds on it, does")
                .isEqualTo(RefusalCode.PRESET_ITEM_NOT_HELD);
        assertThat(refused.getMessage())
                .isEqualTo("the preset `team` of pack `top` requires directive:X_ONE (must_have), but no pack of its"
                        + " own closure (`top`, `mid`) holds it as an artifact file");
        assertThat(refused.violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("presets/team.yaml#/activated_directives/0", "preset_item_not_held"));
        assertThat(refused.packs()).extracting(pack -> pack.packId().value()).startsWith("top");
        assertThat(rig.filesOf(rig.packs.resolve("mid")))
                .as("nothing was stored")
                .isEqualTo(before);
        assertThat(rig.register(
                                "mid",
                                rig.stage(Packs.of(MID_ID, "mid", "2.0.0", "org", DEPENDS_ON_BASE)),
                                Preconditions.none())
                        .outcome())
                .as("the control: the same write keeping base in top's closure")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void anItemNoClosurePackDeclaresIsRefusedUndeclared(@TempDir Path host) {
        Rig rig = new Rig(host);
        Packs undeclared = Packs.of(ACME_ID, "acme", "1.0.0", "org")
                .file("presets/starter.yaml", REQUIRE_X.replace("    must_have: true\n", ""));

        assertThat(refusalOf(() -> rig.register("acme", rig.stage(undeclared), Preconditions.none())))
                .isEqualTo(RefusalCode.PRESET_ITEM_UNDECLARED);
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

        List<ClosureImpactCheck.Finding> findings = rig.impact.census();

        assertThat(findings)
                .extracting(ClosureImpactCheck.Finding::pack, ClosureImpactCheck.Finding::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("acme", RefusalCode.ENHANCES_CYCLE));
        assertThat(findings.getFirst().detail()).contains("acme");
        assertThat(rig.filesOf(rig.packs.resolve("acme")))
                .as("the census reads and writes nothing")
                .containsKey("relations.graph.yaml");
    }

    @Test
    void theCensusKeepsOneFindingPerPackWithEveryFaultOfItsClosure(@TempDir Path host) {
        // #556, FR-011: two separate enhances cycles are two faults of acme's one finding.
        Rig rig = new Rig(host);
        rig.install(
                "acme",
                Packs.of(ACME_ID, "acme", "1.0.0", "org")
                        .directive("A", "A")
                        .directive("B", "B")
                        .directive("C", "C")
                        .directive("D", "D")
                        .file(
                                "relations.graph.yaml",
                                graph(
                                        edge("A", "enhances", "B"),
                                        edge("B", "enhances", "A"),
                                        edge("C", "enhances", "D"),
                                        edge("D", "enhances", "C"))));

        List<ClosureImpactCheck.Finding> findings = rig.impact.census();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.pack()).isEqualTo("acme");
            assertThat(finding.code()).isEqualTo(RefusalCode.ENHANCES_CYCLE);
            assertThat(finding.refusal().violations())
                    .extracting(violation -> violation.code())
                    .containsExactly("enhances_cycle", "enhances_cycle");
        });
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
                .extracting(ClosureImpactCheck.Finding::pack, ClosureImpactCheck.Finding::code)
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
                new ai.speckitty.charter.packs.infra.storage.FileSystemPackSources(rig.locations), rig.generation);
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
                public View with(ImpactCheck.Replacement written) {
                    return counted(session.with(written));
                }

                @Override
                public View without(ai.speckitty.charter.kernel.domain.PackIdentifier pack) {
                    return counted(session.without(pack));
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
        ImpactCheck.Replacement same = new ImpactCheck.Replacement(
                configured, rig.repository.get(configured.id()), rig.repository.compositionFacts(configured.id()));

        new ClosureImpactCheck(views, ActivationImpact.NONE).judge(new ImpactCheck.Registration(same));

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
        ImpactCheck.Replacement same = new ImpactCheck.Replacement(
                base, rig.repository.get(base.id()), rig.repository.compositionFacts(base.id()));
        ClosureImpactCheck impact = new ClosureImpactCheck(
                new ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory(
                        counting, new ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy()),
                ActivationImpact.NONE);

        impact.judge(new ImpactCheck.Registration(same));

        assertThat(listings.get())
                .as("each listing parses every pack.yaml, so one per pack read is a square; the session lists once")
                .isLessThanOrEqualTo(2);
    }

    // ---- C38: the activation gate, on every check path, never swallowed (squad A2) ---------------------------------

    /**
     * A port with one stored activation, rooted at every configured pack, whose gate records the closure it was asked
     * to judge and refuses as it is told to.
     */
    private static final class Gate implements ActivationImpact {
        private final List<ActivationImpact.ClosureChange> judged = new java.util.ArrayList<>();
        private final Optional<CharterRefusal> refusal;

        Gate(Optional<CharterRefusal> refusal) {
            this.refusal = refusal;
        }

        @Override
        public List<ActivationRoots> activationRoots() {
            return List.of(new ActivationRoots("all", Optional.empty()));
        }

        @Override
        public void requireComposable(ClosureChanges closures) {
            closures.of(Optional.empty()).ifPresent(judged::add);
            refusal.ifPresent(refused -> {
                throw refused;
            });
        }
    }

    private static final CharterRefusal SLOT_MISSING =
            new CharterRefusal(RefusalCode.PROMPT_SLOT_MISSING, "Under the activation `a-1`: the slot is missing");

    private static ClosureImpactCheck gated(Rig rig, Gate gate) {
        return new ClosureImpactCheck(
                new ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory(
                        rig.repository, rig.repository, rig.repository),
                gate);
    }

    private static ImpactCheck.Replacement storedAs(Rig rig, String name) {
        ai.speckitty.charter.packs.domain.ConfiguredPack configured = rig.repository.configured().stream()
                .filter(pack -> pack.id().value().equals(name))
                .findFirst()
                .orElseThrow();
        return new ImpactCheck.Replacement(
                configured, rig.repository.get(configured.id()), rig.repository.compositionFacts(configured.id()));
    }

    @Test
    void theWholePackRegistrationAsksTheGateAndItsRefusalIsNotSwallowed(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none());
        Gate gate = new Gate(Optional.of(SLOT_MISSING));

        assertThat(refusal(() -> gated(rig, gate).judge(new ImpactCheck.Registration(storedAs(rig, "base")))))
                .isSameAs(SLOT_MISSING);
        assertThat(gate.judged).singleElement().satisfies(change -> {
            assertThat(change.before()).isPresent();
            assertThat(change.after().closurePackIds()).containsExactly(new PackUlid(BASE_ID));
        });
    }

    @Test
    void thePerArtifactPathOfTheWritesAsksTheGateAndItsRefusalIsNotSwallowed(@TempDir Path host) {
        // The per-artifact registrations, the relationship write and both DELETEs judge through this path.
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none());
        Gate gate = new Gate(Optional.of(SLOT_MISSING));
        ClosureImpactCheck impact = gated(rig, gate);
        ai.speckitty.charter.kernel.domain.PackIdentifier base =
                new ai.speckitty.charter.kernel.domain.PackIdentifier("base");

        assertThat(refusal(() -> impact.judge(new ImpactCheck.Write(base, facts -> facts, Optional.empty()))))
                .isSameAs(SLOT_MISSING);
        assertThat(gate.judged).hasSize(1);
    }

    @Test
    void theGateIsNotAskedWhenAClosureFailsAndTheClosuresRefusalStands(@TempDir Path host) {
        // #556, FR-009: the activations are judged only when no closure failed.
        Rig rig = new Rig(host);
        rig.install(
                "acme",
                Packs.of(ACME_ID, "acme", "1.0.0", "org")
                        .directive("A", "A")
                        .directive("B", "B")
                        .file("relations.graph.yaml", graph(edge("A", "enhances", "B"), edge("B", "enhances", "A"))));
        Gate gate = new Gate(Optional.of(SLOT_MISSING));
        ClosureImpactCheck impact = gated(rig, gate);
        ai.speckitty.charter.kernel.domain.PackIdentifier acme =
                new ai.speckitty.charter.kernel.domain.PackIdentifier("acme");

        ImpactCheck.Impact judged = impact.judge(new ImpactCheck.Write(acme, facts -> facts, Optional.empty()));

        assertThat(judged.verdict().faults())
                .as("the closure's fault, not the gate's refusal")
                .extracting(ClosureFault::code)
                .containsExactly(RefusalCode.ENHANCES_CYCLE);
        assertThat(gate.judged).as("the gate was not asked").isEmpty();
    }

    @Test
    void theTwinAGateThatFindsNothingLetsEveryPathThrough(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none());
        Gate gate = new Gate(Optional.empty());
        ClosureImpactCheck impact = gated(rig, gate);
        ai.speckitty.charter.kernel.domain.PackIdentifier base =
                new ai.speckitty.charter.kernel.domain.PackIdentifier("base");

        assertThat(impact.judge(new ImpactCheck.Registration(storedAs(rig, "base")))
                        .notices())
                .isEqualTo(ImpactCheck.Notices.NONE);
        assertThat(impact.judge(new ImpactCheck.Write(base, facts -> facts, Optional.empty()))
                        .notices())
                .isEqualTo(ImpactCheck.Notices.NONE);
        assertThat(gate.judged).hasSize(2);
    }

    // ---- a substep candidate of another pack (FR-005) --------------------------------------------------------------

    /** A pack that builds on {@code base} and whose mission type's substep delegates to {@code base::<directive>}. */
    private static Packs delegating(String directive) {
        String type = """
                schema_version: '1.0'
                id: rx
                title: Rx
                description: A mission type whose step delegates to another pack.
                steps:
                - id: a
                  title: A
                  substeps:
                  - id: run
                    description: Apply the base directive
                    delegates_to:
                      kind: directive
                      candidates:
                      - base::%s
                """.formatted(directive);
        String graph = """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - source: mission_type:rx
                  target: action:rx/a
                  relation: requires
                - source: action:rx/a
                  target: base::directive:%s
                  relation: scope
                """.formatted(directive);
        return org(LEFT_ID, "left", "1.0.0", dependsOn(BASE_ID))
                .file("missions/rx/rx.mission-type.yaml", type)
                .file("mission_type.graph.yaml", graph);
    }

    @Test
    void aSubstepCandidateTheNamedPackDoesNotHoldIsRefusedAtRegistrationAsValidationFailed(@TempDir Path host) {
        Rig rig = new Rig(host.resolve("missing"));
        rig.register("base", rig.stage(orgBase("1.0.0")), Preconditions.none());

        CharterRefusal refused =
                refusal(() -> rig.register("left", rig.stage(delegating("MISSING")), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refused.getMessage()).contains("base::directive:MISSING", "substep_delegation_unresolved");
        assertThat(refused.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.code()).isEqualTo("substep_delegation_unresolved");
            assertThat(violation.pointer()).isEqualTo("missions/rx/rx.mission-type.yaml");
        });
        assertThat(rig.packs.resolve("left")).as("nothing is stored").doesNotExist();

        Rig control = new Rig(host.resolve("held"));
        control.register("base", control.stage(orgBase("1.0.0")), Preconditions.none());
        assertThat(control.register("left", control.stage(delegating("BASE_ONE")), Preconditions.none())
                        .outcome())
                .as("the control: the named pack holds the candidate")
                .isEqualTo(Outcome.CREATED);
    }

    // ---- own-closure breaches through the walker, closure by closure (#638, T015) ----------------------------------

    private static final String PINNED_ORG_ID = PackRegistrationServiceTest.ulid('T');
    private static final String PINS_ID = PackRegistrationServiceTest.ulid('Y');
    private static final String FALLS_BACK_ID = PackRegistrationServiceTest.ulid('Z');
    private static final String ON_PINNED_ORG = "depends_on:\n  - pack_id: " + PINNED_ORG_ID + "\n    range: '>=1.0'\n";

    private static String reviewer(String id) {
        return "profile-id: " + id + "\nname: " + id + "\nrole: reviewer\n"
                + "purpose: Review the work.\nspecialization:\n  primary-focus: Review\n";
    }

    /** An org pack holding reviewer-renata, and reviewer-rhea unless a version drops her. */
    private static Packs pinnedOrg(String version, boolean holdsRhea) {
        Packs org = Packs.of(PINNED_ORG_ID, "pinned-org", version, "org")
                .file("agent_profiles/reviewer-renata.agent.yaml", reviewer("reviewer-renata"));
        return holdsRhea ? org.file("agent_profiles/reviewer-rhea.agent.yaml", reviewer("reviewer-rhea")) : org;
    }

    private static Packs pinsRhea() {
        return Packs.of(PINS_ID, "a-pins", "1.0.0", "project", ON_PINNED_ORG + "mutability_policy: policy.yaml\n")
                .file("policy.yaml", "schema_version: 1\nrole_pins:\n  reviewer: reviewer-rhea\n");
    }

    private static Packs fallsBackToRhea() {
        return Packs.of(
                FALLS_BACK_ID, "b-falls-back", "1.0.0", "project", ON_PINNED_ORG + "fallback_profile: reviewer-rhea\n");
    }

    @Test
    void ownClosureBreachesOfTwoClosuresStayClosureMajorThroughTheWalker(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("pinned-org", pinnedOrg("1.0.0", true));
        rig.install("a-pins", pinsRhea());
        rig.install("b-falls-back", fallsBackToRhea());

        CharterRefusal refused =
                refusal(() -> rig.register("pinned-org", rig.stage(pinnedOrg("1.1.0", false)), Preconditions.none()));

        assertThat(refused.violations())
                .extracting(RefusalViolation::code)
                .as("the closures in catalog order, each closure's breaches in turn: a-pins' pin breach comes before"
                        + " b-falls-back's fallback breach, though the fallback rule runs first within a closure")
                .containsExactly("role_pin_unknown", "fallback_profile_unknown");
        assertThat(refused.violations())
                .extracting(RefusalViolation::detail)
                .satisfiesExactly(
                        pin -> assertThat(pin).startsWith("In the closure rooted at a-pins: "),
                        fallback -> assertThat(fallback).startsWith("In the closure rooted at b-falls-back: "));
    }

    @Test
    void aPresetItemIsReportedBesideAnOwnClosureBreach(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("pinned-org", pinnedOrg("1.0.0", true));
        Packs both = Packs.of(PINS_ID, "pins", "1.0.0", "project", ON_PINNED_ORG + "mutability_policy: policy.yaml\n")
                .file("policy.yaml", "schema_version: 1\nrole_pins:\n  reviewer: reviewer-rex\n")
                .file("presets/starter.yaml", REQUIRE_X);

        CharterRefusal refused = refusal(() -> rig.register("pins", rig.stage(both), Preconditions.none()));

        assertThat(refused.violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .as("the breach first, then the preset item, ungated by the breach; the item keeps its violation at"
                        + " the item beside the fault's own")
                .containsExactly(
                        tuple("", "role_pin_unknown"),
                        tuple("", "preset_item_not_held"),
                        tuple("presets/starter.yaml#/activated_directives/0", "preset_item_not_held"));
    }

    @Test
    void theCensusNamesThePacksOfARelationFault(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("acme", storedAcme(CYCLE));

        assertThat(rig.impact.census())
                .singleElement()
                .satisfies(finding -> assertThat(finding.detail()).contains(" (packs acme)"));
    }

    @Test
    void theCensusReachesTheOwnClosureRules(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("pinned-org", pinnedOrg("1.0.0", false));
        rig.install("b-falls-back", fallsBackToRhea());

        assertThat(rig.impact.census())
                .extracting(ClosureImpactCheck.Finding::pack, ClosureImpactCheck.Finding::code)
                .containsExactly(tuple("b-falls-back", RefusalCode.FALLBACK_PROFILE_UNKNOWN));
    }
}
