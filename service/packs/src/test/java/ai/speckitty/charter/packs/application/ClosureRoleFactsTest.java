package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ClosureRoleFacts.FallbackFact;
import ai.speckitty.charter.packs.application.ClosureRoleFacts.Member;
import ai.speckitty.charter.packs.application.ClosureRoleFacts.PinFact;
import ai.speckitty.charter.packs.domain.RolePins;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The fallback and the pins in force for a set of packs (ADR-0068 decision 5, ruling D3): the most derived declaring
 * pack's fallback wins, unrelated packs that name different fallbacks conflict, and any differing pin of one role
 * conflicts whatever the derivation. Packs are given in closure order, dependencies first.
 */
class ClosureRoleFactsTest {

    private static final PackIdentifier BUILT_IN = new PackIdentifier("built-in");
    private static final PackIdentifier ORG = new PackIdentifier("org");
    private static final PackIdentifier OTHER_ORG = new PackIdentifier("other-org");
    private static final PackIdentifier PROJECT = new PackIdentifier("project");

    private static Member member(PackIdentifier pack, List<PackIdentifier> buildsOn, String fallback) {
        return new Member(pack, buildsOn, Optional.ofNullable(fallback), RolePins.NONE);
    }

    private static Member pinning(PackIdentifier pack, List<PackIdentifier> buildsOn, Map<String, String> pins) {
        return new Member(pack, buildsOn, Optional.empty(), new RolePins(pins));
    }

    @Test
    void theMostDerivedDeclaringPackNamesTheFallbackInForce() {
        ClosureRoleFacts facts = ClosureRoleFacts.of(List.of(
                member(BUILT_IN, List.of(), "generic-agent"),
                member(ORG, List.of(BUILT_IN), "org-generalist"),
                member(PROJECT, List.of(ORG), null)));

        assertThat(facts.fallbackInForce())
                .as("project builds on org, which builds on built-in; org is the most derived declaring pack")
                .isEqualTo(new FallbackFact.Declared("org-generalist", ORG));
    }

    @Test
    void derivationIsTransitiveAndNotTheClosureOrder() {
        ClosureRoleFacts facts = ClosureRoleFacts.of(List.of(
                member(BUILT_IN, List.of(), "generic-agent"),
                member(ORG, List.of(BUILT_IN), null),
                member(PROJECT, List.of(ORG), "project-generalist")));

        assertThat(facts.fallbackInForce()).isEqualTo(new FallbackFact.Declared("project-generalist", PROJECT));
    }

    @Test
    void unrelatedPacksThatNameDifferentFallbacksConflict() {
        ClosureRoleFacts facts = ClosureRoleFacts.of(List.of(
                member(BUILT_IN, List.of(), "generic-agent"),
                member(ORG, List.of(BUILT_IN), "org-generalist"),
                member(OTHER_ORG, List.of(BUILT_IN), "other-generalist")));

        assertThat(facts.fallbackInForce())
                .as("built-in is outranked by both; the siblings disagree")
                .isEqualTo(new FallbackFact.Conflict(List.of(ORG, OTHER_ORG)));
    }

    @Test
    void unrelatedPacksThatNameTheSameFallbackAgree() {
        ClosureRoleFacts facts = ClosureRoleFacts.of(List.of(
                member(BUILT_IN, List.of(), "generic-agent"),
                member(ORG, List.of(BUILT_IN), "shared-generalist"),
                member(OTHER_ORG, List.of(BUILT_IN), "shared-generalist")));

        assertThat(facts.fallbackInForce())
                .isInstanceOfSatisfying(
                        FallbackFact.Declared.class,
                        declared -> assertThat(declared.profileId()).isEqualTo("shared-generalist"));
    }

    @Test
    void noDeclaringPackIsNone() {
        assertThat(ClosureRoleFacts.of(List.of(member(PROJECT, List.of(), null)))
                        .fallbackInForce())
                .isEqualTo(new FallbackFact.None());
    }

    @Test
    void aPinIsInForceAndAnUnpinnedRoleIsNone() {
        ClosureRoleFacts facts = ClosureRoleFacts.of(List.of(
                member(BUILT_IN, List.of(), null), pinning(PROJECT, List.of(BUILT_IN), Map.of("reviewer", "renata"))));

        assertThat(facts.pinFor("reviewer")).isEqualTo(new PinFact.Pinned("renata", PROJECT));
        assertThat(facts.pinFor("implementer")).isEqualTo(new PinFact.None());
        assertThat(facts.pinnedRoles()).containsExactly("reviewer");
        assertThat(facts.pinsOf(BUILT_IN)).isEqualTo(RolePins.NONE);
    }

    @Test
    void theSamePinRepeatedAlongTheDerivationIsInForceFromTheMostDerivedPack() {
        ClosureRoleFacts facts = ClosureRoleFacts.of(List.of(
                pinning(ORG, List.of(), Map.of("reviewer", "renata")),
                pinning(PROJECT, List.of(ORG), Map.of("reviewer", "renata"))));

        assertThat(facts.pinFor("reviewer")).isEqualTo(new PinFact.Pinned("renata", PROJECT));
    }

    @Test
    void aDifferingPinConflictsEvenWhenOnePackIsDerivedFromTheOther() {
        ClosureRoleFacts facts = ClosureRoleFacts.of(List.of(
                pinning(ORG, List.of(), Map.of("reviewer", "renata")),
                pinning(PROJECT, List.of(ORG), Map.of("reviewer", "project-reviewer"))));

        assertThat(facts.pinFor("reviewer"))
                .as("pins are tighten-only: a derived pack cannot loosen its ancestor's pin (ruling D3)")
                .isEqualTo(new PinFact.Conflict(List.of(ORG, PROJECT)));
    }
}
