package ai.speckitty.charter.contracts.offering;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The canonical {@code ActivationConfig} JSON Schema ({@code contracts/offering/schemas/}, ADR-0017's pattern) and its
 * OpenAPI mirror ({@code contracts/charter/schemas/ActivationConfig.yaml} and its parts) say the same thing (#125,
 * research D-2): with every {@code $ref} resolved, each a file on the mirror side and a {@code #/$defs/} entry on the
 * JSON side, and exactly the annotation keys {@link #STRIPPED} and every {@code x-} key removed, the two trees are
 * equal. The keywords that validate are therefore the same; the corpus tests in {@code service/packs} and
 * {@code service/deployable} prove the verdicts.
 */
class ActivationConfigMirrorTest {

    /** The keys that describe a schema and validate nothing; {@code $defs} is dropped after resolution. */
    static final Set<String> STRIPPED =
            Set.of("title", "description", "examples", "$comment", "$schema", "$id", "$defs");

    private static final Path OFFERING = Path.of(System.getProperty("charter.offeringSchemas"));
    private static final Path CHARTER = Path.of(System.getProperty("charter.charterSchemas"));
    private static final String JSON_FILE = "ActivationConfig.schema.json";
    private static final String MIRROR = "ActivationConfig.yaml";

    @Test
    void theMirrorValidatesWhatTheCanonicalSchemaValidates() {
        assertThat(mirror(CHARTER)).isEqualTo(canonical());
    }

    @Test
    void theComparisonSeesAKeywordThatDiffers() throws IOException {
        Path copy = Files.createTempDirectory("mirror");
        try (var files = Files.list(CHARTER)) {
            for (Path file : files.toList()) {
                Files.copy(file, copy.resolve(file.getFileName()));
            }
        }
        Path denied = copy.resolve("DeniedArtifactIdList.yaml");
        String text = Files.readString(denied, StandardCharsets.UTF_8);
        assertThat(text).contains("minItems: 1");
        Files.writeString(denied, text.replace("minItems: 1", "minItems: 0"), StandardCharsets.UTF_8);

        assertThat(mirror(copy)).isNotEqualTo(canonical());
    }

    @Test
    void theStrippedTreesStillHoldTheClosingAndConstrainingKeywords() {
        String flat = String.valueOf(canonical());
        assertThat(flat).contains("additionalProperties=false", "minItems=1", "required=[schemaVersion]", "maximum=1");
    }

    private static Object canonical() {
        Map<?, ?> root = (Map<?, ?>) load(OFFERING.resolve(JSON_FILE));
        Map<?, ?> defs = (Map<?, ?>) root.get("$defs");
        return resolve(root, ref -> {
            assertThat(ref).startsWith("#/$defs/");
            return defs.get(ref.substring("#/$defs/".length()));
        });
    }

    private static Object mirror(Path directory) {
        return resolve(load(directory.resolve(MIRROR)), ref -> {
            assertThat(ref).doesNotContain("#").endsWith(".yaml");
            return load(directory.resolve(ref));
        });
    }

    private interface Resolver {
        Object target(String ref);
    }

    private static Object resolve(Object node, Resolver resolver) {
        if (node instanceof Map<?, ?> map) {
            if (map.containsKey("$ref")) {
                Map<Object, Object> rest = new LinkedHashMap<>(map);
                Object target = resolver.target(String.valueOf(rest.remove("$ref")));
                // A validating keyword beside a $ref applies with it, not instead of it; merging it over the target
                // would compare the wrong schema, so the mirror and the canonical file keep $ref alone.
                assertThat(rest.keySet().stream()
                                .map(String::valueOf)
                                .filter(name -> !STRIPPED.contains(name) && !name.startsWith("x-")))
                        .as("the validating siblings of %s", map.get("$ref"))
                        .isEmpty();
                assertThat(target).as("the target of %s", map.get("$ref")).isNotNull();
                Map<String, Object> merged = new TreeMap<>(asTree(resolve(target, resolver)));
                merged.putAll(asTree(resolve(rest, resolver)));
                return merged;
            }
            Map<String, Object> out = new TreeMap<>();
            map.forEach((key, value) -> {
                String name = String.valueOf(key);
                if (!STRIPPED.contains(name) && !name.startsWith("x-")) {
                    out.put(name, resolve(value, resolver));
                }
            });
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            list.forEach(element -> out.add(resolve(element, resolver)));
            return out;
        }
        return node;
    }

    private static Map<String, Object> asTree(Object resolved) {
        Map<String, Object> tree = new TreeMap<>();
        if (resolved instanceof Map<?, ?> map) {
            map.forEach((key, value) -> tree.put(String.valueOf(key), value));
        }
        return tree;
    }

    private static Object load(Path file) {
        try {
            return new Load(LoadSettings.builder().build())
                    .loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
