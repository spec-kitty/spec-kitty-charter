package ai.speckitty.charter.packs.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A pack file with what it is: a kind, or the reason it is not validated (#554, S6). The one reader's census lists every
 * regular file it classifies with one, and validate mode's verdict shows them.
 *
 * @param file the pack-relative path
 * @param kind the file kind; empty for an excluded file
 * @param exclusionClass why the file is excluded; empty for a classified file
 */
public record ClassifiedFile(String file, Optional<String> kind, Optional<String> exclusionClass) {
    /**
     * Checks that exactly one of kind and exclusion class is set.
     *
     * @param file the pack-relative path
     * @param kind the file kind, possibly empty
     * @param exclusionClass the exclusion class, possibly empty
     */
    public ClassifiedFile {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(exclusionClass, "exclusionClass");
        if (kind.isPresent() == exclusionClass.isPresent()) {
            throw new IllegalArgumentException("exactly one of kind and exclusionClass must be set: " + file);
        }
    }
}
