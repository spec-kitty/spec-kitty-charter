package ai.speckitty.charter.packs.domain;

import static ai.speckitty.charter.packs.fixtures.FixtureUlids.registeredIdOf;
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
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.fixtures.FixtureUlids;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The resolver orders one closure, settles the contests an {@code overrides} edge settles, and refuses a broken graph
 * with the first fault it finds. Every composition fault, with its code, is {@code CompositionRulesTest}'s (#554,
 * T046); the reference checks and the case-only matches here are the closure walk's (#556).
 */
class PackCompositionResolverTest {

    private static PackUnderResolution pack(String id, String name, String version, PackLevel level) {
        return pack(id, name, version, level, List.of());
    }

    private static PackUnderResolution pack(
            String id, String name, String version, PackLevel level, List<PackDependency> dependencies) {
        return new PackUnderResolution(
                new ConfiguredPack(registeredIdOf(id), level),
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
                new ConfiguredPack(registeredIdOf(id), level),
                new PackDescriptor(id, version, Optional.empty(), name, Optional.of(level), Optional.empty()),
                List.of(artifact));
    }

    /**
     * A distribution pack that settles one contested artifact by naming the member whose version wins: it depends on
     * both contestants, {@code 01KZEFT0000000000000000000} and {@code 01RHZGHT000000000000000000}, as a distribution depends on its members (#358).
     */
    private static PackUnderResolution settling(
            String id, String name, String version, PackLevel level, String artifact, String member) {
        return new PackUnderResolution(
                new ConfiguredPack(registeredIdOf(id), level),
                new PackDescriptor(
                        id,
                        version,
                        Optional.empty(),
                        name,
                        Optional.of(level),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(
                                new PackDependency("01KZEFT0000000000000000000", ">=1.0"),
                                new PackDependency("01RHZGHT000000000000000000", ">=1.0")),
                        List.of(new PackOverride(artifact, member))));
    }

    /** A pack that extends another by {@code parent_pack}, with no dependencies. */
    private static PackUnderResolution extending(String id, String name, PackLevel level, String parent) {
        return new PackUnderResolution(
                new ConfiguredPack(registeredIdOf(id), level),
                new PackDescriptor(id, "1.0.0", Optional.of(parent), name, Optional.of(level), Optional.empty()));
    }

    private static List<String> ids(List<PackUnderResolution> resolved) {
        return resolved.stream().map(entry -> entry.descriptor().packId()).toList();
    }

    // One level, ids sorting the opposite way to the dependencies: only the dependency edges, across three hops, can
    // order this.
    @Test
    void aDependencyComesBeforeThePackThatDeclaresItAcrossAChain() {
        PackUnderResolution base = pack("01ZBASE0000000000000000000", "base", "1.0.0", PackLevel.ORG);
        PackUnderResolution middle = pack(
                "01MHZD01000000000000000000",
                "middle",
                "1.0.0",
                PackLevel.ORG,
                List.of(new PackDependency("01ZBASE0000000000000000000", ">=1.0")));
        PackUnderResolution top = pack(
                "01AATNZP000000000000000000",
                "top",
                "1.0.0",
                PackLevel.ORG,
                List.of(new PackDependency("01MHZD01000000000000000000", ">=1.0")));

        assertThat(ids(PackCompositionResolver.resolve(List.of(top, middle, base))))
                .containsExactly(
                        "01ZBASE0000000000000000000", "01MHZD01000000000000000000", "01AATNZP000000000000000000");
    }

    // The child's id sorts before its parent's: only the parent_pack edge can put the parent first (ADR-0048).
    @Test
    void aParentPackComesBeforeTheChildThatExtendsItWhateverTheirIdsSort() {
        PackUnderResolution parent = pack("01ZPARENT00000000000000000", "parent", "1.0.0", PackLevel.ORG);
        PackUnderResolution child =
                extending("01ACHHZKZD0000000000000000", "child", PackLevel.ORG, "01ZPARENT00000000000000000");

        assertThat(ids(PackCompositionResolver.resolve(List.of(child, parent))))
                .containsExactly("01ZPARENT00000000000000000", "01ACHHZKZD0000000000000000");
    }

    @Test
    void aParentChainIsOrderedFromTheTopDownAcrossAChain() {
        PackUnderResolution top = pack("01ZTNZP0000000000000000000", "top", "1.0.0", PackLevel.ORG);
        PackUnderResolution middle =
                extending("01MHZD00000000000000000000", "middle", PackLevel.ORG, "01ZTNZP0000000000000000000");
        PackUnderResolution leaf =
                extending("01AKZEAF000000000000000000", "leaf", PackLevel.ORG, "01MHZD00000000000000000000");

        assertThat(ids(PackCompositionResolver.resolve(List.of(leaf, middle, top))))
                .containsExactly(
                        "01ZTNZP0000000000000000000", "01MHZD00000000000000000000", "01AKZEAF000000000000000000");
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
    void theMergeOrderIsTheResolvedOrderThenTheUnplacedPacksInTheStableOrder() {
        PackUnderResolution base = pack("01ZBASE0000000000000000000", "base", "1.0.0", PackLevel.ORG);
        PackUnderResolution top = pack(
                "01AATNZP000000000000000000",
                "top",
                "1.0.0",
                PackLevel.ORG,
                List.of(new PackDependency("01ZBASE0000000000000000000", ">=1.0")));
        PackUnderResolution first = pack(
                "01MFHZRST00000000000000000",
                "first",
                "1.0.0",
                PackLevel.ORG,
                List.of(new PackDependency("01NSECNZND0000000000000000", ">=1.0")));
        PackUnderResolution second = pack(
                "01NSECNZND0000000000000000",
                "second",
                "1.0.0",
                PackLevel.ORG,
                List.of(new PackDependency("01MFHZRST00000000000000000", ">=1.0")));

        assertThat(ids(PackCompositionResolver.mergeOrder(List.of(top, base))))
                .isEqualTo(ids(PackCompositionResolver.resolve(List.of(top, base))))
                .containsExactly("01ZBASE0000000000000000000", "01AATNZP000000000000000000");
        assertThat(ids(PackCompositionResolver.mergeOrder(List.of(second, top, first, base))))
                .containsExactly(
                        "01ZBASE0000000000000000000",
                        "01AATNZP000000000000000000",
                        "01MFHZRST00000000000000000",
                        "01NSECNZND0000000000000000");
    }

    @Test
    void theRefusalIsTheFirstFaultTheResolverCollectsAndAComposeThatResolvesHasNone() {
        PackUnderResolution loop =
                extending("01KZNZNZP00000000000000000", "loop", PackLevel.ORG, "01KZNZNZP00000000000000000");
        PackUnderResolution orphan =
                extending("01NZRPH0000000000000000000", "orphan", PackLevel.ORG, "01MHZSSHZNG000000000000000");
        PackUnderResolution base = pack("01BASE00000000000000000000", "base", "1.0.0", PackLevel.ORG);

        List<PackCompositionResolver.Refusal> faults = PackCompositionResolver.faults(List.of(orphan, loop));

        assertThat(faults)
                .extracting(PackCompositionResolver.Refusal::failure)
                .containsExactly(Failure.CYCLE, Failure.PARENT_UNRESOLVED);
        assertThat(refusing(List.of(orphan, loop)))
                .extracting(
                        PackCompositionResolver.Refusal::failure,
                        PackCompositionResolver.Refusal::packs,
                        PackCompositionResolver.Refusal::detail)
                .containsExactly(
                        Failure.CYCLE,
                        faults.getFirst().packs(),
                        "pack `loop` (01KZNZNZP00000000000000000) names itself as its parent_pack");
        assertThat(refusing(List.of(orphan, loop)).packId()).isEqualTo(registeredIdOf("01KZNZNZP00000000000000000"));
        assertThat(PackCompositionResolver.faults(List.of(base))).isEmpty();
        assertThatCode(() -> PackCompositionResolver.refuseFirst(List.of())).doesNotThrowAnyException();
    }

    @Test
    void aSharedDependencyAppearsOnce() {
        PackUnderResolution base = pack("01ZBASE0000000000000000000", "base", "1.0.0", PackLevel.ORG);
        PackUnderResolution left = pack(
                "01AKZEFT000000000000000000",
                "left",
                "1.0.0",
                PackLevel.ORG,
                List.of(new PackDependency("01ZBASE0000000000000000000", ">=1.0")));
        PackUnderResolution right = pack(
                "01BRHZGHT00000000000000000",
                "right",
                "1.0.0",
                PackLevel.ORG,
                List.of(new PackDependency("01ZBASE0000000000000000000", "<2.0")));

        assertThat(ids(PackCompositionResolver.resolve(List.of(left, base, right))))
                .containsExactly(
                        "01ZBASE0000000000000000000", "01AKZEFT000000000000000000", "01BRHZGHT00000000000000000");
    }

    /** A descriptor the read would have called pack_unreadable never reaches the resolver; it is a programming error. */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"1.0.0|banana", "1.0.0|~=1", "1.0.0-alpha.beta|>=1.0"})
    void aDescriptorThatIsNotWellFormedIsAPreconditionFailureNotARefusal(String baseVersion, String range) {
        PackUnderResolution base = pack("01BASE00000000000000000000", "base", baseVersion, PackLevel.BUILT_IN);
        PackUnderResolution dependent = pack(
                "01DEP000000000000000000000",
                "dependent",
                "1.0.0",
                PackLevel.ORG,
                List.of(new PackDependency("01BASE00000000000000000000", range)));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(dependent, base)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("well-formed")
                .satisfies(failure -> assertThat(failure).isNotInstanceOf(PackCompositionResolver.Refusal.class));
    }

    // ---- the sibling refinement contest (FR-012) -----------------------------------------------------

    private static PackUnderResolution defining(String id, String name, PackLevel level, String... artifacts) {
        return new PackUnderResolution(
                new ConfiguredPack(registeredIdOf(id), level),
                new PackDescriptor(id, "1.0.0", Optional.empty(), name, Optional.of(level), Optional.empty()),
                List.of(),
                List.of(artifacts));
    }

    @Test
    void anOverridesEdgeSettlingAMissionTypeAlsoSettlesItsSharedSteps() {
        // A step is part of its mission type: the distribution pack's edge that settles m leaves no contest over m's
        // steps.
        PackUnderResolution left =
                defining("01KZEFT0000000000000000000", "left", PackLevel.ORG, "mission_type:m", "action:m/implement");
        PackUnderResolution right =
                defining("01RHZGHT000000000000000000", "right", PackLevel.ORG, "mission_type:m", "action:m/implement");
        PackUnderResolution distribution = settling(
                "01DSTR00000000000000000000",
                "distribution",
                "1.0.0",
                PackLevel.ORG,
                "mission_type:m",
                "01RHZGHT000000000000000000");

        assertThat(ids(PackCompositionResolver.resolve(List.of(distribution, right, left))))
                .containsExactly(
                        "01KZEFT0000000000000000000", "01RHZGHT000000000000000000", "01DSTR00000000000000000000");
    }

    @Test
    void siblingsDeclaringOneStepNodeWithoutItsMissionTypeCompose() {
        PackUnderResolution left = defining("01KZEFT0000000000000000000", "left", PackLevel.ORG, "action:m/implement");
        PackUnderResolution right =
                defining("01RHZGHT000000000000000000", "right", PackLevel.ORG, "action:m/implement");

        assertThat(ids(PackCompositionResolver.resolve(List.of(right, left))))
                .containsExactly("01KZEFT0000000000000000000", "01RHZGHT000000000000000000");
    }

    @Test
    void packsAtDifferentLevelsDefiningOneArtifactComposeAsALayerOverride() {
        PackUnderResolution org =
                defining("01NZRGP0000000000000000000", "org-pack", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution project =
                defining("01PRNZJ0000000000000000000", "project-pack", PackLevel.PROJECT, "directive:DIRECTIVE_009");

        assertThat(ids(PackCompositionResolver.resolve(List.of(project, org))))
                .containsExactly("01NZRGP0000000000000000000", "01PRNZJ0000000000000000000");
    }

    @Test
    void aSettlingOverridesEdgeResolvesTheContestInOrder() {
        PackUnderResolution left =
                refining("01KZEFT0000000000000000000", "left", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution right =
                refining("01RHZGHT000000000000000000", "right", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution distribution = settling(
                "01DSTR00000000000000000000",
                "distribution",
                "1.0.0",
                PackLevel.ORG,
                "directive:DIRECTIVE_009",
                "01RHZGHT000000000000000000");

        assertThat(ids(PackCompositionResolver.resolve(List.of(distribution, right, left))))
                .containsExactly(
                        "01KZEFT0000000000000000000", "01RHZGHT000000000000000000", "01DSTR00000000000000000000");
    }

    @Test
    void aContestantDoesNotSettleItsOwnContest() {
        // #358: only the distribution pack settles a contest; a contestant's own edge is refused, naming it.
        PackUnderResolution left = new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier("left"), PackLevel.ORG),
                new PackDescriptor(
                        "01KZEFT0000000000000000000",
                        "1.0.4",
                        Optional.empty(),
                        "left",
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(),
                        List.of(new PackOverride("directive:DIRECTIVE_009", "01RHZGHT000000000000000000"))),
                List.of("directive:DIRECTIVE_009"));
        PackUnderResolution right =
                refining("01RHZGHT000000000000000000", "right", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(right, left)))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(PackCompositionResolver.Failure.SIBLING_CONFLICT);
                    assertThat(refusal.detail())
                            .contains(
                                    "declared by pack `left` (01KZEFT0000000000000000000), which is not the distribution pack");
                });
    }

    @Test
    void siblingsChangingDifferentArtifactsResolve() {
        PackUnderResolution left =
                refining("01KZEFT0000000000000000000", "left", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution right =
                refining("01RHZGHT000000000000000000", "right", "1.0.0", PackLevel.ORG, "tactic:alpha-tactic");

        assertThat(ids(PackCompositionResolver.resolve(List.of(right, left))))
                .containsExactly("01KZEFT0000000000000000000", "01RHZGHT000000000000000000");
    }

    @Test
    void packsAtDifferentLevelsChangingOneArtifactComposeAsALayerMerge() {
        PackUnderResolution org =
                refining("01NZRGP0000000000000000000", "org-pack", "1.0.0", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution project = refining(
                "01PRNZJ0000000000000000000", "project-pack", "1.0.0", PackLevel.PROJECT, "directive:DIRECTIVE_009");

        assertThat(ids(PackCompositionResolver.resolve(List.of(project, org))))
                .containsExactly("01NZRGP0000000000000000000", "01PRNZJ0000000000000000000");
    }

    @Test
    void aQualifiedReferenceToAnArtifactThePackHoldsIsAcceptedWhateverItsKind() {
        // an anti_pattern is held by its file, and the qualified form must stand like a directive's
        PackUnderResolution writing = defining(
                "01WRHZ00000000000000000000",
                "writing",
                PackLevel.BUILT_IN,
                "directive:DIRECTIVE_009",
                "anti_pattern:big-ball-of-mud");
        PackUnderResolution reader = pack(
                "01REA000000000000000000000",
                "reader",
                "1.0.0",
                PackLevel.PROJECT,
                List.of(new PackDependency("01WRHZ00000000000000000000", ">=1.0")));

        assertThatCode(() -> check(
                        List.of(writing, reader),
                        "01REA000000000000000000000",
                        "writing::directive:DIRECTIVE_009",
                        "writing::anti_pattern:big-ball-of-mud"))
                .doesNotThrowAnyException();
    }

    @Test
    void aQualifiedReferenceToAnArtifactThePackDoesNotHoldIsRefusedNamingTheReferrerThenTheTarget() {
        PackUnderResolution writing = pack("01WRHZ00000000000000000000", "writing", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution reader = pack(
                "01REA000000000000000000000",
                "reader",
                "1.0.0",
                PackLevel.PROJECT,
                List.of(new PackDependency("01WRHZ00000000000000000000", ">=1.0")));

        assertThatThrownBy(() -> check(
                        List.of(writing, reader), "01REA000000000000000000000", "writing::directive:DIRECTIVE_009"))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.REFERENCE_ARTIFACT_MISSING);
                    assertThat(refusal.failure().code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                    assertThat(refusal.packs())
                            .containsExactly(
                                    new RefusalPack(
                                            new PackUlid("01REA000000000000000000000"),
                                            registeredIdOf("01REA000000000000000000000"),
                                            "reader"),
                                    new RefusalPack(
                                            new PackUlid("01WRHZ00000000000000000000"),
                                            registeredIdOf("01WRHZ00000000000000000000"),
                                            "writing"));
                    assertThat(refusal.detail())
                            .contains("`reader` (01REA000000000000000000000)")
                            .contains("`writing` (01WRHZ00000000000000000000) does not hold `directive:DIRECTIVE_009`");
                });
    }

    @Test
    void everyReferenceFaultOfAPackIsFoundInTheReferencesOrder() {
        PackUnderResolution writing =
                defining("01WRHZ00000000000000000000", "writing", PackLevel.BUILT_IN, "directive:DIRECTIVE_009");
        PackUnderResolution reader = pack(
                "01REA000000000000000000000",
                "reader",
                "1.0.0",
                PackLevel.PROJECT,
                List.of(new PackDependency("01WRHZ00000000000000000000", ">=1.0")));
        List<PackUnderResolution> packs = List.of(writing, reader);
        PackIdentifier from = registeredIdOf("01REA000000000000000000000");

        List<PackCompositionResolver.Refusal> faults = PackCompositionResolver.referenceFaults(
                packs,
                "01REA000000000000000000000",
                java.util.stream.Stream.of(
                                "writing::directive:DIRECTIVE_001",
                                "writing::directive:DIRECTIVE_009",
                                "gone::directive:DIRECTIVE_009",
                                "directive:DIRECTIVE_009")
                        .map(text -> PackCompositionResolver.resolveReference(
                                packs, from, PackQualifiedReference.parse(text)))
                        .toList());

        assertThat(faults)
                .extracting(PackCompositionResolver.Refusal::failure)
                .containsExactly(
                        Failure.REFERENCE_ARTIFACT_MISSING,
                        Failure.REFERENCE_PACK_MISSING,
                        Failure.REFERENCE_UNQUALIFIED);
        assertThat(faults.getFirst().detail()).contains("directive:DIRECTIVE_001");
    }

    @Test
    void aQualifiedReferenceToAPackOnlyAnotherPackDependsOnIsRefusedNamingThatPack() {
        PackUnderResolution writing =
                defining("01WRHZ00000000000000000000", "writing", PackLevel.BUILT_IN, "directive:DIRECTIVE_009");
        PackUnderResolution reader = pack("01REA000000000000000000000", "reader", "1.0.0", PackLevel.PROJECT);
        PackUnderResolution other = pack(
                "01NZTH00000000000000000000",
                "other",
                "1.0.0",
                PackLevel.PROJECT,
                List.of(new PackDependency("01WRHZ00000000000000000000", ">=1.0")));

        assertThatThrownBy(() -> check(
                        List.of(writing, reader, other),
                        "01REA000000000000000000000",
                        "writing::directive:DIRECTIVE_009"))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.REFERENCE_PACK_MISSING);
                    assertThat(refusal.packs())
                            .containsExactly(
                                    new RefusalPack(
                                            new PackUlid("01REA000000000000000000000"),
                                            registeredIdOf("01REA000000000000000000000"),
                                            "reader"),
                                    new RefusalPack(
                                            new PackUlid("01WRHZ00000000000000000000"),
                                            registeredIdOf("01WRHZ00000000000000000000"),
                                            "writing"));
                    assertThat(refusal.detail())
                            .contains("`writing` (01WRHZ00000000000000000000) is outside its depends_on closure");
                });
    }

    @Test
    void aBareReferenceIsHeldByTheReferringPackWhichShadowsAnotherPacksArtifactOfTheSameId() {
        // FR-014: the reader holds directive:DIRECTIVE_009 and so does the writing pack; the reader's own shadows it.
        // A bare reference no pack holds (an unwired kind) resolves nowhere and refuses nothing.
        PackUnderResolution writing =
                defining("01WRHZ00000000000000000000", "writing", PackLevel.BUILT_IN, "directive:DIRECTIVE_009");
        PackUnderResolution reader =
                defining("01REA000000000000000000000", "reader", PackLevel.PROJECT, "directive:DIRECTIVE_009");
        PackQualifiedReference own = PackQualifiedReference.parse("directive:DIRECTIVE_009");

        assertThat(PackCompositionResolver.holderOf(
                        List.of(writing, reader), reader.configured().id(), own))
                .contains(reader);
        assertThatCode(() -> check(
                        List.of(writing, reader),
                        "01REA000000000000000000000",
                        "directive:DIRECTIVE_009",
                        "directive:NOWHERE"))
                .doesNotThrowAnyException();
    }

    @Test
    void aReferenceQualifiedWithTheReferringPacksOwnNameIsHeldByIt() {
        // FR-013: the pack's own artifacts are reachable both bare and qualified (another artifact than the writer's).
        PackUnderResolution reader =
                defining("01REA000000000000000000000", "reader", PackLevel.PROJECT, "directive:OWN_1");
        PackQualifiedReference own = PackQualifiedReference.parse("reader::directive:OWN_1");

        assertThat(PackCompositionResolver.holderOf(
                        List.of(reader), reader.configured().id(), own))
                .contains(reader);
        assertThatCode(() -> check(List.of(reader), "01REA000000000000000000000", "reader::directive:OWN_1"))
                .doesNotThrowAnyException();
    }

    @Test
    void aBareReferenceThatResolvesOnlyInAnotherPackIsRefusedNamingTheHoldingPack() {
        // an anti_pattern is held by its file, as any artifact is: the bare form still finds its holder
        PackUnderResolution writing =
                defining("01WRHZ00000000000000000000", "writing", PackLevel.BUILT_IN, "anti_pattern:big-ball-of-mud");
        PackUnderResolution reader = pack("01REA000000000000000000000", "reader", "1.0.0", PackLevel.PROJECT);

        assertThatThrownBy(() ->
                        check(List.of(writing, reader), "01REA000000000000000000000", "anti_pattern:big-ball-of-mud"))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, refusal -> {
                    assertThat(refusal.failure()).isEqualTo(Failure.REFERENCE_UNQUALIFIED);
                    assertThat(refusal.packs())
                            .containsExactly(
                                    new RefusalPack(
                                            new PackUlid("01REA000000000000000000000"),
                                            registeredIdOf("01REA000000000000000000000"),
                                            "reader"),
                                    new RefusalPack(
                                            new PackUlid("01WRHZ00000000000000000000"),
                                            registeredIdOf("01WRHZ00000000000000000000"),
                                            "writing"));
                });
    }

    @Test
    void aQualifiedReferenceToAPackOutsideTheComposeIsRefusedNamingTheMissingPack() {
        PackUnderResolution reader = pack("01REA000000000000000000000", "reader", "1.0.0", PackLevel.PROJECT);

        assertThatThrownBy(
                        () -> check(List.of(reader), "01REA000000000000000000000", "writing::directive:DIRECTIVE_009"))
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

        private final PackUnderResolution beta = pack("01BETA00000000000000000000", "beta", "1.0.0", PackLevel.ORG);
        private final PackUnderResolution alpha = pack(
                "01AKZPHA000000000000000000",
                "alpha",
                "1.0.0",
                PackLevel.ORG,
                List.of(new PackDependency("01BETA00000000000000000000", ">=1.0")));
        private final PackUnderResolution gamma = pack("01GAMMA0000000000000000000", "gamma", "1.0.0", PackLevel.ORG);

        private List<PackUnderResolution> rooted(List<PackUnderResolution> available, String... roots) {
            PackCompositionResolver.Rooted composed =
                    PackCompositionResolver.composeRooted(available, configuredIds(roots));
            PackCompositionResolver.refuseFirst(composed.faults());
            return composed.order();
        }

        @Test
        void aRootComposesOnlyWhatItBuildsOn() {
            assertThat(ids(rooted(List.of(gamma, alpha, beta), "01AKZPHA000000000000000000")))
                    .containsExactly("01BETA00000000000000000000", "01AKZPHA000000000000000000");
        }

        @Test
        void severalRootsComposeInTheOrderTheWholeComposeGives() {
            List<PackUnderResolution> all = List.of(gamma, alpha, beta);

            assertThat(rooted(all, "01AKZPHA000000000000000000", "01GAMMA0000000000000000000"))
                    .isEqualTo(PackCompositionResolver.resolve(all));
        }

        @Test
        void theParentPackIsInTheCompositionClosureAndComesBeforeItsChild() {
            // The child's id sorts before the parent's, so only the parent_pack edge puts beta first.
            PackUnderResolution child = withParent("01ACHHZKZD0000000000000000", "child", "01BETA00000000000000000000");

            assertThat(ids(rooted(List.of(gamma, alpha, beta, child), "01ACHHZKZD0000000000000000")))
                    .containsExactly("01BETA00000000000000000000", "01ACHHZKZD0000000000000000");
        }

        @Test
        void theRootedComposeKeepsEveryFaultOfItsClosureAndResolveRootedRefusesTheFirst() {
            PackUnderResolution twoUnknown = pack(
                    "01TWZ00000000000000000000Z",
                    "two",
                    "1.0.0",
                    PackLevel.ORG,
                    List.of(
                            new PackDependency("01NZNE00000000000000000000", ">=1.0"),
                            new PackDependency("01NZNE00000000000000000001", ">=1.0")));

            PackCompositionResolver.Rooted rooted = PackCompositionResolver.composeRooted(
                    List.of(twoUnknown, gamma), configuredIds("01TWZ00000000000000000000Z"));

            assertThat(rooted.faults())
                    .extracting(PackCompositionResolver.Refusal::failure)
                    .containsExactly(Failure.UNKNOWN_DEPENDENCY, Failure.UNKNOWN_DEPENDENCY);
            assertThat(refusalOf(() -> rooted(List.of(twoUnknown, gamma), "01TWZ00000000000000000000Z"))
                            .detail())
                    .isEqualTo(rooted.faults().getFirst().detail());
        }

        @Test
        void aRootedComposeThatResolvesHasNoFaultAndTheOrderResolveRootedGives() {
            List<PackUnderResolution> all = List.of(gamma, alpha, beta);
            PackCompositionResolver.Rooted rooted =
                    PackCompositionResolver.composeRooted(all, configuredIds("01AKZPHA000000000000000000"));

            assertThat(rooted.faults()).isEmpty();
            assertThat(rooted.order()).isEqualTo(rooted(all, "01AKZPHA000000000000000000"));
        }

        @Test
        void aParentCycleIsRefusedByTheRootedComposeAndTheClosureWalkTerminates() {
            PackUnderResolution first = withParent("01FHZRST000000000000000000", "first", "01SECNZND00000000000000000");
            PackUnderResolution second =
                    withParent("01SECNZND00000000000000000", "second", "01FHZRST000000000000000000");

            assertThat(PackGraph.compositionClosure(List.of("01FHZRST000000000000000000"), byPackId(first, second)))
                    .containsExactly("01FHZRST000000000000000000", "01SECNZND00000000000000000");
            assertThat(refusalOf(() -> rooted(List.of(first, second, gamma), "01FHZRST000000000000000000"))
                            .failure())
                    .isEqualTo(Failure.CYCLE);
        }

        @Test
        void aDuplicatePackIdOutsideTheClosureIsNeverJudged() {
            PackUnderResolution twin = configuredAs("twin", "01GAMMA0000000000000000000", "gamma-twin");
            List<PackUnderResolution> all = List.of(alpha, beta, gamma, twin);

            assertThat(ids(rooted(all, "01AKZPHA000000000000000000")))
                    .containsExactly("01BETA00000000000000000000", "01AKZPHA000000000000000000");
            assertThat(refusalOf(() -> PackCompositionResolver.resolve(all)).failure())
                    .as("the whole compose still refuses it")
                    .isEqualTo(Failure.DUPLICATE_ID);
        }

        @Test
        void aDuplicateNameOutsideTheClosureIsNeverJudged() {
            PackUnderResolution namesake = pack("01NZTHER000000000000000000", "gamma", "1.0.0", PackLevel.ORG);
            List<PackUnderResolution> all = List.of(alpha, beta, gamma, namesake);

            assertThat(ids(rooted(all, "01AKZPHA000000000000000000")))
                    .containsExactly("01BETA00000000000000000000", "01AKZPHA000000000000000000");
            assertThat(refusalOf(() -> PackCompositionResolver.resolve(all)).failure())
                    .isEqualTo(Failure.NAME_CONFLICT);
        }

        @Test
        void aRootsMissingDependencyIsRefusedAsTheWholeComposeRefusesIt() {
            PackUnderResolution dependent = pack(
                    "01DEP000000000000000000000",
                    "dependent",
                    "1.0.0",
                    PackLevel.ORG,
                    List.of(new PackDependency("01GNZNE0000000000000000000", ">=1.0")));

            PackCompositionResolver.Refusal rooted =
                    refusalOf(() -> rooted(List.of(dependent, gamma), "01DEP000000000000000000000"));
            PackCompositionResolver.Refusal whole =
                    refusalOf(() -> PackCompositionResolver.resolve(List.of(dependent)));

            assertThat(rooted.failure()).isEqualTo(Failure.UNKNOWN_DEPENDENCY);
            assertThat(rooted.packs())
                    .isEqualTo(whole.packs())
                    .containsExactly(new RefusalPack(
                            new PackUlid("01DEP000000000000000000000"),
                            registeredIdOf("01DEP000000000000000000000"),
                            "dependent"));
            assertThat(rooted.detail()).isEqualTo(whole.detail());
        }

        @Test
        void aDuplicatePackIdInsideTheClosureIsRefusedNamingBoth() {
            PackUnderResolution twin = configuredAs("beta-twin", "01BETA00000000000000000000", "beta-twin");

            PackCompositionResolver.Refusal refusal =
                    refusalOf(() -> rooted(List.of(alpha, beta, twin, gamma), "01AKZPHA000000000000000000"));

            assertThat(refusal.failure()).isEqualTo(Failure.DUPLICATE_ID);
            assertThat(refusal.packs())
                    .extracting(pack -> pack.packId().value())
                    .containsExactlyInAnyOrder(
                            registeredIdOf("01BETA00000000000000000000").value(), "beta-twin");
        }

        @Test
        void theCompositionClosureTerminatesOnACycleAndTheRootedComposeRefusesIt() {
            PackUnderResolution first = pack(
                    "01FHZRST000000000000000000",
                    "first",
                    "1.0.0",
                    PackLevel.ORG,
                    List.of(new PackDependency("01SECNZND00000000000000000", ">=1.0")));
            PackUnderResolution second = pack(
                    "01SECNZND00000000000000000",
                    "second",
                    "1.0.0",
                    PackLevel.ORG,
                    List.of(new PackDependency("01FHZRST000000000000000000", ">=1.0")));

            assertThat(PackGraph.compositionClosure(List.of("01FHZRST000000000000000000"), byPackId(first, second)))
                    .containsExactly("01FHZRST000000000000000000", "01SECNZND00000000000000000");
            assertThat(refusalOf(() -> rooted(List.of(first, second, gamma), "01FHZRST000000000000000000"))
                            .failure())
                    .isEqualTo(Failure.CYCLE);
        }

        @Test
        void theCompositionClosureRecordsAMissingTargetWithoutDescendingFromIt() {
            PackUnderResolution orphan =
                    withParent("01NZRPH0000000000000000000", "orphan", "01MHZSSHZNG000000000000000");

            assertThat(PackGraph.compositionClosure(
                            List.of("01AKZPHA000000000000000000", "01NZRPH0000000000000000000"),
                            byPackId(alpha, beta, orphan)))
                    .containsExactly(
                            "01AKZPHA000000000000000000",
                            "01NZRPH0000000000000000000",
                            "01BETA00000000000000000000",
                            "01MHZSSHZNG000000000000000");
        }

        @Test
        void aRootNoAvailablePackIsConfiguredAsIsAnArgumentErrorNotARefusal() {
            assertThatThrownBy(() -> rooted(List.of(alpha, beta), "nowhere"))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nowhere");
        }

        private PackUnderResolution withParent(String id, String name, String parent) {
            return new PackUnderResolution(
                    new ConfiguredPack(registeredIdOf(id), PackLevel.ORG),
                    new PackDescriptor(
                            id, "1.0.0", Optional.of(parent), name, Optional.of(PackLevel.ORG), Optional.empty()));
        }

        /** A pack configured under one id whose descriptor declares another {@code pack_id}. */
        private PackUnderResolution configuredAs(String configuredId, String packId, String name) {
            return new PackUnderResolution(
                    new ConfiguredPack(registeredIdOf(configuredId), PackLevel.ORG),
                    new PackDescriptor(
                            packId, "1.0.0", Optional.empty(), name, Optional.of(PackLevel.ORG), Optional.empty()));
        }

        private Function<String, Optional<PackDescriptor>> byPackId(PackUnderResolution... packs) {
            Map<String, PackUnderResolution> index = new LinkedHashMap<>();
            Arrays.stream(packs)
                    .forEach(each -> index.putIfAbsent(each.descriptor().packId(), each));
            return packId -> Optional.ofNullable(index.get(packId)).map(PackUnderResolution::descriptor);
        }

        private Collection<PackIdentifier> configuredIds(String... ids) {
            return Arrays.stream(ids).map(FixtureUlids::registeredIdOf).toList();
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

    // ---- one pack's checks within a closure: the one body registration and pack-doctor --full read (#632, R-s) ----

    /** A pack's facts that write only these references and these substep delegation candidates. */
    private static PackCompositionFacts writing(
            List<String> references, List<SubstepDelegationRule.QualifiedCandidate> delegations) {
        return new PackCompositionFacts(
                List.of(),
                List.of(),
                List.of(),
                references,
                MutabilityPolicySource.empty(),
                List.of(),
                List.of(),
                Map.of(),
                PromptFacts.NONE,
                List.of(),
                delegations);
    }

    private static SubstepDelegationRule.QualifiedCandidate delegating(String reference) {
        return new SubstepDelegationRule.QualifiedCandidate(
                "missions/rx/mission.yaml", "rx", "implement", "delegate", PackQualifiedReference.parse(reference));
    }

    @Test
    void onePacksDelegationFaultsWithinAClosureAreEveryFaultOfTheCollectAllForm() {
        PackUnderResolution writing = pack("01WRHZ00000000000000000000", "writing", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution reader = pack(
                "01REA000000000000000000000",
                "reader",
                "1.0.0",
                PackLevel.PROJECT,
                List.of(new PackDependency("01WRHZ00000000000000000000", ">=1.0")));
        List<PackUnderResolution> closure = List.of(writing, reader);
        List<SubstepDelegationRule.QualifiedCandidate> candidates =
                List.of(delegating("writing::tactic:first"), delegating("writing::tactic:second"));

        List<ClosureFault> faults =
                PackCompositionResolver.delegationFaultsOf(closure, reader, writing(List.of(), candidates));

        assertThat(faults).hasSize(2).allSatisfy(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(fault.violations())
                    .singleElement()
                    .satisfies(violation -> assertThat(violation.code()).isEqualTo(SubstepDelegationRule.UNRESOLVED));
        });
        assertThat(faults.getFirst().detail()).contains("tactic:first");
        assertThat(faults.get(1).detail()).contains("tactic:second");
        assertThat(faults)
                .isEqualTo(PackCompositionResolver.unresolvedDelegations(
                        closure, "01REA000000000000000000000", candidates));
    }

    @Test
    void onePacksReferenceFaultsWithinAClosureAreEveryFaultAndTheFirstIsWhatAOneRefusalCallerAnswers() {
        PackUnderResolution writing =
                defining("01WRHZ00000000000000000000", "writing", PackLevel.BUILT_IN, "directive:DIRECTIVE_009");
        PackUnderResolution reader = pack(
                "01REA000000000000000000000",
                "reader",
                "1.0.0",
                PackLevel.PROJECT,
                List.of(new PackDependency("01WRHZ00000000000000000000", ">=1.0")));
        List<PackUnderResolution> closure = List.of(writing, reader);
        String[] written = {"writing::directive:DIRECTIVE_001", "writing::directive:DIRECTIVE_009", "gone::directive:X"
        };

        List<ClosureFault> faults =
                PackCompositionResolver.referenceFaultsOf(closure, reader, writing(List.of(written), List.of()));

        assertThat(faults)
                .extracting(ClosureFault::code)
                .containsExactly(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING, RefusalCode.PACK_REFERENCE_PACK_MISSING);
        assertThatThrownBy(() -> check(closure, "01REA000000000000000000000", written))
                .isInstanceOfSatisfying(PackCompositionResolver.Refusal.class, first -> {
                    assertThat(first.failure().code())
                            .isEqualTo(faults.getFirst().code());
                    assertThat(first.detail()).isEqualTo(faults.getFirst().detail());
                    assertThat(first.packs()).isEqualTo(faults.getFirst().packs());
                });
    }

    /** The references one pack writes, each with the pack the compose says holds it, checked as the offering checks. */
    private static void check(List<PackUnderResolution> packs, String referring, String... written) {
        PackIdentifier from = registeredIdOf(referring);
        PackCompositionResolver.refuseFirst(PackCompositionResolver.referenceFaults(
                packs,
                referring,
                Arrays.stream(written)
                        .map(text -> PackCompositionResolver.resolveReference(
                                packs, from, PackQualifiedReference.parse(text)))
                        .toList()));
    }
}
