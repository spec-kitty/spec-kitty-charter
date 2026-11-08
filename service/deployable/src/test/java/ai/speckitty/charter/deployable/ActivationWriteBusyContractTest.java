package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Every write of an activation takes the pack registry's write lock (ADR-0065 D12), so each can answer {@code 503}
 * with {@code Retry-After} when a registration holds it. The contract says so on the operation, with the header, as it
 * does for a registration, instead of leaving the client to read it out of {@code default}.
 */
class ActivationWriteBusyContractTest {

    private static final List<String[]> WRITES = List.of(
            new String[] {"/activations", "post"},
            new String[] {"/activations/{activationId}", "put"},
            new String[] {"/activations/{activationId}", "delete"},
            new String[] {"/activations/{activationId}/selections/{kind}/{artifactId}", "put"},
            new String[] {"/activations/{activationId}/selections/{kind}/{artifactId}", "delete"},
            new String[] {"/activations/{activationId}/exclusions/{kind}/{artifactId}", "put"},
            new String[] {"/activations/{activationId}/exclusions/{kind}/{artifactId}", "delete"},
            new String[] {"/activations/{activationId}/presets/{presetId}", "put"});

    private static final Map<?, ?> DOCUMENT = load();

    @Test
    void everyActivationWriteDeclaresA503WithRetryAfter() {
        for (String[] write : WRITES) {
            Map<?, ?> responses = (Map<?, ?>)
                    child(child(child(DOCUMENT, "paths"), write[0]), write[1]).get("responses");
            Map<?, ?> busy = resolved(responses.get("503"));

            assertThat(busy).as("the 503 of %s %s", write[1], write[0]).isNotNull();
            assertThat(((Map<?, ?>) busy.get("headers"))
                            .keySet().stream().map(String::valueOf).toList())
                    .as("the headers of the 503 of %s %s", write[1], write[0])
                    .contains("Retry-After");
        }
    }

    private static Map<?, ?> resolved(Object response) {
        if (response == null) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) response;
        Object ref = map.get("$ref");
        if (ref == null) {
            return map;
        }
        String name = String.valueOf(ref).substring("#/components/responses/".length());
        return child(child(DOCUMENT, "components"), "responses").get(name) instanceof Map<?, ?> found ? found : null;
    }

    private static Map<?, ?> child(Map<?, ?> parent, String key) {
        return (Map<?, ?>) parent.get(key);
    }

    private static Map<?, ?> load() {
        String path = System.getProperty("charter.test.contract-bundle");
        if (path == null) {
            throw new IllegalStateException(
                    "charter.test.contract-bundle is not set; the build passes the bundle to the tests");
        }
        try {
            String text = new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
            return (Map<?, ?>) new Load(LoadSettings.builder().build()).loadFromString(text);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
