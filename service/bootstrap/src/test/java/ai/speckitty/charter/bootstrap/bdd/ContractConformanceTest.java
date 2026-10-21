package ai.speckitty.charter.bootstrap.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The contract check the scenarios run after every call finds what it is there to find. */
class ContractConformanceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String CONTEXT = "/mission-types/software-dev/actions/plan/context";

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
                        json("{\"title\":\"t\",\"status\":404,\"code\":\"action_not_found\",\"detail\":\"d\"}")))
                .isEmpty();
    }

    @Test
    void aNullOptionalMemberAnUndeclaredMemberAndAMissingRequiredMemberAreViolations() {
        assertThat(contract.violations(
                        "get",
                        CONTEXT,
                        404,
                        PROBLEM_JSON,
                        json("{\"title\":\"t\",\"type\":null,\"timestamp\":\"now\",\"code\":\"action_not_found\"}")))
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
        assertThat(contract.violations("get", "/packs", 200, "text/plain", json("{}")))
                .singleElement()
                .asString()
                .contains("declares no content of type 'text/plain'");
    }

    @Test
    void aStatusTheOperationDoesNotListIsCheckedAgainstItsDefaultProblem() {
        assertThat(contract.violations("get", "/packs", 406, PROBLEM_JSON, json("{\"title\":\"t\",\"status\":406}")))
                .isEmpty();
        assertThat(contract.violations("get", "/packs", 406, PROBLEM_JSON, json("{\"title\":\"t\"}")))
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
        assertThat(contract.violations(
                        "get",
                        "/packs",
                        200,
                        "application/json",
                        json("{\"packs\":[{\"name\":\"n\",\"level\":\"org\",\"packId\":null,\"packVersion\":null,"
                                + "\"parentPack\":null,\"nodeCount\":0,\"edgeCount\":0,\"diagnostics\":[]}]}")))
                .isEmpty();
        assertThat(contract.violations(
                        "get",
                        "/packs",
                        200,
                        "application/json",
                        json("{\"packs\":[{\"name\":\"n\",\"level\":\"org\",\"packVersion\":null,"
                                + "\"parentPack\":null,\"nodeCount\":-1,\"edgeCount\":0,\"diagnostics\":[]}]}")))
                .containsExactlyInAnyOrder(
                        "$.packs[0] lacks the required member 'packId'", "$.packs[0].nodeCount is below 0");
    }

    @Test
    void aBodyThatIsNotJsonIsAViolation() {
        assertThat(contract.violations("get", "/packs", 406, PROBLEM_JSON, null))
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

    @Test
    void anAdditionalPropertiesSchemaIsRefusedBecauseOnlyFalseIsImplemented() throws IOException {
        minimalContract();
        write("charter/schemas/Open.yaml", "type: object\nadditionalProperties: {}\n");

        assertThatThrownBy(() -> new ContractConformance(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("only additionalProperties: false is supported");
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
}
