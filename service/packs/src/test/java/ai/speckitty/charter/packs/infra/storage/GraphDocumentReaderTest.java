package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.RawNode;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The reader reports facts: the document as written, or why it could not be read. Every text asserted here is the
 * one {@link PackPolicy} writes from those facts, so the violations are the real output of the schema validator.
 */
class GraphDocumentReaderTest {

    private static final String SHARD = "tactic.graph.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";
    private static final String HEADER = "schema_version: '1.0'\ngenerated_at: STATIC\n";

    private static RawGraph read(String text, Shape shape, String file) {
        FileRead<RawGraph> read = GraphDocumentReader.read(text, shape, file);
        assertThat(read).isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<RawGraph>) read).value();
    }

    private static FileRead.Unreadable<RawGraph> unreadable(String text, Shape shape, String file) {
        FileRead<RawGraph> read = GraphDocumentReader.read(text, shape, file);
        assertThat(read).isInstanceOf(FileRead.Unreadable.class);
        return (FileRead.Unreadable<RawGraph>) read;
    }

    private static List<String> details(FileRead.Unreadable<RawGraph> unreadable) {
        return PackPolicy.unreadable("qa", unreadable).stream()
                .map(PackFinding::detail)
                .toList();
    }

    // ---- a document that conforms is read as written ----------------------------------------------

    @Test
    void aShardIsReadAsWrittenWithoutBindingAnythingToTheDomain() {
        RawGraph raw = read(HEADER + """
                nodes:
                - urn: tactic:t
                  kind: tactic
                  label: 'A: label'
                - {urn: tactic:u, kind: tactic}
                edges:
                - source: tactic:t
                  target: directive:d
                  relation: requires
                  when: always
                  reason: because
                - {source: tactic:t, target: tactic:u, relation: impacts, impacts: -1}
                - {source: tactic:t, target: tactic:u, relation: in_tension_with}
                """, Shape.SHARD, SHARD);

        assertThat(raw.file()).isEqualTo(SHARD);
        assertThat(raw.shape()).isEqualTo(RawGraph.Shape.SHARD);
        assertThat(raw.nodes())
                .containsExactly(
                        new RawNode("tactic:t", "tactic", Optional.of("A: label")),
                        new RawNode("tactic:u", "tactic", Optional.empty()));
        assertThat(raw.edges())
                .containsExactly(
                        new RawEdge(
                                "tactic:t",
                                "directive:d",
                                "requires",
                                Optional.empty(),
                                Optional.of("always"),
                                Optional.of("because")),
                        new RawEdge(
                                "tactic:t",
                                "tactic:u",
                                "impacts",
                                Optional.of(-1.0),
                                Optional.empty(),
                                Optional.empty()),
                        // The legacy relation is still the word on the page; the domain maps it.
                        new RawEdge(
                                "tactic:t",
                                "tactic:u",
                                "in_tension_with",
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()));
    }

    @Test
    void aFragmentIsReadWithItsBareIdsAndPluralKinds() {
        RawGraph raw = read("""
                nodes:
                - id: QA_001
                  kind: directives
                  title: Traceability
                edges:
                - source: directive:QA_001
                  target: directive:DIRECTIVE_010
                  relation: suggests
                """, Shape.FRAGMENT, FRAGMENT);

        assertThat(raw.shape()).isEqualTo(RawGraph.Shape.FRAGMENT);
        assertThat(raw.nodes()).containsExactly(new RawNode("QA_001", "directives", Optional.of("Traceability")));
        assertThat(raw.edges())
                .containsExactly(new RawEdge(
                        "directive:QA_001",
                        "directive:DIRECTIVE_010",
                        "suggests",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()));
    }

    @Test
    void aFragmentMayLeaveOutItsListsAndAShardNeedsThem() {
        assertThat(read("{}\n", Shape.FRAGMENT, FRAGMENT).nodes()).isEmpty();
        assertThat(details(unreadable(HEADER, Shape.SHARD, SHARD)))
                .containsExactly("qa/tactic.graph.yaml: missing 'nodes'", "qa/tactic.graph.yaml: missing 'edges'");
    }

    @Test
    void writingAGraphAndReadingItBackGivesTheSameGraph() {
        DoctrineGraph graph =
                PackPolicy.graph("qa", read(HEADER + """
                                nodes:
                                - {urn: tactic:t, kind: tactic, label: 'A: label'}
                                edges:
                                - {source: tactic:t, target: directive:d, relation: requires, reason: because}
                                - {source: tactic:t, target: tactic:u, relation: suggests, when: always}
                                """, Shape.SHARD, SHARD)).graph();

        RawGraph reread = read(GraphShardWriter.write(graph), Shape.SHARD, "w.graph.yaml");
        DoctrineGraph rebuilt = PackPolicy.graph("qa", reread).graph();

        assertThat(rebuilt).isEqualTo(graph);
        // The round-trip keeps relation, condition and rationale, present-vs-absent distinct (NFR-003).
        assertThat(rebuilt.relationships())
                .satisfiesExactly(
                        requires -> {
                            assertThat(requires.type()).isEqualTo(RelationType.REQUIRES);
                            assertThat(requires.when()).isEmpty();
                            assertThat(requires.rationale()).contains("because");
                        },
                        suggests -> {
                            assertThat(suggests.type()).isEqualTo(RelationType.SUGGESTS);
                            assertThat(suggests.when()).contains("always");
                            assertThat(suggests.rationale()).isEmpty();
                        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"~", "Null", "NULL"})
    void aStringThatReadsAsNullIsQuotedWhenWrittenSoItIsNotLostOnTheWayBack(String text) {
        Urn tactic = new Urn(NodeKind.TACTIC, "t");
        Urn directive = new Urn(NodeKind.DIRECTIVE, "d");
        DoctrineGraph graph = new DoctrineGraph(
                List.of(new DrgNode(tactic, Optional.of(text)), new DrgNode(directive, Optional.empty())),
                List.of(new Relationship(
                        tactic, directive, RelationType.REQUIRES, Optional.of(text), Optional.of(text))));

        String written = GraphShardWriter.write(graph);
        assertThat(written).contains("label: '" + text + "'", "when: '" + text + "'", "reason: '" + text + "'");

        RawGraph reread = read(written, Shape.SHARD, "w.graph.yaml");
        assertThat(reread.nodes().get(0).label()).contains(text);
        DoctrineGraph rebuilt = PackPolicy.graph("qa", reread).graph();
        assertThat(rebuilt).isEqualTo(graph);
        // The null-reading string survives on the condition and the rationale, not only the label.
        assertThat(rebuilt.relationships()).singleElement().satisfies(relationship -> {
            assertThat(relationship.when()).contains(text);
            assertThat(relationship.rationale()).contains(text);
        });
    }

    @Test
    void aStrengthThatIsNotFiniteIsAFactTheDomainJudgesNotAnInvalidNumber() {
        RawGraph raw = read(
                HEADER + "nodes: []\nedges:\n"
                        + "- {source: tactic:t, target: tactic:u, relation: impacts, impacts: .nan}\n"
                        + "- {source: tactic:t, target: tactic:u, relation: impacts, impacts: .inf}\n"
                        + "- {source: tactic:t, target: tactic:u, relation: impacts, impacts: 2}\n",
                Shape.SHARD,
                SHARD);

        assertThat(raw.edges())
                .extracting(e -> e.impacts().orElseThrow())
                .containsExactly(Double.NaN, Double.POSITIVE_INFINITY, 2.0);
        assertThat(PackPolicy.graph("qa", raw).findings())
                .extracting(PackFinding::detail)
                .containsExactly(
                        "qa/tactic.graph.yaml: 'impacts' NaN is outside [-1, 1]",
                        "qa/tactic.graph.yaml: 'impacts' Infinity is outside [-1, 1]",
                        "qa/tactic.graph.yaml: 'impacts' 2.0 is outside [-1, 1]");
    }

    @Test
    void aNonFiniteNumberBesideANullListItemIsRefusedNotThrown() {
        var fragment = unreadable(
                "nodes: [null, {id: x, kind: directives, title: .nan}]\nedges: []\n", Shape.FRAGMENT, FRAGMENT);
        assertThat(fragment.violations()).anySatisfy(v -> {
            assertThat(v.pointer()).containsExactly("nodes", "1", "title");
            assertThat(v.keyword()).isEqualTo("type");
            assertThat(v.value()).contains("NaN");
        });
        var shard = unreadable(
                HEADER + "nodes: [null, {urn: 'tactic:x', kind: tactic, label: .inf}]\nedges: []\n",
                Shape.SHARD,
                SHARD);
        assertThat(shard.violations()).anySatisfy(v -> {
            assertThat(v.pointer()).containsExactly("nodes", "1", "label");
            assertThat(v.keyword()).isEqualTo("type");
            assertThat(v.value()).contains("Infinity");
        });
    }

    @Test
    void aNonFiniteLabelHasTheTextOfANumberLabelAndTheNullBranchIsCollapsed() {
        for (String number : List.of("1.5", ".nan", "-.inf")) {
            var shard = unreadable(
                    HEADER + "nodes: [{urn: 'tactic:x', kind: tactic, label: " + number + "}]\nedges: []\n",
                    Shape.SHARD,
                    SHARD);
            assertThat(PackPolicy.unreadable("qa", shard))
                    .extracting(PackFinding::detail)
                    .as(number)
                    .containsExactly("qa/tactic.graph.yaml: 'label' must be a string");
        }
    }

    @Test
    void anUndeclaredNonFiniteRootKeyOfAShardGivesExactlyOneFinding() {
        assertThat(unreadable(HEADER + "nodes: []\nedges: []\nextra: .nan\n", Shape.SHARD, SHARD)
                        .violations())
                .singleElement()
                .satisfies(v -> assertThat(v.keyword()).isEqualTo("additionalProperties"));
    }

    // ---- a document that is not YAML --------------------------------------------------------------

    static Stream<String> notYaml() {
        return Stream.of(
                "nodes: [\n",
                "nodes:\n\t- a\n",
                "nodes: !!python/object/apply:os.system [rm]\n",
                "a: 1\na: 2\n",
                "%YAML 9.9\n---\nnodes: []\n",
                "nodes: *missing\n",
                "nodes: {a: b\n",
                "\"unterminated\n",
                "key: [a, b]]\n",
                "nodes: []\n---\nnodes: []\n");
    }

    @ParameterizedTest
    @MethodSource("notYaml")
    void aDocumentThatIsNotYamlIsReportedAsAParseFailureNamingTheFileAndTheParsersFirstLineOnly(String text) {
        for (Shape shape : Shape.values()) {
            FileRead.Unreadable<RawGraph> bad = unreadable(text, shape, "some/file.yaml");

            assertThat(bad.file()).isEqualTo("some/file.yaml");
            assertThat(bad.violations()).isEmpty();
            assertThat(bad.parse()).isNotNull();
            assertThat(bad.parse().file()).isEqualTo("some/file.yaml");
            assertThat(bad.parse().firstLine())
                    .isNotBlank()
                    .doesNotContain("\n")
                    .doesNotContain(System.getProperty("user.home"))
                    .doesNotContain(System.getProperty("user.dir"));
        }
    }

    @Test
    void aMappingKeyThatIsNoStringIsAParseFailureToo() {
        FileRead.Unreadable<RawGraph> bad = unreadable("nodes:\n- {1: x}\n", Shape.SHARD, SHARD);

        assertThat(bad.parse()).isNotNull();
        assertThat(bad.parse().firstLine()).isEqualTo("a mapping key is not a string");
        assertThat(bad.violations()).isEmpty();
        assertThat(details(bad))
                .containsExactly("qa/tactic.graph.yaml is not valid YAML: a mapping key is not a string");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\n", "# nothing\n", "~\n", "42\n", "- a\n", "just text\n", "!!binary aGk=\n"})
    void theReaderNeverThrowsOnContent(String text) {
        for (Shape shape : Shape.values()) {
            assertThat(GraphDocumentReader.read(text, shape, "x.yaml")).isNotNull();
        }
    }

    // ---- a document that breaks its schema: facts, with the real validator's output ------------------

    private static SchemaViolation only(FileRead.Unreadable<RawGraph> bad) {
        assertThat(bad.parse()).isNull();
        assertThat(bad.violations()).hasSize(1);
        return bad.violations().get(0);
    }

    @Test
    void anUnknownRootKeyIsTheKeywordAdditionalPropertiesAtTheRootNamingTheProperty() {
        FileRead.Unreadable<RawGraph> bad =
                unreadable(HEADER + "nodes: []\nedges: []\nsurprise: true\n", Shape.SHARD, SHARD);

        SchemaViolation violation = only(bad);
        assertThat(violation.file()).isEqualTo(SHARD);
        assertThat(violation.keyword()).isEqualTo("additionalProperties");
        assertThat(violation.pointer()).isEmpty();
        assertThat(violation.property()).contains("surprise");
        assertThat(details(bad)).containsExactly("qa/tactic.graph.yaml: unknown key 'surprise'");
    }

    @Test
    void aNodeWithoutItsKindIsTheKeywordRequiredAtTheNodeNamingTheProperty() {
        FileRead.Unreadable<RawGraph> bad =
                unreadable(HEADER + "nodes:\n- urn: tactic:t\nedges: []\n", Shape.SHARD, SHARD);

        SchemaViolation violation = only(bad);
        assertThat(violation.keyword()).isEqualTo("required");
        assertThat(violation.pointer()).containsExactly("nodes", "0");
        assertThat(violation.property()).contains("kind");
        assertThat(details(bad)).containsExactly("qa/tactic.graph.yaml: missing 'kind'");
    }

    @Test
    void aStrengthThatIsNoNumberIsTheKeywordTypeAtTheFieldWithTheFoundAndExpectedTypes() {
        FileRead.Unreadable<RawGraph> bad = unreadable(
                HEADER
                        + "nodes: []\nedges:\n- {source: tactic:t, target: tactic:u, relation: impacts, impacts: strong}\n",
                Shape.SHARD,
                SHARD);

        SchemaViolation violation = only(bad);
        assertThat(violation.keyword()).isEqualTo("type");
        assertThat(violation.pointer()).containsExactly("edges", "0", "impacts");
        assertThat(violation.value()).contains("strong");
        assertThat(violation.args()).containsEntry("0", "string").containsEntry("1", "number");
        assertThat(details(bad)).containsExactly("qa/tactic.graph.yaml: 'impacts' must be a number");
    }

    @Test
    void aBareEndpointIsTheKeywordPatternWithTheRegexAndTheOffendingValue() {
        FileRead.Unreadable<RawGraph> bad = unreadable(
                "nodes: []\nedges:\n- {source: QA_404, target: directive:DIRECTIVE_010, relation: suggests}\n",
                Shape.FRAGMENT,
                FRAGMENT);

        SchemaViolation violation = only(bad);
        assertThat(violation.keyword()).isEqualTo("pattern");
        assertThat(violation.pointer()).containsExactly("edges", "0", "source");
        assertThat(violation.value()).contains("QA_404");
        assertThat(violation.args().get("0")).startsWith("^(directive|tactic|");
        assertThat(details(bad)).containsExactly("qa/drg/fragment.yaml: edge endpoint 'QA_404' is not a URN");
    }

    @Test
    void anUnknownRelationIsTheKeywordEnumAtTheRelation() {
        FileRead.Unreadable<RawGraph> bad = unreadable(
                HEADER + "nodes: []\nedges:\n- {source: tactic:t, target: tactic:u, relation: inspires}\n",
                Shape.SHARD,
                SHARD);

        SchemaViolation violation = only(bad);
        assertThat(violation.keyword()).isEqualTo("enum");
        assertThat(violation.pointer()).containsExactly("edges", "0", "relation");
        assertThat(details(bad)).containsExactly("qa/tactic.graph.yaml: unknown relation 'inspires'");
    }

    @Test
    void aNodeThatIsNoMappingIsTheKeywordTypeAtTheItem() {
        FileRead.Unreadable<RawGraph> bad = unreadable(HEADER + "nodes:\n- a\nedges: []\n", Shape.SHARD, SHARD);

        assertThat(only(bad).pointer()).containsExactly("nodes", "0");
        assertThat(details(bad)).containsExactly("qa/tactic.graph.yaml node: expected a mapping");
    }

    @Test
    void aDocumentThatIsNoMappingIsTheKeywordTypeAtTheRoot() {
        FileRead.Unreadable<RawGraph> bad = unreadable("- a\n", Shape.SHARD, SHARD);

        assertThat(only(bad).pointer()).isEmpty();
        assertThat(details(bad)).containsExactly("qa/tactic.graph.yaml: expected a mapping");
        assertThat(details(unreadable("", Shape.FRAGMENT, FRAGMENT)))
                .containsExactly("qa/drg/fragment.yaml: expected a mapping");
    }

    // ---- the named facts the validator does not emit (WP05's vocabulary argument contract) ------------

    @Test
    void anEmptyFragmentIdCarriesTheSiblingPluralKindSoTheTextNamesIt() {
        FileRead.Unreadable<RawGraph> bad =
                unreadable("nodes:\n- {id: '', kind: directives}\n", Shape.FRAGMENT, FRAGMENT);

        assertThat(bad.violations())
                .extracting(SchemaViolation::keyword)
                .containsExactlyInAnyOrder("minLength", "pattern");
        assertThat(bad.violations()).allSatisfy(v -> {
            assertThat(v.pointer()).containsExactly("nodes", "0", "id");
            assertThat(v.args()).containsEntry("kind", "directives");
        });
        assertThat(details(bad)).containsExactly("qa/drg/fragment.yaml: a node of kind 'directives' has an empty id");
    }

    @Test
    void anUnsafeFragmentIdCarriesTheSiblingPluralKind() {
        FileRead.Unreadable<RawGraph> bad =
                unreadable("nodes:\n- {id: 'a b', kind: tactics}\n", Shape.FRAGMENT, FRAGMENT);

        assertThat(only(bad).args()).containsEntry("kind", "tactics");
        assertThat(details(bad)).containsExactly("qa/drg/fragment.yaml: node id 'a b' is not <kind>:<id>-safe");
    }

    @Test
    void anUnknownFragmentKindCarriesTheNodesId() {
        FileRead.Unreadable<RawGraph> bad = unreadable("nodes:\n- {id: x, kind: widgets}\n", Shape.FRAGMENT, FRAGMENT);

        SchemaViolation violation = only(bad);
        assertThat(violation.keyword()).isEqualTo("enum");
        assertThat(violation.pointer()).containsExactly("nodes", "0", "kind");
        assertThat(violation.args()).containsEntry("id", "x").doesNotContainKey("urn");
        assertThat(details(bad)).containsExactly("qa/drg/fragment.yaml: node x has unknown kind 'widgets'");
    }

    @Test
    void anUnknownShardKindCarriesTheNodesUrn() {
        FileRead.Unreadable<RawGraph> bad =
                unreadable(HEADER + "nodes:\n- {urn: tactic:t, kind: widget}\nedges: []\n", Shape.SHARD, SHARD);

        SchemaViolation violation = only(bad);
        assertThat(violation.args()).containsEntry("urn", "tactic:t").doesNotContainKey("id");
        assertThat(details(bad)).containsExactly("qa/tactic.graph.yaml: node tactic:t has unknown kind 'widget'");
    }

    @Test
    void aKindThatDiffersFromTheUrnIsTheKeywordPatternWithTheKindsPrefixAsRegex() {
        FileRead.Unreadable<RawGraph> bad =
                unreadable(HEADER + "nodes:\n- {urn: tactic:t, kind: directive}\nedges: []\n", Shape.SHARD, SHARD);

        SchemaViolation violation = only(bad);
        assertThat(violation.keyword()).isEqualTo("pattern");
        assertThat(violation.pointer()).containsExactly("nodes", "0", "urn");
        assertThat(violation.args()).containsEntry("0", "^directive:");
        assertThat(details(bad)).containsExactly("qa/tactic.graph.yaml: node tactic:t declares kind 'directive'");
    }

    @Test
    void theNamedFactsOfOneNodeAreNotMixedUpWithAnotherNodesWhenSeveralFail() {
        FileRead.Unreadable<RawGraph> bad = unreadable(
                "nodes:\n- {id: a, kind: directives}\n- {id: b, kind: widgets}\n- {id: '', kind: tactics}\n",
                Shape.FRAGMENT,
                FRAGMENT);

        assertThat(details(bad))
                .containsExactly(
                        "qa/drg/fragment.yaml: node b has unknown kind 'widgets'",
                        "qa/drg/fragment.yaml: a node of kind 'tactics' has an empty id");
    }
}
