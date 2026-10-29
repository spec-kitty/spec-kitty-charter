package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.infra.storage.MeaningComparator.Difference;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The oracle of the whole-pack gate, by example. Every rule that makes two documents equal has an unequal twin, so no
 * rule can be made lenient without a test going red. The rules are ADR-0036's; the examples cite its worked examples (E1–E16) where one applies,
 * to be aligned with the #188 fidelity ADR.
 */
class MeaningComparatorTest {

    private static final String ARTEFACT = "tactic";
    private static final String SHARD = "graph-shard";

    private final MeaningComparator comparator = MeaningComparator.adr0036();

    private Optional<Difference> compare(String kind, String source, String written) {
        return comparator.compareTexts(kind, source, written);
    }

    @Nested
    class WhatDoesNotCount {

        @Test
        void keyOrder() {
            assertThat(compare(ARTEFACT, "a: 1\nb: 2\n", "b: 2\na: 1\n")).isEmpty();
        }

        @Test
        void quotingStyle() {
            assertThat(compare(ARTEFACT, "id: 'x'\n", "id: x\n")).isEmpty();
        }

        @Test
        void commentsAndBlankLines() {
            assertThat(compare(ARTEFACT, "# a comment\n\nid: x\n", "id: x\n")).isEmpty();
        }

        @Test
        void blockOrFlowList() {
            assertThat(compare(ARTEFACT, "l:\n- a\n- b\n", "l: [a, b]\n")).isEmpty();
        }

        @Test
        void anAbsentNullOrEmptyDeclaredFieldIsTheSame() {
            assertThat(compare(ARTEFACT, "id: x\nparent_pack: null\n", "id: x\n"))
                    .isEmpty(); // E2
            assertThat(compare(ARTEFACT, "id: x\nl: []\n", "id: x\n")).isEmpty();
            assertThat(compare(ARTEFACT, "id: x\nm: {}\n", "id: x\nm: null\n")).isEmpty();
            assertThat(compare(ARTEFACT, "steps:\n- title: t\n  description: null\n", "steps:\n- title: t\n"))
                    .isEmpty();
        }

        @Test
        void aDeclaredObjectOfEmptyFieldsIsTheSameAsAnAbsentOne() {
            assertThat(compare("agent-profile", "id: x\nctx:\n  frameworks: []\n", "id: x\n"))
                    .isEmpty();
            assertThat(compare("agent-profile", "id: x\nctx:\n  a: {b: null}\n", "id: x\nctx: {}\n"))
                    .isEmpty();
            // the twin: an open map's content is data, so an entry with an empty value is not nothing
            assertThat(compare("styleguide", "id: x\ntooling:\n  lint: []\n", "id: x\n"))
                    .isPresent();
            assertThat(compare("agent-profile", "id: x\nexcluding:\n  roles: []\n", "id: x\n"))
                    .isPresent();
            // and a declared object with one value is not empty
            assertThat(compare("agent-profile", "id: x\nctx:\n  frameworks: [spring]\n", "id: x\n"))
                    .isPresent();
        }

        @Test
        void numbersCompareByValue() {
            assertThat(compare(ARTEFACT, "impacts: 1\n", "impacts: 1.0\n")).isEmpty(); // E13
        }

        @Test
        void blockOrQuotedText() {
            assertThat(compare(ARTEFACT, "d: |\n  x\n  y\n", "d: \"x\\ny\\n\"\n"))
                    .isEmpty();
        }
    }

    @Nested
    class WhatCounts {

        @Test
        void aMissingKey() {
            assertThat(compare(ARTEFACT, "a: 1\nb: 2\n", "a: 1\n")).contains(new Difference("b", "2", "<absent>"));
        }

        @Test
        void anExtraKey() {
            assertThat(compare(ARTEFACT, "a: 1\n", "a: 1\nz: 3\n")).contains(new Difference("z", "<absent>", "3"));
        }

        @Test
        void aChangedScalar() {
            assertThat(compare(ARTEFACT, "a: 1\n", "a: 2\n")).contains(new Difference("a", "1", "2"));
        }

        @Test
        void aChangedType() {
            assertThat(compare(ARTEFACT, "v: '1.0'\n", "v: 1.0\n"))
                    .hasValueSatisfying(d -> assertThat(d.path()).isEqualTo("v"));
        }

        @Test
        void listOrder() {
            assertThat(compare(ARTEFACT, "l: [a, b]\n", "l: [b, a]\n")).contains(new Difference("l[0]", "a", "b"));
        }

        @Test
        void aShorterList() {
            assertThat(compare(ARTEFACT, "l: [a, b]\n", "l: [a]\n")).contains(new Difference("l[1]", "b", "<absent>"));
        }

        @Test
        void aNestedValueIsNamedByItsPath() {
            assertThat(compare(ARTEFACT, "steps:\n- title: t\n", "steps:\n- title: u\n"))
                    .contains(new Difference("steps[0].title", "t", "u"));
        }

        @Test
        void insideAnOpenMapNullAndEmptyAreData() {
            assertThat(compare("styleguide", "structural_lint_config:\n  rules: {}\n", "structural_lint_config: {}\n"))
                    .contains(new Difference("structural_lint_config.rules", "{}", "<absent>")); // E14
            assertThat(compare("styleguide", "tooling:\n  lint: null\n", "tooling: {}\n"))
                    .isPresent();
            assertThat(compare(
                            "styleguide",
                            "tooling:\n  lint: ruff\n  types: mypy\n",
                            "tooling: {types: mypy, lint: ruff}\n"))
                    .isEmpty(); // E12
        }

        @Test
        void anOpenMapsOwnKeyIsADeclaredField() {
            assertThat(compare("styleguide", "id: x\nstructural_lint_config: null\n", "id: x\n"))
                    .isEmpty();
            assertThat(compare("styleguide", "id: x\ntooling: {}\n", "id: x\n")).isEmpty();
        }

        @Test
        void aNumberKeyIsNotAStringKey() {
            assertThat(compare("styleguide", "tooling:\n  1: x\n", "tooling:\n  '1': x\n"))
                    .isPresent();
        }

        @Test
        void anEmptyValueIsStillAValue() {
            assertThat(compare(ARTEFACT, "l: [x]\n", "l: []\n")).contains(new Difference("l[0]", "x", "<absent>"));
            assertThat(compare(ARTEFACT, "l: [x, null]\n", "l: [x]\n")).isPresent(); // a null item is an item
        }

        @Test
        void aNumberIsNotAString() {
            assertThat(compare(ARTEFACT, "schema_version: '1.0'\n", "schema_version: 1.0\n"))
                    .isPresent(); // E3
        }

        @Test
        void aChangedLineEndingInBlockTextCounts() {
            assertThat(compare(ARTEFACT, "d: |\n  x\n", "d: |-\n  x\n")).contains(new Difference("d", "x\n", "x"));
        }

        @Test
        void everyDifferenceIsListedNotOnlyTheFirst() {
            assertThat(comparator.differencesOfTexts(ARTEFACT, "a: z\nb: 1\nc: [x, y]\n", "b: 2\nc: [x]\n"))
                    .containsExactly(
                            new Difference("a", "z", "<absent>"),
                            new Difference("b", "1", "2"),
                            new Difference("c[1]", "y", "<absent>"));
            assertThat(comparator.differencesOfTexts(ARTEFACT, "a: 1\n", "a: 1\n"))
                    .isEmpty();
        }

        @Test
        void theFirstDifferenceIsChosenInKeyOrder() {
            assertThat(compare(ARTEFACT, "b: 1\na: 1\n", "b: 2\na: 2\n")).contains(new Difference("a", "1", "2"));
        }
    }

    @Nested
    class GraphShards {

        private static final String HEADER = "schema_version: '1.0'\ngenerated_at: STATIC\n";
        private static final String NODES =
                "nodes:\n- urn: tactic:a\n  kind: tactic\n  label: A\n- urn: tactic:b\n  kind: tactic\n  label: B\n";
        private static final String EDGE_AB = "- source: tactic:a\n  target: tactic:b\n  relation: requires\n";
        private static final String EDGE_BA = "- source: tactic:b\n  target: tactic:a\n  relation: suggests\n";

        private static String shard(String nodes, String edges) {
            return HEADER + nodes + "edges:\n" + edges;
        }

        @Test
        void edgeOrderAndDuplicatesDoNotCount() {
            assertThat(compare(SHARD, shard(NODES, EDGE_AB + EDGE_BA), shard(NODES, EDGE_BA + EDGE_AB + EDGE_BA)))
                    .isEmpty();
        }

        @Test
        void aChangedRelationCountsAndNamesTheEdge() {
            String changed = EDGE_AB.replace("requires", "replaces");

            Optional<Difference> difference = compare(SHARD, shard(NODES, EDGE_AB), shard(NODES, changed));

            assertThat(difference).hasValueSatisfying(d -> {
                assertThat(d.path()).startsWith("edges{").contains("\"relation\"=\"requires\"");
                assertThat(d.written()).isEqualTo("<absent>");
            });
        }

        @Test
        void aRemovedEdgeCounts() {
            assertThat(compare(SHARD, shard(NODES, EDGE_AB + EDGE_BA), shard(NODES, EDGE_AB)))
                    .hasValueSatisfying(d -> assertThat(d.path()).contains("\"relation\"=\"suggests\""));
        }

        @Test
        void everyEdgeFieldCountsNotOnlyThePair() {
            String withWhen = EDGE_AB + "  when: always\n";

            assertThat(compare(SHARD, shard(NODES, EDGE_AB), shard(NODES, withWhen)))
                    .isPresent();
        }

        @Test
        void nodeOrderDoesNotCount() {
            String reordered =
                    "nodes:\n- urn: tactic:b\n  kind: tactic\n  label: B\n- urn: tactic:a\n  kind: tactic\n  label: A\n";

            assertThat(compare(SHARD, shard(NODES, EDGE_AB), shard(reordered, EDGE_AB)))
                    .isEmpty();
        }

        @Test
        void aChangedNodeLabelIsNamedByUrn() {
            String relabelled = NODES.replace("label: B", "label: Bee");

            assertThat(compare(SHARD, shard(NODES, EDGE_AB), shard(relabelled, EDGE_AB)))
                    .contains(new Difference("nodes{tactic:b}.label", "B", "Bee"));
        }

        @Test
        void otherRootKeysCompareNormally() {
            assertThat(compare(SHARD, shard(NODES, EDGE_AB), shard(NODES, EDGE_AB) + "generated_by: x\n"))
                    .contains(new Difference("generated_by", "<absent>", "x"));
        }

        @Test
        void aChangedEdgeFieldValueCounts() {
            String withWhen = EDGE_AB + "  when: always\n";

            assertThat(compare(SHARD, shard(NODES, withWhen), shard(NODES, withWhen.replace("always", "never"))))
                    .hasValueSatisfying(d -> assertThat(d.path()).contains("\"when\"=\"always\""));
        }

        @Test
        void anEmptyEdgeFieldIsTheSameAsAnAbsentOne() {
            assertThat(compare(SHARD, shard(NODES, EDGE_AB + "  when: null\n"), shard(NODES, EDGE_AB)))
                    .isEmpty(); // E16
        }

        @Test
        void anEdgeStrengthComparesByValue() {
            assertThat(compare(
                            SHARD,
                            shard(NODES, EDGE_AB + "  impacts: 1\n"),
                            shard(NODES, EDGE_AB + "  impacts: 1.0\n")))
                    .isEmpty();
        }

        @Test
        void anEdgeFieldKeepsItsType() {
            String typed = EDGE_AB + "  when: true\n";

            assertThat(compare(SHARD, shard(NODES, typed), shard(NODES, typed.replace("true", "'true'"))))
                    .isPresent();
        }

        @Test
        void aCommaInsideAFieldCannotMakeTwoEdgesAlike() {
            String oneField = "- source: tactic:a\n  target: tactic:b\n  relation: requires\n  reason: x,when=y\n";
            String twoFields = "- source: tactic:a\n  target: tactic:b\n  relation: requires\n  reason: x\n  when: y\n";

            assertThat(compare(SHARD, shard(NODES, oneField), shard(NODES, twoFields)))
                    .isPresent();
        }

        @Test
        void anAddedOrRemovedNodeCounts() {
            String third = NODES + "- urn: tactic:c\n  kind: tactic\n  label: C\n";

            assertThat(compare(SHARD, shard(NODES, EDGE_AB), shard(third, EDGE_AB)))
                    .contains(new Difference("nodes{tactic:c}", "<absent>", "{urn=tactic:c, kind=tactic, label=C}"));
            assertThat(compare(SHARD, shard(third, EDGE_AB), shard(NODES, EDGE_AB)))
                    .hasValueSatisfying(d -> assertThat(d.written()).isEqualTo("<absent>"));
        }

        @Test
        void nodeTagsCountAndKeepTheirOrder() {
            String tagged = NODES.replace("label: B\n", "label: B\n  tags: [x, y]\n");

            assertThat(compare(SHARD, shard(tagged, EDGE_AB), shard(NODES, EDGE_AB))) // E10
                    .contains(new Difference("nodes{tactic:b}.tags", "[x, y]", "<absent>"));
            assertThat(compare(SHARD, shard(tagged, EDGE_AB), shard(tagged.replace("[x, y]", "[y, x]"), EDGE_AB)))
                    .isPresent(); // E15: tags is a sequence
        }

        @Test
        void aUrnListedTwiceIsNeverCollapsed() {
            String twice = NODES + "- urn: tactic:b\n  kind: tactic\n  label: Other B\n";

            assertThat(compare(SHARD, shard(twice, EDGE_AB), shard(NODES, EDGE_AB)))
                    .contains(new Difference("nodes{tactic:b}", "2 node(s)", "1 node(s)"));
        }

        @Test
        void listOrderStillCountsOutsideAGraphShard() {
            assertThat(compare(ARTEFACT, "edges:\n" + EDGE_AB + EDGE_BA, "edges:\n" + EDGE_BA + EDGE_AB))
                    .isPresent();
        }

        @Test
        void duplicateSourceEdgesAreReportedAsInformation() {
            assertThat(comparator.duplicateEdges(SHARD, shard(NODES, EDGE_AB + EDGE_AB + EDGE_BA)))
                    .singleElement()
                    .asString()
                    .contains("\"relation\"=\"requires\"");
            assertThat(comparator.duplicateEdges(SHARD, shard(NODES, EDGE_AB + EDGE_BA)))
                    .isEmpty();
        }
    }

    /** #195, ADR-0042: a manifest's derived fields are verified against the bytes, not compared; the rest counts. */
    @Nested
    class TheManifest {

        private static final String KIND = MeaningComparator.MANIFEST;
        private static final String A = "a".repeat(64);
        private static final String B = "b".repeat(64);

        private static String manifest(String hash) {
            return """
                    schema_version: '1.0'
                    manifest_hash: %s
                    constituents:
                    - kind: tactic
                      id: t
                      path: tactics/t.tactic.yaml
                      content_hash: %s
                    - kind: directive
                      id: D
                      path: d.directive.yaml
                      content_hash: %s
                    presets:
                    - id: p
                      path: presets/p.yaml
                      content_hash: %s
                    """.formatted(hash, hash, hash, hash);
        }

        @Test
        void everyDerivedFieldIsSkipped() {
            assertThat(compare(KIND, manifest(A), manifest(B))).isEmpty();
            assertThat(compare(ARTEFACT, manifest(A), manifest(B)))
                    .as("another kind compares them")
                    .isPresent();
        }

        @Test
        void aChangedPathOrIdCounts() {
            assertThat(compare(KIND, manifest(A), manifest(B).replace("tactics/t.tactic.yaml", "t.tactic.yaml")))
                    .contains(new Difference("constituents[0].path", "tactics/t.tactic.yaml", "t.tactic.yaml"));
            assertThat(compare(KIND, manifest(A), manifest(B).replace("id: p\n", "id: q\n")))
                    .contains(new Difference("presets[0].id", "p", "q"));
        }

        @Test
        void aDroppedConstituentCounts() {
            String dropped = manifest(B)
                    .replace("- kind: directive\n  id: D\n  path: d.directive.yaml\n  content_hash: " + B + "\n", "");

            assertThat(compare(KIND, manifest(A), dropped)).isPresent();
        }

        @Test
        void aChangedSchemaVersionCounts() {
            assertThat(compare(KIND, manifest(A), manifest(A).replace("'1.0'", "'2.0'")))
                    .contains(new Difference("schema_version", "1.0", "2.0"));
        }
    }
}
