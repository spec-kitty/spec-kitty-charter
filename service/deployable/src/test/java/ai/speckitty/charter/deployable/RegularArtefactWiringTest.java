package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.CompositeComponentResolver;
import ai.speckitty.charter.packs.domain.ComponentResolver;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * WP10 consolidation (NFR-004, C-001/C-002, SC-004): the regular-artefact read and registration surface is wired into
 * the production composition root exactly as it ships. A configured pack serves every new per-kind read with the
 * {@code Charter-Source-Fingerprint} header, a registration {@code PUT} round-trips through the wired write store, and
 * the production {@link CompositeComponentResolver} is a bean that dispatches a neighbour URN to its kind's read path
 * (empty for an unwired kind, ADR-0025 Decision 6). A wiring that drops any controller, service, write store or the
 * resolver fails the context load and this test.
 */
@SpringBootTest(classes = CharterServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegularArtefactWiringTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";
    private static final String SHA256 = "sha256:[0-9a-f]{64}";
    private static final PackIdentifier PACK = new PackIdentifier("qa");
    private static Path packs = Path.of("");

    @LocalServerPort
    private int port;

    @Autowired
    private CompositeComponentResolver resolver;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void configuredPacks(DynamicPropertyRegistry registry) throws IOException {
        packs = Files.createDirectories(
                Files.createTempDirectory("charter-regular-artefact-wiring").resolve(ConfigRoot.PACKS));
        write("qa/pack.yaml", """
                pack_id: qa
                pack_version: 1.0.0
                parent_pack: null
                name: qa
                pack_level: org
                """);
        registry.add("charter.worktree-token", () -> TOKEN);
        registry.add("charter.config-root", () -> packs.getParent().toString());
    }

    private static void write(String relativePath, String content) throws IOException {
        Path file = packs.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @AfterAll
    static void deletePacks() throws IOException {
        try (Stream<Path> walk = Files.walk(packs.getParent())) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void everyNewKindReadServesWithTheFingerprintHeader() throws Exception {
        for (String kind : new String[] {"directive", "styleguide", "paradigm", "toolguide"}) {
            HttpResponse<String> response = send("GET", "/api/v1/packs/qa/artifacts?kind=" + kind, null);
            assertThat(response.statusCode())
                    .as("GET %s: %s", kind, response.body())
                    .isEqualTo(200);
            assertThat(response.headers().firstValue(FINGERPRINT))
                    .as("GET %s carries the source fingerprint", kind)
                    .get()
                    .asString()
                    .matches(SHA256);
        }
    }

    @Test
    void aRegistrationPutRoundTripsThroughTheWiredContext() throws Exception {
        HttpResponse<String> put = send(
                "PUT",
                "/api/v1/packs/qa/artifacts/directive/ROUNDTRIP",
                "{\"schemaVersion\": \"1.0\", \"title\": \"Round trip\", \"intent\": \"prove the wiring\","
                        + " \"enforcement\": \"required\"}");
        assertThat(put.statusCode()).as(put.body()).isEqualTo(201);

        HttpResponse<String> get = send("GET", "/api/v1/packs/qa/artifacts/directive/ROUNDTRIP", null);
        assertThat(get.statusCode()).as(get.body()).isEqualTo(200);
        assertThat(JSON.readTree(get.body()).path("id").asString()).isEqualTo("ROUNDTRIP");
        assertThat(JSON.readTree(get.body()).at("/directive/title").asString()).isEqualTo("Round trip");
        assertThat(JSON.readTree(put.body())).isEqualTo(JSON.readTree(get.body()));
    }

    @Test
    void theCompositeComponentResolverIsABeanThatDispatchesAcrossKinds() throws Exception {
        assertThat(context.getBeansOfType(CompositeComponentResolver.class)).hasSize(1);

        HttpResponse<String> put = send(
                "PUT",
                "/api/v1/packs/qa/artifacts/directive/RESOLVED",
                "{\"schemaVersion\": \"1.0\", \"title\": \"Resolved\", \"intent\": \"prove dispatch\","
                        + " \"enforcement\": \"required\"}");
        assertThat(put.statusCode()).as(put.body()).isEqualTo(201);

        ComponentResolver bound = resolver.forPack(PACK);
        assertThat(bound.resolve(new Urn(NodeKind.DIRECTIVE, "RESOLVED")))
                .as("the resolver dispatches a directive URN to the directive read path")
                .isPresent();
        assertThat(bound.resolve(new Urn(NodeKind.DIRECTIVE, "ABSENT")))
                .as("an absent in-scope component resolves empty, not an error")
                .isEmpty();
        assertThat(bound.resolve(new Urn(NodeKind.PROCEDURE, "unwired")))
                .as("an unwired kind resolves empty so a walk continues (ADR-0025 Decision 6)")
                .isEmpty();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest.BodyPublisher publisher =
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Authorization", "Bearer " + TOKEN)
                    .header("Content-Type", "application/json")
                    .method(method, publisher)
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
