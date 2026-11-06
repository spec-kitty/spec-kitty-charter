package ai.speckitty.charter.activations.infra.storage;

import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.ActivationRepository.DeleteResult;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import java.util.Optional;

/**
 * The compare-and-set decisions both adapters share. Pure: each adapter calls them where its own atomicity holds (the
 * in-memory adapter inside {@code compute}, the file adapter under the id's lock) and carries out what they decide.
 */
final class Cas {

    private Cas() {}

    /**
     * Whether, and how, a replace writes.
     *
     * @param current the stored activation, if any
     * @param expected the revision the caller read, or empty for the last writer wins
     * @param next the activation to store
     * @return {@code Written} when the write goes ahead, else {@code Stale} or {@code Missing}
     */
    static CasResult replace(Optional<StoredActivation> current, Optional<Revision> expected, StoredActivation next) {
        if (current.isEmpty()) {
            return expected.isPresent() ? new CasResult.Missing() : new CasResult.Written(next, true);
        }
        Revision stored = current.get().revision();
        return expected.filter(e -> !e.equals(stored)).isPresent()
                ? new CasResult.Stale(stored)
                : new CasResult.Written(next, false);
    }

    /**
     * Whether a delete goes ahead.
     *
     * @param current the stored activation, if any
     * @param expected the revision the caller read, or empty for an unconditional delete
     * @return {@code Deleted} when the delete goes ahead, else {@code Stale} or {@code Missing}
     */
    static DeleteResult delete(Optional<StoredActivation> current, Optional<Revision> expected) {
        if (current.isEmpty()) {
            return new DeleteResult.Missing();
        }
        Revision stored = current.get().revision();
        return expected.filter(e -> !e.equals(stored)).isPresent()
                ? new DeleteResult.Stale(stored)
                : new DeleteResult.Deleted();
    }
}
