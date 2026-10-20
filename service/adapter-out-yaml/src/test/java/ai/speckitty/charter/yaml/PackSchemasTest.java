package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.PackContents.SchemaViolation;
import com.networknt.schema.InvalidSchemaException;
import com.networknt.schema.InvalidSchemaRefException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** FR-006 and NFR-005: the 14 bundled pack schemas load strictly, offline and once. */
class PackSchemasTest {

    private static final String ID_PREFIX = "https://schemas.spec-kitty.ai/charter/packs/";
    private static final String DRAFT = "https://json-schema.org/draft/2020-12/schema";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern REF = Pattern.compile("\"\\$ref\"\\s*:\\s*\"([^\"]*)\"");

    private static List<String> bundledKinds() {
        return PackFileKind.bundled().fileRules().stream()
                .map(PackFileKind.FileRule::schema)
                .distinct()
                .toList();
    }

    private static String bundledText(String kind) throws IOException {
        try (InputStream in = PackSchemasTest.class.getResourceAsStream("/packs/schemas/" + kind + ".schema.json")) {
            assertThat(in).as(kind).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static PackSchemas fixture(String dir, String... names) {
        return new PackSchemas("schema-fixtures/" + dir + "/", List.of(names));
    }

    // ---- the bundle -------------------------------------------------------------------------

    @Test
    void thereAreFourteenBundledSchemas() {
        assertThat(bundledKinds()).hasSize(14);
    }

    @ParameterizedTest
    @MethodSource("bundledKinds")
    void eachBundledSchemaDeclaresDraft2020AndAnIdUnderThePrefix(String kind) throws IOException {
        JsonNode schema = MAPPER.readTree(bundledText(kind));
        assertThat(schema.path("$schema").asString()).isEqualTo(DRAFT);
        assertThat(schema.path("$id").asString()).isEqualTo(ID_PREFIX + kind + ".schema.json");
    }

    @Test
    void theIdsAreUnique() throws IOException {
        Set<String> ids = new HashSet<>();
        for (String kind : bundledKinds()) {
            assertThat(ids.add(MAPPER.readTree(bundledText(kind)).path("$id").asString()))
                    .as(kind)
                    .isTrue();
        }
    }

    @Test
    void everyIdAndRefEndsInJsonOrIsLocal() throws IOException {
        for (String kind : bundledKinds()) {
            String text = bundledText(kind);
            Matcher m = REF.matcher(text);
            while (m.find()) {
                String ref = m.group(1);
                assertThat(ref.startsWith("#/") || ref.endsWith(".json"))
                        .as("%s: $ref %s", kind, ref)
                        .isTrue();
            }
            assertThat(MAPPER.readTree(text).path("$id").asString()).as(kind).endsWith(".json");
        }
    }

    @Test
    void vendoredMdNamesEveryBundledSchemaFile() throws IOException {
        String vendored = System.getProperty("charter.vendoredMd");
        assertThat(vendored).as("system property charter.vendoredMd").isNotNull();
        String text = Files.readString(Path.of(vendored));
        for (String kind : bundledKinds()) {
            assertThat(text).as(kind).contains("`" + kind + "`");
        }
    }

    @Test
    void theBundleLoadsAndEveryKindIsKnown() {
        PackSchemas schemas = PackSchemas.bundled();
        assertThat(schemas.kinds()).containsExactlyInAnyOrderElementsOf(bundledKinds());
        assertThat(PackSchemas.bundled()).isSameAs(schemas);
    }

    // ---- planted schemas: the refusals --------------------------------------------------------

    @Test
    void aSchemaThatFailsTheMetaschemaIsRefusedWithTheFileNamed() {
        assertThatThrownBy(() -> fixture("broken-type", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.schema.json");
    }

    @Test
    void aSchemaThatIsNotJsonIsRefusedWithTheFileNamedAndTheParseErrorAsItsCause() {
        assertThatThrownBy(() -> fixture("malformed-json", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("x.schema.json is not valid JSON: ")
                .hasCauseInstanceOf(JacksonException.class);
    }

    @Test
    void aSchemaWithAnUnknownKeywordIsRefusedAtLoad() {
        assertThatThrownBy(() -> fixture("unknown-keyword", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.schema.json")
                .hasRootCauseInstanceOf(InvalidSchemaException.class);
    }

    @Test
    void aSchemaWithoutDollarSchemaFailsTheBundleCheckWithTheFileNamed() {
        assertThatThrownBy(() -> fixture("no-schema", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.schema.json")
                .hasMessageContaining("$schema");
    }

    @Test
    void aSchemaWhoseIdIsOutsideThePrefixIsRefused() {
        assertThatThrownBy(() -> fixture("wrong-id", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.schema.json")
                .hasMessageContaining("$id");
    }

    @Test
    void aDanglingLocalRefIsRefusedAtLoad() {
        assertThatThrownBy(() -> fixture("dangling-ref", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.schema.json")
                .hasRootCauseInstanceOf(InvalidSchemaRefException.class)
                .hasRootCauseMessage("Reference /$defs/missing cannot be resolved");
    }

    @Test
    void aRemoteRefIsRefusedByTheLoaderWithInvalidSchemaException() {
        assertThatThrownBy(() -> fixture("remote-ref", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.schema.json")
                .hasRootCauseInstanceOf(InvalidSchemaException.class);
    }

    @Test
    void aNonBundledIriOnTheAllowedPrefixIsRefusedWithoutANetworkCall() {
        assertThatThrownBy(() -> fixture("missing-bundled-ref", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.schema.json");
    }

    @Test
    void theConfiguredLoaderHasNoRemoteResourceLoader() {
        var loader = PackSchemas.bundled().schemaLoader();
        assertThat(loader.getResourceLoaders())
                .noneMatch(l -> l.getClass().getSimpleName().contains("Iri"))
                .allSatisfy(l -> assertThat(l.getClass().getSimpleName()).isEqualTo("ClasspathResourceLoader"));
    }

    // ---- positive controls ---------------------------------------------------------------------

    @Test
    void aSchemaThatRefsAnotherBundledSchemaByItsIdResolves() {
        PackSchemas pair = fixture("pair", "a", "b");
        assertThat(pair.validate("a", "a.yaml", java.util.Map.of("b", "text"))).isEmpty();
        List<SchemaViolation> violations = pair.validate("a", "a.yaml", java.util.Map.of("b", 1));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).keyword()).isEqualTo("type");
        assertThat(violations.get(0).pointer()).containsExactly("b");
        assertThat(violations.get(0).file()).isEqualTo("a.yaml");
    }

    @Test
    void aValidMinimalPackYamlHasNoViolations() {
        Object tree = Yaml.load("""
                pack_id: 01ARZ3NDEKTSV4RRFFQ69G5FAV
                name: demo
                pack_version: 1.0.0
                """);
        assertThat(PackSchemas.bundled().validate("pack-descriptor", "pack.yaml", tree))
                .isEmpty();
    }

    @Test
    void aMissingPackVersionIsARequiredViolationWithAnEmptyPointer() {
        Object tree = Yaml.load("""
                pack_id: 01ARZ3NDEKTSV4RRFFQ69G5FAV
                name: demo
                """);
        List<SchemaViolation> violations = PackSchemas.bundled().validate("pack-descriptor", "pack.yaml", tree);
        assertThat(violations).hasSize(1);
        SchemaViolation v = violations.get(0);
        assertThat(v.keyword()).isEqualTo("required");
        assertThat(v.property()).isEqualTo(Optional.of("pack_version"));
        assertThat(v.pointer()).isEmpty();
        assertThat(v.file()).isEqualTo("pack.yaml");
    }

    @Test
    void anEmptyDocumentValidatesAsNullAndTypeObjectRefusesIt() {
        List<SchemaViolation> violations = PackSchemas.bundled().validate("pack-descriptor", "pack.yaml", null);
        assertThat(violations).extracting(SchemaViolation::keyword).contains("type");
    }

    @Test
    void aViolationDeepInTheTreeCarriesItsPointerSegmentsAndNoMessageText() {
        Object tree = Yaml.load("""
                pack_id: 01ARZ3NDEKTSV4RRFFQ69G5FAV
                name: demo
                pack_version: 1.0.0
                pack_level: galaxy
                """);
        List<SchemaViolation> violations = PackSchemas.bundled().validate("pack-descriptor", "pack.yaml", tree);
        assertThat(violations).hasSize(1);
        SchemaViolation v = violations.get(0);
        assertThat(v.keyword()).isEqualTo("enum");
        assertThat(v.pointer()).containsExactly("pack_level");
        List<String> carried = new ArrayList<>(v.args().keySet());
        carried.addAll(v.args().values());
        assertThat(carried).noneMatch(s -> s.contains("does not have a value in the enumeration"));
    }

    @Test
    void theConversionKeepsYamlTypes() {
        Object tree = Yaml.load("""
                i: 1
                d: 1.0
                s: "1.0"
                b: true
                n: null
                l: [1, two]
                """);
        JsonNode node = PackSchemas.toTree(tree);
        assertThat(node.get("i").isIntegralNumber()).isTrue();
        assertThat(node.get("d").isFloatingPointNumber()).isTrue();
        assertThat(node.get("d").isNumber()).isTrue();
        assertThat(node.get("s").isString()).isTrue();
        assertThat(node.get("s").asString()).isEqualTo("1.0");
        assertThat(node.get("b").isBoolean()).isTrue();
        assertThat(node.get("n").isNull()).isTrue();
        assertThat(node.get("l").get(0).isIntegralNumber()).isTrue();
        assertThat(node.get("l").get(1).isString()).isTrue();
    }

    // ---- numbers that are not finite --------------------------------------------------------------

    private static List<SchemaViolation> violationsAt(String kind, String yaml, String... pointer) {
        return PackSchemas.bundled().validate(kind, "x.yaml", Yaml.load(yaml)).stream()
                .filter(v -> v.pointer().equals(List.of(pointer)))
                .toList();
    }

    @ParameterizedTest
    @CsvSource({".inf,Infinity", "-.inf,-Infinity", ".nan,NaN", "1e400,Infinity", "-1e400,-Infinity"})
    void aNonFiniteRoutingPriorityBreaksTypeAndIsReportedAsTheFileStatesIt(String written, String reported) {
        assertThat(violationsAt("agent-profile", "routing-priority: " + written + "\n", "routing-priority"))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.keyword()).isEqualTo("type");
                    assertThat(v.value()).contains(reported);
                });
    }

    @ParameterizedTest
    @CsvSource({".inf,Infinity", "-.inf,-Infinity", ".nan,NaN"})
    void aNonFiniteMaxConcurrentTasksIsReportedWithTheFilesValueNeverZero(String written, String reported) {
        assertThat(violationsAt("agent-profile", "max-concurrent-tasks: " + written + "\n", "max-concurrent-tasks"))
                .isNotEmpty()
                .allSatisfy(v -> assertThat(v.value()).contains(reported))
                .extracting(SchemaViolation::keyword)
                .contains("type");
    }

    @ParameterizedTest
    @ValueSource(strings = {".nan", ".inf", "-.inf", "1e400"})
    void aNonFiniteSchemaVersionOfTheOrgCharterIsRefused(String written) {
        assertThat(violationsAt("org-charter", "schema_version: " + written + "\n", "schema_version"))
                .singleElement()
                .satisfies(v -> assertThat(v.keyword()).isEqualTo("type"));
    }

    @Test
    void aHugeIntegerIsAnIntegerOutOfRangeNotAnInvalidNumber() {
        String huge = "123456789012345678901234567890123456789";
        assertThat(violationsAt("agent-profile", "routing-priority: " + huge + "\n", "routing-priority"))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.keyword()).isEqualTo("maximum");
                    assertThat(v.value()).contains(huge);
                });
    }

    @Test
    void aNonFiniteNumberWhereAStringIsDeclaredIsRefusedAsTheWrongType() {
        assertThat(violationsAt("pack-descriptor", "pack_id: 01ABC\npack_version: 1.0.0\nname: .nan\n", "name"))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.keyword()).isEqualTo("type");
                    assertThat(v.value()).contains("NaN");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {".nan", ".inf", "-.inf", "1e400"})
    void aNonFiniteImpactsIsNoSchemaFindingBecauseTheDomainJudgesItsRange(String written) {
        String shard =
                "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n- {source: tactic:t, target: tactic:u, relation: impacts, impacts: "
                        + written + "}\n";
        assertThat(PackSchemas.bundled().validate("graph-shard", "x.graph.yaml", Yaml.load(shard)))
                .isEmpty();
        String fragment = "nodes: []\nedges:\n- {source: tactic:t, target: tactic:u, relation: impacts, impacts: "
                + written + "}\n";
        assertThat(PackSchemas.bundled().validate("org-fragment", "drg/fragment.yaml", Yaml.load(fragment)))
                .isEmpty();
    }

    @Test
    void theConversionNeverHandsTheValidatorAFiniteStandIn() {
        JsonNode node = PackSchemas.toTree(Yaml.load("a: .nan\nb: [.inf, -.inf]\n"));
        assertThat(node.get("a").isNumber()).isFalse();
        assertThat(node.get("b").get(0).isNumber()).isFalse();
        assertThat(node.get("a").asString()).isEqualTo("NaN");
        assertThat(node.get("b").get(0).asString()).isEqualTo("Infinity");
        assertThat(node.get("b").get(1).asString()).isEqualTo("-Infinity");
    }
}
