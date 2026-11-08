package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.AuditLog.Credential;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.RegistrationJobs.Kind;
import ai.speckitty.charter.packs.application.RegistrationJobs.Origin;
import ai.speckitty.charter.packs.application.RegistrationJobs.Refused;
import ai.speckitty.charter.packs.application.RegistrationJobs.Reservation;
import ai.speckitty.charter.packs.application.RegistrationJobs.Stager;
import ai.speckitty.charter.packs.application.RegistrationJobs.State;
import ai.speckitty.charter.packs.application.RegistrationJobs.Status;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a job does with the failures of a source (IC-05, IC-14): an upstream that failed or timed out ends it refused as a
 * plain problem of its own kind, a refusal of the source stays coded, and the one audit entry names the source kind and
 * the locator the attempt came with and the outcome, with nothing of the upstream.
 */
class RegistrationJobsUpstreamTest {

    private static final Origin ORIGIN = new Origin(
            "worktree-1",
            Credential.WORKTREE_TOKEN,
            "registerPackFromSource",
            "https_archive",
            "https://packs.example.org/acme.tar.gz");

    private final List<AuditLog.Entry> audit = new CopyOnWriteArrayList<>();
    private RegistrationJobs jobs;

    @AfterEach
    void close() {
        jobs.close();
    }

    private Status run(Path host, Stager stager) throws InterruptedException {
        jobs = new RegistrationJobs(new RegistrationJobs.Dependencies(
                Clock.systemUTC(),
                mock(PackRegistrationService.class),
                pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "not stored");
                },
                audit::add,
                RegistrationJobs.JobGate.OPEN,
                RegistrationJobs.StorageRoom.UNBOUNDED,
                new FileStagingArea(host)));
        Reservation reservation = jobs.reserve("acme", Preconditions.none(), ORIGIN);
        reservation.start(stager);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Status status = reservation.status();
            if (status.state() == State.REFUSED || status.state() == State.SUCCEEDED) {
                return status;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("the job did not end");
    }

    @Test
    void anUpstreamThatFailedEndsTheJobRefusedAsABadGatewayAndNothingElse(@TempDir Path host) throws Exception {
        Status status = run(host, directory -> {
            throw SourceUpstreamException.failed();
        });

        assertThat(status.ending()).isInstanceOfSatisfying(Refused.class, refused -> {
            assertThat(refused.kind()).isEqualTo(Kind.UPSTREAM_FAILED);
            assertThat(refused.refusal()).isNull();
            assertThat(refused.detail())
                    .isEqualTo(SourceUpstreamException.failed().getMessage());
        });
        assertThat(audit).singleElement().satisfies(entry -> {
            assertThat(entry.outcome()).isEqualTo("refused:upstream_failed");
            assertThat(entry.sourceKind()).isEqualTo("https_archive");
            assertThat(entry.locator()).isEqualTo("https://packs.example.org/acme.tar.gz");
            assertThat(entry.operation()).isEqualTo("registerPackFromSource");
        });
    }

    @Test
    void anUpstreamThatDidNotAnswerInTimeEndsTheJobRefusedAsAGatewayTimeout(@TempDir Path host) throws Exception {
        Status status = run(host, directory -> {
            throw SourceUpstreamException.deadline();
        });

        assertThat(status.ending())
                .isInstanceOfSatisfying(
                        Refused.class, refused -> assertThat(refused.kind()).isEqualTo(Kind.UPSTREAM_TIMEOUT));
        assertThat(audit)
                .singleElement()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo("refused:upstream_timeout"));
    }

    @Test
    void aRefusalOfTheSourceStaysACodedRefusalWithItsAuditToken(@TempDir Path host) throws Exception {
        Status status = run(host, directory -> {
            throw SourceRefusal.HOST_NOT_ALLOWED.refusal();
        });

        assertThat(status.ending()).isInstanceOfSatisfying(Refused.class, refused -> {
            assertThat(refused.kind()).isEqualTo(Kind.CODED);
            assertThat(refused.detail()).startsWith("host_not_allowed: ");
        });
        assertThat(audit)
                .singleElement()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo("refused:pack_source_refused"));
    }
}
