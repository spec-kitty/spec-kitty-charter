package ai.speckitty.charter.domain;

import java.util.Objects;

/** A load finding that does not stop the load. The detail names pack-relative paths only. */
public record Diagnostic(Code code, String detail) {

    /** Diagnostic codes; part of the contract ({@code DiagnosticCode}). */
    public enum Code { PACK_LEVEL_MISSING }

    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
    }
}
