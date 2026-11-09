package ai.speckitty.charter.client;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.client.api.ActivationApi;
import ai.speckitty.charter.client.model.Activation;
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
 * FR-042, the client half (decision DM-01M6RN62PPCMSH6M54D2HXYZEQ): a GET then a PUT of the fetched config through the
 * generated client sends back the document it read. A JDK {@link HttpServer} answers the GET with a document whose
 * {@code languages}, {@code deactivated} and most selected kinds are absent, and records the body of the PUT. An absent
 * list must stay absent: the generated model used to start every optional list as {@code []}, so a client that read
 * no {@code languages} sent {@code languages: []} back, which means "keep only untagged artifacts", not "no filter".
 */
class ActivationRoundTripTest {

    private static final String REVISION = "sha256:" + "ab".repeat(32);
    private static final String CONFIG = """
            {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "shared", "version": "1.0.0"}],
             "selected": {"directive": ["SD"], "mission_type": ["software-dev"]}}""";
    private static final String ACTIVATION = """
            {"activationId": "r-1", "revision": "%s", "config": %s,
             "_links": {"self": {"href": "/api/v1/activations/r-1"}}}""".formatted(REVISION, CONFIG);

    private final AtomicReference<String> sent = new AtomicReference<>();
    private HttpServer server;
    private ActivationApi activations;

    @BeforeEach
    void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/activations/r-1", this::answer);
        server.start();
        ApiClient client = new ApiClient();
        client.setBasePath("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1");
        activations = new ActivationApi(client);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            if ("PUT".equals(exchange.getRequestMethod())) {
                sent.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        byte[] activation = ACTIVATION.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("ETag", "\"" + REVISION + "\"");
        exchange.sendResponseHeaders(200, activation.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(activation);
        }
    }

    @Test
    void aFetchedConfigIsSentBackAsItWasRead() throws Exception {
        Activation read = activations.getActivation("r-1", null);

        activations.replaceActivation("r-1", read.getConfig(), "\"" + read.getRevision() + "\"", null);

        JsonElement put = JsonParser.parseString(sent.get());
        assertThat(put).as("the PUT body %s", sent.get()).isEqualTo(JsonParser.parseString(CONFIG));
        assertThat(put.getAsJsonObject().has("languages"))
                .as("an absent languages is never sent as []")
                .isFalse();
        assertThat(put.getAsJsonObject().getAsJsonObject("selected").has("tactic"))
                .isFalse();
    }
}
