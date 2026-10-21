package ai.speckitty.charter.packs.domain;

import java.util.List;

/** The query port that reports what a partial-tolerant load of a pack had to leave out. */
public interface PackInspectionQuery {

    /**
     * The elements of the pack that could not be read or validated.
     *
     * @param pack the pack's identity
     * @return the violations, empty when every element was readable
     */
    List<PackElementViolation> partialReads(PackIdentifier pack);
}
