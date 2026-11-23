package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.ReasonCode;
import java.util.Objects;

/**
 * A pack file that could not be read or validated, reported rather than thrown: at its file, and at the place inside
 * the file when the fault has one (an edge of a graph document, a field the schema refuses).
 *
 * @param file the pack-relative file name
 * @param pointer the RFC 6901 JSON pointer into the file, for example {@code /edges/3}; empty for the whole file
 * @param kind the code of the problem: a {@link PackCodes} constant, or the storage edge's {@code StorageCodes} one
 * @param reason the human-readable reason
 */
public record PackElementViolation(String file, String pointer, ReasonCode kind, String reason) {
    /**
     * Checks that every field is present and that the pointer is a JSON pointer.
     *
     * @param file the file name
     * @param pointer the JSON pointer into the file
     * @param kind the kind of problem
     * @param reason the reason
     * @throws IllegalArgumentException when the pointer is neither empty nor starts with {@code /}
     */
    public PackElementViolation {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(pointer, "pointer");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
        if (!pointer.isEmpty() && !pointer.startsWith("/")) {
            throw new IllegalArgumentException("A JSON pointer starts with '/': " + pointer);
        }
    }

    /**
     * A violation of the whole file: no place inside it is known.
     *
     * @param file the file name
     * @param kind the kind of problem
     * @param reason the reason
     * @return the violation, with the empty pointer
     */
    public static PackElementViolation wholeFile(String file, ReasonCode kind, String reason) {
        return new PackElementViolation(file, "", kind, reason);
    }
}
