package ai.speckitty.charter.domain;

import java.util.Objects;

/**
 * A load finding that does not stop the load. The detail names pack-relative paths only.
 *
 * @param code what was found
 * @param detail a sentence for the reader; it names pack-relative paths only
 */
public record Diagnostic(Code code, String detail) {

    /**
     * Diagnostic codes; part of the contract ({@code DiagnosticCode}). The {@link #token()} is the snake_case
     * wire spelling.
     */
    public enum Code {
        /**
         * The pack has no {@code pack.yaml}, or its {@code pack.yaml} declares no {@code pack_level}; the configured
         * level is used.
         */
        PACK_LEVEL_MISSING("pack_level_missing"),
        /** {@code pack.yaml} has a key the pack descriptor schema does not declare, a retired field included; it is ignored. */
        PACK_UNKNOWN_KEY("pack_unknown_key");

        private final String token;

        Code(String token) {
            this.token = token;
        }

        /**
         * The wire spelling of the code.
         *
         * @return the snake_case token, for example {@code pack_level_missing}
         */
        public String token() {
            return token;
        }
    }

    /**
     * Checks that both fields are present.
     *
     * @param code what was found
     * @param detail a sentence for the reader
     */
    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
    }
}
