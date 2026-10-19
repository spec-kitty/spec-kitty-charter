package ai.speckitty.charter.rest;

import ai.speckitty.charter.api.model.Problem;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The {@code Problem} body of a failure that is not a charter refusal: the title and the status of the HTTP
 * status and nothing else. No {@code code}, as no refusal applies, and no {@code detail}, so neither a
 * framework's wording nor an exception message reaches the wire.
 */
final class Problems {

    private Problems() {}

    /**
     * The problem response of {@code status}.
     *
     * @param status an error status, 4xx or 5xx
     * @param headers headers the response keeps, such as {@code Allow}; its content type is replaced
     * @return the {@code application/problem+json} response
     */
    static ResponseEntity<Problem> of(HttpStatusCode status, HttpHeaders headers) {
        HttpHeaders kept = new HttpHeaders();
        kept.putAll(headers);
        kept.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ResponseEntity.status(status).headers(kept).body(new Problem(title(status), status.value()));
    }

    /** The reason phrase of a known status; {@code HTTP <n>} for a status the HTTP registry does not name. */
    static String title(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "HTTP " + status.value() : known.getReasonPhrase();
    }
}
