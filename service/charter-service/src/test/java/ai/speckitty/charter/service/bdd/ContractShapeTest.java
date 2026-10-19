package ai.speckitty.charter.service.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.read.ActionContextQuery;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Pins the shape that makes raising the request depth maximum compatible: the response {@code depth} of the action
 * context has a minimum and no maximum, while the request parameter's range matches the query's constants.
 */
class ContractShapeTest {

    private static Map<String, Object> contractFile(String relativePath) throws IOException, URISyntaxException {
        Path root = Path.of(
                ContractShapeTest.class.getClassLoader().getResource("contract").toURI());
        Object document =
                new Load(LoadSettings.builder().build()).loadFromString(Files.readString(root.resolve(relativePath)));
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) document;
        return map;
    }

    @Test
    @SuppressWarnings("unchecked")
    void theResponseDepthHasAMinimumOfZeroAndNoMaximum() throws Exception {
        Map<String, Object> schema = contractFile("charter/schemas/ActionContext.yaml");
        Map<String, Object> depth = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("depth");
        assertThat(depth).containsEntry("minimum", 0).doesNotContainKey("maximum");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRequestDepthRangeMatchesTheQueryConstants() throws Exception {
        Map<String, Object> schema = (Map<String, Object>)
                contractFile("charter/parameters/Depth.yaml").get("schema");
        assertThat(schema)
                .containsEntry("minimum", ActionContextQuery.MIN_DEPTH)
                .containsEntry("maximum", ActionContextQuery.MAX_DEPTH)
                .containsEntry("default", ActionContextQuery.DEFAULT_DEPTH);
    }
}
