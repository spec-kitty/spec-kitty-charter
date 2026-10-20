package ai.speckitty.charter.domain;

/**
 * The port through which a pack's descriptor and tactics are read and written back.
 *
 * <p>Unlike {@link CharterPackRepository#load}, which is all-or-nothing, a fetch here is partial-tolerant and a write
 * replaces in place.
 */
public interface PackElementRepository {
    /**
     * Reads the pack's descriptor and tactics. Partial-tolerant: a file that cannot be read or validated is reported
     * as a violation on the snapshot, and the rest of the pack is still returned.
     *
     * @param location where the pack lives
     * @return the descriptor, the readable tactics and the violations
     */
    PackSnapshot fetch(PackLocator location);

    /**
     * Writes a tactic back, replacing the existing tactic with the same id. The adapter resolves where that tactic
     * lives and keeps the write inside the pack.
     *
     * @param location where the pack lives
     * @param tactic the tactic to write
     */
    void write(PackLocator location, Tactic tactic);

    /**
     * Writes the pack's descriptor back, replacing the existing one.
     *
     * @param location where the pack lives
     * @param descriptor the descriptor to write
     */
    void write(PackLocator location, PackDescriptor descriptor);
}
