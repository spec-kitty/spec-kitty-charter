package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.ReasonCode;
import java.util.Objects;

/**
 * A pack file that could not be read or validated, reported rather than thrown.
 *
 * @param file the pack-relative file name
 * @param kind the code of the problem: a {@link PackCodes} constant, or the storage edge's {@code StorageCodes} one
 * @param reason the human-readable reason
 */
public record PackElementViolation(String file, ReasonCode kind, String reason) {
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
