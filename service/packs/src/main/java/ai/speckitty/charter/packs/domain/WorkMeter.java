package ai.speckitty.charter.packs.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts the units of work a read does, for the caller that binds a meter around it: the read budgets of #554 (one walk,
 * one store run and one whole read per surface) and NFR-003 (one in-force resolution per request). A meter counts only
 * the work done inside {@link #measure} on that thread; with no meter bound, {@link #count} does nothing. Nothing is
 * kept between two measurements, so two reads measured at once never see each other's work (ADR-0075 AA5, #659).
 */
public final class WorkMeter {

    /** A unit of work a read does. */
    public enum Work {
        /** A whole read of a pack: {@code FilesystemPackStore.read} and its overloads. */
        WHOLE_READ,
        /** A file parsed by a lookup by id. */
        LOOKUP_PARSE,
        /** A walk of a pack tree. */
        TREE_WALK,
        /** A run of the store half of a read ({@code StoreRead}). */
        STORE_RUN,
        /** An in-force resolution. */
        RESOLUTION,
        /** A node visited by a walk of an in-force resolution's override graph. */
        RESOLUTION_VISIT
    }

    /**
     * The work done, by unit.
     *
     * @param counts how many units of each kind were done; a kind not done counts zero
     */
    public record Counts(Map<Work, Long> counts) {

        /**
         * Copies the map.
         *
         * @param counts how many units of each kind were done
         */
        public Counts {
            counts = Map.copyOf(counts);
        }

        /**
         * How many units of a kind were done.
         *
         * @param work the kind
         * @return the count, zero when none was done
         */
        public long of(Work work) {
            return counts.getOrDefault(Objects.requireNonNull(work, "work"), 0L);
        }
    }

    /**
     * What a measured call answered and the work it did.
     *
     * @param value the answer
     * @param work the work done
     * @param <T> the answer's type
     */
    public record Measured<T>(T value, Counts work) {}

    private static final ScopedValue<WorkMeter> BOUND = ScopedValue.newInstance();

    private final Map<Work, LongAdder> counts = new EnumMap<>(Work.class);

    private WorkMeter() {
        for (Work work : Work.values()) {
            counts.put(work, new LongAdder());
        }
    }

    private LongAdder adder(Work work) {
        return Objects.requireNonNull(counts.get(work), "every kind of work has an adder");
    }

    /**
     * Counts one unit of work on the meter bound to this thread, if one is.
     *
     * @param work the kind of work
     */
    public static void count(Work work) {
        if (BOUND.isBound()) {
            BOUND.get().adder(work).increment();
        }
    }

    /**
     * Runs {@code call} with a fresh meter bound, and answers what it answered and the work it did on this thread.
     *
     * @param call the work to measure
     * @param <T> the answer's type
     * @param <X> what {@code call} may throw
     * @return the answer and the work
     * @throws X what {@code call} threw
     */
    public static <T, X extends Throwable> Measured<T> measure(ScopedValue.CallableOp<? extends T, X> call) throws X {
        Objects.requireNonNull(call, "call");
        WorkMeter meter = new WorkMeter();
        T value = ScopedValue.where(BOUND, meter).call(call);
        return new Measured<>(value, meter.snapshot());
    }

    private Counts snapshot() {
        Map<Work, Long> now = new EnumMap<>(Work.class);
        for (Work work : Work.values()) {
            now.put(work, adder(work).sum());
        }
        return new Counts(now);
    }
}
