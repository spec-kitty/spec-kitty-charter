package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.metering.WorkMeter;

/**
 * What a call read, from a {@link WorkMeter} bound around it alone (#554, FR-016, #659): the walks of a pack tree, the
 * runs of the store half and the store's whole reads. A measurement counts nothing another test does, so two tests that
 * count at once never see each other's reads.
 *
 * @param walks the walks of a pack tree
 * @param runs the runs of the store half
 * @param reads the store's whole reads
 */
public record ReadCounters(long walks, long runs, long reads) {

    /**
     * What a call answered and what it read.
     *
     * @param value the answer
     * @param read what it read
     * @param <T> the answer's type
     */
    public record Measured<T>(T value, ReadCounters read) {}

    /**
     * What a step read.
     *
     * @param step the step
     * @return what it read
     */
    public static ReadCounters during(Runnable step) {
        return measured(() -> {
                    step.run();
                    return Boolean.TRUE;
                })
                .read();
    }

    /**
     * What a call answered and what it read.
     *
     * @param call the call
     * @param <T> the answer's type
     * @param <X> what the call may throw
     * @return the answer and what it read
     * @throws X what the call threw
     */
    public static <T, X extends Throwable> Measured<T> measured(ScopedValue.CallableOp<? extends T, X> call) throws X {
        WorkMeter.Measured<T> measured = WorkMeter.measure(call);
        return new Measured<>(measured.value(), of(measured.work()));
    }

    /**
     * The storage counts of a meter's work.
     *
     * @param work the work
     * @return its walks, runs and whole reads
     */
    public static ReadCounters of(WorkMeter.Counts work) {
        return new ReadCounters(
                work.of(StoreWork.TREE_WALK), work.of(StoreWork.STORE_RUN), work.of(StoreWork.WHOLE_READ));
    }
}
