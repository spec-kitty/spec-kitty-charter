package ai.speckitty.charter.service;

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
 * through Spring configuration and nothing overridden. A wiring that drops or reorders the configured packs
 * fails it.
 */
@SpringBootTest(classes = CharterServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductionWiringTest {

    private static final ObjectMapper JSON = new ObjectMapper();
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
                pack_name: qa
                nodes: []
                edges: []
                """);
        // Configured org first: the listing still answers built-in first, in layer order.
        registry.add("charter.packs[0].name", () -> "qa");
        registry.add("charter.packs[0].level", () -> "org");
        registry.add("charter.packs[0].path", () -> packs.resolve("qa").toString());
        registry.add("charter.packs[1].name", () -> "built-in");
        registry.add("charter.packs[1].level", () -> "built-in");
        registry.add("charter.packs[1].path", () -> packs.resolve("built-in").toString());
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
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void thePacksListedAreTheConfiguredOnes() throws Exception {
        HttpResponse<String> response = get("/api/v1/packs");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("""
                {"packs": [
                  {"name": "built-in", "level": "built-in", "packId": "01ARWG13C000000000000000FG",
                   "packVersion": "1.0.0", "parentPack": null, "nodeCount": 1, "edgeCount": 0, "diagnostics": []},
                  {"name": "qa", "level": "org", "packId": null, "packVersion": null, "parentPack": null,
                   "nodeCount": 0, "edgeCount": 0, "diagnostics": [{"code": "pack_level_missing",
                   "detail": "org pack qa has no pack.yaml; the configured level org is used"}]}
                ]}
                """));
    }

    @Test
    void aPathTheServiceDoesNotHaveIsAProblemOverTheRealServerToo() throws Exception {
        HttpResponse<String> response = get("/api/v1/pack");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("{\"title\":\"Not Found\",\"status\":404}"));
    }
}
