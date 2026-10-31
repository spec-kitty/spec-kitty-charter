package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The contract check the scenarios run after every call finds what it is there to find. */
class ContractConformanceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String CONTEXT = "/packs/p1/tactics/t1";

    private final ContractConformance contract = new ContractConformance();

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    @Test
    void aRefusalThatMatchesItsDeclaredResponseConforms() {
        assertThat(contract.violations(
                        "get",
                        CONTEXT,
                        404,
                        PROBLEM_JSON,
                        json("{\"title\":\"t\",\"status\":404,\"code\":\"tactic_not_found\",\"detail\":\"d\"}")))
                .isEmpty();
    }

    @Test
    void aNullOptionalMemberAnUndeclaredMemberAndAMissingRequiredMemberAreViolations() {
        assertThat(contract.violations(
                        "get",
                        CONTEXT,
                        404,
                        PROBLEM_JSON,
                        json("{\"title\":\"t\",\"type\":null,\"timestamp\":\"now\",\"code\":\"tactic_not_found\"}")))
                .containsExactlyInAnyOrder(
                        "$ lacks the required member 'status'",
                        "$.type is NULL, not [string]",
                        "$ has the undeclared member 'timestamp'");
    }

    @Test
    void aCodeOutsideTheEnumAndAStatusOutsideItsRangeAreViolations() {
        assertThat(contract.violations(
                        "get", CONTEXT, 404, PROBLEM_JSON, json("{\"title\":\"t\",\"status\":42,\"code\":\"nope\"}")))
                .containsExactlyInAnyOrder("$.status is below 400", "$.code is \"nope\", not one of " + codes());
    }

    @Test
    void aContentTypeTheResponseDoesNotDeclareIsAViolation() {
        assertThat(contract.violations("get", "/packs/p1/tactics", 200, "text/plain", json("{}")))
                .singleElement()
                .asString()
                .contains("declares no content of type 'text/plain'");
    }

    @Test
    void aStatusTheOperationDoesNotListIsCheckedAgainstItsDefaultProblem() {
        assertThat(contract.violations(
                        "get", "/packs/p1/tactics", 406, PROBLEM_JSON, json("{\"title\":\"t\",\"status\":406}")))
                .isEmpty();
        assertThat(contract.violations("get", "/packs/p1/tactics", 406, PROBLEM_JSON, json("{\"title\":\"t\"}")))
                .containsExactly("$ lacks the required member 'status'");
    }

    @Test
    void aPathNoOperationDefinesIsCheckedAgainstTheSharedProblem() {
        assertThat(contract.violations("get", "/nowhere", 404, PROBLEM_JSON, json("{\"title\":\"t\",\"status\":404}")))
                .isEmpty();
        assertThat(contract.violations("get", "/nowhere", 404, "application/json", json("{}")))
                .singleElement()
                .asString()
                .contains("declares no content of type 'application/json'");
    }

    @Test
    void aSuccessBodyIsCheckedAgainstItsSchemaWithItsNullableMembers() {
        String tactic = "{\"id\":\"t1\",\"schemaVersion\":\"1\",\"name\":\"n\",\"purpose\":null,"
                + "\"notes\":null,\"steps\":[{\"title\":\"s\",\"description\":null,\"examples\":[],"
                + "\"references\":[]}],\"failureModes\":[],\"appliesToLanguages\":[],\"references\":[]}";
        assertThat(contract.violations("get", "/packs/p1/tactics", 200, "application/json", json("[" + tactic + "]")))
                .isEmpty();
        assertThat(contract.violations(
                        "get",
                        "/packs/p1/tactics/t1",
                        200,
                        "application/json",
                        json(tactic.replace("\"notes\":null,", ""))))
                .containsExactly("$ lacks the required member 'notes'");
    }

    @Test
    void aBodyThatIsNotJsonIsAViolation() {
        assertThat(contract.violations("get", "/packs/p1/tactics", 406, PROBLEM_JSON, null))
                .containsExactly("the 406 body is not JSON");
    }

    /** The refusal codes as the schema's enum prints, built from the domain's tokens, not kept by hand. */
    private static String codes() {
        return Arrays.stream(RefusalCode.values())
                .map(RefusalCode::token)
                .toList()
                .toString();
    }

    // The check fails closed on a keyword it does not implement, at construction: a schema no body walks is
    // vetted too. The contract under test is a fixture tree.

    @TempDir
    Path fixture;

    private void write(String relativePath, String content) throws IOException {
        Path file = fixture.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void minimalContract() throws IOException {
        write("charter/openapi.yaml", "openapi: 3.1.0\npaths: {}\n");
    }

    @Test
    void aFixtureContractWithOnlyImplementedKeywordsIsAccepted() throws IOException {
        minimalContract();
        write("charter/schemas/Thing.yaml", """
                type: object
                additionalProperties: false
                required: [name]
                properties:
                  name: {type: string, maxLength: 4, description: d, x-source: s}
                """);

        assertThatCode(() -> new ContractConformance(fixture)).doesNotThrowAnyException();
    }

    @Test
    void aSchemaNoBodyWalksIsVettedWhenTheCheckIsBuilt() throws IOException {
        minimalContract();
        write("charter/schemas/Unused.yaml", "type: array\nitems: {type: string}\nuniqueItems: true\n");

        assertThatThrownBy(() -> new ContractConformance(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("uniqueItems")
                .hasMessageContaining("does not implement");
    }

    @Test
    void aKeywordBelowAPropertyAnItemAndAnAllOfBranchIsVetted() throws IOException {
        minimalContract();
        write("charter/schemas/Deep.yaml", """
                allOf:
                  - type: object
                    properties:
                      list:
                        type: array
                        items: {type: string, const: x}
                """);

        assertThatThrownBy(() -> new ContractConformance(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("const");
    }

    @Test
    void aSchemaOfAResponseThatNoSchemaFileHoldsIsVettedToo() throws IOException {
        write("charter/openapi.yaml", "openapi: 3.1.0\npaths:\n  /x:\n    $ref: paths/x.yaml\n");
        write("charter/paths/x.yaml", """
                get:
                  responses:
                    '200':
                      description: d
                      content:
                        application/json:
                          schema: {type: string, format: uuid, multipleOf: 2}
                """);

        assertThatThrownBy(() -> new ContractConformance(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("multipleOf");
    }

    /** The contract stays closed everywhere but a map: an additionalProperties that admits any member is refused. */
    @ParameterizedTest
    @ValueSource(strings = {"true", "{}", "null"})
    void anAdditionalPropertiesThatAdmitsAnyMemberIsRefused(String argument) throws IOException {
        minimalContract();
        write("charter/schemas/Open.yaml", "type: object\nadditionalProperties: " + argument + "\n");

        assertThatThrownBy(() -> new ContractConformance(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("only additionalProperties: false or a schema is supported");
    }

    @Test
    void aKeywordInsideAnAdditionalPropertiesSchemaIsVetted() throws IOException {
        minimalContract();
        write("charter/schemas/Map.yaml", "type: object\nadditionalProperties: {type: string, const: x}\n");

        assertThatThrownBy(() -> new ContractConformance(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("uses the keyword 'const'");
    }

    // A map (R-14): each member a schema-valued additionalProperties admits is checked against that schema.

    private void linkedThing() throws IOException {
        write("charter/openapi.yaml", "openapi: 3.1.0\npaths:\n  /things/{id}:\n    $ref: paths/thing.yaml\n");
        write("charter/paths/thing.yaml", """
                get:
                  responses:
                    '200':
                      description: d
                      content:
                        application/json:
                          schema: {$ref: ../schemas/Thing.yaml}
                """);
        write("charter/schemas/Thing.yaml", """
                type: object
                additionalProperties: false
                required: [_links]
                properties:
                  _links: {$ref: Links.yaml}
                """);
        write("charter/schemas/Links.yaml", "type: object\nadditionalProperties: {$ref: Link.yaml}\n");
        write("charter/schemas/Link.yaml", """
                type: object
                additionalProperties: false
                required: [href]
                properties:
                  href: {type: string, pattern: '^/api/v1(/|$|\\{)'}
                  templated: {type: boolean}
                """);
    }

    @Test
    void aLinkWithoutHrefAndALinkWithAnExtraMemberAreViolations() throws IOException {
        linkedThing();
        ContractConformance fixtureContract = new ContractConformance(fixture);

        assertThat(fixtureContract.violations(
                        "get", "/things/t1", 200, "application/json", json("{\"_links\":{\"self\":{}}}")))
                .containsExactly("$._links.self lacks the required member 'href'");
        assertThat(fixtureContract.violations(
                        "get",
                        "/things/t1",
                        200,
                        "application/json",
                        json("{\"_links\":{\"self\":{\"href\":\"/api/v1/things/t1\",\"rel\":\"self\"}}}")))
                .containsExactly("$._links.self has the undeclared member 'rel'");
    }

    @Test
    void validLinksUnderOpenRelationKeysConform() throws IOException {
        linkedThing();
        ContractConformance fixtureContract = new ContractConformance(fixture);

        assertThat(fixtureContract.violations(
                        "get",
                        "/things/t1",
                        200,
                        "application/json",
                        json("{\"_links\":{\"self\":{\"href\":\"/api/v1/things/t1\"},"
                                + "\"any-relation\":{\"href\":\"/api/v1/things{?q}\",\"templated\":true}}}")))
                .isEmpty();
        assertThat(fixtureContract.violations(
                        "get",
                        "/things/t1",
                        200,
                        "application/json",
                        json("{\"_links\":{\"self\":{\"href\":\"/things/t1\"}}}")))
                .containsExactly("$._links.self.href '/things/t1' does not match ^/api/v1(/|$|\\{)");
    }

    @Test
    void theShippedContractHoldsLinksAndTheCheckIsBuiltOverIt() {
        URL links = ContractConformanceTest.class.getClassLoader().getResource("contract/charter/schemas/Links.yaml");

        assertThat(links).isNotNull();
        assertThatCode(ContractConformance::new).doesNotThrowAnyException();
    }

    // Path and method (R-14): a literal PUT path beside a templated GET path is checked against its own operation.

    private static final String ARTIFACT = "/packs/qa/artifacts/directive/x";

    private static String operation(String method, String member) {
        return "    " + method + ":\n      responses:\n        '200':\n          description: d\n"
                + "          content:\n            application/json:\n              schema:\n"
                + "                type: object\n                additionalProperties: false\n"
                + "                required: [" + member + "]\n                properties:\n"
                + "                  " + member + ": {type: string}\n";
    }

    private ContractConformance artifacts(String... pathItems) throws IOException {
        write("charter/openapi.yaml", "openapi: 3.1.0\npaths:\n" + String.join("", pathItems));
        write("_shared/responses/Problem.yaml", """
                description: d
                content:
                  application/problem+json:
                    schema:
                      type: object
                      additionalProperties: false
                      required: [title]
                      properties:
                        title: {type: string}
                """);
        return new ContractConformance(fixture);
    }

    private static final String TEMPLATED = "  /packs/{packId}/artifacts/{kind}/{artifactId}:\n";
    private static final String LITERAL = "  /packs/{packId}/artifacts/directive/{artifactId}:\n";

    @Test
    void aPutAndAGetOfOnePathAreEachCheckedAgainstTheTemplateThatDefinesTheirMethod() throws IOException {
        ContractConformance fixtureContract =
                artifacts(TEMPLATED + operation("get", "read"), LITERAL + operation("put", "written"));

        assertThat(fixtureContract.violations("put", ARTIFACT, 200, "application/json", json("{\"written\":\"w\"}")))
                .isEmpty();
        assertThat(fixtureContract.violations("get", ARTIFACT, 200, "application/json", json("{\"read\":\"r\"}")))
                .isEmpty();
        assertThat(fixtureContract.violations("put", ARTIFACT, 200, "application/json", json("{\"read\":\"r\"}")))
                .containsExactlyInAnyOrder(
                        "$ lacks the required member 'written'", "$ has the undeclared member 'read'");
    }

    @Test
    void theMatchDoesNotDependOnTheOrderOfThePaths() throws IOException {
        ContractConformance fixtureContract =
                artifacts(LITERAL + operation("put", "written"), TEMPLATED + operation("get", "read"));

        assertThat(fixtureContract.violations("get", ARTIFACT, 200, "application/json", json("{\"read\":\"r\"}")))
                .isEmpty();
        assertThat(fixtureContract.violations("put", ARTIFACT, 200, "application/json", json("{\"written\":\"w\"}")))
                .isEmpty();
    }

    @Test
    void whenBothTemplatesDefineTheMethodTheConcreteOneWins() throws IOException {
        ContractConformance fixtureContract =
                artifacts(TEMPLATED + operation("get", "read"), LITERAL + operation("get", "directive"));

        assertThat(fixtureContract.violations("get", ARTIFACT, 200, "application/json", json("{\"directive\":\"d\"}")))
                .isEmpty();
        assertThat(fixtureContract.violations(
                        "get", "/packs/qa/artifacts/tactic/x", 200, "application/json", json("{\"read\":\"r\"}")))
                .isEmpty();
    }

    @Test
    void aMethodNoMatchingTemplateDefinesIsCheckedAgainstTheSharedProblem() throws IOException {
        ContractConformance fixtureContract =
                artifacts(TEMPLATED + operation("get", "read"), LITERAL + operation("put", "written"));

        assertThat(fixtureContract.violations(
                        "post", ARTIFACT, 405, "application/problem+json", json("{\"title\":\"t\"}")))
                .isEmpty();
        assertThat(fixtureContract.violations("post", ARTIFACT, 405, "application/problem+json", json("{}")))
                .containsExactly("$ lacks the required member 'title'");
    }

    @Test
    void aReferenceWithSiblingKeywordsIsRefused() throws IOException {
        minimalContract();
        write("charter/schemas/Other.yaml", "type: string\n");
        write("charter/schemas/Sibling.yaml", "$ref: Other.yaml\ndescription: d\n");

        assertThatThrownBy(() -> new ContractConformance(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("a $ref with sibling keywords is not supported");
    }

    @Test
    void aFragmentReferenceIsRefused() throws IOException {
        minimalContract();
        write("charter/schemas/Fragment.yaml", "$ref: Other.yaml#/properties/x\n");

        assertThatThrownBy(() -> new ContractConformance(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the fragment reference 'Other.yaml#/properties/x' is not supported");
    }

    @Test
    void minLengthAndOneOfAreChecked() throws IOException {
        write("charter/openapi.yaml", "openapi: 3.1.0\npaths:\n  /x:\n    $ref: paths/x.yaml\n");
        write("charter/paths/x.yaml", """
                get:
                  responses:
                    '200':
                      description: d
                      content:
                        application/json:
                          schema: {$ref: ../schemas/Page.yaml}
                """);
        write("charter/schemas/Cursor.yaml", "type: string\nminLength: 1\n");
        write("charter/schemas/Page.yaml", """
                type: object
                properties:
                  next:
                    oneOf:
                      - $ref: Cursor.yaml
                      - type: 'null'
                """);
        ContractConformance fixtureContract = new ContractConformance(fixture);

        assertThat(fixtureContract.violations("get", "/x", 200, "application/json", json("{\"next\":\"c\"}")))
                .isEmpty();
        assertThat(fixtureContract.violations("get", "/x", 200, "application/json", json("{\"next\":null}")))
                .isEmpty();
        assertThat(fixtureContract.violations("get", "/x", 200, "application/json", json("{\"next\":\"\"}")))
                .containsExactly("$.next matches 0 of the oneOf branches, not exactly one");
        assertThat(fixtureContract.violations("get", "/x", 200, "application/json", json("{\"next\":7}")))
                .containsExactly("$.next matches 0 of the oneOf branches, not exactly one");
    }

    @Test
    void maxItemsIsChecked() throws IOException {
        write("charter/openapi.yaml", "openapi: 3.1.0\npaths:\n  /x:\n    $ref: paths/x.yaml\n");
        write("charter/paths/x.yaml", """
                get:
                  responses:
                    '200':
                      description: d
                      content:
                        application/json:
                          schema: {$ref: ../schemas/List.yaml}
                """);
        write("charter/schemas/List.yaml", "type: array\nmaxItems: 2\nitems: {type: string}\n");
        ContractConformance fixtureContract = new ContractConformance(fixture);

        assertThat(fixtureContract.violations("get", "/x", 200, "application/json", json("[\"a\",\"b\"]")))
                .isEmpty();
        assertThat(fixtureContract.violations("get", "/x", 200, "application/json", json("[\"a\",\"b\",\"c\"]")))
                .containsExactly("$ holds more than 2 items");
    }
}
