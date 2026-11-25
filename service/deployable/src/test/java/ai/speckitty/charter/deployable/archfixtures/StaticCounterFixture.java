package ai.speckitty.charter.deployable.archfixtures;

import java.util.concurrent.atomic.LongAdder;

/** A class with the two shapes of static counter the rule refuses: a final adder and a field that is not final. */
public final class StaticCounterFixture {

    static final LongAdder READS = new LongAdder();

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
        return ++walks;
    }
}
