package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.PackRegistrationStatus;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.RegistrationOutcome;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.RegistrationImpact;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.RegistrationJobs.Kind;
import ai.speckitty.charter.packs.application.RegistrationJobs.Refused;
import ai.speckitty.charter.packs.application.RegistrationJobs.State;
import ai.speckitty.charter.packs.application.RegistrationJobs.Succeeded;
import ai.speckitty.charter.web.RefusalHandler;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What the status of a registration from a source answers (IC-05): the source as recorded (a git source with the commit
 * its ref resolved to), and a plain 502 or 504.
 */
class PackRegistrationMapperSourceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private final PackRegistrationMapper mapper =
            new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler());

    private static RegistrationOutcome outcome() {
        return new RegistrationOutcome(
                Outcome.CREATED,
                new PackIdentifier("acme"),
                new PackDescriptor(
                        "01M4P01D5TWB5FKZGNNQG3943P",
                        "1.0.0",
                        Optional.empty(),
                        "acme",
                        Optional.of(PackLevel.ORG),
                        Optional.empty()),
                PackLevel.ORG,
                PackRevision.compute(List.of("x")),
                RegistrationImpact.Notices.NONE,
                List.of());
    }

    private PackRegistrationStatus statusOf(RegistrationJobs.Ending ending, State state) {
        return mapper.status(
                new RegistrationJobs.Status("01M6TQ62HXYF362GAFS2BZ3PGF", "acme", state, NOW, NOW, ending));
    }

    @Test
    void aHttpsArchiveResultCarriesItsKindAndUrlAsSent() {
        PackRegistrationStatus status = statusOf(
                new Succeeded(outcome(), Map.of("kind", "https_archive", "url", "https://packs.example.org/a.tgz?x=1")),
                State.SUCCEEDED);

        assertThat(status.getResult().getSource().getKind().getValue()).isEqualTo("https_archive");
        assertThat(status.getResult().getSource().getUrl()).isEqualTo("https://packs.example.org/a.tgz?x=1");
        assertThat(status.getResult().getSource().getPath()).isNull();
    }

    @Test
    void aGitResultCarriesItsRefAndTheCommitTheRefResolvedTo() {
        String commit = "0123456789abcdef0123456789abcdef01234567";
        PackRegistrationStatus status = statusOf(
                new Succeeded(
                        outcome(),
                        Map.of(
                                "kind", "git",
                                "url", "https://git.example.org/acme/packs.git",
                                "ref", "refs/heads/main",
                                "commit", commit)),
                State.SUCCEEDED);

        assertThat(status.getResult().getSource().getKind().getValue()).isEqualTo("git");
        assertThat(status.getResult().getSource().getUrl()).isEqualTo("https://git.example.org/acme/packs.git");
        assertThat(status.getResult().getSource().getRef()).isEqualTo("refs/heads/main");
        assertThat(status.getResult().getSource().getCommit()).isEqualTo(commit);
    }

    @Test
    void aFilesystemResultCarriesItsKindAndPathAsSent() {
        PackRegistrationStatus status =
                statusOf(new Succeeded(outcome(), Map.of("kind", "filesystem", "path", "packs/acme")), State.SUCCEEDED);

        assertThat(status.getResult().getSource().getKind().getValue()).isEqualTo("filesystem");
        assertThat(status.getResult().getSource().getPath()).isEqualTo("packs/acme");
        assertThat(status.getResult().getSource().getUrl()).isNull();
    }

    @Test
    void aResultOfAnArchiveUploadCarriesNoSource() {
        PackRegistrationStatus status = statusOf(new Succeeded(outcome(), Map.of()), State.SUCCEEDED);

        assertThat(status.getResult().getSource()).isNull();
    }

    @Test
    void anUpstreamThatFailedIsAPlainBadGateway() {
        PackRegistrationStatus status =
                statusOf(new Refused(Kind.UPSTREAM_FAILED, null, "The upstream failed."), State.REFUSED);

        assertThat(status.getProblem().getStatus()).isEqualTo(502);
        assertThat(status.getProblem().getCode()).isNull();
        assertThat(status.getProblem().getDetail()).isEqualTo("The upstream failed.");
    }

    @Test
    void anUpstreamThatTimedOutIsAPlainGatewayTimeout() {
        PackRegistrationStatus status =
                statusOf(new Refused(Kind.UPSTREAM_TIMEOUT, null, "The upstream did not answer."), State.REFUSED);

        assertThat(status.getProblem().getStatus()).isEqualTo(504);
        assertThat(status.getProblem().getCode()).isNull();
    }
}
