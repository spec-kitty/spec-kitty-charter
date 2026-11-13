package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The inherited lock at registration (C22 and C25 as amended, OD-2 (A)): an enhancement of {@code action:M/s} is
 * refused {@code override_not_permitted} when {@code mission_type:M} is locked along the closure without the enhancing
 * pack's own policy, as the {@code overrides} rule beside it judges ({@code judgedWithout}). Each case has a twin that
 * differs only in the level.
 */
class ImpactRelationsEnhancementTest {

    private final List<PackUnderResolution> packs = new ArrayList<>();
    private final Map<String, PackCompositionFacts> facts = new LinkedHashMap<>();

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    private void pack(
            String name,
            List<String> dependsOn,
            MutabilityPolicySource policy,
            List<String> held,
            Relationship... edges) {
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
        packs.add(new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(name), PackLevel.ORG), descriptor, List.of(), held));
        facts.put(name, new PackCompositionFacts(List.of(), held, List.of(), policy, List.of(), List.of(edges)));
    }

    private static Relationship enhances(String from, String to) {
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return new Relationship(
                u(from),
                Optional.empty(),
                target.asUrn().orElseThrow(),
                target.packName(),
                RelationType.ENHANCES,
                Optional.empty(),
                Optional.empty());
    }

    private static MutabilityPolicySource level(String urn, MutabilityLevel level) {
        return MutabilityPolicySource.builder().entry(u(urn), level).build();
    }

    /** A base holding software-dev and its step plan under a policy, and an org pack enhancing plan. */
    private Optional<ImpactRelations.Violation> enhancingPlanUnder(
            MutabilityPolicySource basePolicy, MutabilityPolicySource enhancerPolicy) {
        pack("base", List.of(), basePolicy, List.of("mission_type:software-dev", "action:software-dev/plan"));
        pack(
                "enhancer",
                List.of("base"),
                enhancerPolicy,
                List.of("step_enhancement:spdd-plan"),
                enhances("step_enhancement:spdd-plan", "base::action:software-dev/plan"));
        return ImpactRelations.check(new OfferingReadService.ComposedClosure(packs, facts));
    }

    @Test
    void anEnhancementOfAStepWhoseMissionTypeIsLockedIsOverrideNotPermittedNamingBothPacks() {
        Optional<ImpactRelations.Violation> broken = enhancingPlanUnder(
                level("mission_type:software-dev", MutabilityLevel.LOCKED), MutabilityPolicySource.empty());

        assertThat(broken).hasValueSatisfying(violation -> {
            assertThat(violation.code()).isEqualTo(RefusalCode.OVERRIDE_NOT_PERMITTED);
            assertThat(violation.detail())
                    .contains("step_enhancement:spdd-plan")
                    .contains("mission_type:software-dev")
                    .contains("locked");
            assertThat(violation.packs())
                    .as("the enhancing pack first, then the pack that locks the mission type")
                    .extracting(pack -> pack.configured().id().value())
                    .containsExactly("enhancer", "base");
        });
    }

    @Test
    void theTwinAnExtendableMissionTypeTakesTheEnhancement() {
        assertThat(enhancingPlanUnder(
                        level("mission_type:software-dev", MutabilityLevel.EXTENDABLE), MutabilityPolicySource.empty()))
                .isEmpty();
    }

    @Test
    void theEnhancingPacksOwnLockOnTheMissionTypeIsLeftOut() {
        assertThat(enhancingPlanUnder(
                        MutabilityPolicySource.empty(), level("mission_type:software-dev", MutabilityLevel.LOCKED)))
                .as("judged without the enhancing pack's policy, as the overrides rule is (B8)")
                .isEmpty();
    }
}
