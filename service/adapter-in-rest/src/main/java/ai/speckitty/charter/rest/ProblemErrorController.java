package ai.speckitty.charter.rest;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The servlet container's error page, which a failure outside the Spring MVC handlers reaches (a filter that
 * fails, a {@code sendError}). It answers as {@link Problems} does for the error's status, in place of
 * Spring Boot's own error document ({@code timestamp}, {@code error}, {@code path}).
 */
@RestController
public class ProblemErrorController implements ErrorController {

    /** For Spring, which finds the controller by component scan. */
    public ProblemErrorController() {}

    /**
     * Answers the error the container forwarded.
     *
     * @param request the forwarded request, whose {@code jakarta.servlet.error.status_code} attribute names the
     *     status; 500 when it names none or a status that is no error
     * @return the {@code application/problem+json} response
     */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<Object> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = code instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        return Problems.of(HttpStatusCode.valueOf(status), HttpHeaders.EMPTY);
    }
}
