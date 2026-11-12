package ai.speckitty.charter.packs.infra.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackSourceStagers;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.web.RefusalHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
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
        RegistrationJobs.Reservation ended = new EndedBeforeItStarts(upload.id(), directory);
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
                mock(PackRemovalController.class),
                mock(PackDocumentController.class));
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

    @Test
    void aSourceJobEndedByTheDeadlineBeforeItStartedIsAnsweredWithItsTimeoutToo() throws Exception {
        RegistrationJobs.Reservation ended = new EndedBeforeItStarts("01J0000000000000000000000B", null);
        RegistrationJobs jobs = mock(RegistrationJobs.class);
        when(jobs.reserve(any(), any(), any())).thenReturn(ended);
        ServiceInfoQuery info =
                new ServiceInfoQuery("worktree-expired", new ConsistentRead(new FileSystemPackSources(List::of)));
        PackSourceFetcher fetcher = new PackSourceFetcher() {
            @Override
            public PackSourceKind kind() {
                return PackSourceKind.HTTPS_ARCHIVE;
            }

            @Override
            public RegistrationJobs.Staged fetch(PackSourceRequest request, Path directory) {
                throw new AssertionError("an ended job is never run");
            }
        };
        PackSourceIntake intake = new PackSourceIntake(
                new PackSourceSwitch(Set.of(PackSourceKind.HTTPS_ARCHIVE)),
                new PackSourceStagers(List.of(fetcher), mock(WholePackWriteStore.Loader.class)),
                info::worktree);
        PackRegistrationController controller = new PackRegistrationController(
                jobs,
                mock(PackSummaryQuery.class),
                mock(PackArchiveReader.class),
                new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler()),
                intake,
                mock(PackRemovalController.class),
                mock(PackDocumentController.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new RegistrationAdmission(new RegistrationSwitch(true), jobs, info))
                .setControllerAdvice(new PackSourceBodyAdvice(), new RefusalHandler())
                .addPlaceholderValue("charter.api.base-path", "/api/v1")
                .build();

        mvc.perform(
                        post("/api/v1/packs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"kind\":\"https_archive\",\"packId\":\"acme\",\"url\":\"https://packs.example.org/a.tgz\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("refused"))
                .andExpect(jsonPath("$.problem.code").value("registration_timed_out"));
    }

    /**
     * A reservation the deadline ended before the controller started it: it answers {@code pending} until
     * {@code start} is called, and then its refused end, and {@code start} reports that nothing was started. A controller
     * that does not read the status again after {@code start} answers {@code pending}, which the tests refuse.
     */
    static final class EndedBeforeItStarts implements RegistrationJobs.Reservation {
        private final String id;
        private final Path directory;
        private boolean startAsked;

        EndedBeforeItStarts(String id, Path directory) {
            this.id = id;
            this.directory = directory;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Path directory() {
            return directory;
        }

        @Override
        public boolean start(RegistrationJobs.Stager stager) {
            startAsked = true;
            return false;
        }

        @Override
        public void abandon() {}

        @Override
        public RegistrationJobs.Status status() {
            String detail = "The registration did not finish within 90 seconds.";
            return startAsked
                    ? new RegistrationJobs.Status(
                            id,
                            "acme",
                            RegistrationJobs.State.REFUSED,
                            Instant.parse("2026-11-08T10:00:00Z"),
                            Instant.parse("2026-11-08T10:01:30Z"),
                            new RegistrationJobs.Refused(
                                    RegistrationJobs.Kind.CODED,
                                    new CharterRefusal(RefusalCode.REGISTRATION_TIMED_OUT, detail),
                                    detail))
                    : new RegistrationJobs.Status(
                            id,
                            "acme",
                            RegistrationJobs.State.PENDING,
                            Instant.parse("2026-11-08T10:00:00Z"),
                            null,
                            null);
        }

        @Override
        public String revisionBefore() {
            return "";
        }
    }
}
