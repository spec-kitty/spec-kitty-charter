package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.ArtifactKind;
import ai.speckitty.charter.domain.NodeKind;
import ai.speckitty.charter.domain.PackFindingCode;
import ai.speckitty.charter.domain.Relation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The vocabulary of the bundled schemas and the domain's vocabulary are the same words (T024): a kind added in
 * {@code file-kinds.yaml} and the schemas has to be added to {@code NodeKind} too, or one of these fails. The one
 * place the two differ on purpose is the legacy relation {@code in_tension_with}, which the schemas accept and the
 * domain reads as {@code impacts} with strength -1 (core ADR 2026-07-26-3, plan "Relation").
 */
class VocabularyEqualityTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String LEGACY_TENSION = "in_tension_with";
    private static final Pattern ALTERNATION = Pattern.compile("^\\^\\(([a-z_|]+)\\):");

    private static JsonNode schema(String kind) throws IOException {
        try (InputStream in =
                VocabularyEqualityTest.class.getResourceAsStream("/packs/schemas/" + kind + ".schema.json")) {
            assertThat(in).as(kind).isNotNull();
            return JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static Set<String> alternation(String pattern) {
        Matcher m = ALTERNATION.matcher(pattern);
        assertThat(m.find()).as("an alternation of kinds in %s", pattern).isTrue();
        return new TreeSet<>(Arrays.asList(m.group(1).split("\\|")));
    }

    private static Set<String> nodeKindTokens() {
        return new TreeSet<>(
                Arrays.stream(NodeKind.values()).map(NodeKind::token).toList());
    }

    // ---- node kinds -----------------------------------------------------------------------------

    @Test
    void theShardsNodeKindsAreTheDomainsAndTheFileKindTablesInEveryPlaceTheSchemaListsThem() throws IOException {
        JsonNode defs = schema("graph-shard").get("$defs");
        JsonNode node = defs.get("DRGNode").get("properties");
        Set<String> domain = nodeKindTokens();

        assertThat(new TreeSet<>(texts(node.get("kind").get("enum"))))
                .as("DRGNode.kind enum")
                .isEqualTo(domain);
        assertThat(alternation(node.get("urn").get("pattern").asString()))
                .as("DRGNode.urn alternation")
                .isEqualTo(domain);
        JsonNode edge = defs.get("DRGEdge").get("properties");
        assertThat(alternation(edge.get("source").get("pattern").asString()))
                .as("edge source")
                .isEqualTo(domain);
        assertThat(alternation(edge.get("target").get("pattern").asString()))
                .as("edge target")
                .isEqualTo(domain);
        Set<String> branches = new TreeSet<>();
        defs.get("DRGNode").get("allOf").forEach(branch -> {
            String constant =
                    branch.get("if").get("properties").get("kind").get("const").asString();
            branches.add(constant);
            assertThat(branch.get("then")
                            .get("properties")
                            .get("urn")
                            .get("pattern")
                            .asString())
                    .as("the urn prefix of kind %s", constant)
                    .isEqualTo("^" + constant + ":");
        });
        assertThat(branches).as("one urn-prefix branch per kind").isEqualTo(domain);
        assertThat(PackFileKind.bundled().nodeKinds())
                .as("file-kinds.yaml node_kinds, in the order of NodeKind")
                .containsExactlyElementsOf(
                        Arrays.stream(NodeKind.values()).map(NodeKind::token).toList());
    }

    // ---- fragment kinds ----------------------------------------------------------------------------

    @Test
    void theFragmentsKindEnumIsTheFileKindsPluralsAndTheDomainOfFromPlural() throws IOException {
        Set<String> enumValues = new TreeSet<>(texts(schema("org-fragment")
                .get("$defs")
                .get("_OrgDRGNode")
                .get("properties")
                .get("kind")
                .get("enum")));
        Map<String, String> table = PackFileKind.bundled().fragmentKinds();

        assertThat(enumValues).as("the schema's kind enum").isEqualTo(new TreeSet<>(table.keySet()));
        Set<String> domain = new TreeSet<>();
        for (NodeKind kind : NodeKind.values()) {
            String plural = kind.artifactKind().map(ArtifactKind::plural).orElse(kind.token() + "s");
            if (NodeKind.fromPlural(plural).filter(kind::equals).isPresent()) {
                domain.add(plural);
            }
        }
        assertThat(domain).as("the plurals NodeKind.fromPlural answers").isEqualTo(enumValues);
        table.forEach((plural, token) -> assertThat(NodeKind.fromPlural(plural).map(NodeKind::token))
                .as("fromPlural(%s)", plural)
                .contains(token));
        assertThat(new TreeSet<>(table.values()))
                .as("each node kind has one plural")
                .isEqualTo(nodeKindTokens());
    }

    // ---- artifact kinds ----------------------------------------------------------------------------

    @Test
    void theFileKindsArtifactKindsAreTheDomainsArtifactKinds() throws IOException {
        Object table;
        try (InputStream in = VocabularyEqualityTest.class.getResourceAsStream("/packs/file-kinds.yaml")) {
            table = Yaml.load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        List<?> kinds = (List<?>) ((Map<?, ?>) table).get("artifact_kinds");

        assertThat(kinds.stream().map(String::valueOf).toList())
                .containsExactlyElementsOf(Arrays.stream(ArtifactKind.values())
                        .map(ArtifactKind::token)
                        .toList());
    }

    @Test
    void theInspectorsArtifactSchemaKindsAreTheMappedSchemasThatDeclareAnId() throws IOException {
        Set<String> expected = new TreeSet<>();
        for (PackFileKind.FileRule rule : PackFileKind.bundled().fileRules()) {
            try (InputStream in = VocabularyEqualityTest.class.getResourceAsStream(
                    "/packs/schemas/" + rule.schema() + ".schema.json")) {
                Map<?, ?> properties = (Map<?, ?>)
                        JsonMapper.builder().build().readValue(in, Map.class).get("properties");
                if (properties != null && properties.containsKey("id")) {
                    expected.add(rule.schema());
                }
            }
        }

        assertThat(FileSystemPackInspector.artifactKinds()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(FileSystemPackInspector.artifactKinds())
                .as("the eight kinds that were listed by hand")
                .containsExactlyInAnyOrder(
                        "directive",
                        "tactic",
                        "styleguide",
                        "toolguide",
                        "paradigm",
                        "procedure",
                        "mission-step-contract",
                        "asset");
    }

    // ---- relations ---------------------------------------------------------------------------------

    @Test
    void theSchemasRelationsAreTheDomainsRelationsPlusExactlyTheLegacyInTensionWith() throws IOException {
        Set<String> domain = new TreeSet<>(
                Arrays.stream(Relation.values()).map(Relation::token).toList());
        Set<String> shard = new TreeSet<>(
                texts(schema("graph-shard").get("$defs").get("Relation").get("enum")));
        Set<String> fragment = new TreeSet<>(
                texts(schema("org-fragment").get("$defs").get("Relation").get("enum")));

        assertThat(domain).as("the domain has no " + LEGACY_TENSION).doesNotContain(LEGACY_TENSION);
        Set<String> accepted = new TreeSet<>(domain);
        accepted.add(LEGACY_TENSION);
        assertThat(shard)
                .as("the shard schema accepts the domain's relations and exactly the legacy one")
                .isEqualTo(accepted);
        assertThat(fragment).isEqualTo(shard);
    }

    // ---- the wire codes the load path projects onto ---------------------------------------------------

    @Test
    void everyLoadPathWireTokenIsInTheContractsCodeEnums() throws IOException {
        Path schemas = Path.of(System.getProperty("charter.contractSchemas"));
        Set<String> contract = new LinkedHashSet<>();
        for (String file : List.of("DiagnosticCode.yaml", "CharterRefusalCode.yaml")) {
            Map<?, ?> document = (Map<?, ?>) Yaml.load(Files.readString(schemas.resolve(file), StandardCharsets.UTF_8));
            ((List<?>) document.get("enum")).forEach(v -> contract.add(String.valueOf(v)));
        }
        Set<String> projected = new LinkedHashSet<>();
        for (PackFindingCode code : PackFindingCode.values()) {
            if (code.severity() == PackFindingCode.Severity.WARNING) {
                projected.add(code.diagnostic().token());
            } else if (isOnTheLoadPath(code)) {
                projected.add(code.refusal().token());
            }
        }

        assertThat(projected)
                .contains(
                        "pack_level_missing",
                        "pack_unknown_key",
                        "pack_level_mismatch",
                        "pack_unreadable",
                        "drg_schema_error");
        assertThat(contract).containsAll(projected);
    }

    /** A blocking code that projects onto a refusal; the validate-only codes do not and throw. */
    private static boolean isOnTheLoadPath(PackFindingCode code) {
        try {
            code.refusal();
            return true;
        } catch (IllegalStateException validateOnly) {
            return false;
        }
    }
}
