package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The free-space check runs at admission too (#418, A6): from the declared length, before a byte of the body is read, so
 * a full volume is a {@code 503 pack_storage_full} answered to the client that is about to send 32 MiB, with no job and
 * nothing staged. A body with no declared length is judged as a full-size one.
 */
class StorageRoomAdmissionTest {

    private static final byte[] GZIP = {0x1f, (byte) 0x8b, 8, 0, 0, 0, 0, 0, 0, 0};

    @TempDir
    Path staging;

    private final List<Long> asked = new CopyOnWriteArrayList<>();
    private volatile CharterRefusal full;
    private RegistrationJobs jobs;
    private MockMvc mvc;

    @BeforeEach
    void build() {
        RegistrationJobs.StorageRoom room = new RegistrationJobs.StorageRoom() {
            @Override
            public void require() {
                require(RegistrationJobs.StorageRoom.UNKNOWN_BYTES);
            }

            @Override
            public void require(long archiveBytes) {
                asked.add(archiveBytes);
                if (full != null) {
                    throw full;
                }
            }
        };
        jobs = new RegistrationJobs(new RegistrationJobs.Dependencies(
                Clock.systemUTC(),
                mock(PackRegistrationService.class),
                pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "not stored");
                },
                (AuditLog) entry -> {},
                RegistrationJobs.JobGate.OPEN,
                room,
                new FileStagingArea(staging)));
        ServiceInfoQuery info =
                new ServiceInfoQuery("worktree-room", new ConsistentRead(new FileSystemPackSources(List::of)));
        PackArchiveReader reader = (body, format, into) -> {
            throw new IllegalStateException("the test does not extract");
        };
        PackRegistrationController controller = new PackRegistrationController(
                jobs,
                mock(PackSummaryQuery.class),
                reader,
                new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler()),
                PackSourceIntake.none(),
                mock(PackRemovalController.class),
                mock(PackDocumentController.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new RegistrationAdmission(new RegistrationSwitch(true), jobs, info))
                .setControllerAdvice(new ArchiveBodyAdvice(), new RefusalHandler())
                .addPlaceholderValue("charter.api.base-path", "/api/v1")
                .build();
    }

    @AfterEach
    void close() {
        jobs.close();
    }

    @Test
    void aFullVolumeIsA503PackStorageFullAnsweredBeforeTheBodyIsReadWithNoJobAndNothingStaged() throws Exception {
        full = new CharterRefusal(RefusalCode.PACK_STORAGE_FULL, "The pack volume has 10 MiB free.");

        mvc.perform(put("/api/v1/packs/acme").contentType("application/gzip").content(GZIP))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pack_storage_full"));

        assertThat(jobs.retained()).isZero();
        assertThat(entries(staging)).isEmpty();
    }

    @Test
    void theCheckIsGivenTheDeclaredLengthAtAdmission() throws Exception {
        mvc.perform(put("/api/v1/packs/acme").contentType("application/gzip").content(GZIP))
                .andExpect(status().isAccepted());

        assertThat(asked.get(0)).as("at admission, from Content-Length").isEqualTo((long) GZIP.length);
    }

    @Test
    void aBodyWithNoDeclaredLengthIsJudgedAsAFullSizeOne() throws Exception {
        mvc.perform(put("/api/v1/packs/acme")
                        .contentType("application/gzip")
                        .content(GZIP)
                        .with(request -> withoutADeclaredLength(request, GZIP)))
                .andExpect(status().isAccepted());

        assertThat(asked.get(0)).isEqualTo(RegistrationJobs.StorageRoom.UNKNOWN_BYTES);
    }

    private static List<Path> entries(Path directory) throws IOException {
        try (Stream<Path> listed = Files.list(directory)) {
            return listed.toList();
        }
    }

    private static MockHttpServletRequest withoutADeclaredLength(MockHttpServletRequest from, byte[] body) {
        MockHttpServletRequest chunked =
                new MockHttpServletRequest(from.getServletContext(), from.getMethod(), from.getRequestURI()) {
                    @Override
                    public int getContentLength() {
                        return -1;
                    }

                    @Override
                    public long getContentLengthLong() {
                        return -1;
                    }
                };
        Collections.list(from.getHeaderNames()).stream()
                .filter(name -> !"Content-Length".equalsIgnoreCase(name))
                .forEach(name -> chunked.addHeader(name, from.getHeader(name)));
        chunked.addHeader("Transfer-Encoding", "chunked");
        chunked.setContentType(from.getContentType());
        chunked.setContent(body);
        return chunked;
    }
}
