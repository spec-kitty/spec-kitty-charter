package ai.speckitty.charter.kernel.metering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkMeterTest {

    private enum Fruit implements WorkUnit {
        APPLE,
        PEAR
    }

    private enum Tool implements WorkUnit {
        HAMMER
    }

    @Test
    void aMeasuredCallCountsEachUnitItDidAndAnswersItsValue() {
        // No meter is bound here: the count does nothing, and reaches no later meter.
        WorkMeter.count(Fruit.APPLE);

        WorkMeter.Measured<String> measured = WorkMeter.measure(() -> {
            WorkMeter.count(Fruit.APPLE);
            WorkMeter.count(Fruit.APPLE);
            WorkMeter.count(Tool.HAMMER);
            return "done";
        });

        assertThat(measured.value()).isEqualTo("done");
        assertThat(measured.work().of(Fruit.APPLE))
                .as("the two counts inside the call, and not the one made before any meter was bound")
                .isEqualTo(2);
        assertThat(measured.work().of(Tool.HAMMER))
                .as("a unit of another module's enum, counted apart")
                .isOne();
        assertThat(measured.work().of(Fruit.PEAR))
                .as("a unit not done counts zero")
                .isZero();
    }

    @Test
    void aNestedMeasurementCountsOnTheInnerMeterAloneAndTheOuterGoesOnAfterIt() {
        AtomicReference<WorkMeter.Counts> inner = new AtomicReference<>();

        WorkMeter.Measured<Boolean> outer = WorkMeter.measure(() -> {
            WorkMeter.count(Fruit.APPLE);
            inner.set(WorkMeter.measure(() -> {
                        WorkMeter.count(Fruit.PEAR);
                        return Boolean.TRUE;
                    })
                    .work());
            WorkMeter.count(Fruit.APPLE);
            return Boolean.TRUE;
        });

        assertThat(inner.get().of(Fruit.PEAR)).isOne();
        assertThat(inner.get().of(Fruit.APPLE)).isZero();
        assertThat(outer.work().of(Fruit.APPLE)).isEqualTo(2);
        assertThat(outer.work().of(Fruit.PEAR)).isZero();
    }

    @Test
    void workOnAnotherThreadIsNotCounted() throws Exception {
        WorkMeter.Measured<Boolean> measured = WorkMeter.measure(() -> {
            Thread other = new Thread(() -> WorkMeter.count(Fruit.APPLE));
            other.start();
            other.join();
            WorkMeter.count(Fruit.PEAR);
            return Boolean.TRUE;
        });

        assertThat(measured.work().of(Fruit.APPLE)).isZero();
        assertThat(measured.work().of(Fruit.PEAR))
                .as("the positive control on the same call")
                .isOne();
    }

    @Test
    void aMeasuredCallThatThrowsRethrowsItsFailure() {
        assertThatThrownBy(() -> WorkMeter.measure(() -> {
                    throw new IOException("unreadable");
                }))
                .isInstanceOf(IOException.class)
                .hasMessage("unreadable");
    }
}
