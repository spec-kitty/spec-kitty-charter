package ai.speckitty.charter.activations.application;

import java.util.function.Supplier;

/**
 * The seam that makes an activation write exclude a pack registration (WP15's review, ADR-0065 D12): a write judges the
 * activation against the packs and stores it, and a registration judges every closure it joins against the stored
 * activations, so the two must not interleave. The packs slice's registry write lock does both, and the composition
 * root binds this port to it; an activation write holds it from its first read to its compare-and-set, so a registration
 * that runs meanwhile either sees the activation stored or is seen by the activation's own judgement.
 */
public interface WriteCoordination {

    /** No coordination: the work runs as it is. For a slice that has no pack registry to share. */
    WriteCoordination NONE = new WriteCoordination() {
        @Override
        public <T> T exclusively(Supplier<T> work) {
            return work.get();
        }
    };

    /**
     * Runs {@code work} excluding every other pack-domain write.
     *
     * @param work the write: its judgement against the packs and its store
     * @param <T> what it answers
     * @return what the work answers
     * @throws ai.speckitty.charter.kernel.transport.RetryLaterException when the registry is busy for longer than its
     *     wait ({@code 503}); the work did not run
     */
    <T> T exclusively(Supplier<T> work);

    /**
     * Runs a write that answers nothing.
     *
     * @param work the write
     */
    default void exclusively(Runnable work) {
        exclusively(() -> {
            work.run();
            return null;
        });
    }
}
