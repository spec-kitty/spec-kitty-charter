package ai.speckitty.charter.packs.domain;

import java.util.Objects;

/**
 * A pack file that could not be read or validated, reported rather than thrown.
 *
 * @param file the pack-relative file name
 * @param kind the kind of problem, such as {@code unreadable} or {@code schema}
 * @param reason the human-readable reason
 */
public record PackElementViolation(String file, String kind, String reason) {
    /**
     * Checks that every field is present.
     *
     * @param file the file name
     * @param kind the kind of problem
     * @param reason the reason
     */
    public PackElementViolation {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
    }
}
