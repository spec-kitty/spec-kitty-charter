package ai.speckitty.charter.packvalidation.badfixture;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/** A pack-validation class that fetches over HTTP the old way, a remote schema say: the module never calls out. */
public final class ValidationOpensAUrl {

    private ValidationOpensAUrl() {}

    /** Opens the connection the rule must refuse; a {@link URI} alone is allowed. */
    public static HttpURLConnection open(URI location) throws IOException {
        return (HttpURLConnection) location.toURL().openConnection();
    }
}
