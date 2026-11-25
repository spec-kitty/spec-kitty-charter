package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The reach over five hundred artifacts (NFR-006), report-only: it prints the p95 of a reach from twenty starts, with
 * the lineage on and a deny set, in thread CPU time after a warm-up, and gates nothing (#316, ADR-0057). Tagged
 * {@code timing}: it runs under {@code :packs:timingBudgets}, alone, and never in {@code test}.
 */
@Tag("timing")
class ReachabilityBudgetTest {

    private static final int ARTIFACTS = 500;

    private static final int TACTICS = 400;

    private static final int PROFILES = 40;

    /** Mission types; each has one action, which instantiates one template. */
    private static final int MISSIONS = 20;

    private static final int STARTS = 20;

    private static final int WARM_UP = 10;

    private static final int MEASURED = 50;

    private static final RelationType[] FIVE = {
        RelationType.REQUIRES,
        RelationType.SUGGESTS,
        RelationType.REFINES,
        RelationType.SCOPE,
        RelationType.INSTANTIATES
    };

    @Test
    void aReachOverFiveHundredArtifactsReportsItsP95() {
        EdgeIndex index = fixture();
        Set<Urn> starts = new LinkedHashSet<>();
        for (int start = 0; start < STARTS / 2; start++) {
            starts.add(tactic(start * (TACTICS / (STARTS / 2))));
        }
        for (int start = 0; start < STARTS / 4; start++) {
            starts.add(EdgeIndexTest.urn(missionType(start * (MISSIONS / (STARTS / 4)))));
            starts.add(EdgeIndexTest.urn(profile(start * (PROFILES / (STARTS / 4)) + 1)));
        }
        assertThat(starts).as("twenty distinct starts").hasSize(STARTS);
        Set<Urn> deny = Set.of(tactic(7), tactic(131), tactic(302));
        ReachRequest request = new ReachRequest(
                starts, cascadeRelations(), deny::contains, Optional.of(ReachRequest.Lineage.specializesFrom()));
        assertThat(Reachability.reach(index, request).reached())
                .as("the fixture reaches beyond its starts")
                .hasSizeGreaterThan(STARTS);

        for (int warm = 0; warm < WARM_UP; warm++) {
            Reachability.reach(index, request);
        }
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] nanos = new long[MEASURED];
        for (int measured = 0; measured < MEASURED; measured++) {
            long begin = threads.getCurrentThreadCpuTime();
            Reachability.reach(index, request);
            nanos[measured] = threads.getCurrentThreadCpuTime() - begin;
        }
        Arrays.sort(nanos);
        double p95Millis = nanos[(int) Math.ceil(0.95 * MEASURED) - 1] / 1_000_000.0;
        System.out.printf(
                "ReachabilityBudgetTest: p95 %.3f ms CPU over %d reaches of %d artifacts from %d starts (report-only)%n",
                p95Millis, MEASURED, ARTIFACTS, STARTS);
    }

    /** The cascade's relations: the five, and {@code enhances} in its declared direction (ADR-0059 D2, A4). */
    private static Set<RelationType> cascadeRelations() {
        Set<RelationType> relations = EnumSet.copyOf(Arrays.asList(FIVE));
        relations.add(RelationType.ENHANCES);
        return relations;
    }

    private static Urn tactic(int index) {
        return EdgeIndexTest.urn(tacticText(index));
    }

    private static String tacticText(int index) {
        return "tactic:t" + index;
    }

    private static String profile(int index) {
        return "agent_profile:p" + index;
    }

    private static String missionType(int index) {
        return "mission_type:m" + index;
    }

    private static String action(int index) {
        return "action:m" + index + "/a";
    }

    private static String template(int index) {
        return "template:m" + index + "/t.md";
    }

    /**
     * Five hundred artifacts in one pack, every edge between kinds its relation allows (ADR-0064 decision 13):
     * four hundred tactics, each with a {@code requires}, a {@code suggests} and a {@code refines} edge to
     * {@code (i * k + 1) mod 400} and every tenth enhancing its neighbour; forty agent profiles that require and
     * suggest tactics, every odd one specializing from the one before it; and twenty mission types, each scoping two
     * tactics and a profile and requiring its one action, which scopes a tactic and instantiates its template. The
     * walk meets cycles, shared targets, all five cascade relations and lineage.
     */
    private static EdgeIndex fixture() {
        List<String> held = new ArrayList<>();
        List<Relationship> edges = new ArrayList<>();
        RelationType[] betweenTactics = {RelationType.REQUIRES, RelationType.SUGGESTS, RelationType.REFINES};
        for (int i = 0; i < TACTICS; i++) {
            held.add(tacticText(i));
            for (int k = 0; k < betweenTactics.length; k++) {
                int target = (i * (k + 2) + 1) % TACTICS;
                if (target != i) {
                    edges.add(edge(tacticText(i), betweenTactics[k], tacticText(target)));
                }
            }
            if (i % 10 == 0) {
                edges.add(edge(tacticText(i), RelationType.ENHANCES, tacticText(i + 1)));
            }
        }
        for (int j = 0; j < PROFILES; j++) {
            held.add(profile(j));
            edges.add(edge(profile(j), RelationType.REQUIRES, tacticText(j * (TACTICS / PROFILES))));
            edges.add(edge(profile(j), RelationType.SUGGESTS, tacticText(j * (TACTICS / PROFILES) + 3)));
            if (j % 2 == 1) {
                edges.add(edge(profile(j), RelationType.SPECIALIZES_FROM, profile(j - 1)));
            }
        }
        for (int m = 0; m < MISSIONS; m++) {
            held.add(missionType(m));
            held.add(action(m));
            held.add(template(m));
            int spacing = TACTICS / MISSIONS;
            edges.add(edge(missionType(m), RelationType.SCOPE, tacticText(m * spacing)));
            edges.add(edge(missionType(m), RelationType.SCOPE, tacticText(m * spacing + 1)));
            edges.add(edge(missionType(m), RelationType.SCOPE, profile(2 * m)));
            edges.add(edge(missionType(m), RelationType.REQUIRES, action(m)));
            edges.add(edge(action(m), RelationType.SCOPE, tacticText(m * spacing + 2)));
            edges.add(edge(action(m), RelationType.INSTANTIATES, template(m)));
        }
        assertThat(held).as("the fixture holds five hundred artifacts").hasSize(ARTIFACTS);
        return new EdgeIndexTest.Closure()
                .pack("alpha", held, edges.toArray(Relationship[]::new))
                .index();
    }
}
