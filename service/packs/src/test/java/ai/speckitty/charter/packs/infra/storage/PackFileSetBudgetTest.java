package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * NFR-001 of the verified source fingerprint: the {@link PackFileSetExactnessTest} work over its 200 generated trees
 * takes under 10 s of CPU time. Generating the trees, the digest checks and the load checks are each held to it.
 *
 * <p>A budget is only meaningful on a quiet machine. Tagged {@code timing}, this class runs only in
 * {@code :packs:timingBudgets}, which {@code check} does not include and CI runs as a step of its own after
 * {@code verifyAll}, with no other test JVM on the runner (#316). Under {@code verifyAll}'s parallel module tests the
 * digest checks took 13.1 s of this thread's CPU time on a merge-queue runner, which dropped an unrelated pull request
 * from the queue (10.8–11 s was seen in a 4-core container); alone, they took 7–7.2 s there. Each test prints what it
 * measured, so the margin shows in every run. The correctness of the same checks is asserted in the normal suite, by
 * {@link PackFileSetExactnessTest}; this class repeats them only so that the time it measures is the time of checks
 * that passed.
 *
 * <p>The trees are written into this class's own directory, so it never shares a tree with
 * {@link PackFileSetExactnessTest}, whose checks change files and put them back.
 */
@Tag("timing")
class PackFileSetBudgetTest {

    /** NFR-001: 200 trees in under 10 s of CPU time, measured apart from the wall clock, which a starved runner skews. */
    private static final long CPU_BUDGET_NANOS = 10_000_000_000L;

    /** Only a hang cap: the budget is CPU time. */
    private static final int HANG_CAP_SECONDS = 60;

    @TempDir
    static Path home;

    private static final List<GeneratedPackTree> TREES = new ArrayList<>();
    private static long generationCpuNanos;

    @BeforeAll
    static void generateTheTrees() throws IOException {
        SymbolicLinks.requireSupport(home);
        long start = cpuNanos();
        for (int i = 0; i < PackFileSetExactnessTest.TREES; i++) {
            TREES.add(PackFileSetExactnessTest.generate(home, i));
        }
        generationCpuNanos = cpuNanos() - start;
        report("generating the trees", generationCpuNanos);
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void generatingTheTreesIsWithinBudget() {
        assertThat(generationCpuNanos)
                .as("CPU time to generate %d trees", TREES.size())
                .isLessThan(CPU_BUDGET_NANOS);
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void theDigestChecksAreWithinBudget() throws IOException {
        long start = cpuNanos();
        for (GeneratedPackTree tree : TREES) {
            PackFileSetExactnessTest.assertDigestIsExact(tree, PackFileSetExactnessTest::fingerprintOf);
        }
        long spent = report("the digest checks", cpuNanos() - start);
        assertThat(spent)
                .as("CPU time of the digest checks over %d trees", TREES.size())
                .isLessThan(CPU_BUDGET_NANOS);
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void theLoadChecksAreWithinBudget() {
        long start = cpuNanos();
        for (GeneratedPackTree tree : TREES) {
            PackFileSetExactnessTest.assertLoadIsExact(tree);
        }
        long spent = report("the load checks", cpuNanos() - start);
        assertThat(spent)
                .as("CPU time of the load checks over %d trees", TREES.size())
                .isLessThan(CPU_BUDGET_NANOS);
    }

    /** Prints the CPU time spent on {@code what}, against the budget, and returns it. */
    private static long report(String what, long nanos) {
        System.out.printf(
                Locale.ROOT,
                "NFR-001: %s (%d trees) took %.2f s of CPU time; the budget is %.2f s%n",
                what,
                TREES.size(),
                nanos / 1e9,
                CPU_BUDGET_NANOS / 1e9);
        return nanos;
    }

    /** This thread's CPU time; a JVM that cannot measure it fails the budget rather than passing it unmeasured. */
    private static long cpuNanos() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        assertThat(threads.isCurrentThreadCpuTimeSupported())
                .as("this JVM measures a thread's CPU time")
                .isTrue();
        return threads.getCurrentThreadCpuTime();
    }
}
