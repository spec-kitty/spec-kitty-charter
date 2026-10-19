package ai.speckitty.charter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Problem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ProblemsTest {

    @Test
    void aProblemCarriesTheReasonPhraseAndTheStatusAndNothingElse() {
        ResponseEntity<Problem> problem = Problems.of(HttpStatus.NOT_ACCEPTABLE, HttpHeaders.EMPTY);
        assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(problem.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(problem.getBody()).isEqualTo(new Problem("Not Acceptable", 406));
    }

    @Test
    void aProblemKeepsTheHeadersItIsGivenAndReplacesTheirContentType() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(java.util.Set.of(org.springframework.http.HttpMethod.GET));
        headers.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<Problem> problem = Problems.of(HttpStatus.METHOD_NOT_ALLOWED, headers);
        assertThat(problem.getHeaders().getFirst(HttpHeaders.ALLOW)).isEqualTo("GET");
        assertThat(problem.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(headers.getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void aStatusTheRegistryDoesNotNameIsTitledByItsNumber() {
        assertThat(Problems.title(HttpStatusCode.valueOf(599))).isEqualTo("HTTP 599");
    }
}
