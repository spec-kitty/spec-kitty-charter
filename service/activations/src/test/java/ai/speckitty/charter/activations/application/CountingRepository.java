package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import java.util.Optional;

/**
 * An in-memory register that counts its compare-and-set writes, so a test pins that a no-op writes nothing, and that
 * can make the next write lose to another writer, as one that came in between the load and the write would.
 */
final class CountingRepository implements ActivationRepository {

    private final InMemoryActivationRepository delegate = new InMemoryActivationRepository();
    private int replaces;
    private boolean loseNext;

    @Override
    public Optional<StoredActivation> find(ActivationId id) {
        return delegate.find(id);
    }

    @Override
    public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
        return delegate.create(id, document);
    }

    @Override
    public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
        replaces++;
        if (loseNext) {
            loseNext = false;
            return new CasResult.Stale(find(id).orElseThrow().revision());
        }
        return delegate.replace(id, expected, document);
    }

    @Override
    public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
        return delegate.delete(id, expected);
    }

    /** The next write loses to another writer. */
    void loseNextWrite() {
        loseNext = true;
    }

    /**
     * How often a write was tried.
     *
     * @return the number of {@link #replace} calls
     */
    int replaces() {
        return replaces;
    }
}
