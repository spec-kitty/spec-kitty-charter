package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import org.junit.jupiter.api.Test;

/**
 * Cucumber runs the {@code @After} hooks of every glue class in descending {@code order}, and in no defined order among
 * equals. A scenario that ends on a {@code 202} still has a registration job writing into the scenario's packs, so the
 * hook that waits for the jobs must run before the one that deletes the packs; with equal orders the delete could win
 * and fail on a directory the job was still filling.
 */
class HookOrderTest {

    @Test
    void theHookThatDrainsTheRegistrationJobsRunsBeforeTheOneThatDeletesThePacks() throws NoSuchMethodException {
        int drains = After.class
                .cast(PackRegistrationSteps.class.getMethod("putTheSeamsBack").getAnnotation(After.class))
                .order();
        int deletes = After.class
                .cast(CharterSteps.class.getMethod("deletePacks").getAnnotation(After.class))
                .order();

        assertThat(drains)
                .as("order of the drain hook; a higher order runs first in an After hook")
                .isGreaterThan(deletes);
    }
}
