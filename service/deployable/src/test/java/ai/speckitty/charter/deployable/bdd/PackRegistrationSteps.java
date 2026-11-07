package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.infra.api.RegistrationSwitch;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The registration vocabulary of contracts/charter/scenarios/README.md ("Registration steps") for the whole-pack
 * registration scenarios that WP16 binds: the archives built from the files a scenario names, the {@code PUT} of one
 * with its variants, polling a registration to its end, and the seam of the registration jobs (hold them before they
 * run or while they run, fill the queue, let the retention hour pass). It sends through the one {@link ScenarioHttp} the
 * scenario owns, and puts every seam back at its default when the scenario ends.
 */
public class PackRegistrationSteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API = "/api/v1";
    private static final String GZIP = "application/gzip";
    private static final String ZIP = "application/zip";
    private static final long OVER_THE_LIMIT = 32L * 1024 * 1024 + 1;
    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final Duration RETENTION_PLUS = Duration.ofMinutes(61);
    private static final int FILLERS = RegistrationJobs.RUNNING_LIMIT + RegistrationJobs.WAITING_LIMIT;

    private final ScenarioHttp http;
    private final CharterSteps charter;
    private final RegistrationSteps writes;
    private final RegistrationSwitch registration;
    private final RegistrationJobs jobs;
    private final HoldableGate gate;
    private final ScenarioClock clock;
    private final ScenarioArchives archives = new ScenarioArchives();
    private final List<String> fillers = new ArrayList<>();
    private @Nullable String rememberedLocation;
    private @Nullable CountDownLatch registryRelease;

    /**
     * The steps over the scenario's HTTP seam and the registration seams.
     *
     * @param http the scenario's HTTP seam
     * @param charter the generic steps, whose remembered fields the comparison steps read
     * @param writes the write steps, which remember the {@code ETag}
     * @param registration whether registration is switched on
     * @param jobs the registration jobs
     * @param gate the latch seam of the jobs
     * @param clock the jobs' clock
     */
    public PackRegistrationSteps(
            ScenarioHttp http,
            CharterSteps charter,
            RegistrationSteps writes,
            RegistrationSwitch registration,
            RegistrationJobs jobs,
            HoldableGate gate,
            ScenarioClock clock) {
        this.http = http;
        this.charter = charter;
        this.writes = writes;
        this.registration = registration;
        this.jobs = jobs;
        this.gate = gate;
        this.clock = clock;
    }

    /** Before the scenario's packs are deleted (a higher order runs first): a job may still be writing into them. */
    @After(order = 20000)
    public void putTheSeamsBack() {
        releaseTheRegistry();
        gate.releaseAll();
        registration.set(false);
        awaitNoRunningJob();
    }

    // ---- the switches and the archives ----------------------------------------------------------------------------

    @Given("registration is switched on")
    public void switchedOn() {
        registration.set(true);
    }

    @Given("registration is switched off")
    public void switchedOff() {
        registration.set(false);
    }

    @Given("the archive {string} holds the file {string}:")
    public void archiveHolds(String archive, String path, String text) {
        archives.put(archive, path, text);
    }

    @Given("the archive {string} is a copy of the archive {string}")
    public void archiveCopy(String archive, String of) {
        archives.copy(archive, of);
    }

    @Given("the archive {string} lacks the file {string}")
    public void archiveLacks(String archive, String path) {
        archives.remove(archive, path);
    }

    // ---- sending an archive ----------------------------------------------------------------------------------------

    @When("I PUT {string} with the archive {string}")
    public void putArchive(String path, String archive) throws Exception {
        send(path, archives.tarGz(archive), GZIP, Map.of(), http.suiteToken());
    }

    @When("I PUT {string} with the archive {string} as a zip archive")
    public void putZip(String path, String archive) throws Exception {
        send(path, archives.zip(archive), ZIP, Map.of(), http.suiteToken());
    }

    @When("I PUT {string} with the archive {string} as {string}")
    public void putArchiveAs(String path, String archive, String mediaType) throws Exception {
        send(path, archives.tarGz(archive), mediaType, Map.of(), http.suiteToken());
    }

    @When("I PUT {string} with the gzip archive {string} as {string}")
    public void putGzipAs(String path, String archive, String mediaType) throws Exception {
        send(path, archives.tarGz(archive), mediaType, Map.of(), http.suiteToken());
    }

    @When("I PUT {string} with an archive larger than the body limit")
    public void putTooLarge(String path) throws Exception {
        byte[] body = new byte[Math.toIntExact(OVER_THE_LIMIT)];
        body[0] = 0x1f;
        body[1] = (byte) 0x8b;
        send(path, body, GZIP, Map.of(), http.suiteToken());
    }

    @When("I PUT {string} with the archive {string} and the header {string}: {string}")
    public void putWithHeader(String path, String archive, String name, String value) throws Exception {
        send(path, archives.tarGz(archive), GZIP, Map.of(name, value), http.suiteToken());
    }

    @When("I PUT {string} with the archive {string} and the headers {string}: {string} and {string}: {string}")
    public void putWithHeaders(
            String path, String archive, String first, String firstValue, String second, String secondValue)
            throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(first, firstValue);
        headers.put(second, secondValue);
        send(path, archives.tarGz(archive), GZIP, headers, http.suiteToken());
    }

    @When("I PUT {string} with the archive {string} and the remembered ETag")
    public void putWithRememberedETag(String path, String archive) throws Exception {
        String eTag = writes.rememberedETag();
        assertThat(eTag).as("an ETag was remembered").isNotNull();
        send(path, archives.tarGz(archive), GZIP, Map.of(HttpHeaders.IF_MATCH, eTag), http.suiteToken());
    }

    @When("I PUT {string} with the archive {string} without the worktree token")
    public void putWithoutToken(String path, String archive) throws Exception {
        send(path, archives.tarGz(archive), GZIP, Map.of(), null);
    }

    private void send(String path, byte[] body, String mediaType, Map<String, String> extra, @Nullable String token)
            throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, mediaType);
        headers.putAll(extra);
        http.performBytes("PUT", path, headers, body, token);
    }

    // ---- polling a registration
    // ----------------------------------------------------------------------------------------

    @When("I poll the registration until it ends")
    public void pollUntilItEnds() throws Exception {
        pollUntilEnded(locationOfLastAnswer());
    }

    @When("I remember the Location header")
    public void rememberLocation() {
        rememberedLocation = http.response().getHeader(HttpHeaders.LOCATION);
        assertThat(rememberedLocation).as("the answer carries a Location").isNotNull();
    }

    @When("I GET the remembered registration")
    public void getRemembered() throws Exception {
        http.get(belowBase(remembered()));
    }

    @When("I poll the remembered registration until it ends")
    public void pollRememberedUntilItEnds() throws Exception {
        pollUntilEnded(remembered());
    }

    private void pollUntilEnded(String location) throws Exception {
        String path = belowBase(location);
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (true) {
            http.get(path);
            String state = JSON.readTree(http.body()).path("state").asString();
            if (state.equals("succeeded") || state.equals("refused")) {
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the registration did not end within " + WAIT + ": " + http.body());
            }
            Thread.sleep(10);
        }
    }

    private String locationOfLastAnswer() {
        String location = http.response().getHeader(HttpHeaders.LOCATION);
        assertThat(location).as("the last answer carries a Location").isNotNull();
        return location;
    }

    private String remembered() {
        assertThat(rememberedLocation).as("a Location was remembered").isNotNull();
        return rememberedLocation;
    }

    private static String belowBase(String location) {
        String path = URI.create(location).getRawPath();
        assertThat(path).startsWith(API);
        return path.substring(API.length());
    }

    // ---- checks on the answer
    // -----------------------------------------------------------------------------------------

    @Then("the path of the Location header starts with {string}")
    public void locationStartsWith(String prefix) {
        String location = http.response().getHeader(HttpHeaders.LOCATION);
        assertThat(location)
                .as("the response carries a Location: %s", bodyOrEmpty())
                .isNotNull();
        assertThat(URI.create(location).getRawPath()).startsWith(prefix);
    }

    @Then("the field at {string} is present")
    public void present(String pointer) throws Exception {
        assertThat(JSON.readTree(http.body()).at(pointer).isMissingNode())
                .as("%s is present: %s", pointer, http.body())
                .isFalse();
    }

    @Then("the field at {string} is the remembered one")
    public void sameAsRemembered(String pointer) throws Exception {
        String field = pointer.substring(pointer.lastIndexOf('/') + 1);
        assertThat(charter.remembered(field))
                .as("the field %s was remembered", field)
                .isNotNull();
        assertThat(JSON.readTree(http.body()).at(pointer).asString()).isEqualTo(charter.remembered(field));
    }

    /** The count as the scenarios write it, with the literal {@code (s)}: the vocabulary's own step takes item or items. */
    @Then("the field at {string} holds exactly {int} item\\(s)")
    public void holdsExactly(String pointer, int count) throws Exception {
        JsonNode field = JSON.readTree(http.body()).at(pointer);
        assertThat(field.isArray()).as("%s is a list: %s", pointer, http.body()).isTrue();
        assertThat(field.size()).as("the items of %s", pointer).isEqualTo(count);
    }

    private String bodyOrEmpty() {
        try {
            return http.body();
        } catch (Exception unreadable) {
            return "";
        }
    }

    // ---- the registration jobs seam
    // ----------------------------------------------------------------------------------------

    @Given("the registration jobs are held before they run")
    public void holdBeforeTheyRun() {
        gate.holdBeforeRun();
    }

    @Given("the registration queue is full")
    public void queueFull() {
        RegistrationJobs.Origin origin = new RegistrationJobs.Origin(
                "scenario-worktree", AuditLog.Credential.WORKTREE_TOKEN, "registerPackFromArchive", "archive", "");
        for (int i = 0; i < FILLERS; i++) {
            RegistrationJobs.Reservation filler = jobs.reserve("filler-" + i, Preconditions.none(), origin);
            filler.start(directory -> {
                throw new ai.speckitty.charter.kernel.domain.CharterRefusal(
                        ai.speckitty.charter.kernel.domain.RefusalCode.VALIDATION_FAILED,
                        "a filler that holds a place in the queue");
            });
            fillers.add(filler.id());
        }
    }

    @When("I release the held registration jobs")
    public void release() {
        gate.releaseAll();
        awaitFillersEnded();
    }

    @When("I release the held registration jobs and hold them while they run")
    public void releaseAndHoldWhileRunning() {
        gate.holdWhileRunning();
        gate.releaseBeforeRun();
        gate.awaitHeldWhileRunning();
    }

    @When("the service forgets the ended registrations")
    public void forget() {
        gate.releaseAll();
        awaitNoRunningJob();
        clock.advance(RETENTION_PLUS);
    }

    @Given("the pack registry is held by another write")
    public void registryHeld() throws InterruptedException {
        releaseTheRegistry();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> RegistryWriteLock.shared().exclusively(() -> {
            holding.countDown();
            try {
                release.await(WAIT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        holder.setDaemon(true);
        holder.start();
        assertThat(holding.await(WAIT.toSeconds(), TimeUnit.SECONDS))
                .as("the registry is held")
                .isTrue();
        registryRelease = release;
    }

    @When("the held pack registry is released")
    public void registryReleased() {
        releaseTheRegistry();
    }

    private void releaseTheRegistry() {
        CountDownLatch release = registryRelease;
        registryRelease = null;
        if (release != null) {
            release.countDown();
        }
    }

    private void awaitFillersEnded() {
        await(
                () -> fillers.stream()
                        .allMatch(id -> jobs.status(id).map(this::ended).orElse(true)),
                "the filler registrations did not end");
        fillers.clear();
    }

    private boolean ended(RegistrationJobs.Status status) {
        return status.state() == RegistrationJobs.State.SUCCEEDED || status.state() == RegistrationJobs.State.REFUSED;
    }

    private void awaitNoRunningJob() {
        await(() -> jobs.owned().isEmpty(), "registration jobs of the scenario were still running when it ended");
    }

    private static void await(BooleanSupplier condition, String failure) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT.toSeconds());
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(failure);
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure, interrupted);
            }
        }
    }
}
