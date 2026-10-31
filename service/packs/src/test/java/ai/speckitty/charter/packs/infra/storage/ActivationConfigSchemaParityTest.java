package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The activation document's two schemas agree with each other and with the charter's own vocabulary (#125, research
 * D-2, D-3):
 *
 * <ul>
 *   <li>every document of {@code contracts/offering/corpus/ActivationConfig/valid} passes, and every one of
 *       {@code invalid} fails, against the canonical JSON Schema and against its OpenAPI mirror, its file {@code $ref}s
 *       inlined, both with networknt on Draft 2020-12;
 *   <li>{@code SelectedArtifacts} and {@code DeactivatedArtifacts} declare exactly the activatable artifact kinds other
 *       than {@code mission_type}, which {@code missionTypes} holds;
 *   <li>every {@code activated_<plural>} key of the charter schema but {@code activated_kinds} names one of them,
 *       through {@link ArtifactKind#plural()}: content alignment, not file shape (operator ruling 2026-10-20).
 * </ul>
 */
class ActivationConfigSchemaParityTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Path OFFERING = Path.of(System.getProperty("charter.offering"));
    private static final Path CHARTER_SCHEMAS = Path.of(System.getProperty("charter.contractSchemas"));
    private static final Path CORPUS = OFFERING.resolve("corpus/ActivationConfig");
    private static final SchemaRegistry REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    @TestFactory
    List<DynamicTest> everyCorpusDocumentHasTheSameVerdictOnBothSchemas() throws IOException {
        Schema canonical = REGISTRY.getSchema(text(OFFERING.resolve("schemas/ActivationConfig.schema.json")));
        Schema mirror = REGISTRY.getSchema(MAPPER.writeValueAsString(inlined("ActivationConfig.yaml")));
        List<DynamicTest> tests = new ArrayList<>();
        for (String verdict : List.of("valid", "invalid")) {
            try (Stream<Path> files = Files.list(CORPUS.resolve(verdict))) {
                for (Path file : files.sorted().toList()) {
                    JsonNode document = MAPPER.readTree(text(file));
                    boolean expected = verdict.equals("valid");
                    tests.add(DynamicTest.dynamicTest(verdict + "/" + file.getFileName(), () -> {
                        assertThat(canonical.validate(document).isEmpty())
                                .as("the canonical schema on %s: %s", file.getFileName(), canonical.validate(document))
                                .isEqualTo(expected);
                        assertThat(mirror.validate(document).isEmpty())
                                .as("the mirror on %s: %s", file.getFileName(), mirror.validate(document))
                                .isEqualTo(expected);
                    }));
                }
            }
        }
        assertThat(tests.stream().filter(t -> t.getDisplayName().startsWith("valid/")))
                .hasSizeGreaterThanOrEqualTo(10);
        assertThat(tests.stream().filter(t -> t.getDisplayName().startsWith("invalid/")))
                .hasSizeGreaterThanOrEqualTo(10);
        return tests;
    }

    @Test
    void theDocumentSelectsAndDeactivatesExactlyTheActivatableArtifactKinds() {
        Set<String> activatable = Arrays.stream(ArtifactKind.values())
                .filter(ArtifactKind::activatable)
                .filter(kind -> kind != ArtifactKind.MISSION_TYPE)
                .map(ArtifactKind::token)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(activatable).hasSize(11);
        assertThat(properties("SelectedArtifacts.yaml")).isEqualTo(activatable);
        assertThat(properties("DeactivatedArtifacts.yaml")).isEqualTo(activatable);
    }

    @Test
    void everyActivationKeyOfTheCharterSchemaNamesAKindTheDocumentSelects() throws IOException {
        Map<String, String> byPlural = Arrays.stream(ArtifactKind.values())
                .collect(Collectors.toMap(ArtifactKind::plural, ArtifactKind::token));
        JsonNode charter;
        try (InputStream in = getClass().getResourceAsStream("/packs/schemas/charter.schema.json")) {
            assertThat(in).isNotNull();
            charter = MAPPER.readTree(in);
        }
        List<String> keys = charter.path("properties").propertyNames().stream()
                .filter(name -> name.startsWith("activated_") && !name.equals("activated_kinds"))
                .toList();
        assertThat(keys).as("the activated_<plural> keys of the charter schema").isNotEmpty();
        Set<String> selected = properties("SelectedArtifacts.yaml");
        for (String key : keys) {
            String kind = byPlural.get(key.substring("activated_".length()));
            assertThat(kind).as("the kind of %s", key).isNotNull();
            assertThat(selected)
                    .as("SelectedArtifacts holds the kind of %s", key)
                    .contains(kind);
        }
    }

    private static Set<String> properties(String file) {
        Object properties = ((Map<?, ?>) load(CHARTER_SCHEMAS.resolve(file))).get("properties");
        return ((Map<?, ?>) properties)
                .keySet().stream().map(String::valueOf).collect(Collectors.toCollection(TreeSet::new));
    }

    /** The mirror as one schema: each file {@code $ref} replaced by its file, every {@code x-} key removed. */
    private static Object inlined(String file) {
        return inline(load(CHARTER_SCHEMAS.resolve(file)));
    }

    private static Object inline(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            Object ref = map.get("$ref");
            if (ref != null) {
                assertThat(map.keySet().stream()
                                .map(String::valueOf)
                                .filter(name -> !name.equals("$ref")
                                        && !name.startsWith("x-")
                                        && !Set.of("title", "description").contains(name)))
                        .as("a validating keyword beside %s would apply with it, not over it", ref)
                        .isEmpty();
                if (inline(load(CHARTER_SCHEMAS.resolve(String.valueOf(ref)))) instanceof Map<?, ?> target) {
                    target.forEach((key, value) -> out.put(String.valueOf(key), value));
                }
            }
            map.forEach((key, value) -> {
                String name = String.valueOf(key);
                if (!name.equals("$ref") && !name.startsWith("x-")) {
                    out.put(name, inline(value));
                }
            });
            return out;
        }
        if (node instanceof List<?> list) {
            return list.stream().map(ActivationConfigSchemaParityTest::inline).toList();
        }
        return node;
    }

    private static Object load(Path file) {
        return new Load(LoadSettings.builder().build()).loadFromString(text(file));
    }

    private static String text(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
