package ai.speckitty.charter.activations.infra.storage;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * The activation documents in memory, for a service started without a configuration root, the unit tests and the
 * scenario suite. Every compare-and-set is decided inside {@link ConcurrentMap#compute}, which runs atomically per
 * key, so there is no check-then-act: the comparison and the write of one id are one step.
 */
public final class InMemoryActivationRepository implements ActivationRepository {

    private final ConcurrentMap<ActivationId, StoredActivation> stored = new ConcurrentHashMap<>();

    /** An empty repository. */
    public InMemoryActivationRepository() {}

    @Override
    public Optional<StoredActivation> find(ActivationId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
        StoredActivation fresh = StoredActivation.of(id, document);
        return stored.putIfAbsent(id, fresh) == null ? Optional.of(fresh) : Optional.empty();
    }

    @Override
    public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
        AtomicReference<CasResult> outcome = new AtomicReference<>();
        stored.compute(id, (key, current) -> {
            CasResult decided = Cas.replace(Optional.ofNullable(current), expected, StoredActivation.of(id, document));
            outcome.set(decided);
            return decided instanceof CasResult.Written written ? written.stored() : current;
        });
        return outcome.get();
    }

    @Override
    public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
        AtomicReference<DeleteResult> outcome = new AtomicReference<>();
        stored.compute(id, (key, current) -> {
            DeleteResult decided = Cas.delete(Optional.ofNullable(current), expected);
            outcome.set(decided);
            return keptAfter(decided, current);
        });
        return outcome.get();
    }

    private static @Nullable StoredActivation keptAfter(DeleteResult decided, @Nullable StoredActivation current) {
        return decided instanceof DeleteResult.Deleted ? null : current;
    }

    /** Forgets every activation: the scenario suite's reset between scenarios. Not part of the port. */
    public void clear() {
        stored.clear();
    }
}
