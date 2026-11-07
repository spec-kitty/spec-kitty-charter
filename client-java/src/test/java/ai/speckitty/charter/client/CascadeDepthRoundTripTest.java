package ai.speckitty.charter.client;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.client.api.ActivationApi;
import ai.speckitty.charter.client.model.ActivatableKind;
import ai.speckitty.charter.client.model.ActivationChange;
import ai.speckitty.charter.client.model.CascadeKind;
import ai.speckitty.charter.client.model.ExclusionRequest;
import ai.speckitty.charter.client.model.SelectionRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FR-043, IC-21, the client half: a cascading select and deactivate sent through the generated client carry {@code depth}
 * when it is set and leave it out when it is not, and the answer's {@code skippedByDepth} reads back when the server
 * sent one and is {@code null} when it did not. A JDK {@link HttpServer} records the body of each request and answers
 * with a canned {@code ActivationChange}.
 */
class CascadeDepthRoundTripTest {

    private static final String REVISION = "sha256:" + "cd".repeat(32);
    private static final String BOUNDED = answer("""
            "skippedByDepth": [{"kind": "directive", "artifactId": "HA"}]""");
    private static final String UNBOUNDED = answer("");

    private final AtomicReference<String> sent = new AtomicReference<>();
    private final AtomicReference<String> reply = new AtomicReference<>(UNBOUNDED);
    private HttpServer server;
    private ActivationApi activations;

    private static String answer(String skippedByDepth) {
        String extra = skippedByDepth.isEmpty() ? "" : ", " + skippedByDepth;
        return """
                {"revision": "%s", "applied": [{"kind": "agent_profile", "artifactId": "lead"}], "cleared": [],
                 "skipped": {"skippedByScope": [], "skippedDeactivated": [], "unreachedBehindDeactivated": [],
                             "skippedShared": []%s},
                 "droppedPins": [], "_links": {"up": {"href": "/api/v1/activations/r-1"}}}""".formatted(REVISION, extra);
    }

    @BeforeEach
    void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/activations/r-1", this::respond);
        server.start();
        ApiClient client = new ApiClient();
        client.setBasePath("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1");
        activations = new ActivationApi(client);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            sent.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
        byte[] change = reply.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("ETag", "\"" + REVISION + "\"");
        exchange.sendResponseHeaders(200, change.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(change);
        }
    }

    private JsonElement body() {
        return JsonParser.parseString(sent.get());
    }

    @Test
    void aSelectWithDepthSendsItAndReadsTheFrontierBack() throws Exception {
        reply.set(BOUNDED);

        ActivationChange change = activations.selectActivationArtifact(
                "r-1",
                ActivatableKind.AGENT_PROFILE,
                "lead",
                new SelectionRequest().addCascadeItem(CascadeKind.ALL).depth(1),
                "\"" + REVISION + "\"");

        assertThat(body()).isEqualTo(JsonParser.parseString("{\"cascade\": [\"all\"], \"depth\": 1}"));
        assertThat(change.getSkipped().getSkippedByDepth()).hasSize(1);
        assertThat(change.getSkipped().getSkippedByDepth().get(0).getArtifactId())
                .isEqualTo("HA");
    }

    @Test
    void aSelectWithDepthZeroSendsTheZero() throws Exception {
        activations.selectActivationArtifact(
                "r-1",
                ActivatableKind.AGENT_PROFILE,
                "lead",
                new SelectionRequest().addCascadeItem(CascadeKind.ALL).depth(0),
                null);

        assertThat(body().getAsJsonObject().get("depth").getAsInt()).isZero();
    }

    @Test
    void aSelectWithoutDepthSendsNoDepthAndReadsNoFrontier() throws Exception {
        ActivationChange change = activations.selectActivationArtifact(
                "r-1",
                ActivatableKind.AGENT_PROFILE,
                "lead",
                new SelectionRequest().addCascadeItem(CascadeKind.ALL),
                null);

        assertThat(body().getAsJsonObject().has("depth"))
                .as("an absent depth is never sent: %s", sent.get())
                .isFalse();
        assertThat(change.getSkipped().getSkippedByDepth())
                .as("an absent skippedByDepth stays absent")
                .isNull();
    }

    @Test
    void aDeactivateWithDepthSendsItAndWithoutDepthSendsNone() throws Exception {
        reply.set(BOUNDED);
        ActivationChange bounded = activations.deactivateActivationArtifact(
                "r-1",
                ActivatableKind.AGENT_PROFILE,
                "lead",
                new ExclusionRequest().addCascadeItem(CascadeKind.ALL).depth(1),
                null);
        assertThat(body()).isEqualTo(JsonParser.parseString("{\"cascade\": [\"all\"], \"depth\": 1}"));
        assertThat(bounded.getSkipped().getSkippedByDepth()).hasSize(1);

        reply.set(UNBOUNDED);
        ActivationChange unbounded = activations.deactivateActivationArtifact(
                "r-1",
                ActivatableKind.AGENT_PROFILE,
                "lead",
                new ExclusionRequest().addCascadeItem(CascadeKind.ALL),
                null);
        assertThat(body()).isEqualTo(JsonParser.parseString("{\"cascade\": [\"all\"]}"));
        assertThat(unbounded.getSkipped().getSkippedByDepth()).isNull();
    }
}
