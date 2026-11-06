package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.AGENT_PROFILE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static ai.speckitty.charter.kernel.domain.RelationType.SCOPE;
import static ai.speckitty.charter.kernel.domain.RelationType.SPECIALIZES_FROM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * FR-014, US3 AS4 (the lineage pass-through) and FR-012's {@code unreachedBehindDeactivated}. The lineage fixture: P
 * {@code specializes_from} Q; Q requires R; Q suggests S. Only {@code requires} and {@code scope} pass through, so the
 * walk reaches R and passes Q; S is not reached.
 */
class CascadeRulesLineageTest {

    private static final ArtifactRef P = ref("agent_profile:P");
    private static final ActivationDocument EMPTY =
            ActivationDocument.builder().build().document();

    private static ActivationChange select(ActivationDocument document, CascadeScope scope, Reach reach) {
        return CascadeRules.applySelect(document, P, scope, false, reach, Set.of());
    }

    private static CascadeScope scope(String... kinds) {
        return CascadeScope.parse(Optional.of(List.of(kinds))).scope();
    }

    private static void assertInNoBucket(SkippedReport skipped, ArtifactRef ref) {
        assertThat(skipped.skippedByScope()).doesNotContain(ref);
        assertThat(skipped.unreachedBehindDeactivated()).doesNotContain(ref);
        assertThat(skipped.skippedDeactivated()).noneMatch(s -> s.ref().equals(ref));
        assertThat(skipped.skippedShared()).noneMatch(s -> s.ref().equals(ref));
    }

    @Test
    void theLineagePassesRequiresThroughWithoutPuttingTheAncestorInForce() {
        Reach reach = reach().start("agent_profile:P")
                .reached("directive:R", "agent_profile:Q", REQUIRES)
                .passed("agent_profile:Q")
                .build();

        ActivationChange change = select(EMPTY, CascadeScope.all(), reach);

        assertThat(change.after().selected(AGENT_PROFILE).orElseThrow()).containsExactly("P");
        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("R");
        assertThat(change.applied()).containsExactly(P, ref("directive:R"));
        assertThat(change.applied()).doesNotContain(ref("agent_profile:Q"), ref("directive:S"));
        assertInNoBucket(change.skipped(), ref("agent_profile:Q"));
        assertInNoBucket(change.skipped(), ref("directive:S"));
    }

    @Test
    void aDeniedAncestorStopsThePassThroughAndIsReported() {
        Reach reach = reach().start("agent_profile:P")
                .stopped("agent_profile:Q", "agent_profile:P", SPECIALIZES_FROM)
                .behind("directive:R")
                .build();
        ActivationDocument qDenied = EMPTY.withDeny(AGENT_PROFILE, "Q");

        ActivationChange change = select(qDenied, CascadeScope.all(), reach);

        assertThat(change.skipped().skippedDeactivated())
                .containsExactly(new SkippedReport.SkippedDeactivated(ref("agent_profile:Q"), false, List.of(P)));
        assertThat(change.skipped().unreachedBehindDeactivated()).containsExactly(ref("directive:R"));
        assertThat(change.after().selected(DIRECTIVE)).isEmpty();
        assertThat(change.after().selected(AGENT_PROFILE).orElseThrow()).containsExactly("P");
        assertThat(change.after().deactivated(AGENT_PROFILE)).containsExactly("Q");
    }

    @Test
    void theLineageIsTransitive() {
        Reach reach = reach().start("agent_profile:P")
                .reached("directive:R2", "agent_profile:Q2", SCOPE)
                .passed("agent_profile:Q", "agent_profile:Q2")
                .build();

        ActivationChange change = select(EMPTY, CascadeScope.all(), reach);

        assertThat(change.after().selected(DIRECTIVE).orElseThrow()).containsExactly("R2");
        assertThat(change.after().selected(AGENT_PROFILE).orElseThrow()).containsExactly("P");
    }

    @Test
    void theScopeDoesNotFilterTheAncestorButFiltersWhatPassesThrough() {
        Reach reach = reach().start("agent_profile:P")
                .reached("directive:R", "agent_profile:Q", REQUIRES)
                .passed("agent_profile:Q")
                .build();

        ActivationChange change = select(EMPTY, scope("agent_profile"), reach);

        assertThat(change.after().selected(AGENT_PROFILE).orElseThrow()).containsExactly("P");
        assertThat(change.after().selected()).containsOnlyKeys(AGENT_PROFILE);
        assertThat(change.skipped().skippedByScope()).containsExactly(ref("directive:R"));
        assertInNoBucket(change.skipped(), ref("agent_profile:Q"));
    }

    // unreachedBehindDeactivated

    @Test
    void whatLiesOnlyBehindADeniedArtifactIsReported() {
        Reach reach = reach().start("agent_profile:P")
                .stopped("tactic:X", "agent_profile:P", REQUIRES)
                .behind("tactic:Y")
                .build();

        ActivationChange change = select(EMPTY.withDeny(TACTIC, "X"), CascadeScope.all(), reach);

        assertThat(change.skipped().unreachedBehindDeactivated()).containsExactly(ref("tactic:Y"));
        assertThat(change.after().selected(TACTIC)).isEmpty();
    }

    @Test
    void whatIsAlsoReachedAnotherWayIsSelectedAndNotBehind() {
        Reach reach = reach().start("agent_profile:P")
                .reached("directive:B", "agent_profile:P", REQUIRES)
                .reached("tactic:Y", "directive:B", REQUIRES)
                .stopped("tactic:X", "agent_profile:P", REQUIRES)
                .behind("tactic:Y")
                .build();

        ActivationChange change = select(EMPTY.withDeny(TACTIC, "X"), CascadeScope.all(), reach);

        assertThat(change.after().selected(TACTIC).orElseThrow()).containsExactly("Y");
        assertThat(change.skipped().unreachedBehindDeactivated()).isEmpty();
    }

    @Test
    void whatLiesBehindIsReportedWhateverTheScope() {
        Reach reach = reach().start("agent_profile:P")
                .stopped("tactic:X", "agent_profile:P", REQUIRES)
                .behind("tactic:Y", "directive:Z")
                .build();

        ActivationChange change = select(EMPTY.withDeny(TACTIC, "X"), scope("agent_profile"), reach);

        assertThat(change.skipped().unreachedBehindDeactivated()).containsExactly(ref("directive:Z"), ref("tactic:Y"));
        assertThat(change.skipped().skippedByScope()).isEmpty();
    }
}
