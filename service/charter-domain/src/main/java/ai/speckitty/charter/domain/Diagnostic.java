package ai.speckitty.charter.domain;

import java.util.Objects;

/** A load finding that does not stop the load. The detail names pack-relative paths only. */
public record Diagnostic(Code code, String detail) {

    /**
     * Diagnostic codes; part of the contract ({@code DiagnosticCode}). The {@link #token()} is the snake_case
     * wire spelling.
     */
    public enum Code {
        PACK_LEVEL_MISSING("pack_level_missing");

        private final String token;

        Code(String token) {
            this.token = token;
        }

        /** The wire spelling of the code. */
        public String token() {
            return token;
        }
    }

    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
    }
}
