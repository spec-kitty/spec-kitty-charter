package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.activations.domain.Reaches.refs;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.AGENT_PROFILE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * FR-013, US3 AS3: {@code includeDeactivated} takes back only the deny entries the walk passes through within the
 * cascade's scope: what it reached in scope, the ancestors it passed whose kind is in scope, and the target (ADR-0059 as
 * amended on 2026-11-06, A3). A denied artifact outside the scope stops the walk and keeps its deny entry, as it would
 * without {@code includeDeactivated}. There is no {@code enhances} pull at write time (A4): the write adds to
 * {@code selected} only the target and what the walk reached, which every test checks with
 * {@link #assertOnlyTheWalkSelected}.
 */
class CascadeRulesClearingTest {

    private static final ArtifactRef D = ref("directive:D");
    private static final ArtifactRef T = ref("tactic:T");

    /** D requires T and X; the walk stops nowhere with includeDeactivated, so X is reached. */
    private static final Reach US3 = reach().start("directive:D")
            .reached("tactic:T", "directive:D", REQUIRES)
            .reached("tactic:X", "directive:D", REQUIRES)
            .build();

    private static ActivationDocument denying(String... urns) {
        ActivationDocument document = ActivationDocument.builder().build().document();
        for (ArtifactRef ref : refs(urns)) {
            document = document.withDeny(ref.kind(), ref.id());
        }
        return document;
    }

    private static ActivationChange include(ActivationDocument before, ArtifactRef target, Reach reach) {
        ActivationChange change = CascadeRules.applySelect(before, target, CascadeScope.all(), true, reach);
        assertOnlyTheWalkSelected(before, change, target, reach);
        return change;
    }

    /** The write adds to {@code selected} only the target and what the walk reached. */
    private static void assertOnlyTheWalkSelected(
            ActivationDocument before, ActivationChange change, ArtifactRef target, Reach reach) {
        Set<ArtifactRef> added = ActivationChange.refs(change.after().selected());
        added.removeAll(ActivationChange.refs(before.selected()));
        Set<ArtifactRef> walked = new TreeSet<>(reach.reached().keySet());
        walked.add(target);
        assertThat(walked).containsAll(added);
    }

    private static final CascadeScope DIRECTIVES =
            CascadeScope.parse(Optional.of(List.of("directive"))).scope();

    @Test
    void theIncludePlanStopsOnlyAtTheDenyEntriesOutsideTheScope() {
        ActivationDocument before = denying("tactic:X", "directive:Z");

        ReachPlan all = CascadeRules.planSelect(before, D, CascadeScope.all(), true);
        ReachPlan directives = CascadeRules.planSelect(before, D, DIRECTIVES, true);

        assertThat(all.stopAt()).isEmpty();
        assertThat(all.walks()).isTrue();
        assertThat(directives.stopAt()).containsExactly(ref("tactic:X"));
    }

    @Test
    void aReachedDeniedArtifactIsSelectedAndCleared() {
        ActivationChange change = include(denying("tactic:X"), D, US3);

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T", "X");
        assertThat(change.cleared()).containsExactly(ref("tactic:X"));
        assertThat(change.applied()).containsExactly(D, T, ref("tactic:X"));
    }

    @Test
    void aDenyListItEmptiesIsDropped() {
        ActivationChange change = include(denying("tactic:X", "directive:Q"), D, US3);

        assertThat(change.after().deactivated()).containsOnlyKeys(DIRECTIVE);
        assertThat(change.after().toString()).doesNotContain("\"tactic\":[]");
    }

    @Test
    void aDeniedArtifactOutsideTheScopeStopsTheWalkAndKeepsItsDenyEntry() {
        // A3: X is a tactic, outside a cascade into directives, so the walk stops at it as it would without
        // includeDeactivated: it is reported in skippedDeactivated and its deny entry stays.
        ActivationDocument before = denying("tactic:X");
        Reach reach = reach().start("directive:D")
                .reached("tactic:T", "directive:D", REQUIRES)
                .stopped("tactic:X", "directive:D", REQUIRES)
                .build();

        ActivationChange change = CascadeRules.applySelect(before, D, DIRECTIVES, true, reach);
        assertOnlyTheWalkSelected(before, change, D, reach);

        assertThat(change.cleared()).isEmpty();
        assertThat(change.after().deactivated(TACTIC)).containsExactly("X");
        assertThat(change.skipped().skippedByScope()).containsExactly(T);
        assertThat(change.skipped().skippedDeactivated())
                .containsExactly(new SkippedReport.SkippedDeactivated(ref("tactic:X"), true, List.of(D)));
    }

    @Test
    void aDenyEntryOutsideTheScopeIsNeverClearedEvenWhenTheWalkReachedIt() {
        // The rule is the domain's, not only the plan's: whatever the walk reached, only in-scope deny entries clear.
        ActivationDocument before = denying("tactic:X");

        ActivationChange change = CascadeRules.applySelect(before, D, DIRECTIVES, true, US3);

        assertThat(change.cleared()).isEmpty();
        assertThat(change.after().deactivated(TACTIC)).containsExactly("X");
    }

    @Test
    void aDeniedAncestorTheWalkPassedIsClearedAndNeverSelected() {
        ArtifactRef p = ref("agent_profile:P");
        ArtifactRef q = ref("agent_profile:Q");
        Reach reach = reach().start("agent_profile:P")
                .ancestor("agent_profile:Q", "agent_profile:P")
                .reached("directive:R", "agent_profile:Q", REQUIRES)
                .build();
        ActivationDocument before = denying("agent_profile:Q");

        ActivationChange change = include(before, p, reach);

        assertThat(change.cleared()).containsExactly(q);
        assertThat(change.after().selected(AGENT_PROFILE).orElseThrow()).containsExactly("P");
        assertThat(change.applied()).containsExactly(p, q, ref("directive:R"));
    }

    @Test
    void aDeniedAncestorOutsideTheScopeStopsTheLineageAndKeepsItsDenyEntry() {
        // A3: the ancestor's kind is outside a cascade into directives, so it is a stop, as without includeDeactivated.
        ArtifactRef p = ref("agent_profile:P");
        ActivationDocument before = denying("agent_profile:Q");
        Reach passed = reach().start("agent_profile:P")
                .ancestor("agent_profile:Q", "agent_profile:P")
                .reached("directive:R", "agent_profile:Q", REQUIRES)
                .build();

        ReachPlan plan = CascadeRules.planSelect(before, p, DIRECTIVES, true);
        ActivationChange change = CascadeRules.applySelect(before, p, DIRECTIVES, true, passed);

        assertThat(plan.stopAt()).containsExactly(ref("agent_profile:Q"));
        assertThat(change.cleared()).isEmpty();
        assertThat(change.after().deactivated(AGENT_PROFILE)).containsExactly("Q");
    }
}
