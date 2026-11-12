package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackSourceStagers;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.web.RefusalHandler;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code POST /packs} at the web edge (FR-004, IC-14, IC-18): the checks that come before the job (registration and the
 * kind's own flag, the conditional headers, a stale {@code If-Match}, a full queue, the body's size and media type), the
 * {@code 202} with the status to poll, and the job's audit entry naming the kind and the locator with its query,
 * fragment and user information cut. The fetch itself is a stub: nothing here opens a socket or reads a file.
 */
class PackSourceRegistrationTest {

    private static final String BODY =
            "{\"kind\":\"https_archive\",\"packId\":\"acme\",\"url\":\"https://u:s3cr3t@packs.example.org/a.tgz?token=s3cr3t#f\"}";

    @TempDir
    Path staging;

    private final List<AuditLog.Entry> audit = new CopyOnWriteArrayList<>();
    private final List<PackSourceRequest> fetched = new CopyOnWriteArrayList<>();
    private final RegistrationSwitch registration = new RegistrationSwitch(true);
    private final PackSourceSwitch sources = new PackSourceSwitch(Set.of(PackSourceKind.HTTPS_ARCHIVE));
    private RegistrationJobs jobs;
    private ServiceInfoQuery info;
    private MockMvc mvc;

    @BeforeEach
    void build() {
        jobs = new RegistrationJobs(new RegistrationJobs.Dependencies(
                Clock.systemUTC(),
                mock(PackRegistrationService.class),
                pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "not stored");
                },
                audit::add,
                RegistrationJobs.JobGate.OPEN,
                RegistrationJobs.StorageRoom.UNBOUNDED,
                new FileStagingArea(staging)));
        info = new ServiceInfoQuery("worktree-source", new ConsistentRead(new FileSystemPackSources(List::of)));
        mvc = mvcWith(List.of(stub(PackSourceKind.HTTPS_ARCHIVE)), info);
    }

    private PackSourceFetcher stub(PackSourceKind kind) {
        return new PackSourceFetcher() {
            @Override
            public PackSourceKind kind() {
                return kind;
            }

            @Override
            public Staged fetch(PackSourceRequest request, Path directory) {
                fetched.add(request);
                throw new CharterRefusal(RefusalCode.PACK_SOURCE_REFUSED, "host_not_allowed: stub");
            }
        };
    }

    private MockMvc mvcWith(List<PackSourceFetcher> fetchers, ServiceInfoQuery info) {
        PackSourceIntake intake = new PackSourceIntake(
                sources, new PackSourceStagers(fetchers, mock(WholePackWriteStore.Loader.class)), info::worktree);
        PackRegistrationController controller = new PackRegistrationController(
                jobs,
                mock(PackSummaryQuery.class),
                mock(PackArchiveReader.class),
                new PackRegistrationMapper(new Hypermedia("/api/v1"), new RefusalHandler()),
                intake,
                mock(PackRemovalController.class),
                mock(PackDocumentController.class));
        return MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new RegistrationAdmission(registration, jobs, info))
                .setControllerAdvice(new PackSourceBodyAdvice(), new RefusalHandler())
                .addPlaceholderValue("charter.api.base-path", "/api/v1")
                .build();
    }

    @AfterEach
    void close() {
        jobs.close();
    }

    private ResultActions send(String body, String... headers) throws Exception {
        var request =
                post("/api/v1/packs").contentType(MediaType.APPLICATION_JSON).content(body);
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return mvc.perform(request);
    }

    private void awaitAudit() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (audit.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(audit).isNotEmpty();
    }

    @Test
    void aSourceIsAcceptedWithAStatusToPollAndTheJobAuditsItsKindAndACutLocator() throws Exception {
        MockHttpServletResponse response = send(BODY)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/pack-registrations/")))
                .andExpect(jsonPath("$.packId").value("acme"))
                .andExpect(jsonPath("$.state").value("pending"))
                .andReturn()
                .getResponse();

        awaitAudit();
        assertThat(response.getContentAsString()).doesNotContain("s3cr3t");
        assertThat(audit).singleElement().satisfies(entry -> {
            assertThat(entry.operation()).isEqualTo("registerPackFromSource");
            assertThat(entry.worktree()).isEqualTo("worktree-source");
            assertThat(entry.packId()).isEqualTo("acme");
            assertThat(entry.sourceKind()).isEqualTo("https_archive");
            assertThat(entry.locator()).isEqualTo("https://packs.example.org/a.tgz");
            assertThat(entry.outcome()).isEqualTo("refused:pack_source_refused");
        });
        assertThat(fetched).singleElement().satisfies(request -> {
            assertThat(request.kind()).isEqualTo(PackSourceKind.HTTPS_ARCHIVE);
            assertThat(request.url()).contains("token=s3cr3t");
        });
    }

    private static final String GIT_URL = "https://git.example.org/acme/packs.git";

    private static String git(String ref, String commit) {
        return "{\"kind\":\"git\",\"packId\":\"acme\",\"url\":\"" + GIT_URL + "\",\"ref\":\"" + ref + "\""
                + (commit == null ? "" : ",\"commit\":\"" + commit + "\"") + "}";
    }

    private void gitIsBuiltAndOn() {
        sources.set(PackSourceKind.GIT, true);
        mvc = mvcWith(List.of(stub(PackSourceKind.HTTPS_ARCHIVE), stub(PackSourceKind.GIT)), info);
    }

    @Test
    void aBranchRefWithoutACommitOrAPreconditionIsPreconditionRequiredAndTakesNoSlot() throws Exception {
        gitIsBuiltAndOn();

        MockHttpServletResponse response = send(git("refs/heads/main", null))
                .andExpect(status().is(428))
                .andReturn()
                .getResponse();

        assertThat(response.getContentAsString()).doesNotContain("\"code\"").doesNotContain("git.example.org");
        assertThat(jobs.retained()).isZero();
        assertThat(fetched).isEmpty();
    }

    @Test
    void aBranchRefWithIfMatchPassesTheRuleAndMeetsTheStalenessCheckInstead() throws Exception {
        gitIsBuiltAndOn();

        send(git("refs/heads/main", null), "If-Match", "\"sha256:" + "a".repeat(64) + "\"")
                .andExpect(status().isPreconditionFailed());
        assertThat(jobs.retained()).isZero();
    }

    @Test
    void aBranchRefThatCreatesTheTargetOrNamesItsCommitOrIsATagOrACommitIsAccepted() throws Exception {
        gitIsBuiltAndOn();
        String sha = "0123456789abcdef0123456789abcdef01234567";

        send(git("refs/heads/main", null), "If-None-Match", "*").andExpect(status().isAccepted());
        send(git("refs/heads/main", sha)).andExpect(status().isAccepted());
        send(git("refs/tags/v1.0.0", null)).andExpect(status().isAccepted());
        send(git(sha, null)).andExpect(status().isAccepted());
    }

    @Test
    void theBranchRuleIsOfTheGitSourceAlone() throws Exception {
        gitIsBuiltAndOn();

        send(BODY).andExpect(status().isAccepted());
    }

    @Test
    void aGitSourceThatIsSwitchedOffIsUnavailableBeforeThePreconditionRule() throws Exception {
        gitIsBuiltAndOn();
        sources.set(PackSourceKind.GIT, false);

        send(git("refs/heads/main", null))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pack_source_unavailable"));
    }

    @Test
    void registrationSwitchedOffIsUnavailableBeforeAnythingIsTaken() throws Exception {
        registration.set(false);

        send(BODY)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pack_source_unavailable"));

        assertThat(jobs.retained()).isZero();
    }

    @Test
    void aKindThatIsSwitchedOffIsUnavailableAndTakesNoSlot() throws Exception {
        sources.set(PackSourceKind.HTTPS_ARCHIVE, false);

        send(BODY)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pack_source_unavailable"));

        assertThat(jobs.retained()).isZero();
        assertThat(fetched).isEmpty();
    }

    @Test
    void aKindNobodyBuiltIsUnavailableEvenWhenItsFlagIsOn() throws Exception {
        sources.set(PackSourceKind.GIT, true);

        send("{\"kind\":\"git\",\"packId\":\"acme\",\"url\":\"https://git.example.org/a.git\",\"ref\":\"refs/tags/v1\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pack_source_unavailable"))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("git.example.org"))));

        assertThat(jobs.retained()).isZero();
    }

    @Test
    void bothValidatorsTogetherAndMalformedOnesAreBadRequests() throws Exception {
        send(BODY, "If-Match", "*", "If-None-Match", "*").andExpect(status().isBadRequest());
        send(BODY, "If-None-Match", "\"x\"").andExpect(status().isBadRequest());
        send(BODY, "If-Match", "not-a-revision").andExpect(status().isBadRequest());

        assertThat(jobs.retained()).isZero();
    }

    @Test
    void aStaleIfMatchIsAPreconditionFailureBeforeTheJob() throws Exception {
        send(BODY, "If-Match", "\"sha256:" + "a".repeat(64) + "\"").andExpect(status().isPreconditionFailed());

        assertThat(jobs.retained()).isZero();
        assertThat(fetched).isEmpty();
    }

    @Test
    void aFullQueueIsToldToRetryLater() throws Exception {
        RegistrationJobs.Origin origin = new RegistrationJobs.Origin(
                "worktree-source", AuditLog.Credential.WORKTREE_TOKEN, "registerPackFromSource", "https_archive", "");
        List<RegistrationJobs.Reservation> held = new ArrayList<>();
        for (int i = 0; i < RegistrationJobs.RUNNING_LIMIT + RegistrationJobs.WAITING_LIMIT; i++) {
            held.add(jobs.reserve("held-" + i, Preconditions.none(), origin));
        }

        send(BODY).andExpect(status().isServiceUnavailable()).andExpect(header().exists("Retry-After"));

        held.forEach(RegistrationJobs.Reservation::abandon);
    }

    @Test
    void aBodyOverTheSharedLimitIsRefused413BeforeItIsRead() throws Exception {
        String big = "{\"kind\":\"https_archive\",\"packId\":\"acme\",\"url\":\"https://x.example/"
                + "a".repeat(1024 * 1024) + "\"}";

        MockHttpServletResponse response =
                send(big).andExpect(status().is(413)).andReturn().getResponse();

        assertThat(response.getContentAsString()).doesNotContain("\"code\"");
        assertThat(jobs.retained()).isZero();
    }

    @Test
    void aBodyThatIsNotJsonIsAnUnsupportedMediaType() throws Exception {
        mvc.perform(post("/api/v1/packs").contentType(MediaType.TEXT_PLAIN).content(BODY))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(jobs.retained()).isZero();
    }

    @Test
    void theStatusTheAcceptanceAnswersIsTheOneTheJobsHold() throws Exception {
        String location = send(BODY)
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).isNotNull();
        String id = location.substring(location.lastIndexOf('/') + 1);
        assertThat(jobs.status(id)).isPresent();
    }
}
