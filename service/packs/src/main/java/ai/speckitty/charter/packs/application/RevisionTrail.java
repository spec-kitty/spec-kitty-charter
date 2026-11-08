package ai.speckitty.charter.packs.application;

/**
 * Told by a write, under the locks it ran with, which revision of the pack it found and which it left, so the caller can
 * enter both in the audit log (NFR-010). The revisions are the ones the write itself matched and swapped, not ones read
 * around it.
 */
@FunctionalInterface
public interface RevisionTrail {

    /** A trail nobody listens to. */
    RevisionTrail NONE = (before, after) -> {};

    /**
     * The write read the stored revision to judge its {@code If-Match}: told whether the precondition then passes or not,
     * so a removal that is refused (409, 412, 422) can still enter the revision it found. Not told when the write is
     * refused before it reads the revision (a missing {@code If-Match}, a pack, kind or artifact that is not there). The
     * default does nothing.
     *
     * @param before the stored revision the write found
     */
    default void found(PackRevision before) {}

    /**
     * The write changed the pack.
     *
     * @param before the revision the write found, the one an {@code If-Match} was judged against
     * @param after the revision it left
     */
    void moved(PackRevision before, PackRevision after);
}
