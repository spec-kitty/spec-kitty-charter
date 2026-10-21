package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** One test per row of the plan's detail vocabulary, with the exact text each produces. */
class PackPolicyVocabularyTest {

    private static final String SHARD = "tactic.graph.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";
    private static final String PACK_YAML = "pack.yaml";

    private static SchemaViolation violation(
            String file,
            List<String> pointer,
            String keyword,
            String property,
            String value,
            Map<String, String> args) {
        return new SchemaViolation(
                file, pointer, keyword, Optional.ofNullable(property), Optional.ofNullable(value), args);
    }

    private static SchemaViolation at(String file, String keyword, String... pointer) {
        return violation(file, List.of(pointer), keyword, null, null, Map.of());
    }

    /** A {@code type} violation with networknt's argument shape: {0} found, {1} expected. */
    private static SchemaViolation typeAt(String file, String found, String expected, String... pointer) {
        return violation(file, List.of(pointer), "type", null, null, Map.of("0", found, "1", expected));
    }

    private static List<String> details(SchemaViolation... violations) {
        return PackPolicy.schemaFindings("qa", List.of(violations)).stream()
                .map(PackFinding::detail)
                .toList();
    }

    private static void assertDetail(SchemaViolation violation, String expected) {
        assertThat(details(violation)).containsExactly(expected);
    }

    // ---- codes ---------------------------------------------------------------------------------

    @Test
    void theCodeFollowsTheFileKind() {
        assertThat(code(PACK_YAML)).isEqualTo(PackFindingCode.PACK_UNREADABLE);
        assertThat(code(SHARD)).isEqualTo(PackFindingCode.DRG_SCHEMA_ERROR);
        assertThat(code(FRAGMENT)).isEqualTo(PackFindingCode.DRG_SCHEMA_ERROR);
        assertThat(code("org-charter.yaml")).isEqualTo(PackFindingCode.ORG_CHARTER_SCHEMA_ERROR);
        assertThat(code("tactics/x.tactic.yaml")).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
    }

    private static PackFindingCode code(String file) {
        List<PackFinding> findings = PackPolicy.schemaFindings("qa", List.of(typeAt(file, "array", "object")));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).file()).isEqualTo(file);
        return findings.get(0).code();
    }

    // ---- parse failure -------------------------------------------------------------------------

    @Test
    void aParseFailureNamesTheOriginAndTheParsersFirstLine() {
        List<PackFinding> findings = PackPolicy.unreadable(
                "qa",
                new FileRead.Unreadable<>(SHARD, new ParseFailure(SHARD, "while parsing a block mapping"), List.of()));
        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.DRG_SCHEMA_ERROR,
                        SHARD,
                        List.of(),
                        "qa/tactic.graph.yaml is not valid YAML: while parsing a block mapping"));
    }

    @Test
    void aFragmentParseFailureIsADrgSchemaError() {
        assertThat(PackPolicy.unreadable(
                        "qa",
                        new FileRead.Unreadable<>(
                                FRAGMENT, new ParseFailure(FRAGMENT, "while scanning a quoted"), List.of())))
                .containsExactly(new PackFinding(
                        PackFindingCode.DRG_SCHEMA_ERROR,
                        FRAGMENT,
                        List.of(),
                        "qa/drg/fragment.yaml is not valid YAML: while scanning a quoted"));
    }

    @Test
    void anOrgCharterParseFailureIsAnOrgCharterSchemaError() {
        assertThat(PackPolicy.unreadable(
                        "qa",
                        new FileRead.Unreadable<>(
                                "org-charter.yaml", new ParseFailure("org-charter.yaml", "bad indent"), List.of())))
                .containsExactly(new PackFinding(
                        PackFindingCode.ORG_CHARTER_SCHEMA_ERROR,
                        "org-charter.yaml",
                        List.of(),
                        "qa/org-charter.yaml is not valid YAML: bad indent"));
    }

    @Test
    void anArtifactParseFailureIsAnArtifactSchemaError() {
        String artifact = "tactics/x.tactic.yaml";
        assertThat(PackPolicy.unreadable(
                        "qa",
                        new FileRead.Unreadable<>(artifact, new ParseFailure(artifact, "found a tab"), List.of())))
                .containsExactly(new PackFinding(
                        PackFindingCode.ARTIFACT_SCHEMA_ERROR,
                        artifact,
                        List.of(),
                        "qa/tactics/x.tactic.yaml is not valid YAML: found a tab"));
    }

    @Test
    void aDescriptorParseFailureHasNoParserLine() {
        List<PackFinding> findings = PackPolicy.unreadable(
                "qa", new FileRead.Unreadable<>(PACK_YAML, new ParseFailure(PACK_YAML, "while scanning"), List.of()));
        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.PACK_UNREADABLE, PACK_YAML, List.of(), "pack.yaml of qa is not valid YAML"));
    }

    @Test
    void unreadableCarryingViolationsGivesThemTheirSchemaTexts() {
        List<PackFinding> findings = PackPolicy.unreadable(
                "qa",
                new FileRead.Unreadable<>(
                        SHARD,
                        null,
                        List.of(violation(SHARD, List.of(), "additionalProperties", "surprise", null, Map.of()))));
        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.DRG_SCHEMA_ERROR,
                        SHARD,
                        List.of(),
                        "qa/tactic.graph.yaml: unknown key 'surprise'"));
    }

    // ---- type ----------------------------------------------------------------------------------

    @Test
    void typeObjectAtTheRootExpectsAMapping() {
        assertDetail(typeAt(SHARD, "array", "object"), "qa/tactic.graph.yaml: expected a mapping");
    }

    @Test
    void typeObjectAtANodeOrEdgeItemExpectsAMapping() {
        assertDetail(typeAt(SHARD, "string", "object", "nodes", "3"), "qa/tactic.graph.yaml node: expected a mapping");
        assertDetail(typeAt(SHARD, "string", "object", "edges", "0"), "qa/tactic.graph.yaml edge: expected a mapping");
    }

    @Test
    void typeArrayExpectsAList() {
        assertDetail(typeAt(SHARD, "object", "array", "nodes"), "qa/tactic.graph.yaml nodes: expected a list");
    }

    @Test
    void typeStringMustBeAString() {
        assertDetail(
                typeAt(SHARD, "integer", "string", "nodes", "0", "label"),
                "qa/tactic.graph.yaml: 'label' must be a string");
    }

    @Test
    void typeNumberIntegerAndBooleanHaveTheirOwnTexts() {
        assertDetail(
                typeAt(SHARD, "string", "number", "edges", "0", "impacts"),
                "qa/tactic.graph.yaml: 'impacts' must be a number");
        assertDetail(
                typeAt(SHARD, "string", "integer", "edges", "0", "n"), "qa/tactic.graph.yaml: 'n' must be an integer");
        assertDetail(
                typeAt(SHARD, "string", "boolean", "edges", "0", "b"), "qa/tactic.graph.yaml: 'b' must be a boolean");
    }

    @Test
    void aTypeViolationWithoutTheExpectedTypeUsesTheFallback() {
        assertDetail(
                at(SHARD, "type", "nodes", "0", "label"),
                "qa/tactic.graph.yaml: '/nodes/0/label' breaks schema keyword 'type'");
        assertDetail(
                violation(SHARD, List.of(), "type", null, null, Map.of("0", "array")),
                "qa/tactic.graph.yaml: '/' breaks schema keyword 'type'");
    }

    @Test
    void anAdditionalPropertiesOrRequiredViolationWithoutAPropertyUsesTheFallback() {
        assertDetail(
                at(SHARD, "additionalProperties", "nodes", "0"),
                "qa/tactic.graph.yaml: '/nodes/0' breaks schema keyword 'additionalProperties'");
        assertDetail(
                at(SHARD, "required", "nodes", "0"),
                "qa/tactic.graph.yaml: '/nodes/0' breaks schema keyword 'required'");
    }

    @Test
    void aDescriptorSaysItIsNotAMappingAndNamesTheFieldWithoutQuotes() {
        assertDetail(typeAt(PACK_YAML, "array", "object"), "pack.yaml of qa is not a mapping");
        assertDetail(typeAt(PACK_YAML, "integer", "string", "name"), "pack.yaml of qa: name must be a string");
    }

    // ---- additionalProperties and required -----------------------------------------------------

    @Test
    void anUnknownKeyIsNamedAtTheRootAndAtANodeOrEdge() {
        assertDetail(
                violation(SHARD, List.of(), "additionalProperties", "surprise", null, Map.of()),
                "qa/tactic.graph.yaml: unknown key 'surprise'");
        assertDetail(
                violation(SHARD, List.of("nodes", "0"), "additionalProperties", "extra", null, Map.of()),
                "qa/tactic.graph.yaml node: unknown key 'extra'");
        assertDetail(
                violation(SHARD, List.of("edges", "2"), "additionalProperties", "extra", null, Map.of()),
                "qa/tactic.graph.yaml edge: unknown key 'extra'");
    }

    @Test
    void aMissingKeyIsNamed() {
        assertDetail(
                violation(SHARD, List.of("nodes", "1"), "required", "kind", null, Map.of()),
                "qa/tactic.graph.yaml: missing 'kind'");
    }

    @Test
    void aMissingDescriptorKeyHasTheDescriptorStyle() {
        assertDetail(
                violation(PACK_YAML, List.of(), "required", "pack_version", null, Map.of()),
                "pack.yaml of qa has no pack_version");
    }

    // ---- minLength and pattern -----------------------------------------------------------------

    @Test
    void anEmptyFragmentNodeIdNamesItsKind() {
        assertDetail(
                violation(
                        FRAGMENT,
                        List.of("nodes", "0", "id"),
                        "minLength",
                        null,
                        "",
                        Map.of("0", "1", "kind", "directives")),
                "qa/drg/fragment.yaml: a node of kind 'directives' has an empty id");
    }

    @Test
    void anEmptyDescriptorStringNamesItsKey() {
        assertDetail(
                violation(PACK_YAML, List.of("pack_version"), "minLength", null, "", Map.of("0", "1")),
                "pack.yaml of qa: pack_version must not be empty");
    }

    @Test
    void aDescriptorMinLengthOffTheRootUsesTheFallback() {
        assertDetail(
                violation(PACK_YAML, List.of("extra", "0"), "minLength", null, "", Map.of("0", "1")),
                "pack.yaml of qa: '/extra/0' breaks schema keyword 'minLength'");
    }

    @Test
    void theIdPatternOnAnEmptyIdGivesTheSameTextOnceNotTwice() {
        SchemaViolation minLength = violation(
                FRAGMENT, List.of("nodes", "0", "id"), "minLength", null, "", Map.of("0", "1", "kind", "directives"));
        SchemaViolation pattern = violation(
                FRAGMENT,
                List.of("nodes", "0", "id"),
                "pattern",
                null,
                "",
                Map.of("0", "^[A-Za-z0-9_/.-]+$", "kind", "directives"));
        assertThat(details(minLength, pattern))
                .containsExactly("qa/drg/fragment.yaml: a node of kind 'directives' has an empty id");
    }

    @Test
    void aFragmentIdWithUnsafeCharactersIsNamed() {
        assertDetail(
                violation(
                        FRAGMENT,
                        List.of("nodes", "0", "id"),
                        "pattern",
                        null,
                        "a b",
                        Map.of("0", "^[A-Za-z0-9_/.-]+$", "kind", "tactics")),
                "qa/drg/fragment.yaml: node id 'a b' is not <kind>:<id>-safe");
    }

    @Test
    void aFragmentNodeIdViolationWithoutTheKindUsesTheFallback() {
        assertDetail(
                violation(FRAGMENT, List.of("nodes", "0", "id"), "minLength", null, "", Map.of("0", "1")),
                "qa/drg/fragment.yaml: '/nodes/0/id' breaks schema keyword 'minLength'");
        assertDetail(
                violation(
                        FRAGMENT, List.of("nodes", "0", "id"), "pattern", null, "", Map.of("0", "^[A-Za-z0-9_/.-]+$")),
                "qa/drg/fragment.yaml: '/nodes/0/id' breaks schema keyword 'pattern'");
        assertDetail(
                violation(
                        FRAGMENT,
                        List.of("nodes", "0", "id"),
                        "pattern",
                        null,
                        "a b",
                        Map.of("0", "^[A-Za-z0-9_/.-]+$")),
                "qa/drg/fragment.yaml: '/nodes/0/id' breaks schema keyword 'pattern'");
    }

    @Test
    void aShardNodeUrnThatIsNotAUrnIsNamed() {
        assertDetail(
                violation(
                        SHARD,
                        List.of("nodes", "0", "urn"),
                        "pattern",
                        null,
                        "nonsense",
                        Map.of("0", "^(directive|tactic|styleguide):[A-Za-z0-9_/.-]+$")),
                "qa/tactic.graph.yaml: node urn 'nonsense' is not <kind>:<id>");
    }

    @Test
    void aShardNodeWhoseUrnPrefixContradictsItsKindNamesTheUrnAndTheKind() {
        assertDetail(
                violation(SHARD, List.of("nodes", "0", "urn"), "pattern", null, "directive:a", Map.of("0", "^tactic:")),
                "qa/tactic.graph.yaml: node directive:a declares kind 'tactic'");
    }

    @Test
    void anEdgeEndpointThatIsNotAUrnIsRefusedUnderR6() {
        assertDetail(
                violation(SHARD, List.of("edges", "0", "target"), "pattern", null, "QA_404", Map.of()),
                "qa/tactic.graph.yaml: edge endpoint 'QA_404' is not a URN");
        assertDetail(
                violation(FRAGMENT, List.of("edges", "4", "source"), "pattern", null, "QA_404", Map.of()),
                "qa/drg/fragment.yaml: edge endpoint 'QA_404' is not a URN");
    }

    @Test
    void anyOtherPatternSaysTheKeyDoesNotMatch() {
        assertDetail(
                violation(SHARD, List.of("schema_version"), "pattern", null, "x", Map.of()),
                "qa/tactic.graph.yaml: 'schema_version' does not match its pattern");
    }

    // ---- enum and const ------------------------------------------------------------------------

    @Test
    void anUnknownRelationIsNamed() {
        assertDetail(
                violation(SHARD, List.of("edges", "0", "relation"), "enum", null, "befriends", Map.of()),
                "qa/tactic.graph.yaml: unknown relation 'befriends'");
    }

    @Test
    void anUnknownNodeKindNamesTheNode() {
        assertDetail(
                violation(FRAGMENT, List.of("nodes", "0", "kind"), "enum", null, "tacticz", Map.of("id", "t1")),
                "qa/drg/fragment.yaml: node t1 has unknown kind 'tacticz'");
    }

    @Test
    void anUnknownNodeKindWithoutTheNodeIdentityUsesTheFallback() {
        assertDetail(
                violation(FRAGMENT, List.of("nodes", "0", "kind"), "enum", null, "tacticz", Map.of("0", "[tactics]")),
                "qa/drg/fragment.yaml: '/nodes/0/kind' breaks schema keyword 'enum'");
    }

    @Test
    void anUnknownShardNodeKindNamesTheNodeByItsUrn() {
        assertDetail(
                violation(SHARD, List.of("nodes", "1", "kind"), "enum", null, "tacticz", Map.of("urn", "tactic:a")),
                "qa/tactic.graph.yaml: node tactic:a has unknown kind 'tacticz'");
    }

    @Test
    void anUnknownPackLevelIsNamedInTheDescriptorStyle() {
        assertDetail(
                violation(PACK_YAML, List.of("pack_level"), "enum", null, "galaxy", Map.of()),
                "pack.yaml of qa declares an unknown pack_level 'galaxy'");
    }

    @Test
    void anyOtherEnumOrConstIsAnUnsupportedValue() {
        assertDetail(
                violation(SHARD, List.of("schema_version"), "enum", null, "9", Map.of()),
                "qa/tactic.graph.yaml: 'schema_version' has an unsupported value '9'");
        assertDetail(
                violation(SHARD, List.of("schema_version"), "const", null, "9", Map.of()),
                "qa/tactic.graph.yaml: 'schema_version' has an unsupported value '9'");
    }

    // ---- ranges and sizes ----------------------------------------------------------------------

    @Test
    void minItemsNamesTheLeastCount() {
        assertDetail(
                violation(SHARD, List.of("nodes"), "minItems", null, null, Map.of("0", "1", "1", "0")),
                "qa/tactic.graph.yaml: 'nodes' needs at least 1 item(s)");
    }

    @Test
    void minItemsWithoutTheMinimumUsesTheFallback() {
        assertDetail(
                violation(SHARD, List.of("nodes"), "minItems", null, null, Map.of()),
                "qa/tactic.graph.yaml: '/nodes' breaks schema keyword 'minItems'");
    }

    @Test
    void theRangeKeywordsSayTheValueIsOutOfRange() {
        for (String keyword : List.of("minimum", "maximum", "exclusiveMinimum")) {
            assertDetail(
                    violation(SHARD, List.of("layer_index"), keyword, null, "-3", Map.of()),
                    "qa/tactic.graph.yaml: 'layer_index' -3 is out of range");
        }
    }

    // ---- anyOf ---------------------------------------------------------------------------------

    @Test
    void aNullableAnyOfCollapsesToTheNonNullBranch() {
        List<String> pointer = List.of("nodes", "0", "label");
        List<String> collapsed = details(
                violation(SHARD, pointer, "anyOf", null, "7", Map.of()),
                violation(SHARD, pointer, "type", null, "7", Map.of("0", "integer", "1", "string")),
                violation(SHARD, pointer, "type", null, "7", Map.of("0", "integer", "1", "null")));
        assertThat(collapsed).containsExactly("qa/tactic.graph.yaml: 'label' must be a string");
    }

    @Test
    void theRealNullableShapeWithNoAnyOfErrorCollapsesToTheNonNullBranch() {
        assertThat(details(
                        typeAt(SHARD, "integer", "string", "nodes", "0", "label"),
                        typeAt(SHARD, "integer", "null", "nodes", "0", "label")))
                .containsExactly("qa/tactic.graph.yaml: 'label' must be a string");
        assertThat(details(
                        typeAt(SHARD, "integer", "null", "nodes", "0", "label"),
                        typeAt(SHARD, "integer", "string", "nodes", "0", "label")))
                .containsExactly("qa/tactic.graph.yaml: 'label' must be a string");
    }

    @Test
    void theDescriptorRealNullableShapeCollapsesToo() {
        assertThat(details(
                        typeAt(PACK_YAML, "integer", "string", "requires_core"),
                        typeAt(PACK_YAML, "integer", "null", "requires_core")))
                .containsExactly("pack.yaml of qa: requires_core must be a string");
    }

    @Test
    void aLoneNullExpectingTypeViolationIsKeptAndGetsTheFallback() {
        assertThat(details(typeAt(SHARD, "integer", "null", "nodes", "0", "label")))
                .containsExactly("qa/tactic.graph.yaml: '/nodes/0/label' breaks schema keyword 'type'");
    }

    @Test
    void typeViolationsAtDifferentPointersAreNotCollapsed() {
        assertThat(details(
                        typeAt(SHARD, "integer", "string", "nodes", "0", "label"),
                        typeAt(SHARD, "integer", "null", "nodes", "1", "label")))
                .containsExactly(
                        "qa/tactic.graph.yaml: 'label' must be a string",
                        "qa/tactic.graph.yaml: '/nodes/1/label' breaks schema keyword 'type'");
    }

    @Test
    void twoNonNullTypeViolationsAtOnePointerAreBothKept() {
        // Only a branch that expects null is dropped, and only beside one that does not: a oneOf of a string and an
        // integer that the value matches neither of says both.
        assertThat(details(
                        typeAt(SHARD, "boolean", "string", "nodes", "0", "x"),
                        typeAt(SHARD, "boolean", "integer", "nodes", "0", "x")))
                .containsExactly(
                        "qa/tactic.graph.yaml: 'x' must be a string", "qa/tactic.graph.yaml: 'x' must be an integer");
    }

    @Test
    void aKeyWithASlashIsNotTheSamePointerAsTwoKeys() {
        // The pointer ["a/b"] and ["a", "b"] are two places; joined with "/" they were one, and the string branch at
        // the first took the lone null branch at the second with it.
        assertThat(details(typeAt(SHARD, "integer", "null", "a", "b"), typeAt(SHARD, "integer", "string", "a/b")))
                .containsExactly(
                        "qa/tactic.graph.yaml: '/a/b' breaks schema keyword 'type'",
                        "qa/tactic.graph.yaml: 'a/b' must be a string");
    }

    @Test
    void anAnyOfWithoutNestedTypeViolationsUsesTheFallback() {
        assertDetail(
                violation(SHARD, List.of("nodes", "0", "x"), "anyOf", null, null, Map.of()),
                "qa/tactic.graph.yaml: '/nodes/0/x' breaks schema keyword 'anyOf'");
    }

    // ---- fallback ------------------------------------------------------------------------------

    @Test
    void anyOtherKeywordUsesTheFixedLocaleFreeFallback() {
        assertDetail(
                violation(SHARD, List.of("nodes", "0", "tags"), "uniqueItems", null, null, Map.of()),
                "qa/tactic.graph.yaml: '/nodes/0/tags' breaks schema keyword 'uniqueItems'");
        assertDetail(
                at(SHARD, "dependentRequired"), "qa/tactic.graph.yaml: '/' breaks schema keyword 'dependentRequired'");
    }

    @Test
    void aDescriptorKeywordWithoutARowUsesTheDescriptorOriginInTheFallback() {
        assertDetail(
                violation(PACK_YAML, List.of("pack_id"), "maxLength", null, "", Map.of()),
                "pack.yaml of qa: '/pack_id' breaks schema keyword 'maxLength'");
    }

    @Test
    void theTextsDoNotDependOnTheDefaultLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertDetail(
                    violation(SHARD, List.of("nodes", "0", "tags"), "uniqueItems", null, null, Map.of()),
                    "qa/tactic.graph.yaml: '/nodes/0/tags' breaks schema keyword 'uniqueItems'");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    // ---- ordering inside a place ---------------------------------------------------------------

    @Test
    void atOnePlaceTypeComesFirstThenUnknownKeysThenMissingKeysThenTheRest() {
        List<String> ordered = details(
                violation(SHARD, List.of(), "uniqueItems", null, null, Map.of()),
                violation(SHARD, List.of(), "required", "nodes", null, Map.of()),
                violation(SHARD, List.of(), "additionalProperties", "surprise", null, Map.of()));
        assertThat(ordered)
                .containsExactly(
                        "qa/tactic.graph.yaml: unknown key 'surprise'",
                        "qa/tactic.graph.yaml: missing 'nodes'",
                        "qa/tactic.graph.yaml: '/' breaks schema keyword 'uniqueItems'");
        assertThat(details(
                        violation(SHARD, List.of(), "required", "nodes", null, Map.of()),
                        typeAt(SHARD, "array", "object")))
                .containsExactly("qa/tactic.graph.yaml: expected a mapping", "qa/tactic.graph.yaml: missing 'nodes'");
    }
}
