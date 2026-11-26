package ai.speckitty.charter.kernel.metering;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts the units of work one call does, for the caller that binds a meter around it with {@link #measure}: the read
 * budgets that hold a surface to one walk, one store run and one whole read (#554), and a request to one in-force
 * resolution (NFR-003). Kept on purpose as a measurement point for those budgets (ADR-0075 AA5 amendment, #659).
 *
 * <p>A meter counts only the work done inside {@link #measure} on the thread that called it, and on the forks of a
 * structured task scope opened there, which inherit the binding (hence a concurrent map of adders); work handed to any
 * other thread is not counted, so a budget asserts each of its units at least once. With no meter bound, {@link #count}
 * does nothing. Nothing is kept between two measurements, so two calls measured at once never see each other's work.
 */
public final class WorkMeter {

    private static final ScopedValue<WorkMeter> BOUND = ScopedValue.newInstance();

    private final Map<WorkUnit, LongAdder> counts = new ConcurrentHashMap<>();

    private WorkMeter() {}

    /**
     * The work a measured call did, by unit.
     *
     * @param counts how many of each unit were done; a unit not done is absent and counts zero
     */
    public record Counts(Map<WorkUnit, Long> counts) {

        /**
         * Copies the map.
         *
         * @param counts how many of each unit were done
         */
        public Counts {
            counts = Map.copyOf(counts);
        }

        /**
         * How many of a unit were done.
         *
         * @param unit the unit
         * @return the count, zero when none was done
         */
        public long of(WorkUnit unit) {
            return counts.getOrDefault(Objects.requireNonNull(unit, "unit"), 0L);
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

    /**
     * Counts one unit of work on the meter bound to this thread, if one is.
     *
     * @param unit the unit
     */
    public static void count(WorkUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (BOUND.isBound()) {
            BOUND.get().counts.computeIfAbsent(unit, any -> new LongAdder()).increment();
        }
    }

    /**
     * Runs {@code call} with a fresh meter bound, and answers what it answered and the work it did on this thread. A
     * measurement inside another counts on the inner meter alone.
     *
     * @param call the call to measure
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
        Map<WorkUnit, Long> now = new HashMap<>();
        counts.forEach((unit, adder) -> now.put(unit, adder.sum()));
        return new Counts(now);
    }
}
