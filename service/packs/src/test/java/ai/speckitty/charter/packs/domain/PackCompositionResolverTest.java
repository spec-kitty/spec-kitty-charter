package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The resolver orders one closure and refuses a broken graph where it breaks. */
class PackCompositionResolverTest {

    private static PackUnderResolution pack(String id, String name, String version, PackLevel level) {
        return pack(id, name, version, level, List.of());
    }

    private static PackUnderResolution pack(
            String id, String name, String version, PackLevel level, List<PackDependency> dependencies) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(
                        id,
                        version,
                        Optional.empty(),
                        name,
                        Optional.of(level),
                        Optional.empty(),
                        PackPaths.none(),
                        dependencies));
    }

    /** A pack whose graphs say it changes one artifact (an {@code overrides} relation on it; A14). */
    private static PackUnderResolution refining(
            String id, String name, String version, PackLevel level, String artifact) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(id, version, Optional.empty(), name, Optional.of(level), Optional.empty()),
                List.of(artifact));
    }

    /** A distribution pack that settles one contested artifact by naming the member whose version wins. */
    private static PackUnderResolution settling(
            String id, String name, String version, PackLevel level, String artifact, String member) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(
                        id,
                        version,
                        Optional.empty(),
                        name,
                        Optional.of(level),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(),
                        List.of(new PackOverride(artifact, member))));
    }

    /** A pack that extends another by {@code parent_pack}, with no dependencies. */
    private static PackUnderResolution extending(String id, String name, PackLevel level, String parent) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(id, "1.0.0", Optional.of(parent), name, Optional.of(level), Optional.empty()));
    }

    private static List<String> ids(List<PackUnderResolution> resolved) {
        return resolved.stream().map(entry -> entry.descriptor().packId()).toList();
    }

    // One level, ids sorting the opposite way to the dependencies: only the dependency edges, across three hops, can
    // order this.
    @Test
    void aDependencyComesBeforeThePackThatDeclaresItAcrossAChain() {
        PackUnderResolution base = pack("01ZBASE", "base", "1.0.0", PackLevel.ORG);
        PackUnderResolution middle =
                pack("01MID0", "middle", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01ZBASE", ">=1.0")));
        PackUnderResolution top =
                pack("01AATOP", "top", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01MID0", ">=1.0")));

        assertThat(ids(PackCompositionResolver.resolve(List.of(top, middle, base))))
                .containsExactly("01ZBASE", "01MID0", "01AATOP");
    }

    // The child's id sorts before its parent's: only the parent_pack edge can put the parent first (ADR-0048).
    @Test
    void aParentPackComesBeforeTheChildThatExtendsItWhateverTheirIdsSort() {
        PackUnderResolution parent = pack("01ZPARENT", "parent", "1.0.0", PackLevel.ORG);
        PackUnderResolution child = extending("01ACHILD", "child", PackLevel.ORG, "01ZPARENT");

        assertThat(ids(PackCompositionResolver.resolve(List.of(child, parent))))
                .containsExactly("01ZPARENT", "01ACHILD");
    }

    @Test
    void aParentChainIsOrderedFromTheTopDownAcrossAChain() {
        PackUnderResolution top = pack("01ZTOP", "top", "1.0.0", PackLevel.ORG);
        PackUnderResolution middle = extending("01MID", "middle", PackLevel.ORG, "01ZTOP");
        PackUnderResolution leaf = extending("01ALEAF", "leaf", PackLevel.ORG, "01MID");

        assertThat(ids(PackCompositionResolver.resolve(List.of(leaf, middle, top))))
                .containsExactly("01ZTOP", "01MID", "01ALEAF");
    }

    @Test
    void aPackThatNamesItselfAsParentIsADependencyCycle() {
        PackUnderResolution loop = extending("01LOOP", "loop", PackLevel.ORG, "01LOOP");

        PackCompositionResolver.Refusal refusal = refusing(List.of(loop));

        assertThat(refusal.failure()).isEqualTo(Failure.CYCLE);
        assertThat(refusal.failure().code()).isEqualTo(RefusalCode.PACK_DEPENDENCY_CYCLE);
        assertThat(refusal.packId()).isEqualTo("01LOOP");
        assertThat(refusal.detail()).contains("parent_pack");
    }

    @Test
    void twoPacksThatNameEachOtherAsParentAreADependencyCycle() {
        PackUnderResolution first = extending("01FIRST", "first", PackLevel.ORG, "01SECOND");
        PackUnderResolution second = extending("01SECOND", "second", PackLevel.ORG, "01FIRST");

        PackCompositionResolver.Refusal refusal = refusing(List.of(first, second));

        assertThat(refusal.failure()).isEqualTo(Failure.CYCLE);
        assertThat(refusal.packs()).extracting(RefusalPack::packId).containsExactlyInAnyOrder("01FIRST", "01SECOND");
    }

    @Test
    void aParentCycleThroughADependencyIsADependencyCycle() {
        PackUnderResolution parent =
                pack("01PARENT", "parent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01CHILD", ">=1.0")));
        PackUnderResolution child = extending("01CHILD", "child", PackLevel.ORG, "01PARENT");

        assertThat(refusing(List.of(parent, child)).failure()).isEqualTo(Failure.CYCLE);
    }

    /** Two packs of one level, related by nothing, hold the preset {@code team}: the resolver names no winner. */
    @Nested
    class PresetContests {

        private final Map<String, List<String>> presetIds = new LinkedHashMap<>();

        private PackUnderResolution holding(PackUnderResolution pack, String... presets) {
            presetIds.put(pack.descriptor().packId(), List.of(presets));
            return pack;
        }

        private void check(PackUnderResolution... packs) {
            PackCompositionResolver.refusePresetContests(
                    List.of(packs),
                    pack -> presetIds.getOrDefault(pack.descriptor().packId(), List.of()));
        }

        @Test
        void twoUnrelatedPacksOfOneLevelHoldingOnePresetIdAreRefused() {
            PackUnderResolution left = holding(pack("01LEFT", "left", "1.0.0", PackLevel.ORG), "team");
            PackUnderResolution right = holding(pack("01RIGHT", "right", "1.0.0", PackLevel.ORG), "team");

            PackCompositionResolver.Refusal refusal = refusalOf(() -> check(left, right));

            assertThat(refusal.failure()).isEqualTo(Failure.SIBLING_CONFLICT);
            assertThat(refusal.failure().code()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
            assertThat(refusal.packs()).extracting(RefusalPack::packId).containsExactly("01RIGHT", "01LEFT");
            assertThat(refusal.detail())
                    .isEqualTo("packs `left` (01LEFT) and `right` (01RIGHT) at org both hold the preset `team`,"
                            + " and neither extends the other by parent_pack");
        }

        @Test
        void aChildAndItsParentMayHoldOnePresetIdTheChildOverridesIt() {
            PackUnderResolution parent = holding(pack("01PARENT", "parent", "1.0.0", PackLevel.ORG), "team");
            PackUnderResolution child = holding(extending("01CHILD", "child", PackLevel.ORG, "01PARENT"), "team");

            assertThatCode(() -> check(parent, child)).doesNotThrowAnyException();
        }

        @Test
        void aGrandchildAndItsGrandparentAreRelatedThroughTheChain() {
            PackUnderResolution top = holding(pack("01TOP", "top", "1.0.0", PackLevel.ORG), "team");
            PackUnderResolution middle = extending("01MID", "middle", PackLevel.ORG, "01TOP");
            PackUnderResolution leaf = holding(extending("01LEAF", "leaf", PackLevel.ORG, "01MID"), "team");

            assertThatCode(() -> check(top, middle, leaf)).doesNotThrowAnyException();
        }

        @Test
        void twoChildrenOfOneParentAreSiblingsAndAreRefused() {
            PackUnderResolution parent = pack("01PARENT", "parent", "1.0.0", PackLevel.ORG);
            PackUnderResolution one = holding(extending("01ONE", "one", PackLevel.ORG, "01PARENT"), "team");
            PackUnderResolution two = holding(extending("01TWO", "two", PackLevel.ORG, "01PARENT"), "team");

            assertThat(refusalOf(() -> check(parent, one, two)).failure()).isEqualTo(Failure.SIBLING_CONFLICT);
        }

        @Test
        void packsOfDifferentLevelsMayHoldOnePresetIdTheNearerToTheProjectOverrides() {
            PackUnderResolution org = holding(pack("01ORG", "org", "1.0.0", PackLevel.ORG), "team");
            PackUnderResolution project = holding(pack("01PROJ", "project", "1.0.0", PackLevel.PROJECT), "team");

            assertThatCode(() -> check(org, project)).doesNotThrowAnyException();
        }

        @Test
        void differentPresetIdsContestNothing() {
            PackUnderResolution left = holding(pack("01LEFT", "left", "1.0.0", PackLevel.ORG), "team");
            PackUnderResolution right = holding(pack("01RIGHT", "right", "1.0.0", PackLevel.ORG), "crew");

            assertThatCode(() -> check(left, right)).doesNotThrowAnyException();
        }

        private PackCompositionResolver.Refusal refusalOf(Runnable compose) {
            try {
                compose.run();
            } catch (PackCompositionResolver.Refusal refusal) {
                return refusal;
            }
            throw new AssertionError("the check was expected to refuse");
        }
    }

    /** A preset item or a policy lock that names a held id only by case would silently govern nothing. */
    @Nested
    class CaseOnlyMatches {

        private PackUnderResolution holding(String id, String name, PackLevel level, String... urns) {
            return new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(id), level),
                    new PackDescriptor(id, "1.0.0", Optional.empty(), name, Optional.of(level), Optional.empty()),
                    List.of(),
                    List.of(urns));
        }

        private void check(List<PackUnderResolution> packs, Map<String, List<String>> mentions) {
            PackCompositionResolver.refuseCaseOnlyMatches(
                    packs, pack -> mentions.getOrDefault(pack.descriptor().packId(), List.of()));
        }

        @Test
        void aMentionOfAHeldIdOnlyByCaseIsRefusedNamingTheMentioningAndTheHoldingPack() {
            PackUnderResolution holder = holding("01HOLD", "holder", PackLevel.ORG, "directive:DIRECTIVE_001");
            PackUnderResolution mentioner = holding("01MENT", "mentioner", PackLevel.ORG);

            PackCompositionResolver.Refusal refusal = refusing(
                    () -> check(List.of(holder, mentioner), Map.of("01MENT", List.of("directive:directive_001"))));

            assertThat(refusal.failure()).isEqualTo(Failure.REFERENCE_ARTIFACT_MISSING);
            assertThat(refusal.packs()).extracting(RefusalPack::packId).containsExactly("01MENT", "01HOLD");
            assertThat(refusal.detail())
                    .contains("directive:directive_001", "directive:DIRECTIVE_001", "differ only by case");
        }

        @Test
        void anExactMentionAndAMentionOfAnIdNoPackHoldsAreNotRefused() {
            PackUnderResolution holder = holding("01HOLD", "holder", PackLevel.ORG, "directive:DIRECTIVE_001");

            assertThatCode(() -> check(
                            List.of(holder),
                            Map.of("01HOLD", List.of("directive:DIRECTIVE_001", "directive:NOT_HELD", "tactic:x"))))
                    .doesNotThrowAnyException();
        }

        @Test
        void aMentionOfAnotherKindWithTheSameIdIsNotACaseMatch() {
            PackUnderResolution holder = holding("01HOLD", "holder", PackLevel.ORG, "directive:DIRECTIVE_001");

            assertThatCode(() -> check(List.of(holder), Map.of("01HOLD", List.of("tactic:directive_001"))))
                    .doesNotThrowAnyException();
        }

        private PackCompositionResolver.Refusal refusing(Runnable check) {
            try {
                check.run();
            } catch (PackCompositionResolver.Refusal refusal) {
                return refusal;
            }
            throw new AssertionError("the check was expected to refuse");
        }
    }

    private static PackCompositionResolver.Refusal refusing(List<PackUnderResolution> packs) {
        try {
            PackCompositionResolver.resolve(packs);
        } catch (PackCompositionResolver.Refusal refusal) {
            return refusal;
        }
        throw new AssertionError("the compose was expected to refuse");
    }

    @Test
    void aSharedDependencyAppearsOnce() {
        PackUnderResolution base = pack("01ZBASE", "base", "1.0.0", PackLevel.ORG);
        PackUnderResolution left =
                pack("01ALEFT", "left", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01ZBASE", ">=1.0")));
        PackUnderResolution right =
                pack("01BRIGHT", "right", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01ZBASE", "<2.0")));

        assertThat(ids(PackCompositionResolver.resolve(List.of(left, base, right))))
                .containsExactly("01ZBASE", "01ALEFT", "01BRIGHT");
    }

    @Test
    void aCycleIsRefusedAndNamesAPackOnItNotOneOnlyDownstreamOfIt() {
        // 01AAA only depends on the cycle 01BBB <-> 01CCC, and sorts first among the packs Kahn's pass leaves over.
        PackUnderResolution downstream =
                pack("01AAA", "a", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BBB", ">=1.0")));
        PackUnderResolution b =
                pack("01BBB", "b", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01CCC", ">=1.0")));
        PackUnderResolution c =
                pack("01CCC", "c", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BBB", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(downstream, b, c)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.CYCLE);
                    assertThat(refusal.packId()).isIn("01BBB", "01CCC");
                });
    }

    @Test
    void aPackThatDependsOnItselfIsACycle() {
        PackUnderResolution self =
                pack("01SELF", "self", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01SELF", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(self)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.CYCLE);
                    assertThat(refusal.packId()).isEqualTo("01SELF");
                    assertThat(refusal.detail()).contains("depends on itself");
                });
    }

    @Test
    void anUnknownDependencyIsRefusedAndNamesTheMissingId() {
        PackUnderResolution dependent =
                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01GONE", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(dependent)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.UNKNOWN_DEPENDENCY))
                .hasMessageContaining("01GONE");
    }

    /**
     * Two packs ask one pack for ranges; the held version satisfies the first in every row. Which ranges share a
     * version is {@code Pep440RangeTest}'s truth table, so two rows show the refusal wiring.
     */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {">=1.0,<2|>=2.0|1.5.0|false", ">=1.0,<3|>=2.0|2.5.0|true"})
    void rangesNoVersionSatisfiesTogetherAreRefusedNamingBothPacksBeforeTheHeldVersionIsChecked(
            String firstRange, String secondRange, String held, boolean resolves) {
        PackUnderResolution base = pack("01BASE", "base", held, PackLevel.BUILT_IN);
        PackUnderResolution first =
                pack("01ONE", "one", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", firstRange)));
        PackUnderResolution second =
                pack("01TWO", "two", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", secondRange)));

        if (resolves) {
            assertThat(ids(PackCompositionResolver.resolve(List.of(second, first, base))))
                    .containsExactly("01BASE", "01ONE", "01TWO");
            return;
        }
        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(second, first, base)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.OUT_OF_RANGE);
                    assertThat(refusal.packId()).isEqualTo("01TWO");
                    assertThat(refusal.detail())
                            .contains("01ONE", "01TWO", firstRange, secondRange)
                            .contains("no version satisfies together")
                            .doesNotContain("held version");
                });
    }

    /** A descriptor the read would have called pack_unreadable never reaches the resolver; it is a programming error. */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"1.0.0|banana", "1.0.0|~=1", "1.0.0-alpha.beta|>=1.0"})
    void aDescriptorThatIsNotWellFormedIsAPreconditionFailureNotARefusal(String baseVersion, String range) {
        PackUnderResolution base = pack("01BASE", "base", baseVersion, PackLevel.BUILT_IN);
        PackUnderResolution dependent =
                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", range)));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(dependent, base)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("well-formed")
                .satisfies(failure -> assertThat(failure).isNotInstanceOf(PackCompositionResolver.Refusal.class));
    }

    @Test
    void twoPacksWithOneNameAreRefused() {
        PackUnderResolution first = pack("01AAA", "shared-name", "1.0.0", PackLevel.ORG);
        PackUnderResolution second = pack("01BBB", "shared-name", "1.0.0", PackLevel.ORG);

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(first, second)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.NAME_CONFLICT));
    }

    /** Every pair of levels, for each edge: a pack reaches its own level or one nearer built-in, and no further. */
    private static final List<PackLevel> FROM_BUILT_IN = List.of(PackLevel.BUILT_IN, PackLevel.ORG, PackLevel.PROJECT);

    static Stream<Arguments> levelMatrix() {
        List<Arguments> rows = new ArrayList<>();
        for (boolean parent : new boolean[] {false, true}) {
            for (PackLevel own : PackLevel.values()) {
                for (PackLevel target : PackLevel.values()) {
                    rows.add(Arguments.of(
                            parent, own, target, FROM_BUILT_IN.indexOf(target) <= FROM_BUILT_IN.indexOf(own)));
                }
            }
        }
        return rows.stream();
    }

    @ParameterizedTest(name = "{0}: {1} -> {2} allowed={3}")
    @MethodSource("levelMatrix")
    void aPackTakesADependencyOrParentOnlyAtItsOwnLevelOrOneNearerBuiltIn(
            boolean parentEdge, PackLevel own, PackLevel target, boolean allowed) {
        PackUnderResolution targetPack = pack("01TRGT", "target", "1.0.0", target);
        PackUnderResolution ownPack = parentEdge
                ? new PackUnderResolution(
                        new ConfiguredPack(new PackIdentifier("01OWN0"), own),
                        new PackDescriptor(
                                "01OWN0", "1.0.0", Optional.of("01TRGT"), "own", Optional.of(own), Optional.empty()))
                : pack("01OWN0", "own", "1.0.0", own, List.of(new PackDependency("01TRGT", ">=1.0")));

        if (allowed) {
            assertThat(ids(PackCompositionResolver.resolve(List.of(ownPack, targetPack))))
                    .containsExactlyInAnyOrder("01OWN0", "01TRGT");
            return;
        }
        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(ownPack, targetPack)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.LEVEL_VIOLATION);
                    assertThat(refusal.packs())
                            .containsExactly(
                                    new RefusalPack("01OWN0", "01OWN0", "own"),
                                    new RefusalPack("01TRGT", "01TRGT", "target"));
                    assertThat(refusal.detail())
                            .contains("01TRGT", own.token(), target.token())
                            .contains("its own level or one nearer built-in");
                });
    }

    @Test
    void anUnresolvableParentIsRefusedNotDegraded() {
        PackUnderResolution orphan = new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier("01ORPH"), PackLevel.ORG),
                new PackDescriptor(
                        "01ORPH",
                        "1.0.0",
                        Optional.of("01MISSING"),
                        "orphan",
                        Optional.of(PackLevel.ORG),
                        Optional.empty()));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(orphan)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.PARENT_UNRESOLVED));
    }

    // ---- the sibling refinement contest (FR-012) -----------------------------------------------------

    @Test
    void twoSiblingsChangingOneArtifactAreRefusedNamingBothPacksAndTheArtifact() {
        PackUnderResolution left = refining("01LEFT", "left", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution right = refining("01RIGHT", "right", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(left, right)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> {
                    assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                            .isEqualTo(Failure.SIBLING_CONFLICT);
                    assertThat(((PackCompositionResolver.Refusal) refusal).packId())
                            .isEqualTo("01RIGHT");
                })
                .hasMessageContaining("01LEFT")
                .hasMessageContaining("01RIGHT")
                .hasMessageContaining("directive:DIRECTIVE_009")
                .hasMessageContaining("overrides");
    }

    private static PackUnderResolution defining(String id, String name, PackLevel level, String... artifacts) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(id, "1.0.0", Optional.empty(), name, Optional.of(level), Optional.empty()),
                List.of(),
                List.of(artifacts));
    }

    @Test
    void twoSiblingsDefiningOneArtifactAreRefusedNamingBothPacksAndTheArtifact() {
        PackUnderResolution left = defining("01LEFT", "left", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution right = defining("01RIGHT", "right", PackLevel.ORG, "directive:DIRECTIVE_009");

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(right, left)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.SIBLING_CONFLICT);
                    assertThat(refusal.packId()).isEqualTo("01RIGHT");
                    assertThat(refusal.detail())
                            .contains("01LEFT", "01RIGHT", "both define the artifact `directive:DIRECTIVE_009`");
                });
    }

    @Test
    void packsAtDifferentLevelsDefiningOneArtifactComposeAsALayerOverride() {
        PackUnderResolution org = defining("01ORGP", "org-pack", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution project = defining("01PROJ", "project-pack", PackLevel.PROJECT, "directive:DIRECTIVE_009");

        assertThat(ids(PackCompositionResolver.resolve(List.of(project, org)))).containsExactly("01ORGP", "01PROJ");
    }

    @Test
    void aQualifierDoesNotMakeTwoWritingsOfOneArtifactTwoContests() {
        PackUnderResolution bare = refining("01LEFT", "left", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution qualified =
                refining("01RIGHT", "right", "1.0.0", PackLevel.ORG, "base::directive:DIRECTIVE_009");

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(bare, qualified)))
                .isInstanceOfSatisfying(
                        PackCompositionResolver.Refusal.class,
                        refusal -> assertThat(refusal.failure()).isEqualTo(Failure.SIBLING_CONFLICT));
    }

    @Test
    void aSettlingOverridesEdgeResolvesTheContestInOrder() {
        PackUnderResolution left = refining("01LEFT", "left", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution right = refining("01RIGHT", "right", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution distribution =
                settling("01DSTR", "distribution", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009", "01RIGHT");

        assertThat(ids(PackCompositionResolver.resolve(List.of(distribution, right, left))))
                .containsExactly("01DSTR", "01LEFT", "01RIGHT");
    }

    @Test
    void twoPacksNamingDifferentWinnersLeaveTheContestUnsettled() {
        PackUnderResolution left = refining("01LEFT", "left", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution right = refining("01RIGHT", "right", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution first =
                settling("01DONE", "done", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009", "01LEFT");
        PackUnderResolution second =
                settling("01DTWO", "dtwo", "1.0.0", PackLevel.ORG, "org::directive:DIRECTIVE_009", "01RIGHT");

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(second, first, right, left)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.SIBLING_CONFLICT);
                    assertThat(refusal.detail()).contains("different winners", "01LEFT", "01RIGHT");
                });
        // two packs naming the same winner still settle it
        PackUnderResolution agreeing =
                settling("01DTWO", "dtwo", "1.0.0", PackLevel.ORG, "org::directive:DIRECTIVE_009", "01LEFT");
        assertThat(ids(PackCompositionResolver.resolve(List.of(agreeing, first, right, left))))
                .containsExactly("01DONE", "01DTWO", "01LEFT", "01RIGHT");
    }

    // A14 (FR-035, DM-01M6RA92BS6GGCYTRCFQDYYY5S, rule C15): a settlement names exactly one winner. One descriptor
    // cannot name two (it is refused as it is read), and the overrides edges of the compose naming two contestants
    // answer the conflict refusal; exactly one winner settles the contest.
    @Test
    void a14ASettlementNamingTwoWinnersAnswersTheSiblingConflictRefusalAndExactlyOneWinnerSettlesIt() {
        assertThatThrownBy(() -> new PackDescriptor(
                        "01DSTR",
                        "1.0.0",
                        Optional.empty(),
                        "distribution",
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(),
                        List.of(
                                new PackOverride("directive:DIRECTIVE_009", "01LEFT"),
                                new PackOverride("directive:DIRECTIVE_009", "01RIGHT"))))
                .as("one descriptor naming two winners of one artifact")
                .isInstanceOf(IllegalArgumentException.class);
        PackUnderResolution left = refining("01LEFT", "left", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution right = refining("01RIGHT", "right", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution namingLeft =
                settling("01DSTR", "distribution", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009", "01LEFT");
        PackUnderResolution namingRight =
                settling("01DTWO", "dtwo", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009", "01RIGHT");

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(namingLeft, namingRight, left, right)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.SIBLING_CONFLICT);
                    assertThat(refusal.failure().code()).isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
                    assertThat(refusal.detail()).contains("different winners", "01LEFT", "01RIGHT");
                });
        assertThat(ids(PackCompositionResolver.resolve(List.of(namingLeft, left, right))))
                .as("same fixture: exactly one winner settles the contest")
                .containsExactly("01DSTR", "01LEFT", "01RIGHT");
    }

    @Test
    void aContestantMayDeclareTheOverridesEdgeThatSettlesItsOwnContest() {
        PackUnderResolution left = new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier("01LEFT"), PackLevel.ORG),
                new PackDescriptor(
                        "01LEFT",
                        "1.0.4",
                        Optional.empty(),
                        "left",
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(),
                        List.of(new PackOverride("directive:DIRECTIVE_009", "01RIGHT"))),
                List.of("directive:DIRECTIVE_009"));
        PackUnderResolution right = refining("01RIGHT", "right", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");

        assertThat(ids(PackCompositionResolver.resolve(List.of(right, left)))).containsExactly("01LEFT", "01RIGHT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01GHOST", "01DSTR"})
    void anOverridesEdgeNamingAMemberThatIsNotAContestantDoesNotSettleTheContest(String member) {
        PackUnderResolution left = refining("01LEFT", "left", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution right = refining("01RIGHT", "right", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution distribution =
                settling("01DSTR", "distribution", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009", member);

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(distribution, left, right)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.SIBLING_CONFLICT))
                .hasMessageContaining(member)
                .hasMessageContaining("not a contestant");
    }

    @Test
    void siblingsChangingDifferentArtifactsResolve() {
        PackUnderResolution left = refining("01LEFT", "left", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution right = refining("01RIGHT", "right", "1.0.0", PackLevel.ORG, "tactic:alpha-tactic");

        assertThat(ids(PackCompositionResolver.resolve(List.of(right, left)))).containsExactly("01LEFT", "01RIGHT");
    }

    @Test
    void packsAtDifferentLevelsChangingOneArtifactComposeAsALayerMerge() {
        PackUnderResolution org = refining("01ORGP", "org-pack", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution project =
                refining("01PROJ", "project-pack", "1.0.0", PackLevel.PROJECT, "directive:DIRECTIVE_009");

        assertThat(ids(PackCompositionResolver.resolve(List.of(project, org)))).containsExactly("01ORGP", "01PROJ");
    }

    @Test
    void aQualifiedReferenceToAnArtifactThePackHoldsIsAcceptedWhateverItsKind() {
        // an anti_pattern is held only as a graph node, and the qualified form must stand like a directive's
        PackUnderResolution writing = defining(
                "01WRI", "writing", PackLevel.BUILT_IN, "directive:DIRECTIVE_009", "anti_pattern:big-ball-of-mud");
        PackUnderResolution reader =
                pack("01REA", "reader", "1.0.0", PackLevel.PROJECT, List.of(new PackDependency("01WRI", ">=1.0")));

        assertThatCode(() -> check(
                        List.of(writing, reader),
                        "01REA",
                        "writing::directive:DIRECTIVE_009",
                        "writing::anti_pattern:big-ball-of-mud"))
                .doesNotThrowAnyException();
    }

    @Test
    void aQualifiedReferenceToAnArtifactThePackDoesNotHoldIsRefusedNamingTheReferrerThenTheTarget() {
        PackUnderResolution writing = pack("01WRI", "writing", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution reader =
                pack("01REA", "reader", "1.0.0", PackLevel.PROJECT, List.of(new PackDependency("01WRI", ">=1.0")));

        assertThatThrownBy(() -> check(List.of(writing, reader), "01REA", "writing::directive:DIRECTIVE_009"))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.REFERENCE_ARTIFACT_MISSING);
                    assertThat(refusal.failure().code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                    assertThat(refusal.packs())
                            .containsExactly(
                                    new RefusalPack("01REA", "01REA", "reader"),
                                    new RefusalPack("01WRI", "01WRI", "writing"));
                    assertThat(refusal.detail())
                            .contains("`reader` (01REA)")
                            .contains("`writing` (01WRI) does not hold `directive:DIRECTIVE_009`");
                });
    }

    @Test
    void aQualifiedReferenceToAPackOnlyAnotherPackDependsOnIsRefusedNamingThatPack() {
        PackUnderResolution writing = defining("01WRI", "writing", PackLevel.BUILT_IN, "directive:DIRECTIVE_009");
        PackUnderResolution reader = pack("01REA", "reader", "1.0.0", PackLevel.PROJECT);
        PackUnderResolution other =
                pack("01OTH", "other", "1.0.0", PackLevel.PROJECT, List.of(new PackDependency("01WRI", ">=1.0")));

        assertThatThrownBy(() -> check(List.of(writing, reader, other), "01REA", "writing::directive:DIRECTIVE_009"))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.REFERENCE_PACK_MISSING);
                    assertThat(refusal.packs())
                            .containsExactly(
                                    new RefusalPack("01REA", "01REA", "reader"),
                                    new RefusalPack("01WRI", "01WRI", "writing"));
                    assertThat(refusal.detail()).contains("`writing` (01WRI) is outside its depends_on closure");
                });
    }

    @Test
    void aBareReferenceIsHeldByTheReferringPackWhichShadowsAnotherPacksArtifactOfTheSameId() {
        // FR-014: the reader holds directive:DIRECTIVE_009 and so does the writing pack; the reader's own shadows it.
        // A bare reference no pack holds (an unwired kind) resolves nowhere and refuses nothing.
        PackUnderResolution writing = defining("01WRI", "writing", PackLevel.BUILT_IN, "directive:DIRECTIVE_009");
        PackUnderResolution reader = defining("01REA", "reader", PackLevel.PROJECT, "directive:DIRECTIVE_009");
        PackQualifiedReference own = PackQualifiedReference.parse("directive:DIRECTIVE_009");

        assertThat(PackCompositionResolver.holderOf(
                        List.of(writing, reader), reader.configured().id(), own))
                .contains(reader);
        assertThatCode(() -> check(List.of(writing, reader), "01REA", "directive:DIRECTIVE_009", "directive:NOWHERE"))
                .doesNotThrowAnyException();
    }

    @Test
    void aReferenceQualifiedWithTheReferringPacksOwnNameIsHeldByIt() {
        // FR-013: the pack's own artifacts are reachable both bare and qualified (another artifact than the writer's).
        PackUnderResolution reader = defining("01REA", "reader", PackLevel.PROJECT, "directive:OWN_1");
        PackQualifiedReference own = PackQualifiedReference.parse("reader::directive:OWN_1");

        assertThat(PackCompositionResolver.holderOf(
                        List.of(reader), reader.configured().id(), own))
                .contains(reader);
        assertThatCode(() -> check(List.of(reader), "01REA", "reader::directive:OWN_1"))
                .doesNotThrowAnyException();
    }

    @Test
    void aBareReferenceThatResolvesOnlyInAnotherPackIsRefusedNamingTheHoldingPack() {
        // the anti_pattern kind is held only as a graph node: the bare form still finds its holder
        PackUnderResolution writing = defining("01WRI", "writing", PackLevel.BUILT_IN, "anti_pattern:big-ball-of-mud");
        PackUnderResolution reader = pack("01REA", "reader", "1.0.0", PackLevel.PROJECT);

        assertThatThrownBy(() -> check(List.of(writing, reader), "01REA", "anti_pattern:big-ball-of-mud"))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.REFERENCE_UNQUALIFIED);
                    assertThat(refusal.packs())
                            .containsExactly(
                                    new RefusalPack("01REA", "01REA", "reader"),
                                    new RefusalPack("01WRI", "01WRI", "writing"));
                });
    }

    @Test
    void aQualifiedReferenceToAPackOutsideTheComposeIsRefusedNamingTheMissingPack() {
        PackUnderResolution reader = pack("01REA", "reader", "1.0.0", PackLevel.PROJECT);

        assertThatThrownBy(() -> check(List.of(reader), "01REA", "writing::directive:DIRECTIVE_009"))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.REFERENCE_PACK_MISSING);
                    assertThat(refusal.detail()).contains("writing");
                });
    }

    /**
     * The compose rooted at a set of packs (#360): only the {@code depends_on} and {@code parent_pack} closure of the
     * roots is judged, so a broken pack outside it never refuses the roots' compose. Fixture: alpha depends on beta,
     * gamma is unrelated.
     */
    @Nested
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    class Rooted {

        private final PackUnderResolution beta = pack("01BETA", "beta", "1.0.0", PackLevel.ORG);
        private final PackUnderResolution alpha =
                pack("01ALPHA", "alpha", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BETA", ">=1.0")));
        private final PackUnderResolution gamma = pack("01GAMMA", "gamma", "1.0.0", PackLevel.ORG);

        private List<PackUnderResolution> rooted(List<PackUnderResolution> available, String... roots) {
            return PackCompositionResolver.resolveRooted(available, configuredIds(roots));
        }

        @Test
        void aRootComposesOnlyWhatItBuildsOn() {
            assertThat(ids(rooted(List.of(gamma, alpha, beta), "01ALPHA"))).containsExactly("01BETA", "01ALPHA");
        }

        @Test
        void severalRootsComposeInTheOrderTheWholeComposeGives() {
            List<PackUnderResolution> all = List.of(gamma, alpha, beta);

            assertThat(rooted(all, "01ALPHA", "01GAMMA")).isEqualTo(PackCompositionResolver.resolve(all));
        }

        @Test
        void theParentPackIsInTheCompositionClosureAndComesBeforeItsChild() {
            // The child's id sorts before the parent's, so only the parent_pack edge puts beta first.
            PackUnderResolution child = withParent("01ACHILD", "child", "01BETA");

            assertThat(ids(rooted(List.of(gamma, alpha, beta, child), "01ACHILD")))
                    .containsExactly("01BETA", "01ACHILD");
        }

        @Test
        void aParentCycleIsRefusedByTheRootedComposeAndTheClosureWalkTerminates() {
            PackUnderResolution first = withParent("01FIRST", "first", "01SECOND");
            PackUnderResolution second = withParent("01SECOND", "second", "01FIRST");

            assertThat(PackCompositionResolver.compositionClosure(List.of("01FIRST"), byPackId(first, second)))
                    .containsExactly("01FIRST", "01SECOND");
            assertThat(refusalOf(() -> rooted(List.of(first, second, gamma), "01FIRST"))
                            .failure())
                    .isEqualTo(Failure.CYCLE);
        }

        @Test
        void aDuplicatePackIdOutsideTheClosureIsNeverJudged() {
            PackUnderResolution twin = configuredAs("twin", "01GAMMA", "gamma-twin");
            List<PackUnderResolution> all = List.of(alpha, beta, gamma, twin);

            assertThat(ids(rooted(all, "01ALPHA"))).containsExactly("01BETA", "01ALPHA");
            assertThat(refusalOf(() -> PackCompositionResolver.resolve(all)).failure())
                    .as("the whole compose still refuses it")
                    .isEqualTo(Failure.DUPLICATE_ID);
        }

        @Test
        void aDuplicateNameOutsideTheClosureIsNeverJudged() {
            PackUnderResolution namesake = pack("01OTHER", "gamma", "1.0.0", PackLevel.ORG);
            List<PackUnderResolution> all = List.of(alpha, beta, gamma, namesake);

            assertThat(ids(rooted(all, "01ALPHA"))).containsExactly("01BETA", "01ALPHA");
            assertThat(refusalOf(() -> PackCompositionResolver.resolve(all)).failure())
                    .isEqualTo(Failure.NAME_CONFLICT);
        }

        @Test
        void aRootsMissingDependencyIsRefusedAsTheWholeComposeRefusesIt() {
            PackUnderResolution dependent =
                    pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01GONE", ">=1.0")));

            PackCompositionResolver.Refusal rooted = refusalOf(() -> rooted(List.of(dependent, gamma), "01DEP"));
            PackCompositionResolver.Refusal whole =
                    refusalOf(() -> PackCompositionResolver.resolve(List.of(dependent)));

            assertThat(rooted.failure()).isEqualTo(Failure.UNKNOWN_DEPENDENCY);
            assertThat(rooted.packs())
                    .isEqualTo(whole.packs())
                    .containsExactly(new RefusalPack("01DEP", "01DEP", "dependent"));
            assertThat(rooted.detail()).isEqualTo(whole.detail());
        }

        @Test
        void aDuplicatePackIdInsideTheClosureIsRefusedNamingBoth() {
            PackUnderResolution twin = configuredAs("beta-twin", "01BETA", "beta-twin");

            PackCompositionResolver.Refusal refusal =
                    refusalOf(() -> rooted(List.of(alpha, beta, twin, gamma), "01ALPHA"));

            assertThat(refusal.failure()).isEqualTo(Failure.DUPLICATE_ID);
            assertThat(refusal.packs())
                    .extracting(RefusalPack::packId)
                    .containsExactlyInAnyOrder("01BETA", "beta-twin");
        }

        @Test
        void theCompositionClosureTerminatesOnACycleAndTheRootedComposeRefusesIt() {
            PackUnderResolution first =
                    pack("01FIRST", "first", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01SECOND", ">=1.0")));
            PackUnderResolution second =
                    pack("01SECOND", "second", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01FIRST", ">=1.0")));

            assertThat(PackCompositionResolver.compositionClosure(List.of("01FIRST"), byPackId(first, second)))
                    .containsExactly("01FIRST", "01SECOND");
            assertThat(refusalOf(() -> rooted(List.of(first, second, gamma), "01FIRST"))
                            .failure())
                    .isEqualTo(Failure.CYCLE);
        }

        @Test
        void theCompositionClosureRecordsAMissingTargetWithoutDescendingFromIt() {
            PackUnderResolution orphan = withParent("01ORPH", "orphan", "01MISSING");

            assertThat(PackCompositionResolver.compositionClosure(
                            List.of("01ALPHA", "01ORPH"), byPackId(alpha, beta, orphan)))
                    .containsExactly("01ALPHA", "01ORPH", "01BETA", "01MISSING");
        }

        @Test
        void aRootNoAvailablePackIsConfiguredAsIsAnArgumentErrorNotARefusal() {
            assertThatThrownBy(() -> rooted(List.of(alpha, beta), "nowhere"))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nowhere");
        }

        private PackUnderResolution withParent(String id, String name, String parent) {
            return new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(id), PackLevel.ORG),
                    new PackDescriptor(
                            id, "1.0.0", Optional.of(parent), name, Optional.of(PackLevel.ORG), Optional.empty()));
        }

        /** A pack configured under one id whose descriptor declares another {@code pack_id}. */
        private PackUnderResolution configuredAs(String configuredId, String packId, String name) {
            return new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(configuredId), PackLevel.ORG),
                    new PackDescriptor(
                            packId, "1.0.0", Optional.empty(), name, Optional.of(PackLevel.ORG), Optional.empty()));
        }

        private Map<String, PackUnderResolution> byPackId(PackUnderResolution... packs) {
            Map<String, PackUnderResolution> index = new LinkedHashMap<>();
            Arrays.stream(packs)
                    .forEach(each -> index.putIfAbsent(each.descriptor().packId(), each));
            return index;
        }

        private Collection<PackIdentifier> configuredIds(String... ids) {
            return Arrays.stream(ids).map(PackIdentifier::new).toList();
        }

        private PackCompositionResolver.Refusal refusalOf(Runnable compose) {
            try {
                compose.run();
            } catch (PackCompositionResolver.Refusal refusal) {
                return refusal;
            }
            throw new AssertionError("the compose was expected to refuse");
        }
    }

    /** The references one pack writes, each with the pack the compose says holds it, checked as the offering checks. */
    private static void check(List<PackUnderResolution> packs, String referring, String... written) {
        PackIdentifier from = new PackIdentifier(referring);
        PackCompositionResolver.checkReferences(
                packs,
                referring,
                Arrays.stream(written)
                        .map(text -> PackCompositionResolver.resolveReference(
                                packs, from, PackQualifiedReference.parse(text)))
                        .toList());
    }
}
