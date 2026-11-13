package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * What an upload leaves on disk while its job is held or running is the owner's alone (plan, security squad 8): the
 * staging root the service created, the job's directory, the spooled archive and the directory the archive is
 * extracted into. Read as POSIX permissions, as the other staging tests do.
 */
class StagingPermissionsTest {

    private static final Set<PosixFilePermission> DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE = PosixFilePermissions.fromString("rw-------");
    private static final byte[] GZIP = {0x1f, (byte) 0x8b, 8, 0, 0, 0, 0, 0, 0, 0};

    @TempDir
    Path host;

    @Test
    void theStagingRootTheJobDirectoryTheArchiveAndTheExtractDirectoryAreOwnerOnly() throws Exception {
        Path staging = host.resolve("spool").resolve("staging");
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Set<PosixFilePermission>> extractModes = new CopyOnWriteArrayList<>();
        PackArchiveReader reader = readerRecordingTheExtractMode(extractModes);
        try (RegistrationJobs jobs = jobsStagingIn(staging, gateHeldUntilReleased(held, release))) {
            MockMvc mvc = mvc(jobs, reader);

            mvc.perform(put("/api/v1/packs/acme")
                            .contentType("application/gzip")
                            .content(GZIP))
                    .andExpect(status().isAccepted());

            assertThat(held.await(10, TimeUnit.SECONDS))
                    .as("the job reached its gate")
                    .isTrue();
            assertThat(Files.getPosixFilePermissions(staging))
                    .as("the staging root the service created")
                    .isEqualTo(DIRECTORY);
            assertThat(Files.getPosixFilePermissions(staging.getParent()))
                    .as("a parent the service had to create to get to it")
                    .isEqualTo(DIRECTORY);
            Path job;
            try (var jobsOnDisk = Files.list(staging)) {
                job = jobsOnDisk.findFirst().orElseThrow();
            }
            assertThat(Files.getPosixFilePermissions(job))
                    .as("the job directory")
                    .isEqualTo(DIRECTORY);
            assertThat(Files.getPosixFilePermissions(job.resolve("archive")))
                    .as("the uploaded archive")
                    .isEqualTo(FILE);

            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (extractModes.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(extractModes)
                    .as("the directory the archive is extracted into")
                    .containsExactly(DIRECTORY);
        } finally {
            release.countDown();
        }
    }

    private static PackArchiveReader readerRecordingTheExtractMode(List<Set<PosixFilePermission>> extractModes) {
        return (body, format, into) -> {
            try {
                extractModes.add(Files.getPosixFilePermissions(into));
            } catch (IOException unreadable) {
                throw new IllegalStateException(unreadable);
            }
            throw new IllegalStateException("the test reads the mode of the directory, then ends the job");
        };
    }

    private static RegistrationJobs.JobGate gateHeldUntilReleased(CountDownLatch held, CountDownLatch release) {
        return new RegistrationJobs.JobGate() {
            @Override
            public void beforeRun(String id) {
                held.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    private static RegistrationJobs jobsStagingIn(Path staging, RegistrationJobs.JobGate gate) {
        return new RegistrationJobs(new RegistrationJobs.Dependencies(
                Clock.systemUTC(),
                mock(PackRegistrationService.class),
                pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "not stored");
                },
                (AuditLog) entry -> {},
                gate,
                RegistrationJobs.StorageRoom.UNBOUNDED,
                new FileStagingArea(staging)));
    }

    private static MockMvc mvc(RegistrationJobs jobs, PackArchiveReader reader) {
        ServiceInfoQuery info =
                new ServiceInfoQuery("worktree-permissions", new ConsistentRead(new FileSystemPackSources(List::of)));
        PackRegistrationController controller = new PackRegistrationController(
                jobs,
                mock(PackSummaryQuery.class),
                reader,
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
}
