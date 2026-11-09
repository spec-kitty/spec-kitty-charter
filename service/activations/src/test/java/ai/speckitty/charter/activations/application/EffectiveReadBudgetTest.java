package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * NFR-003, report-only (#316, ADR-0057): an effective read over ten packs and two thousand artifacts, end to end through
 * MockMvc, its wall time at the 95th percentile of fifty runs after ten warm-up runs, reported against 300 ms at 1x and
 * at 3x (the runner varies about 2x). The cache key changes on every run, so the facts cache cannot flatter it, and the
 * closure's own digest (the extra read of the closure-scoped tags) is part of every run. It gates nothing: the gate is
 * {@link EffectiveReadOperationBudgetTest}'s count of the work. Tagged {@code timing}: it runs under
 * {@code :activations:timingBudgets}, alone, and never in {@code test}.
 */
@Tag("timing")
class EffectiveReadBudgetTest {

    private static final int WARM_UP = 10;
    private static final int MEASURED = 50;
    private static final double BUDGET_MILLIS = 300.0;

    private static double millis(EffectiveReadFixture fixture) throws Exception {
        fixture.revise();
        long begin = System.nanoTime();
        MockHttpServletResponse answer = fixture.mvc
                .perform(get(EffectiveReadFixture.PATH).accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse();
        long nanos = System.nanoTime() - begin;
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        return nanos / 1_000_000.0;
    }

    @Test
    void anEffectiveReadOverTenPacksAndTwoThousandArtifactsReportsItsP95(@TempDir Path root) throws Exception {
        EffectiveReadFixture fixture = new EffectiveReadFixture(root);
        for (int warm = 0; warm < WARM_UP; warm++) {
            millis(fixture);
        }
        double[] runs = new double[MEASURED];
        for (int run = 0; run < MEASURED; run++) {
            runs[run] = millis(fixture);
        }
        Arrays.sort(runs);
        double p95 = runs[(int) Math.ceil(0.95 * MEASURED) - 1];
        System.out.printf(
                "EffectiveReadBudgetTest: p95 %.1f ms wall over %d reads of %d artifacts in %d packs (cache key varied per run);"
                        + " budget %.0f ms at 1x: %s, at 3x (%.0f ms): %s (report-only)%n",
                p95,
                MEASURED,
                EffectiveReadFixture.ARTIFACTS,
                EffectiveReadFixture.PACKS,
                BUDGET_MILLIS,
                p95 <= BUDGET_MILLIS ? "within" : "over",
                3 * BUDGET_MILLIS,
                p95 <= 3 * BUDGET_MILLIS ? "within" : "over");
    }
}
