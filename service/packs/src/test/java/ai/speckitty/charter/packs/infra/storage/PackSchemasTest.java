package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** FR-006 and NFR-005: the 16 bundled pack schemas load strictly, offline and once. */
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

    private static JsonNode schema(String kind) throws IOException {
        return MAPPER.readTree(bundledText(kind));
    }

    private static PackSchemas fixture(String dir, String... names) {
        return new PackSchemas("schema-fixtures/" + dir + "/", List.of(names));
    }

    // ---- the bundle -------------------------------------------------------------------------

    @Test
    void thereAreTwentyEightBundledSchemas() {
        assertThat(bundledKinds()).hasSize(28);
    }

    @ParameterizedTest
    @MethodSource("bundledKinds")
    void eachBundledSchemaDeclaresDraft2020AndAnIdUnderThePrefix(String kind) throws IOException {
        JsonNode schema = MAPPER.readTree(bundledText(kind));
        assertThat(schema.path("$schema").asString()).isEqualTo(DRAFT);
        assertThat(schema.path("$id").asString()).isEqualTo(ID_PREFIX + kind + ".schema.json");
    }

    /** ADR-0067 decision 1: paradigms and styleguides carry the one principle element, byte for byte the same. */
    @Test
    void paradigmAndStyleguideShareThePrincipleElement() throws IOException {
        JsonNode paradigm = schema("paradigm");
        JsonNode styleguide = schema("styleguide");

        assertThat(paradigm.at("/$defs/Principle")).isEqualTo(styleguide.at("/$defs/Principle"));
        assertThat(paradigm.at("/$defs/Principle/additionalProperties").asBoolean(true))
                .isFalse();
        assertThat(paradigm.at("/properties/principles/items/$ref").asString()).isEqualTo("#/$defs/Principle");
        assertThat(styleguide.at("/properties/principles/items/$ref").asString())
                .isEqualTo("#/$defs/Principle");
        assertThat(paradigm.at("/properties/principles").has("maxItems"))
                .as("no count cap")
                .isFalse();
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
    void everySchemaFileOfTheRepositoryIsListedInFileKinds() throws IOException {
        String root = System.getProperty("charter.repositoryRoot");
        assertThat(root).as("system property charter.repositoryRoot").isNotNull();
        try (Stream<Path> files = Files.list(Path.of(root, "contracts/packs/schemas"))) {
            List<String> onDisk = files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".schema.json"))
                    .map(name -> name.substring(0, name.length() - ".schema.json".length()))
                    .toList();
            assertThat(onDisk)
                    .as("schema files in contracts/packs/schemas")
                    .containsExactlyInAnyOrderElementsOf(bundledKinds());
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

    /** The schema and {@code PackQualifiedReference} read one name grammar, so every authored pack can be referenced. */
    @Test
    void theDescriptorSchemaConstrainsNameToThePackQualifiedReferenceGrammar() throws IOException {
        assertThat(MAPPER.readTree(bundledText("pack-descriptor"))
                        .at("/properties/name/pattern")
                        .asString())
                .isEqualTo(PackQualifiedReference.PACK_NAME_GRAMMAR);
    }

    /** Every schema that lets a reference carry a {@code <pack-name>::} qualifier spells it as the Java grammar does. */
    @ParameterizedTest
    @CsvSource({
        "graph-shard,/$defs/DRGEdge/properties/source/pattern",
        "graph-shard,/$defs/DRGEdge/properties/target/pattern",
        "org-fragment,/$defs/_OrgDRGEdge/properties/source/pattern",
        "org-fragment,/$defs/_OrgDRGEdge/properties/target/pattern"
    })
    void aReferencePatternQualifiesWithThePackNameGrammar(String kind, String pointer) throws IOException {
        String grammar = PackQualifiedReference.PACK_NAME_GRAMMAR;
        String qualifier = "(" + grammar.substring(1, grammar.length() - 1) + "::)?";

        assertThat(MAPPER.readTree(bundledText(kind)).at(pointer).asString()).startsWith("^" + qualifier);
    }

    /**
     * The artifact fields that minted, or carried, a relationship are gone (ADR-0064): each kind's minimal file is
     * valid, and the same file with the field is refused for that one undeclared key, so a relationship can only be
     * authored in the pack's graph documents.
     */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                "directive|id: D1\\nschema_version: '1.0'\\ntitle: One\\nintent: one\\nenforcement: required|references|"
                        + "references: [{type: tactic, id: t1}]",
                "directive|id: D1\\nschema_version: '1.0'\\ntitle: One\\nintent: one\\nenforcement: required|procedures|"
                        + "procedures: [Run the gate.]",
                "paradigm|id: p1\\nschema_version: '1.0'\\nname: One\\nsummary: one|directive_refs|directive_refs: [D1]",
                "paradigm|id: p1\\nschema_version: '1.0'\\nname: One\\nsummary: one|references|"
                        + "references: [{type: tactic, id: t1}]",
                "tactic|id: t1\\nschema_version: '1.0'\\nname: One\\nsteps: [{title: s}]|references|"
                        + "references: [{type: directive, id: D1}]",
                "procedure|id: p1\\nschema_version: '1.0'\\ntitle: One\\ndescription: one\\nentry_condition: e\\n"
                        + "exit_condition: x\\nsteps: [{title: s}]|references|references: [{type: directive, id: D1}]",
                "mission-type|id: m1\\nschema_version: '1.0'\\ntitle: One\\ndescription: one\\nsteps: [{id: s, title: S}]"
                        + "|references|references: [{type: directive, id: D1}]",
                "styleguide|id: s1\\nschema_version: '1.0'\\ntitle: One\\nscope: code\\nprinciples: [{name: p, description: p.}]|references|"
                        + "references: [packs/built-in/tactics/t1.tactic.yaml]",
                "toolguide|id: g1\\nschema_version: '1.0'\\ntool: git\\ntitle: One\\nguide_path: g1.guide.md\\nsummary: one|"
                        + "references|references: [https://example.org]"
            })
    void aRemovedRelationshipFieldIsAnUndeclaredKey(String kind, String minimal, String field, String line) {
        String base = minimal.replace("\\n", "\n") + "\n";
        String file = "x." + kind + ".yaml";
        assertThat(PackSchemas.bundled().validate(kind, file, Yaml.load(base)))
                .as("the minimal %s is valid", kind)
                .isEmpty();

        assertThat(PackSchemas.bundled().validate(kind, file, Yaml.load(base + line + "\n")))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.keyword()).isEqualTo("additionalProperties");
                    assertThat(violation.property()).contains(field);
                });
    }

    /** A step's references went with its kind's (ADR-0064): a tactic step and a mission-type step refuse them. */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "tactic|id: t1\\nschema_version: '1.0'\\nname: One\\nsteps: [{title: s, references: [{type: directive, id: D1}]}]",
                "mission-type|id: m1\\nschema_version: '1.0'\\ntitle: One\\ndescription: one\\n"
                        + "steps: [{id: s, title: S, references: [{type: directive, id: D1}]}]"
            })
    void aStepReferenceIsAnUndeclaredKey(String kind, String text) {
        assertThat(PackSchemas.bundled()
                        .validate(kind, "x." + kind + ".yaml", Yaml.load(text.replace("\\n", "\n") + "\n")))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.keyword()).isEqualTo("additionalProperties");
                    assertThat(violation.property()).contains("references");
                });
    }

    /** An org fragment's edge holds a {@code when}, as a root shard's does, so a fragment loses nothing a field held. */
    @Test
    void anOrgFragmentEdgeAcceptsWhen() {
        Object tree = Yaml.load("""
                nodes: []
                edges:
                - source: tactic:t1
                  target: directive:D1
                  relation: suggests
                  when: when the rule applies
                """);
        assertThat(PackSchemas.bundled().validate("org-fragment", "drg/fragment.yaml", tree))
                .isEmpty();
    }

    /** The service contract's qualified-reference schema spells the qualifier as the Java grammar does. */
    @Test
    void theContractQualifiedReferenceSchemaSpellsTheQualifierWithThePackNameGrammar() throws IOException {
        String grammar = PackQualifiedReference.PACK_NAME_GRAMMAR;
        String qualifier = "(" + grammar.substring(1, grammar.length() - 1) + "::)?";
        Path schema = Path.of("..", "..", "contracts", "charter", "schemas", "PackQualifiedReference.yaml");

        Object parsed = Yaml.load(Files.readString(schema));

        assertThat(parsed).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) parsed).get("pattern"))
                .as("the schema's parsed pattern value, whatever the YAML quoting")
                .isEqualTo("^" + qualifier + "[a-z_]+:.+$");
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
        assertThat(violationsAt(
                        "pack-descriptor",
                        "pack_id: 01ARWG13C00000000000001ABC\npack_version: .nan\nname: qa\n",
                        "pack_version"))
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

    // ---- structure schemas: a markdown rule's schema validates the parsed model (#194) ----------------

    private static final PackSchemas STRUCTURE = new PackSchemas("markdown-rule-fixtures/", List.of("notes"));

    private static MarkdownDocument notes(List<MarkdownDocument.Section> sections) {
        return new MarkdownDocument(
                Optional.of(Map.of("id", "weekly")), Optional.of("Weekly notes"), "A preamble.", sections);
    }

    @Test
    void aStructureSchemaLoadsThroughTheSameStrictLoaderAndAcceptsAConformingModel() {
        MarkdownDocument document = notes(List.of(
                new MarkdownDocument.Section("Summary", "One line."), new MarkdownDocument.Section("Detail", "")));

        assertThat(STRUCTURE.kinds()).containsExactly("notes");
        assertThat(STRUCTURE.validateMarkdown("notes", "p/notes/a.md", document))
                .isEmpty();
    }

    @Test
    void aStructureSchemaRefusesAModelMissingARequiredSection() {
        MarkdownDocument document = notes(List.of(new MarkdownDocument.Section("Detail", "Only detail.")));

        assertThat(STRUCTURE.validateMarkdown("notes", "p/notes/a.md", document))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.file()).isEqualTo("p/notes/a.md");
                    assertThat(v.pointer()).containsExactly("sections");
                    // The validator reports an unmet `contains` as its implied `minContains: 1`.
                    assertThat(v.keyword()).isEqualTo("minContains");
                });
    }

    @Test
    void theModelTreeOmitsAnAbsentFrontmatterAndTitleRatherThanWritingNull() {
        MarkdownDocument bare = new MarkdownDocument(
                Optional.empty(), Optional.empty(), "", List.of(new MarkdownDocument.Section("Summary", "x")));

        assertThat(PackSchemas.markdownTree(bare))
                .containsOnlyKeys("preamble", "sections")
                .containsEntry("sections", List.of(Map.of("title", "Summary", "content", "x")));
        assertThat(PackSchemas.markdownTree(notes(List.of())))
                .containsOnlyKeys("frontmatter", "title", "preamble", "sections");
        assertThat(STRUCTURE.validateMarkdown("notes", "p/notes/a.md", bare))
                .as("the schema requires frontmatter and title, so their absence is reported, not a null type")
                .extracting(SchemaViolation::keyword)
                .containsOnly("required");
    }

    @Test
    void aStructureSchemaWithAnUnknownKeywordIsRefusedWhenItLoads() {
        assertThatThrownBy(() -> new PackSchemas("markdown-rule-fixtures/unloadable/", List.of("notes")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notes.schema.json")
                .hasRootCauseInstanceOf(InvalidSchemaException.class);
    }

    @Test
    void validatingMarkdownAgainstAKindThatIsNotLoadedIsRefused() {
        assertThatThrownBy(() -> STRUCTURE.validateMarkdown("guide", "p/a.md", MarkdownDocument.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No schema of kind 'guide' is loaded");
    }
}
