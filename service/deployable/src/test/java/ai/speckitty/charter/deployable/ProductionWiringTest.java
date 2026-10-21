package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * The service as it ships: a real server on a free loopback port, the packs named by {@code charter.packs[*]}
 * through Spring configuration and nothing overridden. A wiring that drops a configured pack fails it.
 */
@SpringBootTest(classes = CharterServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductionWiringTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static Path packs = Path.of("");

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void configuredPacks(DynamicPropertyRegistry registry) throws IOException {
        packs = Files.createTempDirectory("charter-wiring");
        write("built-in/pack.yaml", """
                pack_id: 01ARWG13C000000000000000FG
                pack_version: 1.0.0
                parent_pack: null
                name: built-in
                pack_level: built-in
                """);
        write("built-in/directive.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes:
                - urn: directive:DIRECTIVE_010
                  kind: directive
                  label: Specification Fidelity Requirement
                edges: []
                """);
        write("qa/drg/fragment.yaml", """
                nodes: []
                edges: []
                """);
        write("audit/drg/fragment.yaml", """
                nodes: []
                edges: []
                """);
        // The token is configured as the launcher does, not with the development flag: the production wiring is
        // guarded.
        registry.add("charter.worktree-token", () -> TOKEN);
        registry.add("charter.packs[0].name", () -> "qa");
        registry.add("charter.packs[0].level", () -> "org");
        registry.add("charter.packs[0].path", () -> packs.resolve("qa").toString());
        registry.add("charter.packs[1].name", () -> "built-in");
        registry.add("charter.packs[1].level", () -> "built-in");
        registry.add("charter.packs[1].path", () -> packs.resolve("built-in").toString());
        registry.add("charter.packs[2].name", () -> "audit");
        registry.add("charter.packs[2].level", () -> "org");
        registry.add("charter.packs[2].path", () -> packs.resolve("audit").toString());
    }

    private static void write(String relativePath, String content) throws IOException {
        Path file = packs.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @AfterAll
    static void deletePacks() throws IOException {
        try (Stream<Path> walk = Files.walk(packs)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return get(path, "Authorization", "Bearer " + TOKEN);
    }

    private HttpResponse<String> get(String path, String header, String value)
            throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .header(header, value)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void aConfiguredPackIsServedThroughTheRealWiringAndAnUnconfiguredOneIsNotFound() throws Exception {
        HttpResponse<String> configured = get("/api/v1/packs/built-in/tactics");
        assertThat(configured.statusCode()).as(configured.body()).isEqualTo(200);
        assertThat(JSON.readTree(configured.body())).isEqualTo(JSON.readTree("[]"));

        HttpResponse<String> unconfigured = get("/api/v1/packs/not-configured/tactics");
        assertThat(unconfigured.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(unconfigured.body()).path("code").asString()).isEqualTo("pack_not_found");
    }

    @Test
    void aPathTheServiceDoesNotHaveIsAProblemOverTheRealServerToo() throws Exception {
        HttpResponse<String> response = get("/api/v1/pack");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("{\"title\":\"Not Found\",\"status\":404}"));
    }

    @Test
    void theServiceAsItShipsRefusesARequestWithoutTheTokenBeforeItRoutesIt() throws Exception {
        for (String path : new String[] {"/api/v1/packs", "/api/v1/info", "/api/v1/no-such-path", "/error", "/"}) {
            HttpResponse<String> response = get(path, "Accept", "*/*");
            assertThat(response.statusCode()).as(path).isEqualTo(401);
            assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
            assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
            assertThat(response.headers().firstValue("Charter-Source-Fingerprint"))
                    .isEmpty();
            assertThat(JSON.readTree(response.body()))
                    .isEqualTo(JSON.readTree("{\"title\":\"Unauthorized\",\"status\":401}"));
        }
    }

    @Test
    void theServiceAsItShipsRefusesAWrongTokenAndNeverEchoesIt() throws Exception {
        HttpResponse<String> response = get("/api/v1/info", "Authorization", "Bearer " + TOKEN + "x");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain(TOKEN);
        assertThat(response.headers().map().toString()).doesNotContain(TOKEN);
    }

    @Test
    void theServiceAsItShipsRefusesABrowserEvenWithTheRightToken() throws Exception {
        HttpResponse<String> response = get("/api/v1/info", "Origin", "https://evil.example");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("{\"title\":\"Forbidden\",\"status\":403}"));
    }
}
