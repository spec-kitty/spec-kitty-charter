package ai.speckitty.charter.packvalidation.badfixture;

import java.net.http.HttpClient;

/** A pack-validation class that opens an HTTP client: the module checks files, it never calls out. */
public final class ValidationSpeaksHttp {

    private ValidationSpeaksHttp() {}

    /** Builds the client the rule must refuse. */
    public static HttpClient client() {
        return HttpClient.newHttpClient();
    }
}
