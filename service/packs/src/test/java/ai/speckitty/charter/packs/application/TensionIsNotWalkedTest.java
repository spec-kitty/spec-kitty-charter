package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.linked;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.urn;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.EdgeIndexTest.Closure;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A tension is a relation of the catalogue, but no walk follows it (ADR-0045 decisions 10 and 13): it never brings an
 * artifact into an action context, a governance read or a reach. The activations module pins that the cascade's
 * relation set does not name it ({@code TensionIsNotCascadedTest}). Each case has a twin where a {@code requires} on the
 * same pair does reach the other side, so the assertion cannot pass vacuously.
 */
@DisplayName("No walk follows a tension (ADR-0045)")
class TensionIsNotWalkedTest {

    private static final String PROFILE = "agent_profile:P";
    private static final String ACTION = "action:M/a";
    private static final String A = "directive:A";
    private static final String B = "directive:B";

    private static boolean everything(Urn urn) {
        return true;
    }

    @Test
    void theGovernanceWalkDoesNotFollowATension() {
        Closure tension = new Closure()
                .pack(
                        "alpha",
                        List.of(PROFILE, A, B),
                        edge(PROFILE, RelationType.REQUIRES, A),
                        edge(A, RelationType.TENSION, B));
        Closure requires = new Closure()
                .pack(
                        "alpha",
                        List.of(PROFILE, A, B),
                        edge(PROFILE, RelationType.REQUIRES, A),
                        edge(A, RelationType.REQUIRES, B));

        assertThat(governance(tension)).contains(A).doesNotContain(B);
        assertThat(governance(requires)).contains(A, B);
    }

    @Test
    void theActionContextWalkDoesNotFollowATension() {
        Closure tension = new Closure()
                .pack(
                        "alpha",
                        List.of("mission_type:M", ACTION, A, B),
                        linked(
                                "M",
                                List.of("a"),
                                edge(ACTION, RelationType.SCOPE, A),
                                edge(A, RelationType.TENSION, B)));
        Closure requires = new Closure()
                .pack(
                        "alpha",
                        List.of("mission_type:M", ACTION, A, B),
                        linked(
                                "M",
                                List.of("a"),
                                edge(ACTION, RelationType.SCOPE, A),
                                edge(A, RelationType.REQUIRES, B)));

        assertThat(context(tension)).contains(A).doesNotContain(B);
        assertThat(context(requires)).contains(A, B);
    }

    @Test
    void theReachFromASideOverTheCascadesRelationsDoesNotListTheOtherSide() {
        // The cascade's relations (CascadeRules.CASCADE_RELATIONS in the activations module): core's five and enhances.
        Set<RelationType> cascade = EnumSet.of(
                RelationType.REQUIRES,
                RelationType.SUGGESTS,
                RelationType.REFINES,
                RelationType.SCOPE,
                RelationType.INSTANTIATES,
                RelationType.ENHANCES);
        EdgeIndex tension = new Closure()
                .pack("alpha", List.of(A, B), edge(A, RelationType.TENSION, B))
                .merged();
        EdgeIndex requires = new Closure()
                .pack("alpha", List.of(A, B), edge(A, RelationType.REQUIRES, B))
                .merged();

        assertThat(reached(tension, cascade)).doesNotContain(urn(B));
        assertThat(reached(requires, cascade)).contains(urn(B));
    }

    private static List<String> governance(Closure closure) {
        return GovernanceWalk.over(closure.merged())
                .walk(urn(PROFILE), 1, TensionIsNotWalkedTest::everything)
                .entries()
                .stream()
                .map(entry -> entry.urn().toString())
                .toList();
    }

    private static List<String> context(Closure closure) {
        return new ActionContextWalk(closure.merged())
                .walk("M", "a", 1, TensionIsNotWalkedTest::everything).entries().stream()
                        .map(entry -> entry.urn().toString())
                        .toList();
    }

    private static Set<Urn> reached(EdgeIndex index, Set<RelationType> relations) {
        return Reachability.reach(index, new ReachRequest(Set.of(urn(A)), relations, urn -> false, Optional.empty()))
                .reached()
                .keySet();
    }
}
