package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * One audit entry per registration or DELETE attempt that ends at the web edge (NFR-010): whatever refused it, a request
 * the job took over is entered by its job, and a request of any other kind is not entered at all.
 */
class RegistrationAuditFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private final List<AuditLog.Entry> entries = new ArrayList<>();
    private final RegistrationAuditFilter filter = new RegistrationAuditFilter(
            entries::add,
            new ServiceInfoQuery("worktree-9", new ConsistentRead(new FileSystemPackSources(List::of))),
            "/api/v1",
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aRefusedAttemptIsEnteredWithTheStatusItGot() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/packs/acme");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                AuditContext.of(request).revisionBefore("sha256:aa");
                ((MockHttpServletResponse) rs).setStatus(413);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.outcome()).isEqualTo("refused:413");
            assertThat(entry.operation()).isEqualTo("registerPackFromArchive");
            assertThat(entry.packId()).isEqualTo("acme");
            assertThat(entry.sourceKind()).isEqualTo("archive");
            assertThat(entry.worktree()).isEqualTo("worktree-9");
            assertThat(entry.at()).isEqualTo(NOW);
            assertThat(entry.revisionBefore()).isEqualTo("sha256:aa");
            assertThat(entry.revisionAfter()).as("a refusal changed nothing").isEqualTo("sha256:aa");
        });
    }

    @Test
    void anAttemptTheJobTookOverIsLeftToTheJob() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/packs/acme");
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                AuditContext.of(request).handedToJob();
                ((MockHttpServletResponse) rs).setStatus(202);
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(entries).as("the job writes the entry when it ends").isEmpty();
    }

    @Test
    void aRequestThatIsNoAttemptIsNotEntered() throws ServletException, IOException {
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/packs/acme"),
                new MockHttpServletResponse(),
                new MockFilterChain());
        filter.doFilter(
                new MockHttpServletRequest("PUT", "/api/v1/packs/acme/relationships"),
                new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(entries).isEmpty();
    }

    @Test
    void aFailureOfTheChainIsEnteredOnceAsA500AndRethrown() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/packs");
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                throw new IllegalStateException("boom");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.outcome()).isEqualTo("refused:500");
            assertThat(entry.operation()).isEqualTo("registerPackFromSource");
        });
    }

    @Test
    void aSuccessfulDeleteIsEnteredAsSucceeded() throws ServletException, IOException {
        MockHttpServletRequest request =
                new MockHttpServletRequest("DELETE", "/api/v1/packs/acme/artifacts/directive/A");
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                ((MockHttpServletResponse) rs).setStatus(204);
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(entries)
                .singleElement()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo("succeeded:204"));
    }

    @Test
    void theCredentialTheGuardRecordedIsTheEntrys() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/packs/acme");
        request.setAttribute(AuditContext.CREDENTIAL_ATTRIBUTE, AuditLog.Credential.WORKTREE_TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(entries)
                .singleElement()
                .satisfies(entry -> assertThat(entry.credential()).isEqualTo(AuditLog.Credential.WORKTREE_TOKEN));
    }
}
