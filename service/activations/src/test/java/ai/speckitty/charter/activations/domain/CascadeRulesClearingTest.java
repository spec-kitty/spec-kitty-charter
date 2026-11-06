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
 * FR-013, US3 AS3: {@code includeDeactivated} clears the deny entries the walk goes through. There is no {@code enhances}
 * pull at write time (ADR-0059 D2 as amended on 2026-11-06): the write adds to {@code selected} only the target and what
 * the walk reached, which every test checks with {@link #assertOnlyTheWalkSelected}.
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

    @Test
    void theIncludePlanStopsNowhere() {
        ReachPlan plan = CascadeRules.planSelect(denying("tactic:X"), D, CascadeScope.all(), true);

        assertThat(plan.stopAt()).isEmpty();
        assertThat(plan.walks()).isTrue();
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
    void aDeniedArtifactOutsideTheScopeIsClearedButNotSelected() {
        // The walk went through X (includeDeactivated stops nowhere), so its deny entry is cleared whatever the scope;
        // X is out of the scope, so it is reported there and not selected.
        CascadeScope directives =
                CascadeScope.parse(Optional.of(List.of("directive"))).scope();
        ActivationDocument before = denying("tactic:X");

        ActivationChange change = CascadeRules.applySelect(before, D, directives, true, US3);
        assertOnlyTheWalkSelected(before, change, D, US3);

        assertThat(change.cleared()).containsExactly(ref("tactic:X"));
        assertThat(change.after().deactivated()).isEmpty();
        assertThat(change.after().selected(TACTIC)).isEmpty();
        assertThat(change.skipped().skippedByScope()).containsExactly(T, ref("tactic:X"));
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
}
