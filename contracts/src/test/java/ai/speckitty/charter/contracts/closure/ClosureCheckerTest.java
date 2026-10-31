package ai.speckitty.charter.contracts.closure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One case per fixture of {@code src/test/resources/closure/}: each must-report fixture names the JSON Pointer of
 * its violation and the reason, each must-pass fixture reports nothing.
 */
class ClosureCheckerTest {

    private static final String PROBE =
            "/paths/~1probe/get/responses/200/content/application~1json/schema/properties/probe";
    private static final String PACK =
            "/paths/~1packs/get/responses/200/content/application~1json/schema/properties/packs/items";
    private static final String EXTRA = PACK + "/properties/extra";
    private static final String WITHOUT_IF = "without an if, then and else have no effect, so they close nothing";
    private static final String CONVENTION = "this pairing is refused by convention, as a likely authoring mistake,"
            + " even where a value satisfies both parts";
    private static final String REFUSAL =
            "/paths/~1mission-types~1{missionType}~1actions~1{action}~1context/get/responses/404"
                    + "/content/application~1problem+json/schema";

    @Nested
    class MovedFromTheSpectralRule {

        @Test
        void aNamedObjectWithoutAdditionalPropertiesFalse() {
            assertReports(
                    "open-named-object.yaml", PACK + "/properties/diagnostics/items", "no additionalProperties: false");
        }

        @Test
        void anUntitledOpenObjectProperty() {
            assertReports("anonymous.yaml", PACK + "/properties/meta", "no additionalProperties: false");
        }

        @Test
        void itemsTrue() {
            assertReports("items-true.yaml", EXTRA + "/items", "the true schema admits any value");
        }

        @Test
        void anAllOfOverAnAllOfOverAnOpenObject() {
            assertReports(
                    "nested-allof.yaml",
                    EXTRA,
                    "allOf/0: no part closes it: it has no type and no closing keyword;"
                            + " allOf/0: no additionalProperties: false");
        }

        @Test
        void aNullableOpenObject() {
            assertReports(
                    "nullable-anyof.yaml", EXTRA, "anyOf: its alternative 0 is open: no additionalProperties: false");
        }

        @Test
        void anOpenArrayItem() {
            assertReports("open-array-item.yaml", EXTRA + "/items", "no additionalProperties: false");
        }

        @Test
        void aCompositeWhoseFirstPartIsOpen() {
            assertReports("open-first-part.yaml", REFUSAL, "additionalProperties: true admits any member");
        }

        @Test
        void anOpenTupleItemBehindAClosedTail() {
            assertReports(
                    "open-tuple-item-closed-tail.yaml", EXTRA + "/prefixItems/0", "no additionalProperties: false");
        }

        @Test
        void anOpenTupleItemAndAnOpenTail() {
            assertThat(violations("open-tuple-item.yaml"))
                    .extracting(Violation::pointer)
                    .extracting(JsonPointer::toString)
                    .containsExactly(EXTRA + "/prefixItems/0", EXTRA);
            assertThat(violations("open-tuple-item.yaml").get(1).reason()).contains("after its prefixItems");
        }

        @Test
        void patternPropertiesBesideAdditionalPropertiesFalse() {
            assertReports("pattern-properties.yaml", EXTRA, "patternProperties admits members by pattern");
        }

        @Test
        void aScalarPartNeverExcusesAnOpenObjectPart() {
            assertReports(
                    "scalar-first-allof.yaml",
                    EXTRA,
                    "an open object (no additionalProperties: false; from allOf/1)"
                            + " sits beside a part that admits no object"
                            + " (its type [string] admits no object; from allOf/0): "
                            + CONVENTION);
        }

        @Test
        void anUntypedMap() {
            assertReports("untyped-map.yaml", EXTRA, "additionalProperties holds a schema, so it is a map");
        }
    }

    @Nested
    class RoundFiveProbes {

        @Test
        void aRequiredMemberBesideNull() {
            assertReports(
                    "union-required-or-null.yaml",
                    PROBE,
                    "anyOf: its alternative 0 is open: it has no type and no closing keyword");
        }

        @Test
        void theExactlyOneOfIdiomOnAnUntypedHolder() {
            assertReports(
                    "exactly-one-of-untyped-holder.yaml",
                    PROBE,
                    "oneOf: its alternative 0 is open: it has no type and no closing keyword");
        }

        @Test
        void aNegatedRequiredMemberBesideNull() {
            assertReports(
                    "union-not-required-or-null.yaml",
                    PROBE,
                    "anyOf: its alternative 0 is open: it has no type and no closing keyword");
        }

        @Test
        void aMinPropertiesMemberBesideAString() {
            assertReports(
                    "min-properties-or-string.yaml",
                    PROBE,
                    "oneOf: its alternative 0 is open: it has no type and no closing keyword");
        }

        @Test
        void aRequiredMemberWithANegatedRefinementBesideNull() {
            assertReports(
                    "union-required-and-not-const.yaml",
                    PROBE,
                    "anyOf: its alternative 0 is open: it has no type and no closing keyword");
        }
    }

    @Nested
    class RoundFiveFiringCases {

        @Test
        void aMemberWithRequiredAndAdditionalPropertiesTrue() {
            assertReports(
                    "member-required-additional-true.yaml", PROBE, "additionalProperties: true admits any member");
        }

        @Test
        void aMemberWithMinPropertiesAndPatternProperties() {
            assertReports("member-min-properties-pattern.yaml", PROBE, "patternProperties admits members by pattern");
        }

        @Test
        void aConstraintMemberNestingAOneOfOverAnOpenObject() {
            assertReports(
                    "constraint-member-nesting-open-oneof.yaml",
                    PROBE,
                    "anyOf: its alternative 0 is open: no part closes it: it has no type and no closing keyword;"
                            + " oneOf: its alternative 0 is open: no additionalProperties: false");
        }

        @Test
        void aDependentSchemaHidingAnOpenObject() {
            assertReports(
                    "dependent-schemas-hiding-open-object.yaml",
                    PROBE + "/dependentSchemas/k/properties/extra",
                    "no additionalProperties: false");
        }

        @Test
        void additionalPropertiesTrue() {
            assertReports("additional-properties-true.yaml", PROBE, "additionalProperties: true admits any member");
        }
    }

    @Nested
    class OtherMustReportCases {

        @Test
        void anArrayWithoutItems() {
            assertReports("array-without-items.yaml", PROBE, "an array with no items admits any item");
        }

        @Test
        void anUnknownKeywordFailsClosed() {
            assertReports("unknown-keyword.yaml", PROBE + "/unevaluatedProperties", "'unevaluatedProperties'");
        }

        @Test
        void aLeftoverRefIsNamedAndItsPositionCannotBeProven() {
            List<Violation> violations = violations("leftover-ref.yaml");
            assertThat(violations).extracting(v -> v.pointer().toString()).containsExactly(PROBE + "/$ref", PROBE);
            assertThat(violations.getFirst().reason()).contains("a $ref is left in the bundle");
        }

        @Test
        void aResponseLeftAsARef() {
            assertReports("response-ref.yaml", "/paths/~1probe/get/responses/200/$ref", "a $ref is left in the bundle");
        }

        @Test
        void aThenWithoutElseClosesNothingWhenTheConditionFails() {
            assertReports("then-without-else.yaml", PROBE, "then|else: its alternative else is open: there is no else");
        }

        @Test
        void aDependentSchemaNeverClosesItsHolder() {
            assertReports("dependent-schema-closing.yaml", PROBE, "no additionalProperties: false");
        }

        @Test
        void anUntypedClosedObjectStillAdmitsAnyArray() {
            assertReports("untyped-closed-object.yaml", PROBE, "an array with no items admits any item");
        }

        @Test
        void aSchemaThatContainsItself() {
            assertReports("recursive-alias.yaml", PROBE + "/properties/child", "contains itself");
        }
    }

    @Nested
    class RoundSixProbes {

        private static final String JSON_MEDIA = "/paths/~1probe/get/responses/200/content/application~1json";

        @Test
        void thenAndElseWithoutAnIfCloseNothing() {
            assertReports(
                    "then-else-without-if.yaml",
                    PROBE,
                    "no part closes it: no additionalProperties: false; then|else: " + WITHOUT_IF);
        }

        @Test
        void untypedThenAndElseWithoutAnIfCloseNothing() {
            assertReports(
                    "then-else-without-if-untyped.yaml",
                    PROBE,
                    "no part closes it: it has no type and no closing keyword; then|else: " + WITHOUT_IF);
        }

        @Test
        void aThenWithoutAnIfStillHasItsNestedPositionsJudged() {
            assertReports(
                    "then-without-if-nesting-open-object.yaml",
                    PROBE + "/then/properties/extra",
                    "no additionalProperties: false");
        }

        @Test
        void aMediaTypeWithoutASchemaIsUnconstrained() {
            assertReports(
                    "media-type-without-schema.yaml",
                    JSON_MEDIA,
                    "the media type has no schema, so its body is unconstrained");
        }

        @Test
        void aResponseWithoutContentHasNoBodyToJudge() {
            assertPasses("response-without-content.yaml");
        }

        @Test
        void aDeclaredObjectTypeBesideANonObjectPartIsRefusedByConventionEvenWhenSatisfiable() {
            assertReports(
                    "object-or-string-beside-string-part.yaml",
                    PROBE,
                    "an open object (no additionalProperties: false; from its own keywords) sits beside a part that"
                            + " admits no object (its type [string] admits no object; from allOf/0): " + CONVENTION);
        }

        @Test
        void aUnionPartIsNamedByItsKeyword() {
            assertReports(
                    "string-beside-union-with-open-object.yaml",
                    PROBE,
                    "an open object (its alternative 0 is open: no additionalProperties: false; from anyOf) sits"
                            + " beside a part that admits no object (its type [string] admits no object; from its own"
                            + " keywords): " + CONVENTION);
        }

        @Test
        void anEnumThatIsNotAListIsMalformed() {
            assertMalformed("enum-not-a-list.yaml", PROBE + "/enum", "must be a non-empty list of values");
        }

        @Test
        void aYamlNullInATypeListIsMalformed() {
            assertMalformed(
                    "type-list-yaml-null.yaml",
                    PROBE + "/type",
                    "a type name must be a string: an unquoted YAML null is not the type name 'null'; quote it");
        }

        @Test
        void aTypeListEntryThatIsNotAStringIsMalformed() {
            assertMalformed("type-list-non-string.yaml", PROBE + "/type", "a type name must be a string, not 1");
        }

        @Test
        void anUnknownTypeNameIsMalformed() {
            assertMalformed("unknown-type-name.yaml", PROBE + "/type", "'objekt' is not a JSON Schema type");
        }

        @Test
        void anOpenApi30DocumentIsNotJudged() {
            assertReports(
                    "openapi-3-0.yaml",
                    "/openapi",
                    "the checker reads OpenAPI 3.1 (JSON Schema 2020-12) only, not openapi 3.0.3");
        }

        @Test
        void aForeignJsonSchemaDialectIsNotJudged() {
            assertReports(
                    "foreign-json-schema-dialect.yaml",
                    "/jsonSchemaDialect",
                    "the checker reads the OAS 3.1 default dialect " + ClosureChecker.DEFAULT_DIALECT
                            + " only, not https://json-schema.org/draft/2019-09/schema");
        }

        @Test
        void theDefaultDialectDeclaredExplicitlyIsJudged() {
            assertPasses("default-json-schema-dialect.yaml");
        }

        /** A malformed probe is named, and the probe itself, which the checker then cannot prove closed, is open. */
        private static void assertMalformed(String fixture, String pointer, String reason) {
            List<Violation> violations = violations(fixture);
            assertThat(violations).extracting(v -> v.pointer().toString()).containsExactly(pointer, PROBE);
            assertThat(violations.getFirst().reason()).isEqualTo(reason);
        }
    }

    /** The document around the response schemas fails closed too: a key the checker does not know is reported. */
    @Nested
    class DocumentLevel {

        private static final String GET = "/paths/~1probe/get";
        private static final String RESPONSE = GET + "/responses/200";
        private static final String HEADER = RESPONSE + "/headers/X-Probe";

        @Test
        void anUpperCaseMethodIsAnUnknownPathItemKey() {
            assertReports(
                    "path-item-unknown-key.yaml",
                    "/paths/~1probe/GET",
                    "'GET' is not a path item key the checker knows, so what it holds goes unjudged");
        }

        @Test
        void aMisspeltResponsesIsAnUnknownOperationKey() {
            // The misspelling leaves the operation with no responses too; both are reported.
            List<Violation> violations = violations("operation-unknown-key.yaml");
            assertThat(violations)
                    .extracting(v -> v.pointer().toString())
                    .containsExactlyInAnyOrder(GET + "/responsez", GET);
            assertThat(violations)
                    .extracting(Violation::reason)
                    .anyMatch(reason -> reason.contains("'responsez' is not an operation key the checker knows"));
        }

        @Test
        void anOperationWithoutResponsesIsReported() {
            assertReports("operation-without-responses.yaml", GET, "the operation has no responses");
        }

        @Test
        void anOperationWithEmptyResponsesIsReported() {
            assertReports("operation-empty-responses.yaml", GET + "/responses", "the operation's responses is empty");
        }

        @Test
        void responsesThatAllLackABodyPass() {
            assertPasses("operation-bodyless-responses.yaml");
        }

        @Test
        void allowReservedIsAKnownHeaderKey() {
            assertPasses("header-allow-reserved.yaml");
        }

        @Test
        void allowEmptyValueIsAKnownHeaderKey() {
            assertPasses("header-allow-empty-value.yaml");
        }

        @Test
        void aMisspeltContentIsAnUnknownResponseKey() {
            assertReports(
                    "response-unknown-key.yaml",
                    RESPONSE + "/contents",
                    "'contents' is not a response key the checker knows");
        }

        @Test
        void aResponsesKeyThatIsNoStatusIsReported() {
            assertReports(
                    "response-status-unknown-key.yaml",
                    GET + "/responses/ok",
                    "'ok' is not a status code, a status range such as 4XX, or default");
        }

        @Test
        void aMisspeltExamplesIsAnUnknownMediaTypeKey() {
            assertReports(
                    "media-type-unknown-key.yaml",
                    RESPONSE + "/content/application~1json/examplez",
                    "'examplez' is not a media type key the checker knows");
        }

        @Test
        void webhooksAreRefused() {
            assertReports(
                    "webhooks.yaml",
                    "/webhooks",
                    "the charter contract uses no webhooks, and the checker would need to judge their responses");
        }

        @Test
        void callbacksAreRefused() {
            assertReports(
                    "callbacks.yaml",
                    GET + "/callbacks",
                    "the charter contract uses no callbacks, and the checker would need to judge their responses");
        }

        @Test
        void aResponseHeaderSchemaIsAPosition() {
            assertReports("header-open-object.yaml", HEADER + "/schema", "no additionalProperties: false");
        }

        @Test
        void aResponseHeaderContentSchemaIsAPosition() {
            assertReports(
                    "header-content-open-object.yaml",
                    HEADER + "/content/application~1json/schema",
                    "no additionalProperties: false");
        }

        @Test
        void aResponseHeaderWithoutASchemaIsUnconstrained() {
            assertReports(
                    "header-without-schema.yaml",
                    HEADER,
                    "the header has neither a schema nor content, so its value is unconstrained");
        }

        @Test
        void scalarAndClosedHeadersPassAndAreCounted() throws IOException, URISyntaxException {
            ClosureReport report = ClosureChecker.check(Path.of(ClosureCheckerTest.class
                    .getResource("/closure/header-closed.yaml")
                    .toURI()));
            assertThat(report.violations()).isEmpty();
            assertThat(report.positions()).isEqualTo(3);
        }

        @Test
        void aDocumentThatJudgesNoResponseSchemaFails() {
            assertReports(
                    "no-judged-position.yaml", "", "no response schema was judged, so the checker proved nothing");
        }

        @Test
        void anAdditionalPropertiesThatIsNoSchemaIsMalformedNotAMap() {
            List<Violation> violations = violations("additional-properties-not-a-schema.yaml");
            assertThat(violations)
                    .extracting(v -> v.pointer().toString())
                    .containsExactly(PROBE + "/additionalProperties", PROBE);
            assertThat(violations.getFirst().reason()).isEqualTo("must be a schema: a mapping or a boolean");
            assertThat(violations.get(1).reason()).doesNotContain("map");
        }

        @Test
        void aPathItemThatIsNotAMappingIsReported() {
            Map<String, Object> document = new HashMap<>(probe(false));
            document.put("paths", Map.of("/probe", "text", "/other", Map.of("get", Map.of("responses", Map.of()))));
            assertThat(ClosureChecker.check(document).violations())
                    .extracting(Violation::toString)
                    .contains("/paths/~1probe: must be a mapping");
        }

        @Test
        void aPathThatDoesNotStartWithASlashIsReported() {
            Map<String, Object> document = new HashMap<>(probe(false));
            Map<String, Object> paths = new HashMap<>(castMap(document.get("paths")));
            paths.put("probe", Map.of());
            paths.put("x-note", "an extension is not a path");
            document.put("paths", paths);
            assertThat(ClosureChecker.check(document).violations())
                    .extracting(Violation::toString)
                    .containsExactly("/paths/probe: a path must start with /");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @Nested
    class MustPass {

        @Test
        void theExactlyOneOfIdiomOnAClosedHolder() {
            assertPasses("exactly-one-of-closed-holder.yaml");
        }

        @Test
        void minPropertiesZeroIsAConstraintOnAClosedHolder() {
            assertPasses("min-properties-zero-on-closed-holder.yaml");
        }

        @Test
        void aClosedTuple() {
            assertPasses("closed-tuple.yaml");
        }

        @Test
        void anIfConditionHoldingABareConst() {
            assertPasses("if-bare-const.yaml");
        }

        @Test
        void aThenAndAnElseThatBothClose() {
            assertPasses("then-and-else-closed.yaml");
        }

        @Test
        void aMemberCannotReopenAClosedHolder() {
            assertPasses("member-cannot-reopen-closed-holder.yaml");
        }

        @Test
        void provableNonObjectsAndANullableClosedObject() {
            assertPasses("non-objects.yaml");
        }

        @Test
        void theLintRulesetsCleanFixture() throws IOException {
            ClosureReport report = ClosureChecker.check(Path.of(System.getProperty("charter.lintCleanFixture")));
            assertThat(report.violations()).isEmpty();
            assertThat(report.positions()).isEqualTo(16);
            assertThat(report.passes()).isTrue();
        }
    }

    /**
     * The one exemption (ADR-0019 D3): a property named {@code _links} whose values are the closed {@code Link}.
     * Each lookalike stays an open map.
     */
    @Nested
    class LinksMap {

        private static final String LINKS =
                "/paths/~1probe/get/responses/200/content/application~1json/schema/properties/_links";
        private static final String MAP = "additionalProperties holds a schema, so it is a map";

        @Test
        void aLinksPropertyOfClosedLinksPasses() {
            assertPasses("links-map-exempt.yaml");
        }

        @Test
        void theSameMapUnderAnotherPropertyNameIsReported() {
            assertReports(
                    "links-map-other-property.yaml",
                    "/paths/~1probe/get/responses/200/content/application~1json/schema/properties/links",
                    MAP);
        }

        @Test
        void aLinksMapOfAValueWithAnotherTitleIsReported() {
            assertReports("links-map-other-value-title.yaml", LINKS, MAP);
        }

        /** The open Link is a position of its own, and the map is not exempt because its Link is not proven closed. */
        @Test
        void aLinksMapOfAnOpenLinkIsReportedAtTheLinkAndAtTheMap() {
            assertThat(violations("links-map-open-link.yaml"))
                    .extracting(Violation::toString)
                    .containsExactly(
                            LINKS + "/additionalProperties: no additionalProperties: false", LINKS + ": " + MAP);
        }
    }

    @Nested
    class MalformedInput {

        @Test
        void aDocumentWithoutPathsIsReported() {
            ClosureReport report = ClosureChecker.check(Map.of("openapi", "3.1.0"));
            assertThat(report.passes()).isFalse();
            assertThat(report.violations()).extracting(Violation::reason).containsExactly("the document has no paths");
        }

        @Test
        void anUnknownTypeIsReported() {
            ClosureReport report = ClosureChecker.check(probe(Map.of("type", "objekt")));
            assertThat(report.violations())
                    .extracting(Violation::toString)
                    .contains(PROBE + "/type: 'objekt' is not a JSON Schema type");
        }

        @Test
        void anEmptyUnionIsReported() {
            ClosureReport report = ClosureChecker.check(probe(Map.of("type", "string", "anyOf", List.of())));
            assertThat(report.violations())
                    .extracting(Violation::toString)
                    .containsExactly(PROBE + "/anyOf: must be a non-empty list of schemas");
        }

        @Test
        void anEmptyEnumIsReported() {
            ClosureReport report = ClosureChecker.check(probe(Map.of("type", "string", "enum", List.of())));
            assertThat(report.violations())
                    .extracting(Violation::toString)
                    .containsExactly(PROBE + "/enum: must be a non-empty list of values");
        }

        @Test
        void aDocumentWithoutAnOpenApiVersionIsReported() {
            Map<String, Object> document = new HashMap<>(probe(false));
            document.remove("openapi");
            assertThat(ClosureChecker.check(document).violations())
                    .extracting(Violation::toString)
                    .containsExactly("(document root): the document declares no openapi version; the checker reads"
                            + " OpenAPI 3.1 only");
        }

        @Test
        void anOpenApiVersionThatIsNotAStringIsReported() {
            Map<String, Object> document = new HashMap<>(probe(false));
            document.put("openapi", 3.1);
            assertThat(ClosureChecker.check(document).violations())
                    .extracting(Violation::toString)
                    .containsExactly("/openapi: the openapi version must be the string 3.1.x, not the YAML"
                            + " number 3.1; quote it");
        }

        @Test
        void aValueThatIsNotASchemaIsReported() {
            ClosureReport report = ClosureChecker.check(probe("text"));
            assertThat(report.violations())
                    .extracting(Violation::toString)
                    .containsExactly(PROBE + ": is neither a schema nor a boolean");
        }
    }

    private static Map<String, Object> probe(Object schema) {
        Map<String, Object> closed =
                Map.of("type", "object", "additionalProperties", false, "properties", Map.of("probe", schema));
        Map<String, Object> response = Map.of("content", Map.of("application/json", Map.of("schema", closed)));
        return Map.of(
                "openapi",
                "3.1.0",
                "paths",
                Map.of("/probe", Map.of("get", Map.of("responses", Map.of("200", response)))));
    }

    private static void assertReports(String fixture, String pointer, String reasonPart) {
        List<Violation> violations = violations(fixture);
        assertThat(violations).extracting(v -> v.pointer().toString()).containsExactly(pointer);
        assertThat(violations.getFirst().reason()).contains(reasonPart);
    }

    private static void assertPasses(String fixture) {
        assertThat(violations(fixture)).isEmpty();
    }

    private static List<Violation> violations(String fixture) {
        try {
            return ClosureChecker.check(Path.of(ClosureCheckerTest.class
                            .getResource("/closure/" + fixture)
                            .toURI()))
                    .violations();
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("cannot read the fixture " + fixture, e);
        }
    }
}
