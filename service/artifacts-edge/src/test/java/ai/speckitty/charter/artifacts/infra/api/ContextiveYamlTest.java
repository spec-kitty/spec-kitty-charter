package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.artifacts.application.ContextiveProjection.Context;
import ai.speckitty.charter.artifacts.application.ContextiveProjection.Document;
import ai.speckitty.charter.artifacts.application.ContextiveProjection.Meta;
import ai.speckitty.charter.artifacts.application.ContextiveProjection.Term;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The YAML text of a Contextive document (ADR-0076 decision 6): pinned byte for byte, in block style with the keys in
 * the decision's order and an absent member omitted, and read back as the same tree. A long definition is not folded,
 * and a value YAML would read as another type or that holds a colon is quoted, so it reads back as written.
 */
class ContextiveYamlTest {

    private static final Term FLAKY = new Term(
            "flaky test",
            "A test that passes and fails on the same code.",
            Optional.empty(),
            Optional.empty(),
            new Meta("term:qa/flaky-test", "active"));
    private static final Term LANE = new Term(
            "lane",
            "The old name of a work package's worktree.",
            Optional.empty(),
            Optional.empty(),
            new Meta("term:spec-kitty-core/lane", "deprecated"));
    private static final Term MISSION = new Term(
            "mission",
            "A unit of planned work that runs from a specification to a merge.",
            Optional.of(List.of("Each mission has one specification.")),
            Optional.of(List.of("feature")),
            new Meta("term:spec-kitty-core/mission", "active"));

    /** The document of the contract's example: two contexts, one with paths, and terms with and without the lists. */
    private static final Document TWO_CONTEXTS = new Document(List.of(
            new Context(
                    "Quality assurance",
                    "The vocabulary of testing and its reports.",
                    Optional.empty(),
                    List.of(FLAKY)),
            new Context(
                    "Spec Kitty core",
                    "Canonical Spec Kitty terminology, as this project uses it.",
                    Optional.of(List.of("kitty-specs/**", ".kittify/**")),
                    List.of(LANE, MISSION))));

    private static final String TWO_CONTEXTS_YAML = """
            contexts:
            - name: Quality assurance
              domainVisionStatement: The vocabulary of testing and its reports.
              terms:
              - name: flaky test
                definition: A test that passes and fails on the same code.
                meta:
                  urn: term:qa/flaky-test
                  status: active
            - name: Spec Kitty core
              domainVisionStatement: Canonical Spec Kitty terminology, as this project uses it.
              paths:
              - kitty-specs/**
              - .kittify/**
              terms:
              - name: lane
                definition: The old name of a work package's worktree.
                meta:
                  urn: term:spec-kitty-core/lane
                  status: deprecated
              - name: mission
                definition: A unit of planned work that runs from a specification to a merge.
                examples:
                - Each mission has one specification.
                aliases:
                - feature
                meta:
                  urn: term:spec-kitty-core/mission
                  status: active
            """;

    private static Object load(String yaml) {
        return new Load(LoadSettings.builder().build()).loadFromString(yaml);
    }

    @Test
    void theDocumentIsPinnedByteForByteInBlockStyleWithTheKeysInTheDecisionsOrder() {
        assertThat(ContextiveYaml.render(TWO_CONTEXTS)).isEqualTo(TWO_CONTEXTS_YAML);
    }

    @Test
    void oneDocumentAlwaysRendersTheSameText() {
        assertThat(ContextiveYaml.render(TWO_CONTEXTS)).isEqualTo(ContextiveYaml.render(TWO_CONTEXTS));
    }

    @Test
    void theTextReadsBackAsTheTreeWithEachAbsentMemberLeftOut() {
        Object tree = load(ContextiveYaml.render(TWO_CONTEXTS));

        assertThat(tree)
                .isEqualTo(Map.of(
                        "contexts",
                        List.of(
                                Map.of(
                                        "name", "Quality assurance",
                                        "domainVisionStatement", "The vocabulary of testing and its reports.",
                                        "terms",
                                                List.of(Map.of(
                                                        "name", "flaky test",
                                                        "definition", "A test that passes and fails on the same code.",
                                                        "meta",
                                                                Map.of(
                                                                        "urn", "term:qa/flaky-test",
                                                                        "status", "active")))),
                                Map.of(
                                        "name",
                                        "Spec Kitty core",
                                        "domainVisionStatement",
                                        "Canonical Spec Kitty terminology, as this project uses it.",
                                        "paths",
                                        List.of("kitty-specs/**", ".kittify/**"),
                                        "terms",
                                        List.of(
                                                Map.of(
                                                        "name", "lane",
                                                        "definition", "The old name of a work package's worktree.",
                                                        "meta",
                                                                Map.of(
                                                                        "urn", "term:spec-kitty-core/lane",
                                                                        "status", "deprecated")),
                                                Map.of(
                                                        "name", "mission",
                                                        "definition",
                                                                "A unit of planned work that runs from a"
                                                                        + " specification to a merge.",
                                                        "examples", List.of("Each mission has one specification."),
                                                        "aliases", List.of("feature"),
                                                        "meta",
                                                                Map.of(
                                                                        "urn", "term:spec-kitty-core/mission",
                                                                        "status", "active")))))));
    }

    @Test
    void aValueYamlWouldReadAsAnotherTypeOrThatHoldsAColonReadsBackAsWritten() {
        String definition = "Means: whatever the gate says, " + "x".repeat(200) + ".";
        Document document = new Document(List.of(new Context(
                "yes",
                "1.0",
                Optional.empty(),
                List.of(new Term(
                        "null",
                        definition,
                        Optional.of(List.of("- a dash", "# a hash")),
                        Optional.empty(),
                        new Meta("term:core/null", "draft"))))));

        String yaml = ContextiveYaml.render(document);

        assertThat(yaml).as("a long definition is one line").contains(definition.substring(0, 40));
        assertThat(yaml.lines().filter(line -> line.contains("xxxx"))).hasSize(1);
        assertThat(load(yaml))
                .isEqualTo(Map.of(
                        "contexts",
                        List.of(Map.of(
                                "name", "yes",
                                "domainVisionStatement", "1.0",
                                "terms",
                                        List.of(Map.of(
                                                "name",
                                                "null",
                                                "definition",
                                                definition,
                                                "examples",
                                                List.of("- a dash", "# a hash"),
                                                "meta",
                                                Map.of("urn", "term:core/null", "status", "draft")))))));
    }

    @Test
    void noContextInForceIsAnEmptyContextsList() {
        String yaml = ContextiveYaml.render(new Document(List.of()));

        assertThat(yaml).isEqualTo("contexts: []\n");
        assertThat(load(yaml)).isEqualTo(Map.of("contexts", List.of()));
    }
}
