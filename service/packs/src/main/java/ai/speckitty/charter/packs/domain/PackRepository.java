package ai.speckitty.charter.packs.domain;

/** The aggregating port that fetches a whole {@link Pack}: descriptor, readable tactics and read violations. */
public interface PackRepository {

    /**
     * Loads the pack. Partial-tolerant: an element that cannot be read is left out and reported through
     * {@link PackInspectionQuery}, and the rest of the pack is still returned.
     *
     * @param pack the pack's identity
     * @return the pack aggregate
     */
    Pack load(PackIdentifier pack);
}
