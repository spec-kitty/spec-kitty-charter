package ai.speckitty.charter.web;

import ai.speckitty.charter.api.model.Problem;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The body of a failure that is not a charter refusal. A 500 is the {@code internal_error} refusal, as every 500
 * of the contract is a {@code CharterRefusal}. Any other status is a {@code Problem} of the status's title and
 * the status: no {@code code}, as no refusal applies, and no {@code detail} unless the service wrote it, so neither a
 * framework's wording nor an exception message reaches the wire. A refused request parameter is the one {@code 400}
 * with a detail ({@link InvalidParameter}, #411).
 */
public final class Problems {

    private Problems() {}

    /**
     * The problem response of {@code status}.
     *
     * @param status an error status, 4xx or 5xx
     * @param headers headers the response keeps, such as {@code Allow}; its content type is replaced
     * @return the {@code application/problem+json} response: a {@code CharterRefusal} for 500, a {@code Problem}
     *     otherwise
     */
    public static ResponseEntity<Object> of(HttpStatusCode status, HttpHeaders headers) {
        HttpHeaders kept = new HttpHeaders();
        kept.putAll(headers);
        kept.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        Object body = status.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()
                ? RefusalHandler.internalError().getBody()
                : new Problem(title(status), status.value());
        return ResponseEntity.status(status).headers(kept).body(body);
    }

    /**
     * The {@code 400} of a refused request parameter: a {@code Problem} without a code, whose detail the service wrote.
     *
     * @param detail the {@link InvalidParameter#detail()} of the parameter, or several joined
     * @return the {@code application/problem+json} response
     */
    public static ResponseEntity<Object> badRequest(String detail) {
        Problem problem = new Problem(title(HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST.value());
        problem.setDetail(detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * The reason phrase of a known status; {@code HTTP <n>} for a status the HTTP registry does not name.
     *
     * @param status the status
     * @return its title
     */
    public static String title(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "HTTP " + status.value() : known.getReasonPhrase();
    }
}
