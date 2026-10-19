package ai.speckitty.charter.rest;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Tomcat's error report, replaced: the servlet container answers a request it rejects before Spring MVC sees it
 * (a malformed path, an encoded slash, a missing {@code Host}, an oversized header) with this valve's body, the
 * closed {@code Problem} of the status as {@code application/problem+json}. Nothing of the container is named in
 * it: no server version, no exception, no stack trace.
 */
final class ProblemErrorReportValve extends ErrorReportValve {

    private final Supplier<ObjectMapper> json;

    /**
     * @param json the application's JSON mapper, read when the first report is written, not before, so the
     *     valve can be built while the web server starts
     */
    ProblemErrorReportValve(Supplier<ObjectMapper> json) {
        this.json = json;
    }

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int status = response.getStatus();
        // As the base class: nothing on a non-error, nothing over a body already written, one report per response.
        if (status < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        AtomicBoolean ioAllowed = new AtomicBoolean(false);
        response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, ioAllowed);
        if (!ioAllowed.get()) {
            return;
        }
        try {
            Object problem = Problems.of(HttpStatusCode.valueOf(status), HttpHeaders.EMPTY)
                    .getBody();
            String body = json.get().writeValueAsString(problem);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            Writer writer = response.getReporter();
            if (writer != null) {
                writer.write(body);
                response.finishResponse();
            }
        } catch (IOException | RuntimeException unwritable) {
            // The client has gone or the mapper failed; the status line is all the response can still carry.
            container.getLogger().warn("The problem body of a " + status + " could not be written", unwritable);
        }
    }
}
