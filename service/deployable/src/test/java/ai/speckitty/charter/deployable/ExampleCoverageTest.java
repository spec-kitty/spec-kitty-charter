package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import ai.speckitty.charter.packs.infra.storage.FreeSpaceCheck;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every refusal code the contract marks {@code raised: true} has an example (WP03, T012, FR-003, SC-003): a contract
 * scenario produces it, or this class produces it through the running service from an example pack.
 *
 * <p>A scenario produces a code by a step {@code the problem code is "<code>"}, or {@code the field at "/problem/code"}
 * or {@code "/problem/violations/<n>/code"} {@code is "<code>"}, where a {@code <column>} placeholder of a scenario
 * outline stands for every value of that column in its examples. The declaration pins of {@code errors.feature}
 * ({@code the contract declares the refusal code}) and its list of raised codes produce nothing and do not count.
 *
 * <p>Two request-level codes have no scenario, and this class produces them by registering a copy of the built-in
 * example pack: {@code pack_storage_full} against the service's own {@link FreeSpaceCheck} over a volume that reports
 * 1 MiB free, and {@code registration_timed_out} with the job held at the service's job gate while the registration
 * clock passes {@link RegistrationJobs#DEADLINE}. Both seams are beans the service declares for tests to replace.
 */
@SpringBootTest(
        classes = {CharterServiceApplication.class, ExampleCoverageTest.Seams.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExampleCoverageTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final long MIB = 1024L * 1024L;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final MovableClock CLOCK = new MovableClock();
    private static final HeldGate GATE = new HeldGate();
    private static final AtomicLong USABLE = new AtomicLong(Long.MAX_VALUE / 2);
    private static Path configRoot = Path.of("");

    @LocalServerPort
    private int port;

    @Autowired
    private RegistrationJobs jobs;

    /** The service's registration seams: its clock, its job gate and its free-space check over a sized volume. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Seams {

        @Bean
        @Primary
        Clock movableRegistrationClock() {
            return CLOCK;
        }

        @Bean
        @Primary
        RegistrationJobs.JobGate heldJobGate() {
            return GATE;
        }

        @Bean
        @Primary
        RegistrationJobs.StorageRoom sizedStorageRoom() {
            return new FreeSpaceCheck(() -> new SizedStore(USABLE.get()));
        }
    }

    @DynamicPropertySource
    static void registrationOn(DynamicPropertyRegistry registry) throws IOException {
        configRoot = Files.createTempDirectory("charter-example-coverage");
        Files.createDirectories(configRoot.resolve(ConfigRoot.PACKS));
        registry.add("charter.worktree-token", () -> TOKEN);
        registry.add("charter.config-root", () -> configRoot.toString());
        registry.add("charter.registration.enabled", () -> "true");
    }

    @AfterAll
    static void deleteRoot() throws IOException {
        deleteTree(configRoot);
    }

    private static final String REFUSAL_CODES = "contract/charter/schemas/CharterRefusalCode.yaml";

    private static final Pattern PRODUCED = Pattern.compile(
            "(?:the problem code is|the field at \"/problem(?:/violations/\\d+)?/code\" is) \"([^\"]+)\"");
    private static final Pattern CELL = Pattern.compile("\\|");
    private static final Pattern PLACEHOLDER = Pattern.compile("<(\\w+)>");
    private static final Pattern SCENARIO = Pattern.compile("(?m)^\\s*(?=Scenario(?: Outline| Template)?:)");

    @Test
    void everyRaisedRefusalCodeIsProducedByAScenarioOrAnExamplePack(@TempDir Path scratch) throws Exception {
        Set<String> produced = producedCodes(scenarioTexts());
        String storageFull = storageFullCode(scratch);
        String timedOut = timedOutCode(scratch);
        assertThat(List.of(storageFull, timedOut)).containsExactly("pack_storage_full", "registration_timed_out");
        produced.add(storageFull);
        produced.add(timedOut);

        assertThat(raisedCodes()).as("raised codes").isNotEmpty();
        assertThat(raisedCodes().stream()
                        .filter(code -> !produced.contains(code))
                        .map(code -> code + " is raised but no contract scenario or example pack produces it")
                        .toList())
                .isEmpty();
    }

    /** The control: a declaration pin produces nothing; a produced code and an outline placeholder do. */
    @Test
    void onlyAProducingStepCounts() {
        String feature = """
                Feature: f
                  Scenario: pin
                    Then the contract declares the refusal code "pinned_only"
                  Scenario: plain
                    And the problem code is "plain_code"
                  Scenario Outline: outline
                    And the field at "/problem/violations/0/code" is "<code>"
                    Examples:
                      | code         | other |
                      | outline_code | x     |
                """;

        assertThat(producedCodes(List.of(feature))).containsExactly("outline_code", "plain_code");
    }

    /** Registers the built-in example pack while the volume reports 1 MiB free; returns the refusal's code. */
    private String storageFullCode(Path scratch) throws Exception {
        USABLE.set(MIB);
        try {
            return refusalCode(put(archive(scratch.resolve("full"))));
        } finally {
            USABLE.set(Long.MAX_VALUE / 2);
        }
    }

    /** Registers it with the job held at the gate past the deadline; returns the refusal's code. */
    private String timedOutCode(Path scratch) throws Exception {
        GATE.hold();
        try {
            HttpResponse<String> accepted = put(archive(scratch.resolve("late")));
            assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(202);
            CLOCK.advance(RegistrationJobs.DEADLINE.plusSeconds(1));
            jobs.expireOverdue();
            return refusalCode(accepted);
        } finally {
            GATE.release();
        }
    }

    /** The code of a refusal: the response's own, or the problem of the registration it accepted once it ends. */
    private String refusalCode(HttpResponse<String> response) throws Exception {
        JsonNode body = JSON.readTree(response.body());
        if (response.statusCode() != 202) {
            return body.path("code").asString();
        }
        String id = body.path("registrationId").asString();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            JsonNode status = JSON.readTree(send(to("/pack-registrations/" + id)
                            .header("Authorization", "Bearer " + TOKEN)
                            .GET())
                    .body());
            String state = status.path("state").asString();
            if ("refused".equals(state) || "succeeded".equals(state)) {
                return status.path("problem").path("code").asString();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("registration " + id + " did not end");
    }

    private HttpResponse<String> put(byte[] archive) throws Exception {
        return send(to("/packs/" + ExamplePacks.BUILT_IN)
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/zip")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(archive)));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpRequest.Builder to(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
    }

    /** A zip of a fresh copy of the built-in example pack, its files at the archive root. */
    private static byte[] archive(Path target) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, target);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes);
                Stream<Path> walk = Files.walk(pack)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                zip.putNextEntry(new ZipEntry(pack.relativize(file).toString().replace('\\', '/')));
                zip.write(Files.readAllBytes(file));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** A clock the test moves forward. */
    private static final class MovableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-11T00:00:00Z"));

        void advance(Duration by) {
            now.updateAndGet(instant -> instant.plus(by));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    /** A job gate that holds a job before it runs until released. */
    private static final class HeldGate implements RegistrationJobs.JobGate {
        private volatile CountDownLatch latch = new CountDownLatch(0);

        void hold() {
            latch = new CountDownLatch(1);
        }

        void release() {
            latch.countDown();
        }

        @Override
        public void beforeRun(String id) {
            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void whileRunning(String id) {}
    }

    /** A volume that reports one usable size. */
    private static final class SizedStore extends FileStore {
        private final long usable;

        SizedStore(long usable) {
            this.usable = usable;
        }

        @Override
        public long getUsableSpace() {
            return usable;
        }

        @Override
        public long getUnallocatedSpace() {
            return usable;
        }

        @Override
        public long getTotalSpace() {
            return Long.MAX_VALUE;
        }

        @Override
        public String name() {
            return "sized";
        }

        @Override
        public String type() {
            return "sized";
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
            return false;
        }

        @Override
        public boolean supportsFileAttributeView(String name) {
            return false;
        }

        @Override
        public <V extends FileStoreAttributeView> @Nullable V getFileStoreAttributeView(Class<V> type) {
            return null;
        }

        @Override
        public Object getAttribute(String attribute) {
            throw new UnsupportedOperationException(attribute);
        }
    }

    static Set<String> producedCodes(List<String> features) {
        Set<String> produced = new TreeSet<>();
        for (String feature : features) {
            for (String scenario : SCENARIO.splitAsStream(feature).toList()) {
                Matcher step = PRODUCED.matcher(scenario);
                while (step.find()) {
                    Matcher placeholder = PLACEHOLDER.matcher(step.group(1));
                    if (placeholder.matches()) {
                        produced.addAll(column(scenario, placeholder.group(1)));
                    } else {
                        produced.add(step.group(1));
                    }
                }
            }
        }
        return produced;
    }

    /** The values of one column across the example tables of a scenario outline. */
    private static List<String> column(String scenario, String name) {
        List<String> values = new ArrayList<>();
        int index = -1;
        int width = 0;
        for (String line : scenario.lines().toList()) {
            String row = line.strip();
            if (!row.startsWith("|")) {
                continue;
            }
            List<String> cells = Stream.of(CELL.split(row.substring(1, row.length() - 1)))
                    .map(String::strip)
                    .toList();
            if (cells.contains(name)) {
                index = cells.indexOf(name);
                width = cells.size();
            } else if (index >= 0 && cells.size() == width) {
                values.add(cells.get(index));
            }
        }
        return values;
    }

    private static List<String> raisedCodes() {
        Map<?, ?> schema;
        try (InputStream in = ExampleCoverageTest.class.getClassLoader().getResourceAsStream(REFUSAL_CODES)) {
            if (in == null) {
                throw new IllegalStateException(REFUSAL_CODES + " is not on the test classpath");
            }
            schema = (Map<?, ?>) new Load(LoadSettings.builder().build())
                    .loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> raised = new ArrayList<>();
        ((Map<?, ?>) schema.get("x-refusal")).forEach((code, entry) -> {
            if (Boolean.TRUE.equals(((Map<?, ?>) entry).get("raised"))) {
                raised.add((String) code);
            }
        });
        return raised;
    }

    private static List<String> scenarioTexts() {
        try {
            Path scenarios = Path.of(ExampleCoverageTest.class
                    .getClassLoader()
                    .getResource("scenarios")
                    .toURI());
            try (Stream<Path> files = Files.walk(scenarios)) {
                List<String> texts = new ArrayList<>();
                for (Path feature : files.filter(file -> file.toString().endsWith(".feature"))
                        .sorted()
                        .toList()) {
                    texts.add(Files.readString(feature, StandardCharsets.UTF_8));
                }
                return texts;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
