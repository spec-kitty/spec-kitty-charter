package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
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

    /** A pack whose graphs say it changes one artifact (an {@code enhances} or {@code overrides} relation on it). */
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

    private static List<String> ids(List<PackUnderResolution> resolved) {
        return resolved.stream().map(entry -> entry.descriptor().packId()).toList();
    }

    @Test
    void aDependencyComesBeforeThePackThatDeclaresIt() {
        PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution dependent =
                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", ">=1.0")));

        assertThat(ids(PackCompositionResolver.resolve(List.of(dependent, base))))
                .containsExactly("01BASE", "01DEP");
    }

    @Test
    void aSharedDependencyAppearsOnce() {
        PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution left =
                pack("01LEFT", "left", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", ">=1.0")));
        PackUnderResolution right =
                pack("01RIGHT", "right", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", "<2.0")));

        assertThat(ids(PackCompositionResolver.resolve(List.of(left, base, right))))
                .containsExactly("01BASE", "01LEFT", "01RIGHT");
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

    @Test
    void aMalformedRangeIsRefusedNamingTheDeclaringPackNotTheTarget() {
        PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution dependent =
                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", "banana")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(base, dependent)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.OUT_OF_RANGE);
                    assertThat(refusal.packId()).isEqualTo("01DEP");
                    assertThat(refusal.detail()).contains("01DEP", "banana", "01BASE");
                });
    }

    /** Two packs ask one pack for ranges; the held version satisfies the first in every row. */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                ">=1.0,<2|>=2.0|1.5.0|false",
                "<2|>=2|1.5.0|false",
                "==1.2.*|>=1.3|1.2.4|false",
                ">=1.0,<3|>=2.0|2.5.0|true",
                "~=1.4.5|>=1.4|1.4.7|true",
                // a wildcard exclusion leaves the versions past the prefix: 2.0 satisfies both
                "!=1.*|>=1.0|2.0.0|true",
                // a prerelease of 1.0 sits above 1.0rc1 and below 1.0, as admits orders them
                ">1.0rc1|<1.0|1.0rc2|true",
                // an epoch outranks the release, so no epoch-0 version reaches it
                ">=1!1|<5|1.0.0|false",
                // an identity clause names one text, which no ordering clause can admit
                "===foo|>=1|1.5.0|false",
                // numbers of any size are numbers, not an error
                ">=1,<99999999999|<5|2.0.0|true"
            })
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

    /** A malformed range beside another declaration is refused naming its declarer, never thrown past the resolver. */
    @ParameterizedTest
    @ValueSource(strings = {">=2,banana", "~=1"})
    void aMalformedRangeBesideAnotherDeclarationIsRefusedNamingItsDeclarer(String malformed) {
        PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution first =
                pack("01ONE", "one", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", malformed)));
        PackUnderResolution second =
                pack("01TWO", "two", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", ">=1")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(second, first, base)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.OUT_OF_RANGE);
                    assertThat(refusal.packId()).isEqualTo("01ONE");
                    assertThat(refusal.detail()).contains("not a PEP 440 range");
                });
    }

    @Test
    void aTargetVersionThatIsNoPep440VersionIsRefusedNamingTheTargetNotTheDeclarersRange() {
        PackUnderResolution base = pack("01BASE", "base", "1.0.0-alpha.beta", PackLevel.BUILT_IN);
        PackUnderResolution dependent =
                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(dependent, base)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.OUT_OF_RANGE);
                    assertThat(refusal.packId()).isEqualTo("01BASE");
                    assertThat(refusal.detail()).contains("1.0.0-alpha.beta", "not a PEP 440 version");
                });
    }

    @Test
    void aVersionOutsideEveryRangeIsRefusedWithBothVersions() {
        PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution dependent =
                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", ">=2.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(dependent, base)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.OUT_OF_RANGE))
                .hasMessageContaining("1.0.0");
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
                    assertThat(refusal.packId()).isEqualTo("01OWN0");
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

    private static PackUnderResolution defining(String id, String name, PackLevel level, String artifact) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(id, "1.0.0", Optional.empty(), name, Optional.of(level), Optional.empty()),
                List.of(),
                List.of(artifact));
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
                            .contains("01LEFT", "01RIGHT", "both define the artifact directive:DIRECTIVE_009");
                });
    }

    @Test
    void packsAtDifferentLevelsDefiningOneArtifactComposeAsALayerOverrideAndAnOverridesEdgeSettlesSiblings() {
        PackUnderResolution org = defining("01ORGP", "org-pack", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution project = defining("01PROJ", "project-pack", PackLevel.PROJECT, "directive:DIRECTIVE_009");
        PackUnderResolution sibling = defining("01SIBL", "sibling", PackLevel.ORG, "directive:DIRECTIVE_009");
        // The settling edge is written pack-qualified, the definitions bare: both name the one artifact.
        PackUnderResolution settler =
                settling("01SETL", "settler", "1.0.0", PackLevel.ORG, "org-pack::directive:DIRECTIVE_009", "01ORGP");

        assertThat(ids(PackCompositionResolver.resolve(List.of(project, org)))).containsExactly("01ORGP", "01PROJ");
        assertThat(ids(PackCompositionResolver.resolve(List.of(sibling, org, settler))))
                .containsExactly("01ORGP", "01SETL", "01SIBL");
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
    void theLayerMergeAcrossLevelsStillComposesWithNoDependenciesAndNoContest() {
        PackUnderResolution builtIn = pack("01BUILT", "built-in-pack", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution org = pack("01ORGP", "org-pack", "1.0.0", PackLevel.ORG);
        PackUnderResolution project = pack("01PROJ", "project-pack", "1.0.0", PackLevel.PROJECT);

        assertThat(ids(PackCompositionResolver.resolve(List.of(project, org, builtIn))))
                .containsExactly("01BUILT", "01ORGP", "01PROJ");
    }

    @Test
    void aQualifiedReferenceToAPackOfTheComposeIsAccepted() {
        PackUnderResolution writing = pack("01WRI", "writing", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution reader =
                pack("01REA", "reader", "1.0.0", PackLevel.PROJECT, List.of(new PackDependency("01WRI", ">=1.0")));

        PackCompositionResolver.checkReferences(
                List.of(writing, reader),
                "01REA",
                List.of(new PackCompositionResolver.ResolvedReference(
                        PackQualifiedReference.parse("writing::directive:DIRECTIVE_009"), "01WRI")));
    }

    @Test
    void aQualifiedReferenceToAPackOnlyAnotherPackDependsOnIsRefusedNamingThatPack() {
        PackUnderResolution writing = pack("01WRI", "writing", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution reader = pack("01REA", "reader", "1.0.0", PackLevel.PROJECT);
        PackUnderResolution other =
                pack("01OTH", "other", "1.0.0", PackLevel.PROJECT, List.of(new PackDependency("01WRI", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.checkReferences(
                        List.of(writing, reader, other),
                        "01REA",
                        List.of(new PackCompositionResolver.ResolvedReference(
                                PackQualifiedReference.parse("writing::directive:DIRECTIVE_009"), "01WRI"))))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.REFERENCE_PACK_MISSING);
                    assertThat(refusal.packId()).isEqualTo("writing");
                    assertThat(refusal.detail()).contains("depends_on closure of pack 01REA");
                });
    }

    @Test
    void aBareReferenceThatResolvesInTheReferringPackIsAccepted() {
        PackUnderResolution reader = pack("01REA", "reader", "1.0.0", PackLevel.PROJECT);

        PackCompositionResolver.checkReferences(
                List.of(reader),
                "01REA",
                List.of(new PackCompositionResolver.ResolvedReference(
                        PackQualifiedReference.parse("directive:DIRECTIVE_009"), "01REA")));
    }

    @Test
    void aBareReferenceThatResolvesOnlyInAnotherPackIsRefusedNamingTheHoldingPack() {
        PackUnderResolution writing = pack("01WRI", "writing", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution reader = pack("01REA", "reader", "1.0.0", PackLevel.PROJECT);

        assertThatThrownBy(() -> PackCompositionResolver.checkReferences(
                        List.of(writing, reader),
                        "01REA",
                        List.of(new PackCompositionResolver.ResolvedReference(
                                PackQualifiedReference.parse("directive:DIRECTIVE_009"), "01WRI"))))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.REFERENCE_UNQUALIFIED))
                .hasMessageContaining("writing");
    }

    @Test
    void aQualifiedReferenceToAPackOutsideTheComposeIsRefusedNamingTheMissingPack() {
        PackUnderResolution reader = pack("01REA", "reader", "1.0.0", PackLevel.PROJECT);

        assertThatThrownBy(() -> PackCompositionResolver.checkReferences(
                        List.of(reader),
                        "01REA",
                        List.of(new PackCompositionResolver.ResolvedReference(
                                PackQualifiedReference.parse("writing::directive:DIRECTIVE_009"), "01WRI"))))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.REFERENCE_PACK_MISSING))
                .hasMessageContaining("writing");
    }
}
