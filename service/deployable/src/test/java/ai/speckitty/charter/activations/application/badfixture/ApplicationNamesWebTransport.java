package ai.speckitty.charter.activations.application.badfixture;

import ai.speckitty.charter.web.Problems;
import org.springframework.http.HttpStatus;

/** An activations use case that names the shared web transport: the known-bad of the web ring rule. */
public final class ApplicationNamesWebTransport {

    private ApplicationNamesWebTransport() {}

    /**
     * A problem response built in the application ring.
     *
     * @return the title of a 400
     */
    public static String title() {
        return Problems.title(HttpStatus.BAD_REQUEST);
    }
}
