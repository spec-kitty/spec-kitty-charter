package ai.speckitty.charter.deployable.archfixtures;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * A class with the shapes of static mutable state the rule refuses: a final adder, atomic arrays and accumulators, and
 * a field that is not final.
 */
public final class StaticCounterFixture {

    static final LongAdder READS = new LongAdder();
    static final AtomicIntegerArray SLOTS = new AtomicIntegerArray(1);
    static final AtomicLongArray CELLS = new AtomicLongArray(1);
    static final AtomicReferenceArray<String> TAGS = new AtomicReferenceArray<>(1);
    static final LongAccumulator PEAK = new LongAccumulator(Math::max, 0);
    static final DoubleAccumulator MEAN = new DoubleAccumulator(Double::sum, 0);

    @SuppressWarnings("NonFinalStaticField")
    static int walks;

    private StaticCounterFixture() {}

    /**
     * Counts one read and one walk.
     *
     * @return the walks so far
     */
    public static int count() {
        READS.increment();
        SLOTS.incrementAndGet(0);
        CELLS.incrementAndGet(0);
        TAGS.set(0, "read");
        PEAK.accumulate(walks);
        MEAN.accumulate(walks);
        return ++walks;
    }
}
