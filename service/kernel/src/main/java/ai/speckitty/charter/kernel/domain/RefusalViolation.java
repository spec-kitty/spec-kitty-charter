package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One part of a request body the service refuses (ADR-0020 D10), as the {@code violations} of a
 * {@code 422 activation_invalid} name it, so a client need not parse the detail.
 *
 * @param pointer an RFC 6901 JSON Pointer into the request body, for example {@code /deactivated/tactic/0}; empty
 *     for the whole body
 * @param code a stable machine-readable reason, for example {@code unknown_artifact}: {@code ^[a-z][a-z0-9_]*$}
 * @param detail a sentence for the reader that explains this violation
 */
public record RefusalViolation(String pointer, String code, String detail) {

    private static final Pattern CODE_SHAPE = Pattern.compile("[a-z][a-z0-9_]*");

    /**
     * Checks the fields.
     *
     * @param pointer the JSON Pointer
     * @param code the reason
     * @param detail the explanation
     * @throws IllegalArgumentException when {@code code} is not a snake-case token
     */
    public RefusalViolation {
        Objects.requireNonNull(pointer, "pointer");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        if (!CODE_SHAPE.matcher(code).matches()) {
            throw new IllegalArgumentException("A violation code is a snake-case token, not '" + code + "'.");
        }
    }
}
