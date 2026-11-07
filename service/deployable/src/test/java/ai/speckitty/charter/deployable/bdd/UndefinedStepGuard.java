package ai.speckitty.charter.deployable.bdd;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStep;
import io.cucumber.plugin.event.TestStepFinished;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fails the contract suite when a scenario has an undefined or a pending step (decision
 * DM-01M6RN6GC7PJS8BGHMAYM4JSSW). Cucumber reports such a step as an aborted test, which the JUnit Platform counts as
 * skipped, so the build would stay green while a scenario asserts nothing. The listener records every step that
 * finished {@link Status#UNDEFINED} or {@link Status#PENDING} and, when the run finishes, fails it with the list of
 * scenario and step names. A {@code @WIP} scenario is not selected by {@link CharterContractSuite}, so it never trips
 * the guard. Registered as a plugin of {@link CharterContractSuite}.
 */
public final class UndefinedStepGuard implements ConcurrentEventListener {

    private static final Set<Status> NOT_RUN = Set.of(Status.UNDEFINED, Status.PENDING);

    private final List<String> unbound = new CopyOnWriteArrayList<>();

    /** For Cucumber, which instantiates a plugin by its class name. */
    public UndefinedStepGuard() {}

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestStepFinished.class, this::stepFinished);
        publisher.registerHandlerFor(TestRunFinished.class, this::runFinished);
    }

    private void stepFinished(TestStepFinished event) {
        Status status = event.getResult().getStatus();
        if (NOT_RUN.contains(status)) {
            unbound.add(event.getTestCase().getName() + ": " + status + " step '" + text(event.getTestStep()) + "'");
        }
    }

    private void runFinished(TestRunFinished event) {
        if (!unbound.isEmpty()) {
            throw new AssertionError("A contract scenario has an undefined or pending step; bind it or tag the scenario"
                    + " @WIP:\n  " + String.join("\n  ", unbound));
        }
    }

    private static String text(TestStep step) {
        return step instanceof PickleStepTestStep pickle ? pickle.getStep().getText() : step.getCodeLocation();
    }
}
