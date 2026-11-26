package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The worst cases of collecting a pack's violations take about as long as reading the pack (#648): a registration of
 * a tactic of 20 000 broken steps, and of a graph document of 14 000 self-joins beside four tactics of 32 000 broken
 * steps (files near the size cap). The quadratic collection took 44 s and about 50 s (46–53 s) of them, against
 * about 1 s and a few seconds of CPU time for the same uploads with valid content.
 *
 * <p>A budget is only meaningful on a quiet machine, and ADR-0057 keeps one from gating a pull request. Tagged
 * {@code timing}, this class runs only in {@code :packs:timingBudgets}, report-only in the nightly run, and never in
 * {@code verifyAll}. Each test prints what it measured. What the registrations refuse, in what order and in what
 * words, is asserted in the normal suite, by {@link PackRegistrationScaleTest} and the unit tests of the collection;
 * this class asserts only that the refusal holds every violation, so that the time it measures is the time of the
 * whole collection.
 */
@Tag("timing")
class PackViolationCollectionBudgetTest {

    /** About four times the 2.7 s of CPU time the largest case takes; the quadratic collection took 44 s and more. */
    private static final long CPU_BUDGET_NANOS = 10_000_000_000L;

    /** Only a hang cap: the budget is CPU time. */
    private static final int HANG_CAP_SECONDS = 300;

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void aWholePackUploadWithTwentyThousandSchemaViolationsIsRefusedWithinBudget(@TempDir Path host) {
        CharterRefusal refused = timed("20 000 schema violations", host, PackRegistrationScaleTest.brokenSteps(20_000));

        assertThat(refused.violations()).hasSize(20_000);
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void aGraphDocumentOfSelfJoinsBesideManySchemaViolationsIsRefusedWithinBudget(@TempDir Path host) {
        CharterRefusal refused = timed(
                "14 000 self-joins beside 4 x 32 000 schema violations",
                host,
                PackRegistrationScaleTest.selfJoinsBesideBrokenTactics("", 14_000, 4, 32_000));

        assertThat(refused.violations()).hasSize(14_000 + 4 * 32_000);
    }

    private static CharterRefusal timed(String what, Path host, Packs upload) {
        Rig rig = new Rig(host);
        StagedPack staged = rig.stage(upload);
        long start = cpuNanos();
        CharterRefusal refused =
                PackRegistrationServiceTest.refusal(() -> rig.register("fresh", staged, Preconditions.none()));
        long spent = cpuNanos() - start;
        System.out.printf(
                Locale.ROOT,
                "#648: refusing %s took %.2f s of CPU time; the budget is %.2f s%n",
                what,
                spent / 1e9,
                CPU_BUDGET_NANOS / 1e9);
        assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(spent).as("CPU time of refusing %s", what).isLessThan(CPU_BUDGET_NANOS);
        return refused;
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
