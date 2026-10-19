package ai.speckitty.charter.rest;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private final SupportedMethods supported;

    /**
     * For Spring, which finds the controller by component scan.
     *
     * @param supported the methods the service supports
     */
    ProblemErrorController(SupportedMethods supported) {
        this.supported = supported;
    }

    /**
     * Answers the error the container forwarded.
     *
     * @param request the forwarded request, whose {@code jakarta.servlet.error.status_code} attribute names the
     *     status; 404 when it names none, as the container forwarded nothing and the request asked for a path
     *     the service does not have; 500 when it names a status that is no error
     * @param response the response, whose {@code Allow} header a container-level 405 gets corrected
     * @return the {@code application/problem+json} response
     */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<Object> error(HttpServletRequest request, HttpServletResponse response) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = code == null ? 404 : code instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        if (status == 405 && response.containsHeader(HttpHeaders.ALLOW)) {
            // Only Tomcat's refusal of TRACE reaches here with an Allow header, which lists every method the
            // dispatcher servlet class implements. A method Spring MVC refuses does not come through the container.
            response.setHeader(HttpHeaders.ALLOW, supported.header());
        }
        return Problems.of(HttpStatusCode.valueOf(status), HttpHeaders.EMPTY);
    }
}
