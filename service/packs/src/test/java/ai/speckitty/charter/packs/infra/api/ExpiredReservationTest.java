package ai.speckitty.charter.packs.infra.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.web.RefusalHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * A job the deadline ended while it was reserved, before the controller started it, is answered with its own end: the
 * {@code 202} carries the refused status with {@code registration_timed_out}, the outcome the audit records, never a
 * {@code 500} (#418, A4).
 */
class ExpiredReservationTest {

    @TempDir
    Path host;

    @Test
    void aJobEndedByTheDeadlineBeforeItStartedIsAnsweredWithItsTimeoutNotA500() throws Exception {
        Path directory = Files.createDirectory(host.resolve("01J0000000000000000000000A"));
        RegistrationJobs.Upload upload = new RegistrationJobs.Upload() {
            @Override
            public String id() {
                return "01J0000000000000000000000A";
            }

            @Override
            public Path directory() {
                return directory;
            }

            @Override
            public void discard() {}
        };
        String detail = "The registration did not finish within 90 seconds.";
        RegistrationJobs.Status timedOut = new RegistrationJobs.Status(
                upload.id(),
                "acme",
                RegistrationJobs.State.REFUSED,
                Instant.parse("2026-11-08T10:00:00Z"),
                Instant.parse("2026-11-08T10:01:30Z"),
                new RegistrationJobs.Refused(
                        RegistrationJobs.Kind.CODED,
                        new CharterRefusal(RefusalCode.REGISTRATION_TIMED_OUT, detail),
                        detail));
        RegistrationJobs.Reservation ended = new RegistrationJobs.Reservation() {
            @Override
            public String id() {
                return upload.id();
            }

            @Override
            public Path directory() {
                return directory;
            }

            @Override
            public boolean start(RegistrationJobs.Stager stager) {
                return false;
            }

            @Override
            public void abandon() {}

            @Override
            public RegistrationJobs.Status status() {
                return timedOut;
            }

            @Override
            public String revisionBefore() {
                return "";
            }
        };
        RegistrationJobs jobs = mock(RegistrationJobs.class);
        when(jobs.openUpload(anyLong())).thenReturn(upload);
        when(jobs.staging()).thenReturn(new FileStagingArea(host));
        when(jobs.reserve(any(), any(), any(), any(), anyLong())).thenReturn(ended);
        ServiceInfoQuery info =
                new ServiceInfoQuery("worktree-expired", new ConsistentRead(new FileSystemPackSources(List::of)));
        PackRegistrationController controller = new PackRegistrationController(
                jobs,
                mock(PackSummaryQuery.class),
                mock(PackArchiveReader.class),
                new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler()),
                PackSourceIntake.none(),
                mock(PackRemovalController.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(HttpConverters.serverConverters()
                        .toArray(new org.springframework.http.converter.HttpMessageConverter<?>[0]))
                .addInterceptors(new RegistrationAdmission(new RegistrationSwitch(true), jobs, info))
                .setControllerAdvice(new ArchiveBodyAdvice(), new RefusalHandler())
                .addPlaceholderValue("charter.api.base-path", "/api/v1")
                .build();

        mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(new byte[] {0x1f, (byte) 0x8b, 8, 0}))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("refused"))
                .andExpect(jsonPath("$.problem.code").value("registration_timed_out"));
    }
}
