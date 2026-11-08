package ai.speckitty.charter.activations.infra.storage;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * The activation documents in memory, for a service started without a configuration root, the unit tests and the
 * scenario suite. It keeps each document as the file store does, as the bytes of {@link ActivationFileCodec}, and reads
 * them back through the same codec, so a stored {@code schemaVersion: 1} document is answered as its version 2 upgrade
 * and only the next write stores version 2 (decision {@code DM-01M6RFGMDH859CJP3RHHF9YC3H}). Every compare-and-set is
 * decided inside {@link ConcurrentMap#compute}, which runs atomically per key, so there is no check-then-act: the
 * comparison and the write of one id are one step.
 */
public final class InMemoryActivationRepository implements ActivationRepository {

    private final ConcurrentMap<ActivationId, byte[]> stored = new ConcurrentHashMap<>();

    /** An empty repository. */
    public InMemoryActivationRepository() {}

    @Override
    public List<StoredActivation> list() {
        List<StoredActivation> listed = new ArrayList<>();
        stored.forEach((id, bytes) -> {
            try {
                listed.add(read(id, bytes));
            } catch (ActivationStorageException unreadable) {
                // Left out, as the file store leaves out a document that does not read.
            }
        });
        return List.copyOf(listed);
    }

    @Override
    public Optional<StoredActivation> find(ActivationId id) {
        return Optional.ofNullable(stored.get(id)).map(bytes -> read(id, bytes));
    }

    @Override
    public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
        StoredActivation fresh = StoredActivation.of(id, document);
        return stored.putIfAbsent(id, ActivationFileCodec.encode(document)) == null
                ? Optional.of(fresh)
                : Optional.empty();
    }

    @Override
    public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
        AtomicReference<CasResult> outcome = new AtomicReference<>();
        stored.compute(id, (key, current) -> {
            CasResult decided = Cas.replace(current(id, current), expected, StoredActivation.of(id, document));
            outcome.set(decided);
            return decided instanceof CasResult.Written ? ActivationFileCodec.encode(document) : current;
        });
        return outcome.get();
    }

    @Override
    public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
        AtomicReference<DeleteResult> outcome = new AtomicReference<>();
        stored.compute(id, (key, current) -> {
            DeleteResult decided = Cas.delete(current(id, current), expected);
            outcome.set(decided);
            return keptAfter(decided, current);
        });
        return outcome.get();
    }

    private static byte @Nullable [] keptAfter(DeleteResult decided, byte @Nullable [] current) {
        return decided instanceof DeleteResult.Deleted ? null : current;
    }

    /**
     * Holds bytes under an id as the store keeps them, bypassing every write and check: the scenario suite's and the
     * tests' seed of a stored document, such as one at {@code schemaVersion: 1}. Not part of the port.
     *
     * @param id the activation's id
     * @param bytes the stored form, read by {@link #find} as a file of the file store is
     */
    public void putStoredBytes(ActivationId id, byte[] bytes) {
        stored.put(id, bytes.clone());
    }

    /**
     * The bytes held under an id, which only a write changes. Not part of the port.
     *
     * @param id the activation's id
     * @return a copy of the stored form; empty when nothing is stored under the id
     */
    public Optional<byte[]> storedBytes(ActivationId id) {
        return Optional.ofNullable(stored.get(id)).map(byte[]::clone);
    }

    /** Forgets every activation: the scenario suite's reset between scenarios. Not part of the port. */
    public void clear() {
        stored.clear();
    }

    private static Optional<StoredActivation> current(ActivationId id, byte @Nullable [] bytes) {
        return Optional.ofNullable(bytes).map(held -> read(id, held));
    }

    private static StoredActivation read(ActivationId id, byte[] bytes) {
        return StoredActivation.of(id, ActivationFileCodec.decode(id, bytes));
    }
}
