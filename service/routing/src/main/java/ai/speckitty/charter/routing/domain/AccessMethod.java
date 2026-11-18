package ai.speckitty.charter.routing.domain;

import java.util.Arrays;
import java.util.Optional;

/** How a catalog's source was reached (ADR-0080 decision 5): through an API, a dataset, or by hand. */
public enum AccessMethod {
    /** Through an API. */
    API("api"),
    /** From a dataset. */
    DATASET("dataset"),
    /** By hand. */
    MANUAL("manual");

    private final String token;

    AccessMethod(String token) {
        this.token = token;
    }

    /**
     * The method's token, as a catalog writes it.
     *
     * @return the token
     */
    public String token() {
        return token;
    }

    /**
     * The method a token names.
     *
     * @param token the token a catalog writes
     * @return the method, or empty when no method has that token
     */
    public static Optional<AccessMethod> ofToken(String token) {
        return Arrays.stream(values()).filter(m -> m.token.equals(token)).findFirst();
    }
}
