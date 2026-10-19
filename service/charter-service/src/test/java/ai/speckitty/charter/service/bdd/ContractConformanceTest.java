package ai.speckitty.charter.service.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
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

    private static String codes() {
        return "[mission_type_not_found, action_not_found, pack_level_mismatch, pack_unreadable, drg_schema_error,"
                + " unresolved_edge_endpoint]";
    }
}
