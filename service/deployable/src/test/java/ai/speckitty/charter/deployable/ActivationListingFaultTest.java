package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A listing of the activation store that fails (issue 418, R6) is the service's fault, and no refusal code names it, so
 * the registration impact check ends the per-artifact write and the {@code DELETE} as {@code 500 internal_error} and
 * the registration job as refused with {@code internal_error} (DIV-614): deliberately, with the generic body, with
 * nothing written and with the failure in the operator log. The store lists fine until the test switches the fault on.
 */
@SpringBootTest(
        classes = {CharterServiceApplication.class, ActivationListingFaultTest.Faulty.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivationListingFaultTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String GENERIC = "The charter service failed unexpectedly; the failure is logged.";
    private static final AtomicBoolean LISTING_FAILS = new AtomicBoolean();
    private static final List<AuditLog.Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final String DIRECTIVE =
            "{\"schemaVersion\": \"1.0\", \"title\": \"One\", \"intent\": \"be there\", \"enforcement\": \"required\"}";
    private static Path configRoot = Path.of("");

    @LocalServerPort
    private int port;

    /** An activation store that holds nothing and, when switched on, cannot be listed; and a recording audit log. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Faulty {

        @Bean
        @Primary
        ActivationRepository unlistableActivationRepository() {
            return new ActivationRepository() {
                @Override
                public List<StoredActivation> list() {
                    if (LISTING_FAILS.get()) {
                        throw new IllegalStateException("injected: /secret/path cannot be listed");
                    }
                    return List.of();
                }

                @Override
                public Optional<StoredActivation> find(ActivationId id) {
                    return Optional.empty();
                }

                @Override
                public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        @Primary
        AuditLog recordingAuditLog() {
            return ENTRIES::add;
        }
    }

    @DynamicPropertySource
    static void registrationOn(DynamicPropertyRegistry registry) throws IOException {
        configRoot = Files.createTempDirectory("charter-listing-fault");
        Path pack = Files.createDirectories(configRoot.resolve(ConfigRoot.PACKS).resolve("qa"));
        Files.writeString(pack.resolve("pack.yaml"), """
                pack_id: 01ARWG13C000000000000000QA
                pack_version: 1.0.0
                parent_pack: null
                name: qa
                pack_level: org
                """, StandardCharsets.UTF_8);
        registry.add("charter.worktree-token", () -> TOKEN);
        registry.add("charter.config-root", () -> configRoot.toString());
        registry.add("charter.registration.enabled", () -> "true");
    }

    @AfterAll
    static void deleteRoot() throws IOException {
        LISTING_FAILS.set(false);
        try (Stream<Path> walk = Files.walk(configRoot)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    request.header("Authorization", "Bearer " + TOKEN).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpRequest.Builder to(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
    }

    private static void isAnInternalErrorThatLeaksNothing(HttpResponse<String> answer) {
        assertThat(answer.statusCode()).as(answer.body()).isEqualTo(500);
        JsonNode body = JSON.readTree(answer.body());
        assertThat(body.path("code").asString()).isEqualTo("internal_error");
        assertThat(body.path("detail").asString()).isEqualTo(GENERIC);
        assertThat(answer.body()).doesNotContain("secret").doesNotContain("Exception");
    }

    private static byte[] archive() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("pack.yaml"));
            zip.write("""
                    pack_id: 01M4P01D5TWB5FKZGNNQG3943R
                    pack_version: 1.0.0
                    parent_pack: null
                    name: acme
                    pack_level: org
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void aListingThatFailsEndsAPerArtifactWriteADeleteAndARegistrationJobAsAnInternalError() throws Exception {
        ENTRIES.clear();
        LISTING_FAILS.set(false);
        HttpResponse<String> control = send(to("/packs/qa/artifacts/directive/ONE")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(DIRECTIVE)));
        assertThat(control.statusCode())
                .as("the control: the same write lands while the store lists")
                .isEqualTo(201);
        Path written = configRoot.resolve(ConfigRoot.PACKS).resolve("qa").resolve("ONE.directive.yaml");
        assertThat(written).exists();

        LISTING_FAILS.set(true);
        HttpResponse<String> put = send(to("/packs/qa/artifacts/directive/TWO")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(DIRECTIVE)));
        HttpResponse<String> delete = send(
                to("/packs/qa/artifacts/directive/ONE").header("If-Match", "*").DELETE());
        HttpResponse<String> accepted = send(to("/packs/acme")
                .header("Content-Type", "application/zip")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(archive())));

        isAnInternalErrorThatLeaksNothing(put);
        isAnInternalErrorThatLeaksNothing(delete);
        assertThat(configRoot.resolve(ConfigRoot.PACKS).resolve("qa").resolve("TWO.directive.yaml"))
                .as("the refused write left nothing")
                .doesNotExist();
        assertThat(written).as("the refused DELETE removed nothing").exists();
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(202);
        String statusUri =
                JSON.readTree(accepted.body()).at("/_links/self/href").asString();
        JsonNode status = awaitEnd(statusUri);
        assertThat(status.path("state").asString()).isEqualTo("refused");
        assertThat(status.at("/problem/code").asString()).isEqualTo("internal_error");
        assertThat(configRoot.resolve(ConfigRoot.PACKS).resolve("acme"))
                .as("nothing was registered")
                .doesNotExist();
        assertThat(ENTRIES)
                .extracting(AuditLog.Entry::operation, AuditLog.Entry::outcome)
                .contains(org.assertj.core.api.Assertions.tuple("registerPackFromArchive", "failed:internal_error"));
    }

    private JsonNode awaitEnd(String statusUri) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        JsonNode status;
        do {
            Thread.sleep(50);
            HttpResponse<String> answer = send(HttpRequest.newBuilder(
                    URI.create(statusUri.startsWith("http") ? statusUri : "http://127.0.0.1:" + port + statusUri)));
            status = JSON.readTree(answer.body());
        } while (System.nanoTime() < deadline
                && !List.of("succeeded", "refused")
                        .contains(status.path("state").asString()));
        return status;
    }
}
