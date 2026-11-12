package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two compose checks of a step enhancement (ADR-0066 decision 8, FR-002): the target held (C37) and the lock a step
 * inherits from its mission type (C22 and C25 as amended, OD-2 (A)). Each refusal has a failing fixture and a twin that
 * differs only in the faulty part.
 */
class EnhancementComposeTest {

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    private static PackUnderResolution pack(String name, List<String> dependsOn, String... held) {
        return pack(name, PackLevel.ORG, dependsOn, held);
    }

    private static PackUnderResolution pack(String name, PackLevel level, List<String> dependsOn, String... held) {
        PackDescriptor descriptor = new PackDescriptor(
                "id-" + name,
                "1.0.0",
                Optional.empty(),
                name,
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                dependsOn.stream()
                        .map(other -> new PackDependency("id-" + other, ">=0"))
                        .toList());
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), Arrays.asList(held));
    }

    private static Relationship edge(String from, RelationType type, String to) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return new Relationship(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                type,
                Optional.empty(),
                Optional.empty());
    }

    private static Relationship enhances(String from, String to) {
        return edge(from, RelationType.ENHANCES, to);
    }

    private static PackUnderResolution base() {
        return pack("base", List.of(), "mission_type:M", "action:M/s");
    }

    private static PackUnderResolution enhancer() {
        return pack("enhancer", List.of("base"), "step_enhancement:x");
    }

    // ---- C37: the target is held by a pack of the enhancing pack's depends_on closure ------------------------------

    @Nested
    class TargetHeld {

        @Test
        void aQualifiedTargetTheNamedPackDoesNotHoldIsArtifactMissingNamingBothPacks() {
            PackUnderResolution base = base();
            PackUnderResolution enhancer = enhancer();

            Optional<EnhancementCompose.TargetViolation> violation = EnhancementCompose.targetHeld(
                    List.of(base, enhancer),
                    enhancer,
                    List.of(enhances("step_enhancement:x", "base::action:M/nosuch")));

            assertThat(violation).hasValueSatisfying(found -> {
                assertThat(found.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                assertThat(found.enhancement()).isEqualTo(u("step_enhancement:x"));
                assertThat(found.target()).isEqualTo(u("action:M/nosuch"));
                assertThat(found.packs()).extracting(RefusalPack::packId).containsExactly("enhancer", "base");
                assertThat(found.detail())
                        .contains("step_enhancement:x")
                        .contains("action:M/nosuch")
                        .contains("`base`");
            });
        }

        @Test
        void theTwinAQualifiedTargetTheNamedPackHoldsPasses() {
            PackUnderResolution base = base();
            PackUnderResolution enhancer = enhancer();

            assertThat(EnhancementCompose.targetHeld(
                            List.of(base, enhancer),
                            enhancer,
                            List.of(enhances("step_enhancement:x", "base::action:M/s"))))
                    .isEmpty();
        }

        @Test
        void aQualifierNamingAPackOutsideTheDependsOnClosureIsPackMissing() {
            PackUnderResolution base = base();
            PackUnderResolution other = pack("other", List.of(), "mission_type:M", "action:M/s");
            PackUnderResolution enhancer = enhancer();

            Optional<EnhancementCompose.TargetViolation> violation = EnhancementCompose.targetHeld(
                    List.of(base, other, enhancer),
                    enhancer,
                    List.of(enhances("step_enhancement:x", "other::action:M/s")));

            assertThat(violation).hasValueSatisfying(found -> {
                assertThat(found.code()).isEqualTo(RefusalCode.PACK_REFERENCE_PACK_MISSING);
                assertThat(found.packs()).extracting(RefusalPack::packId).containsExactly("enhancer", "other");
            });
        }

        @Test
        void theTwinAQualifierNamingAPackOfTheDependsOnClosurePasses() {
            PackUnderResolution base = base();
            PackUnderResolution other = pack("other", List.of(), "mission_type:M", "action:M/s");
            PackUnderResolution enhancer = enhancer();

            assertThat(EnhancementCompose.targetHeld(
                            List.of(base, other, enhancer),
                            enhancer,
                            List.of(enhances("step_enhancement:x", "base::action:M/s"))))
                    .isEmpty();
        }

        @Test
        void aQualifierNamingNoPackOfTheComposeIsPackMissingNamingTheEnhancingPack() {
            PackUnderResolution base = base();
            PackUnderResolution enhancer = enhancer();

            Optional<EnhancementCompose.TargetViolation> violation = EnhancementCompose.targetHeld(
                    List.of(base, enhancer), enhancer, List.of(enhances("step_enhancement:x", "gone::action:M/s")));

            assertThat(violation).hasValueSatisfying(found -> {
                assertThat(found.code()).isEqualTo(RefusalCode.PACK_REFERENCE_PACK_MISSING);
                assertThat(found.packs()).extracting(RefusalPack::packId).containsExactly("enhancer");
            });
        }

        @Test
        void aBareTargetIsResolvedInTheEnhancingPackItself() {
            PackUnderResolution holding =
                    pack("enhancer", List.of(), "mission_type:M", "action:M/s", "step_enhancement:x");

            assertThat(EnhancementCompose.targetHeld(
                            List.of(holding), holding, List.of(enhances("step_enhancement:x", "action:M/s"))))
                    .as("the enhancing pack holds the step")
                    .isEmpty();
        }

        @Test
        void aBareTargetTheEnhancingPackDoesNotHoldIsArtifactMissingThoughAnotherPackHoldsIt() {
            PackUnderResolution base = base();
            PackUnderResolution enhancer = enhancer();

            Optional<EnhancementCompose.TargetViolation> violation = EnhancementCompose.targetHeld(
                    List.of(base, enhancer), enhancer, List.of(enhances("step_enhancement:x", "action:M/s")));

            assertThat(violation).hasValueSatisfying(found -> {
                assertThat(found.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                assertThat(found.packs()).extracting(RefusalPack::packId).containsExactly("enhancer");
            });
        }

        @Test
        void anActionDeclaredWithoutItsMissionTypeIsNoHeldStep() {
            PackUnderResolution base = pack("base", List.of(), "action:M/s");
            PackUnderResolution enhancer = enhancer();

            assertThat(EnhancementCompose.targetHeld(
                            List.of(base, enhancer),
                            enhancer,
                            List.of(enhances("step_enhancement:x", "base::action:M/s"))))
                    .hasValueSatisfying(
                            found -> assertThat(found.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING));
        }

        @Test
        void anEnhancerOfAnotherShapeIsNotJudged() {
            PackUnderResolution base = base();
            PackUnderResolution enhancer = pack("enhancer", List.of("base"), "step_enhancement:x", "tactic:t");

            assertThat(EnhancementCompose.targetHeld(
                            List.of(base, enhancer),
                            enhancer,
                            List.of(
                                    enhances("step_enhancement:x", "base::action:M/s"),
                                    enhances("step_enhancement:x", "base::action:M/nosuch"),
                                    enhances("tactic:t", "base::action:M/nosuch"))))
                    .as("a step enhancement with two targets, and a tactic, keep rule E6 as built")
                    .isEmpty();
        }

        @Test
        void eachEdgeIsVisitedOnceSoTheCheckIsLinear() {
            assertThat(visitsFor(1_000)).isEqualTo(1_000);
            assertThat(visitsFor(2_000)).isEqualTo(2_000);
        }

        /** The visits of the edge list for {@code n} held enhancements of {@code n} held steps. */
        private int visitsFor(int n) {
            List<String> held = new ArrayList<>(List.of("mission_type:M"));
            IntStream.range(0, n).forEach(i -> held.add("action:M/s" + i));
            PackUnderResolution base = pack("base", List.of(), held.toArray(String[]::new));
            PackUnderResolution enhancer = enhancer();
            List<Relationship> edges = IntStream.range(0, n)
                    .mapToObj(i -> enhances("step_enhancement:x" + i, "base::action:M/s" + i))
                    .toList();
            AtomicInteger visits = new AtomicInteger();
            List<Relationship> counted = new AbstractList<>() {
                @Override
                public Relationship get(int index) {
                    visits.incrementAndGet();
                    return edges.get(index);
                }

                @Override
                public int size() {
                    return edges.size();
                }
            };

            assertThat(EnhancementCompose.targetHeld(List.of(base, enhancer), enhancer, counted))
                    .isEmpty();
            return visits.get();
        }
    }

    // ---- the inherited lock: a step takes its mission type's level, the enhancing pack's own policy left out ----

    @Nested
    class InheritedLock {

        private static PerPackPolicy policy(String packId, String urn, MutabilityLevel level) {
            return new PerPackPolicy(Map.of(
                    packId,
                    MutabilityPolicySource.builder().entry(u(urn), level).build()));
        }

        private static List<Relationship> edgesOf(PackUnderResolution pack) {
            return pack.configured().id().value().equals("enhancer")
                    ? List.of(enhances("step_enhancement:x", "base::action:M/s"))
                    : List.of();
        }

        @Test
        void anEnhancementOfAStepWhoseMissionTypeIsLockedIsNamedWithTheLockingPack() {
            PackUnderResolution base = base();
            PackUnderResolution enhancer = enhancer();

            List<EnhancementCompose.LockedEnhancement> locked = EnhancementCompose.lockedTargets(
                    List.of(base, enhancer),
                    EnhancementComposeTest.InheritedLock::edgesOf,
                    policy("id-base", "mission_type:M", MutabilityLevel.LOCKED));

            assertThat(locked).singleElement().satisfies(found -> {
                assertThat(found.enhancement()).isEqualTo(u("step_enhancement:x"));
                assertThat(found.target()).isEqualTo(u("action:M/s"));
                assertThat(found.missionType()).isEqualTo(u("mission_type:M"));
                assertThat(found.packs()).extracting(RefusalPack::packId).containsExactly("enhancer", "base");
                assertThat(found.detail())
                        .contains("step_enhancement:x")
                        .contains("mission_type:M")
                        .contains("locked");
            });
        }

        @Test
        void theTwinAnExtendableMissionTypeTakesTheEnhancement() {
            PackUnderResolution base = base();
            PackUnderResolution enhancer = enhancer();

            assertThat(EnhancementCompose.lockedTargets(
                            List.of(base, enhancer),
                            EnhancementComposeTest.InheritedLock::edgesOf,
                            policy("id-base", "mission_type:M", MutabilityLevel.EXTENDABLE)))
                    .isEmpty();
        }

        @Test
        void theEnhancingPacksOwnLockIsLeftOut() {
            PackUnderResolution base = base();
            PackUnderResolution enhancer = enhancer();

            assertThat(EnhancementCompose.lockedTargets(
                            List.of(base, enhancer),
                            EnhancementComposeTest.InheritedLock::edgesOf,
                            policy("id-enhancer", "mission_type:M", MutabilityLevel.LOCKED)))
                    .isEmpty();
        }

        @Test
        void aLevelOnTheStepItselfIsNotTheSubject() {
            PackUnderResolution base = base();
            PackUnderResolution enhancer = enhancer();

            assertThat(EnhancementCompose.lockedTargets(
                            List.of(base, enhancer),
                            EnhancementComposeTest.InheritedLock::edgesOf,
                            policy("id-base", "action:M/s", MutabilityLevel.EXTENDABLE)))
                    .isEmpty();
        }
    }
}
