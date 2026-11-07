package ai.speckitty.charter.deployable.bdd;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** The clock of the registration jobs in the scenarios: the real time, moved forward by a step that makes an hour pass. */
final class ScenarioClock extends Clock {

    private volatile Duration shift = Duration.ZERO;

    void advance(Duration by) {
        shift = shift.plus(by);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(shift);
    }
}
