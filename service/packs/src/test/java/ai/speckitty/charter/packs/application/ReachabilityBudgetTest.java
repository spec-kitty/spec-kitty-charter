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
        for (int start = 0; start < STARTS; start++) {
            starts.add(tactic(start * (ARTIFACTS / STARTS)));
        }
        Set<Urn> deny = Set.of(tactic(7), tactic(131), tactic(402));
        ReachRequest request = new ReachRequest(
                starts,
                EnumSet.copyOf(Arrays.asList(FIVE)),
                deny::contains,
                Optional.of(ReachRequest.Lineage.specializesFrom()));
        assertThat(Reachability.reach(index, request).reached())
                .as("the fixture reaches beyond its starts")
                .hasSizeGreaterThan(STARTS);

        for (int warm = 0; warm < WARM_UP; warm++) {
            Reachability.reach(index, request);
            Reachability.enhancesPull(index, starts);
        }
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] nanos = new long[MEASURED];
        for (int measured = 0; measured < MEASURED; measured++) {
            long begin = threads.getCurrentThreadCpuTime();
            Reachability.reach(index, request);
            Reachability.enhancesPull(index, starts);
            nanos[measured] = threads.getCurrentThreadCpuTime() - begin;
        }
        Arrays.sort(nanos);
        double p95Millis = nanos[(int) Math.ceil(0.95 * MEASURED) - 1] / 1_000_000.0;
        System.out.printf(
                "ReachabilityBudgetTest: p95 %.3f ms CPU over %d reaches of %d artifacts from %d starts (report-only)%n",
                p95Millis, MEASURED, ARTIFACTS, STARTS);
    }

    private static Urn tactic(int index) {
        return EdgeIndexTest.urn("tactic:t" + index);
    }

    /**
     * Five hundred tactics in one pack: each {@code i} has a cascade edge of each of the five relations to
     * {@code (i * k + 1) mod 500}, every tenth enhances its neighbour, and every twentieth specializes from the one
     * before it, so the walk meets cycles, shared targets and lineage.
     */
    private static EdgeIndex fixture() {
        List<String> held = new ArrayList<>();
        List<Relationship> edges = new ArrayList<>();
        for (int i = 0; i < ARTIFACTS; i++) {
            held.add("tactic:t" + i);
            for (int k = 0; k < FIVE.length; k++) {
                int target = (i * (k + 2) + 1) % ARTIFACTS;
                if (target != i) {
                    edges.add(edge("tactic:t" + i, FIVE[k], "tactic:t" + target));
                }
            }
            if (i % 10 == 0) {
                edges.add(edge("tactic:t" + i, RelationType.ENHANCES, "tactic:t" + (i + 1)));
            }
            if (i % 20 == 1) {
                edges.add(edge("tactic:t" + i, RelationType.SPECIALIZES_FROM, "tactic:t" + (i - 1)));
            }
        }
        return new EdgeIndexTest.Closure()
                .pack("alpha", held, edges.toArray(Relationship[]::new))
                .index();
    }
}
