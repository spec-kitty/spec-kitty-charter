package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.activations.domain.Reaches.refs;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.AGENT_PROFILE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.STYLEGUIDE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

/**
 * FR-013, US3 AS3: {@code includeDeactivated} clears the deny entries of what the walk selects and of what the
 * read-time {@code enhances} pull reaches from it (both directions, transitively, R1), and never selects a pulled
 * artifact (ADR-0059 D2.2). Every test checks that property with {@link #assertNoEnhancerSelected}.
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

    private static ActivationChange include(
            ActivationDocument before, ArtifactRef target, Reach reach, Set<ArtifactRef> pulled) {
        ActivationChange change = CascadeRules.applySelect(before, target, CascadeScope.all(), true, reach, pulled);
        assertNoEnhancerSelected(before, change, target, reach);
        return change;
    }

    /**
     * The property (ADR-0059 D2.2): the write adds to {@code selected} only the target and what the walk reached over
     * the five relations, never an artifact only the {@code enhances} pull reached.
     */
    private static void assertNoEnhancerSelected(
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
    void thePullStartsFromWhatTheWalkSelects() {
        assertThat(CascadeRules.walkSelected(D, CascadeScope.all(), US3)).containsExactly(D, T, ref("tactic:X"));
        assertThat(CascadeRules.walkSelected(
                        D, CascadeScope.parse(Optional.of(List.of("directive"))).scope(), US3))
                .containsExactly(D);
        assertThat(CascadeRules.walkSelected(D, CascadeScope.none(), US3)).containsExactly(D);
    }

    @Test
    void aReachedDeniedArtifactIsSelectedAndCleared() {
        ActivationChange change = include(denying("tactic:X"), D, US3, Set.of());

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("T", "X");
        assertThat(change.cleared()).containsExactly(ref("tactic:X"));
        assertThat(change.applied()).containsExactly(D, T, ref("tactic:X"));
    }

    @Test
    void aDeniedEnhancerOfASelectedArtifactIsClearedNotSelected() {
        ActivationChange change = include(denying("tactic:X", "styleguide:F"), D, US3, refs("styleguide:F"));

        assertThat(change.cleared()).containsExactly(ref("styleguide:F"), ref("tactic:X"));
        assertThat(change.applied()).containsExactly(D, ref("styleguide:F"), T, ref("tactic:X"));
        assertThat(change.after().selected()).doesNotContainKey(STYLEGUIDE);
        assertThat(change.after().deactivated()).isEmpty();
    }

    @Test
    void anEnhancerThatIsNotDeniedIsLeftAlone() {
        ActivationChange change = include(denying("tactic:X"), D, US3, refs("styleguide:E"));

        assertThat(change.cleared()).containsExactly(ref("tactic:X"));
        assertThat(change.applied()).doesNotContain(ref("styleguide:E"));
        assertThat(change.after().selected()).doesNotContainKey(STYLEGUIDE);
    }

    @Test
    void theDeniedTargetOfASelectedEnhancerIsClearedNotSelected() {
        Reach reach = reach().start("directive:D")
                .reached("styleguide:E", "directive:D", REQUIRES)
                .build();

        ActivationChange change = include(denying("tactic:T"), D, reach, refs("tactic:T"));

        assertThat(change.cleared()).containsExactly(T);
        assertThat(change.applied()).containsExactly(D, ref("styleguide:E"), T);
        assertThat(change.after().selected()).doesNotContainKey(TACTIC);
    }

    @Test
    void aDeniedEnhancerChainIsClearedTransitivelyAndNothingOfItSelected() {
        Reach reach = reach().start("tactic:T").build();
        ActivationDocument before = denying("styleguide:E1", "styleguide:E2");

        ActivationChange change = include(before, T, reach, refs("styleguide:E1", "styleguide:E2"));

        assertThat(change.cleared()).containsExactly(ref("styleguide:E1"), ref("styleguide:E2"));
        assertThat(change.applied()).containsExactly(ref("styleguide:E1"), ref("styleguide:E2"), T);
        assertThat(change.after().selected()).containsOnlyKeys(TACTIC);
    }

    @Test
    void thePullDoesNotCrossTheFiveRelations() {
        Reach reach = reach().start("tactic:T").build();

        ActivationChange change = include(denying("directive:Z"), T, reach, refs("styleguide:E"));

        assertThat(change.cleared()).isEmpty();
        assertThat(change.after().deactivated(DIRECTIVE)).containsExactly("Z");
    }

    @Test
    void aDenyListItEmptiesIsDropped() {
        ActivationChange change = include(denying("tactic:X", "directive:Q"), D, US3, Set.of());

        assertThat(change.after().deactivated()).containsOnlyKeys(DIRECTIVE);
        assertThat(change.after().toString()).doesNotContain("\"tactic\":[]");
    }

    @Test
    void withoutIncludeDeactivatedThePullClearsNothing() {
        Reach reach = reach().start("directive:D")
                .reached("tactic:T", "directive:D", REQUIRES)
                .stopped("tactic:X", "directive:D", REQUIRES)
                .build();
        ActivationDocument before = denying("tactic:X", "styleguide:F");

        ActivationChange change =
                CascadeRules.applySelect(before, D, CascadeScope.all(), false, reach, refs("styleguide:F"));
        assertNoEnhancerSelected(before, change, D, reach);

        assertThat(change.cleared()).isEmpty();
        assertThat(change.after().deactivated()).isEqualTo(before.deactivated());
    }

    /** The property over generated cases: random walks, random pulls, random deny entries. */
    @RepeatedTest(50)
    void noPulledArtifactIsEverSelected(RepetitionInfo repetition) {
        Random random = new Random(repetition.getCurrentRepetition());
        List<ArtifactRef> universe = universe();
        Reaches walk = reach().start("directive:A0");
        Set<ArtifactRef> pulled = new TreeSet<>();
        ActivationDocument before = ActivationDocument.builder().build().document();
        for (ArtifactRef ref : universe) {
            int roll = random.nextInt(4);
            if (roll == 0) {
                walk.reached(ref.urn(), "directive:A0", RelationType.SUGGESTS);
            } else if (roll == 1) {
                pulled.add(ref);
            }
            before = random.nextBoolean() ? before.withDeny(ref.kind(), ref.id()) : before;
        }
        Reach reach = walk.build();

        ActivationChange change = include(before, ref("directive:A0"), reach, pulled);

        Set<ArtifactRef> pulledOnly = new TreeSet<>(pulled);
        pulledOnly.removeAll(reach.reached().keySet());
        assertThat(ActivationChange.refs(change.after().selected())).noneMatch(pulledOnly::contains);
    }

    private static List<ArtifactRef> universe() {
        List<ArtifactRef> universe = new ArrayList<>();
        for (ArtifactKind kind : List.of(DIRECTIVE, TACTIC, STYLEGUIDE)) {
            for (int i = 1; i <= 4; i++) {
                universe.add(new ArtifactRef(kind, kind.token().substring(0, 1).toUpperCase(Locale.ROOT) + i));
            }
        }
        return universe;
    }

    @Test
    void aDeniedArtifactOutsideTheScopeIsClearedButNotSelected() {
        // The walk went through X (includeDeactivated stops nowhere), so its deny entry is cleared whatever the scope;
        // X is out of the scope, so it is reported there and not selected.
        CascadeScope directives =
                CascadeScope.parse(Optional.of(List.of("directive"))).scope();
        ActivationDocument before = denying("tactic:X");

        ActivationChange change = CascadeRules.applySelect(before, D, directives, true, US3, Set.of());
        assertNoEnhancerSelected(before, change, D, US3);

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

        ActivationChange change = include(before, p, reach, Set.of());

        assertThat(change.cleared()).containsExactly(q);
        assertThat(change.after().selected(AGENT_PROFILE).orElseThrow()).containsExactly("P");
        assertThat(change.applied()).containsExactly(p, q, ref("directive:R"));
    }

    @Test
    void thePullIsBoundedByTheKindsTheDocumentActivatesAndAbsentMeansNone() {
        ActivationDocument absent = denying("tactic:X");
        ActivationDocument none =
                ActivationDocument.builder().activatedKinds(List.of()).build().document();
        ActivationDocument some = ActivationDocument.builder()
                .activatedKinds(List.of(TACTIC, DIRECTIVE))
                .build()
                .document();

        assertThat(CascadeRules.pullKinds(absent)).isEmpty();
        assertThat(CascadeRules.pullKinds(none)).isEmpty();
        assertThat(CascadeRules.pullKinds(some)).containsExactlyInAnyOrder(DIRECTIVE, TACTIC);
    }
}
