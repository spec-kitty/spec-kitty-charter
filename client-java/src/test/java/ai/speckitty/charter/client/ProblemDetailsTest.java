package ai.speckitty.charter.client;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.client.model.Problem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProblemDetailsTest {

    private static final Map<String, List<String>> PROBLEM_JSON =
            Map.of("Content-Type", List.of("application/problem+json"));

    private static ApiException answered(int status, String body) {
        return new ApiException(status, PROBLEM_JSON, body);
    }

    @Test
    void aProblemBodyIsReadAsTheContractsProblem() {
        Problem problem = ProblemDetails.from(answered(404, """
                        {"title": "Not Found", "status": 404, "detail": "no pack named acme"}""")).orElseThrow();

        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("no pack named acme");
        assertThat(problem.getCode()).isNull();
    }

    @Test
    void aRefusalKeepsItsCode() {
        Problem problem = ProblemDetails.from(answered(409, """
                        {"title": "Conflict", "status": 409, "code": "pack_unreadable"}""")).orElseThrow();

        assertThat(problem.getCode()).isEqualTo("pack_unreadable");
    }

    @Test
    void aMemberThisClientDoesNotKnowIsKeptAndDoesNotFailTheRead() {
        Problem problem = ProblemDetails.from(answered(400, """
                        {"title": "Bad Request", "status": 400, "addedInALaterMinor": {"x": 1}}""")).orElseThrow();

        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getAdditionalProperty("addedInALaterMinor")).isNotNull();
    }

    @Test
    void anEmptyOrMissingBodyIsNoProblem() {
        assertThat(ProblemDetails.from(answered(500, ""))).isEmpty();
        assertThat(ProblemDetails.from(answered(500, "  \n"))).isEmpty();
        assertThat(ProblemDetails.from(new ApiException("connection refused"))).isEmpty();
    }

    @Test
    void aBodyThatIsNotAProblemIsNoProblem() {
        assertThat(ProblemDetails.from(answered(502, "<html>Bad Gateway</html>")))
                .isEmpty();
        assertThat(ProblemDetails.from(answered(502, "[1, 2]"))).isEmpty();
        assertThat(ProblemDetails.from(answered(502, "\"text\""))).isEmpty();
        assertThat(ProblemDetails.from(answered(502, "{\"title\": \"null status\", \"status\": null}")))
                .isEmpty();
        assertThat(ProblemDetails.from(answered(502, "{\"title\": null, \"status\": 502}")))
                .isEmpty();
        assertThat(ProblemDetails.from(answered(502, "{\"title\": \"no status\"}")))
                .isEmpty();
        assertThat(ProblemDetails.from(answered(502, "{\"title\": \"truncated\", \"sta")))
                .isEmpty();
    }

    @Test
    void theContentTypeIsNotConsultedTheBodyDecides() {
        ApiException relabelled = new ApiException(
                404, Map.of("Content-Type", List.of("text/html")), "{\"title\": \"Not Found\", \"status\": 404}");

        assertThat(ProblemDetails.from(relabelled))
                .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(404));
    }
}
