package ai.speckitty.charter.deployable.archfixtures;

import ai.speckitty.charter.kernel.metering.WorkMeter;

/** A class that measures work, as only tests may: the fixture the measure rule must refuse. */
public final class MeasuringFixture {

    private MeasuringFixture() {}

    /**
     * Measures nothing in particular.
     *
     * @return the answer of the measured call
     */
    public static String measured() {
        return WorkMeter.measure(() -> "measured").value();
    }
}
