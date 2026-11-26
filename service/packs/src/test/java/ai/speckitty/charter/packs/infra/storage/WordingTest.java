package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.rules.Findings;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** One test per row of the plan's detail vocabulary, with the exact text each produces. */
class WordingTest {

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
        return Wording.schemaFindings("qa", List.of(violations)).stream()
                .map(Reason::detail)
                .toList();
    }

    private static void assertDetail(SchemaViolation violation, String expected) {
        assertThat(details(violation)).containsExactly(expected);
    }

    // ---- codes ---------------------------------------------------------------------------------

    @Test
    void theCodeFollowsTheFileKind() {
        assertThat(code(PACK_YAML)).isEqualTo(StorageCodes.PACK_UNREADABLE);
        assertThat(code(SHARD)).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
        assertThat(code(FRAGMENT)).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
        assertThat(code("tactics/x.tactic.yaml")).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
    }

    private static ReasonCode code(String file) {
        List<Reason> findings = Wording.schemaFindings("qa", List.of(typeAt(file, "array", "object")));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).location().file()).isEqualTo(file);
        return findings.get(0).code();
    }

    // ---- parse failure -------------------------------------------------------------------------

    @Test
    void aParseFailureNamesTheOriginAndTheParsersFirstLine() {
        List<Reason> findings = Wording.unreadable(
                "qa",
                new FileRead.Unreadable<>(SHARD, new ParseFailure(SHARD, "while parsing a block mapping"), List.of()));
        assertThat(findings)
                .containsExactly(Findings.at(
                        PackCodes.DRG_SCHEMA_ERROR,
                        SHARD,
                        List.of(),
                        "tactic.graph.yaml: while parsing a block mapping"));
    }

    @Test
    void aFragmentParseFailureIsADrgSchemaError() {
        assertThat(Wording.unreadable(
                        "qa",
                        new FileRead.Unreadable<>(
                                FRAGMENT, new ParseFailure(FRAGMENT, "while scanning a quoted"), List.of())))
                .containsExactly(Findings.at(
                        PackCodes.DRG_SCHEMA_ERROR, FRAGMENT, List.of(), "drg/fragment.yaml: while scanning a quoted"));
    }

    @Test
    void aRetiredOrgCharterIsRetiredLikeEveryRetiredFile() {
        assertThat(Wording.unreadable(
                        "qa",
                        new FileRead.Unreadable<>(
                                "org-charter.yaml",
                                ParseFailure.refused(
                                        "org-charter.yaml", "org enforcement is a preset (#431)", StorageCodes.RETIRED),
                                List.of())))
                .containsExactly(Findings.at(
                        StorageCodes.RETIRED,
                        "org-charter.yaml",
                        List.of(),
                        "org-charter.yaml: org enforcement is a preset (#431)"));
    }

    @Test
    void aRefusalWithNoCodeTakesTheCodeOfItsFilesKind() {
        String artifact = "tactics/x.tactic.yaml";
        ParseFailure refused = new ParseFailure(
                artifact, "names x.md: missing", Optional.of("names x.md: missing"), Optional.empty(), List.of());
        assertThat(Wording.unreadable("qa", new FileRead.Unreadable<>(artifact, refused, List.of())))
                .containsExactly(Findings.at(
                        PackCodes.ARTIFACT_SCHEMA_ERROR, artifact, List.of(), artifact + ": names x.md: missing"));
    }

    @Test
    void anArtifactParseFailureIsUnreadable() {
        String artifact = "tactics/x.tactic.yaml";
        assertThat(Wording.unreadable(
                        "qa",
                        new FileRead.Unreadable<>(artifact, new ParseFailure(artifact, "found a tab"), List.of())))
                .containsExactly(Findings.at(
                        StorageCodes.UNREADABLE, artifact, List.of(), "tactics/x.tactic.yaml: found a tab"));
    }

    @Test
    void aDescriptorParseFailureNamesTheParsersLine() {
        List<Reason> findings = Wording.unreadable(
                "qa", new FileRead.Unreadable<>(PACK_YAML, new ParseFailure(PACK_YAML, "while scanning"), List.of()));
        assertThat(findings)
                .containsExactly(Findings.at(
                        StorageCodes.PACK_UNREADABLE,
                        PACK_YAML,
                        List.of(),
                        "pack.yaml of qa is not valid YAML: while scanning"));
    }

    @Test
    void unreadableCarryingViolationsGivesThemTheirSchemaTexts() {
        List<Reason> findings = Wording.unreadable(
                "qa",
                new FileRead.Unreadable<>(
                        SHARD,
                        null,
                        List.of(violation(SHARD, List.of(), "additionalProperties", "surprise", null, Map.of()))));
        assertThat(findings)
                .containsExactly(Findings.at(
                        PackCodes.DRG_SCHEMA_ERROR, SHARD, List.of(), "tactic.graph.yaml: unknown key 'surprise'"));
    }

    // ---- type ----------------------------------------------------------------------------------

    @Test
    void typeObjectAtTheRootExpectsAMapping() {
        assertDetail(typeAt(SHARD, "array", "object"), "tactic.graph.yaml: expected a mapping");
    }

    @Test
    void typeObjectAtANodeOrEdgeItemExpectsAMapping() {
        assertDetail(typeAt(SHARD, "string", "object", "nodes", "3"), "tactic.graph.yaml node: expected a mapping");
        assertDetail(typeAt(SHARD, "string", "object", "edges", "0"), "tactic.graph.yaml edge: expected a mapping");
    }

    @Test
    void typeArrayExpectsAList() {
        assertDetail(typeAt(SHARD, "object", "array", "nodes"), "tactic.graph.yaml nodes: expected a list");
    }

    @Test
    void typeStringMustBeAString() {
        assertDetail(
                typeAt(SHARD, "integer", "string", "nodes", "0", "label"),
                "tactic.graph.yaml: 'label' must be a string");
    }

    @Test
    void typeNumberIntegerAndBooleanHaveTheirOwnTexts() {
        assertDetail(
                typeAt(SHARD, "string", "number", "edges", "0", "impacts"),
                "tactic.graph.yaml: 'impacts' must be a number");
        assertDetail(
                typeAt(SHARD, "string", "integer", "edges", "0", "n"), "tactic.graph.yaml: 'n' must be an integer");
        assertDetail(typeAt(SHARD, "string", "boolean", "edges", "0", "b"), "tactic.graph.yaml: 'b' must be a boolean");
    }

    /** A {@code type} violation of a quoted value: the string {@code value} found where {@code expected} is declared. */
    private static SchemaViolation quotedAt(String file, String value, String expected, String... pointer) {
        return violation(file, List.of(pointer), "type", null, value, Map.of("0", "string", "1", expected));
    }

    @Test
    void aQuotedNumberInANumberFieldSaysHowToFixIt() {
        assertDetail(
                quotedAt("routing_catalogs/c.routing-catalog.yaml", "15", "number", "models", "0", "cost", "price"),
                "routing_catalogs/c.routing-catalog.yaml: 'price': \"15\" must be a number: write 15 without quotes,"
                        + " or run pack-doctor remediate");
        assertDetail(
                quotedAt(SHARD, "0.6", "number", "edges", "0", "impacts"),
                "tactic.graph.yaml: 'impacts': \"0.6\" must be a number: write 0.6 without quotes, or run pack-doctor"
                        + " remediate");
        assertDetail(
                quotedAt(SHARD, "1e-3", "number", "edges", "0", "impacts"),
                "tactic.graph.yaml: 'impacts': \"1e-3\" must be a number: write 1e-3 without quotes, or run"
                        + " pack-doctor remediate");
    }

    @Test
    void aQuotedIntegerInAnIntegerFieldSaysHowToFixIt() {
        assertDetail(
                quotedAt(SHARD, "50", "integer", "edges", "0", "n"),
                "tactic.graph.yaml: 'n': \"50\" must be an integer: write 50 without quotes, or run pack-doctor"
                        + " remediate");
    }

    @Test
    void aQuotedValueThatIsNotThatNumberKeepsThePlainText() {
        // Not a number at all, or not one the reader reads plain as a finite number of the field's type.
        for (String text : List.of("fifteen", "", " 15", "+1", ".5", "1_000", "0x1F", "015", "1e400", ".inf", ".nan")) {
            assertDetail(quotedAt(SHARD, text, "number", "edges", "0", "x"), "tactic.graph.yaml: 'x' must be a number");
        }
        // An integer field takes integers only.
        for (String text : List.of("1.5", "15.0", "1e3")) {
            assertDetail(
                    quotedAt(SHARD, text, "integer", "edges", "0", "n"), "tactic.graph.yaml: 'n' must be an integer");
        }
        // A number of the wrong kind is no quoted value.
        assertDetail(
                violation(
                        SHARD, List.of("edges", "0", "n"), "type", null, "2.5", Map.of("0", "number", "1", "integer")),
                "tactic.graph.yaml: 'n' must be an integer");
    }

    @Test
    void aQuotedNumberOutsideAYamlFileKeepsThePlainText() {
        // Remediate repairs YAML files only; a Markdown front matter's fault names no repair it cannot make.
        assertDetail(
                quotedAt("missions/m/steps/s.prompt.md", "15", "number", "frontmatter", "x"),
                "missions/m/steps/s.prompt.md: 'x' must be a number");
    }

    @Test
    void aTypeViolationWithoutTheExpectedTypeUsesTheFallback() {
        assertDetail(
                at(SHARD, "type", "nodes", "0", "label"),
                "tactic.graph.yaml: '/nodes/0/label' breaks schema keyword 'type'");
        assertDetail(
                violation(SHARD, List.of(), "type", null, null, Map.of("0", "array")),
                "tactic.graph.yaml: '/' breaks schema keyword 'type'");
    }

    @Test
    void anAdditionalPropertiesOrRequiredViolationWithoutAPropertyUsesTheFallback() {
        assertDetail(
                at(SHARD, "additionalProperties", "nodes", "0"),
                "tactic.graph.yaml: '/nodes/0' breaks schema keyword 'additionalProperties'");
        assertDetail(
                at(SHARD, "required", "nodes", "0"), "tactic.graph.yaml: '/nodes/0' breaks schema keyword 'required'");
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
                "tactic.graph.yaml: unknown key 'surprise'");
        assertDetail(
                violation(SHARD, List.of("nodes", "0"), "additionalProperties", "extra", null, Map.of()),
                "tactic.graph.yaml node: unknown key 'extra'");
        assertDetail(
                violation(SHARD, List.of("edges", "2"), "additionalProperties", "extra", null, Map.of()),
                "tactic.graph.yaml edge: unknown key 'extra'");
    }

    @Test
    void aMissingKeyIsNamed() {
        assertDetail(
                violation(SHARD, List.of("nodes", "1"), "required", "kind", null, Map.of()),
                "tactic.graph.yaml: missing 'kind'");
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
                "drg/fragment.yaml: a node of kind 'directives' has an empty id");
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
                .containsExactly("drg/fragment.yaml: a node of kind 'directives' has an empty id");
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
                "drg/fragment.yaml: node id 'a b' is not <kind>:<id>-safe");
    }

    @Test
    void aFragmentNodeIdViolationWithoutTheKindUsesTheFallback() {
        assertDetail(
                violation(FRAGMENT, List.of("nodes", "0", "id"), "minLength", null, "", Map.of("0", "1")),
                "drg/fragment.yaml: '/nodes/0/id' breaks schema keyword 'minLength'");
        assertDetail(
                violation(
                        FRAGMENT, List.of("nodes", "0", "id"), "pattern", null, "", Map.of("0", "^[A-Za-z0-9_/.-]+$")),
                "drg/fragment.yaml: '/nodes/0/id' breaks schema keyword 'pattern'");
        assertDetail(
                violation(
                        FRAGMENT,
                        List.of("nodes", "0", "id"),
                        "pattern",
                        null,
                        "a b",
                        Map.of("0", "^[A-Za-z0-9_/.-]+$")),
                "drg/fragment.yaml: '/nodes/0/id' breaks schema keyword 'pattern'");
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
                "tactic.graph.yaml: node urn 'nonsense' is not <kind>:<id>");
    }

    @Test
    void aShardNodeWhoseUrnPrefixContradictsItsKindNamesTheUrnAndTheKind() {
        assertDetail(
                violation(SHARD, List.of("nodes", "0", "urn"), "pattern", null, "directive:a", Map.of("0", "^tactic:")),
                "tactic.graph.yaml: node directive:a declares kind 'tactic'");
    }

    @Test
    void anEdgeEndpointThatIsNotAUrnIsRefusedUnderR6() {
        assertDetail(
                violation(SHARD, List.of("edges", "0", "target"), "pattern", null, "QA_404", Map.of()),
                "tactic.graph.yaml: edge endpoint 'QA_404' is not a URN");
        assertDetail(
                violation(FRAGMENT, List.of("edges", "4", "source"), "pattern", null, "QA_404", Map.of()),
                "drg/fragment.yaml: edge endpoint 'QA_404' is not a URN");
    }

    @Test
    void anyOtherPatternSaysTheKeyDoesNotMatch() {
        assertDetail(
                violation(SHARD, List.of("schema_version"), "pattern", null, "x", Map.of()),
                "tactic.graph.yaml: 'schema_version' does not match its pattern");
    }

    // ---- enum and const ------------------------------------------------------------------------

    @Test
    void anUnknownRelationIsNamed() {
        assertDetail(
                violation(SHARD, List.of("edges", "0", "relation"), "enum", null, "befriends", Map.of()),
                "tactic.graph.yaml: unknown relation 'befriends'");
    }

    @Test
    void anUnknownNodeKindNamesTheNode() {
        assertDetail(
                violation(FRAGMENT, List.of("nodes", "0", "kind"), "enum", null, "tacticz", Map.of("id", "t1")),
                "drg/fragment.yaml: node t1 has unknown kind 'tacticz'");
    }

    @Test
    void anUnknownNodeKindWithoutTheNodeIdentityUsesTheFallback() {
        assertDetail(
                violation(FRAGMENT, List.of("nodes", "0", "kind"), "enum", null, "tacticz", Map.of("0", "[tactics]")),
                "drg/fragment.yaml: '/nodes/0/kind' breaks schema keyword 'enum'");
    }

    @Test
    void anUnknownShardNodeKindNamesTheNodeByItsUrn() {
        assertDetail(
                violation(SHARD, List.of("nodes", "1", "kind"), "enum", null, "tacticz", Map.of("urn", "tactic:a")),
                "tactic.graph.yaml: node tactic:a has unknown kind 'tacticz'");
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
                "tactic.graph.yaml: 'schema_version' has an unsupported value '9'");
        assertDetail(
                violation(SHARD, List.of("schema_version"), "const", null, "9", Map.of()),
                "tactic.graph.yaml: 'schema_version' has an unsupported value '9'");
    }

    // ---- ranges and sizes ----------------------------------------------------------------------

    @Test
    void minItemsNamesTheLeastCount() {
        assertDetail(
                violation(SHARD, List.of("nodes"), "minItems", null, null, Map.of("0", "1", "1", "0")),
                "tactic.graph.yaml: 'nodes' needs at least 1 item(s)");
    }

    @Test
    void minItemsWithoutTheMinimumUsesTheFallback() {
        assertDetail(
                violation(SHARD, List.of("nodes"), "minItems", null, null, Map.of()),
                "tactic.graph.yaml: '/nodes' breaks schema keyword 'minItems'");
    }

    @Test
    void theRangeKeywordsSayTheValueIsOutOfRange() {
        for (String keyword : List.of("minimum", "maximum", "exclusiveMinimum")) {
            assertDetail(
                    violation(SHARD, List.of("layer_index"), keyword, null, "-3", Map.of()),
                    "tactic.graph.yaml: 'layer_index' -3 is out of range");
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
        assertThat(collapsed).containsExactly("tactic.graph.yaml: 'label' must be a string");
    }

    @Test
    void theRealNullableShapeWithNoAnyOfErrorCollapsesToTheNonNullBranch() {
        assertThat(details(
                        typeAt(SHARD, "integer", "string", "nodes", "0", "label"),
                        typeAt(SHARD, "integer", "null", "nodes", "0", "label")))
                .containsExactly("tactic.graph.yaml: 'label' must be a string");
        assertThat(details(
                        typeAt(SHARD, "integer", "null", "nodes", "0", "label"),
                        typeAt(SHARD, "integer", "string", "nodes", "0", "label")))
                .containsExactly("tactic.graph.yaml: 'label' must be a string");
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
                .containsExactly("tactic.graph.yaml: '/nodes/0/label' breaks schema keyword 'type'");
    }

    @Test
    void typeViolationsAtDifferentPointersAreNotCollapsed() {
        assertThat(details(
                        typeAt(SHARD, "integer", "string", "nodes", "0", "label"),
                        typeAt(SHARD, "integer", "null", "nodes", "1", "label")))
                .containsExactly(
                        "tactic.graph.yaml: 'label' must be a string",
                        "tactic.graph.yaml: '/nodes/1/label' breaks schema keyword 'type'");
    }

    @Test
    void twoNonNullTypeViolationsAtOnePointerAreBothKept() {
        // Only a branch that expects null is dropped, and only beside one that does not: a oneOf of a string and an
        // integer that the value matches neither of says both.
        assertThat(details(
                        typeAt(SHARD, "boolean", "string", "nodes", "0", "x"),
                        typeAt(SHARD, "boolean", "integer", "nodes", "0", "x")))
                .containsExactly(
                        "tactic.graph.yaml: 'x' must be a string", "tactic.graph.yaml: 'x' must be an integer");
    }

    @Test
    void aKeyWithASlashIsNotTheSamePointerAsTwoKeys() {
        // The pointer ["a/b"] and ["a", "b"] are two places; joined with "/" they were one, and the string branch at
        // the first took the lone null branch at the second with it.
        assertThat(details(typeAt(SHARD, "integer", "null", "a", "b"), typeAt(SHARD, "integer", "string", "a/b")))
                .containsExactly(
                        "tactic.graph.yaml: '/a/b' breaks schema keyword 'type'",
                        "tactic.graph.yaml: 'a/b' must be a string");
    }

    @Test
    void twentyThousandNullableViolationsAreWordedOnceEachInPlaceOrder() {
        // #648: the collapse dropped the null branch of each place with one pass over every violation, so 20 000
        // places took a square of passes. Each step breaks a nullable string as networknt reports
        // it: an anyOf and one type violation per branch, listed in reverse so the order comes from the sort.
        String tactic = "tactics/x.tactic.yaml";
        List<SchemaViolation> violations = new ArrayList<>();
        for (int i = 19_999; i >= 0; i--) {
            List<String> pointer = List.of("steps", Integer.toString(i), "description");
            violations.add(violation(tactic, pointer, "anyOf", null, "1", Map.of()));
            violations.add(violation(tactic, pointer, "type", null, "1", Map.of("0", "integer", "1", "string")));
            violations.add(violation(tactic, pointer, "type", null, "1", Map.of("0", "integer", "1", "null")));
        }

        List<Reason> findings = Wording.schemaFindings("qa", violations);

        assertThat(findings).hasSize(20_000);
        assertThat(findings.subList(0, 100))
                .containsExactlyElementsOf(IntStream.range(0, 100)
                        .mapToObj(i -> Findings.at(
                                PackCodes.ARTIFACT_SCHEMA_ERROR,
                                tactic,
                                List.of("steps", Integer.toString(i), "description"),
                                tactic + ": 'description' must be a string"))
                        .toList());
        assertThat(findings.get(19_999))
                .isEqualTo(Findings.at(
                        PackCodes.ARTIFACT_SCHEMA_ERROR,
                        tactic,
                        List.of("steps", "19999", "description"),
                        tactic + ": 'description' must be a string"));
    }

    @Test
    void anAnyOfWithoutNestedTypeViolationsUsesTheFallback() {
        assertDetail(
                violation(SHARD, List.of("nodes", "0", "x"), "anyOf", null, null, Map.of()),
                "tactic.graph.yaml: '/nodes/0/x' breaks schema keyword 'anyOf'");
    }

    // ---- fallback ------------------------------------------------------------------------------

    @Test
    void anyOtherKeywordUsesTheFixedLocaleFreeFallback() {
        assertDetail(
                violation(SHARD, List.of("nodes", "0", "tags"), "uniqueItems", null, null, Map.of()),
                "tactic.graph.yaml: '/nodes/0/tags' breaks schema keyword 'uniqueItems'");
        assertDetail(
                at(SHARD, "dependentRequired"), "tactic.graph.yaml: '/' breaks schema keyword 'dependentRequired'");
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
                    "tactic.graph.yaml: '/nodes/0/tags' breaks schema keyword 'uniqueItems'");
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
                        "tactic.graph.yaml: unknown key 'surprise'",
                        "tactic.graph.yaml: missing 'nodes'",
                        "tactic.graph.yaml: '/' breaks schema keyword 'uniqueItems'");
        assertThat(details(
                        violation(SHARD, List.of(), "required", "nodes", null, Map.of()),
                        typeAt(SHARD, "array", "object")))
                .containsExactly("tactic.graph.yaml: expected a mapping", "tactic.graph.yaml: missing 'nodes'");
    }
}
