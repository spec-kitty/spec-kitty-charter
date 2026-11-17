package ai.speckitty.charter.packs.domain;

import java.util.Objects;

/**
 * Validate mode, the use case of FR-010: reads a pack root in full and returns every finding with the classification
 * of every file, where a load stops at the first blocking finding of the load path.
 *
 * <p>It is a pure domain service over the {@link PackReading} port and owns no rule: the reader reads the pack once and
 * {@link PackPolicy#judge} decides. It is the seam the pack doctor (#115) composes, with a {@link PackRoot} whose
 * configured level is empty to judge a bare path as core's {@code validate_pack(path)} does. The validation edge's
 * {@code PackCheck} replaces it (#554, WP09).
 */
public final class PackValidation {

    private final PackReading reading;

    /**
     * A validation that reads packs through {@code reading}.
     *
     * @param reading the port that reads one pack root once
     */
    public PackValidation(PackReading reading) {
        this.reading = Objects.requireNonNull(reading, "reading");
    }

    /**
     * Validates one pack: every file of the pack is read and every rule runs.
     *
     * @param root the pack to validate
     * @return the classification of every file and every finding, in precedence order; a pack with no finding is
     *     valid
     */
    public PackVerdict validate(PackRoot root) {
        return PackPolicy.judge(reading.read(root));
    }
}
