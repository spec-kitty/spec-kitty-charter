package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.web.RefusalHandler;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * NFR-008, T081: {@code 202} is answered within 1 s of the last body byte at p95 over 50 runs of a 32 MiB archive. It runs
 * the real edge (the admission, the archive guard, the controller that spools the body and starts the job, the mapper) over
 * the real registration jobs, and measures what the client waits for: from the request, whose body is complete, to the
 * answer. The time is reported at 1x and 3x the budget and gates nothing, as every timing budget (ADR-0057, decision
 * DM-01M6RN5SG19KXAHH4VGT9QBYA1: runner variance is about 2x); the deterministic gate of NFR-008 is the fake-clock deadline
 * test of RegistrationJobsTest. Tagged {@code timing}: not part of {@code test} or {@code check}, run by
 * {@code :packs:timingBudgets}.
 */
@Tag("timing")
class RegistrationAcceptanceTimingTest {

    private static final int RUNS = 50;
    private static final int ARCHIVE_BYTES = 32 * 1024 * 1024;
    private static final double BUDGET_MILLIS = 1000.0;

    @Test
    void theTwoHundredAndTwoIsAnsweredWithinASecondOfTheLastByteAtP95(@TempDir Path staging) throws Exception {
        RegistrationJobs jobs = jobsStagingIn(staging);
        try {
            MockMvc mvc = realEdgeOver(jobs);
            byte[] archive = new byte[ARCHIVE_BYTES];
            archive[0] = 0x1f;
            archive[1] = (byte) 0x8b;

            double[] sorted = sortedMillisOfEveryRun(mvc, archive);
            double p95 = sorted[(int) Math.ceil(0.95 * sorted.length) - 1];
            System.out.printf(
                    "NFR-008 202 within 1 s of the last byte: p95 %.0f ms over %d runs of a 32 MiB archive (median %.0f ms);"
                            + " 1x budget %.0f ms: %s, 3x budget %.0f ms: %s (report-only)%n",
                    p95,
                    RUNS,
                    sorted[sorted.length / 2],
                    BUDGET_MILLIS,
                    p95 <= BUDGET_MILLIS ? "within" : "over",
                    3 * BUDGET_MILLIS,
                    p95 <= 3 * BUDGET_MILLIS ? "within" : "over");
            assertThat(Arrays.stream(sorted).count())
                    .as("every run was answered 202")
                    .isEqualTo(RUNS);
        } finally {
            jobs.close();
        }
    }

    private static RegistrationJobs jobsStagingIn(Path staging) {
        AuditLog quiet = entry -> {};
        return new RegistrationJobs(new RegistrationJobs.Dependencies(
                Clock.systemUTC(),
                mock(PackRegistrationService.class),
                pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "not stored");
                },
                quiet,
                RegistrationJobs.JobGate.OPEN,
                RegistrationJobs.StorageRoom.UNBOUNDED,
                new FileStagingArea(staging)));
    }

    private static MockMvc realEdgeOver(RegistrationJobs jobs) {
        PackArchiveReader refusing = (body, format, root) -> {
            throw new ArchiveRefusal(List.of(), 0, 0);
        };
        ServiceInfoQuery info =
                new ServiceInfoQuery("worktree-timing", new ConsistentRead(new FileSystemPackSources(List::of)));
        PackRegistrationController controller = new PackRegistrationController(
                jobs,
                mock(PackSummaryQuery.class),
                refusing,
                new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler()),
                PackSourceIntake.none(),
                mock(PackRemovalController.class),
                mock(PackDocumentController.class));
        return MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new RegistrationAdmission(new RegistrationSwitch(true), jobs, info))
                .setControllerAdvice(new ArchiveBodyAdvice(), new RefusalHandler())
                .addPlaceholderValue("charter.api.base-path", "/api/v1")
                .build();
    }

    private static double[] sortedMillisOfEveryRun(MockMvc mvc, byte[] archive) throws Exception {
        List<Double> millis = new ArrayList<>();
        for (int run = 0; run < RUNS; run++) {
            long began = System.nanoTime();
            mvc.perform(put("/api/v1/packs/acme")
                            .contentType("application/gzip")
                            .content(archive))
                    .andExpect(status().isAccepted());
            millis.add((System.nanoTime() - began) / 1_000_000.0);
        }
        return millis.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    }
}
