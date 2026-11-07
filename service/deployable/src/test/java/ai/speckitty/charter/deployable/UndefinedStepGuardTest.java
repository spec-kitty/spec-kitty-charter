package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.deployable.bdd.UndefinedStepGuard;
import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.Step;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStepFinished;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The contract suite's guard (decision DM-01M6RN6GC7PJS8BGHMAYM4JSSW): Cucumber reports an undefined or pending step
 * as a skipped test, so the build would stay green; the guard fails the run instead, naming each scenario and step. It
 * is fed synthetic events here; {@code CharterContractSuite} registers it as a plugin.
 */
class UndefinedStepGuardTest {

    private final Map<Class<?>, Consumer<Object>> registered = new HashMap<>();

    private final EventPublisher publisher = new EventPublisher() {
        @Override
        public <T> void registerHandlerFor(Class<T> eventType, EventHandler<T> handler) {
            registered.put(eventType, event -> handler.receive(eventType.cast(event)));
        }

        @Override
        public <T> void removeHandlerFor(Class<T> eventType, EventHandler<T> handler) {
            registered.remove(eventType);
        }
    };

    private UndefinedStepGuard guard() {
        UndefinedStepGuard guard = new UndefinedStepGuard();
        guard.setEventPublisher(publisher);
        return guard;
    }

    private void publish(Object event) {
        registered.get(event.getClass()).accept(event);
    }

    private static <T> T stub(Class<T> type, Map<String, Object> answers) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> answers.get(method.getName())));
    }

    private static TestStepFinished finished(String scenario, String step, Status status) {
        TestCase testCase = stub(TestCase.class, Map.of("getName", scenario));
        Step pickled = stub(Step.class, Map.of("getText", step));
        PickleStepTestStep testStep = stub(PickleStepTestStep.class, Map.of("getStep", pickled));
        return new TestStepFinished(Instant.EPOCH, testCase, testStep, new Result(status, Duration.ZERO, null));
    }

    private static TestRunFinished runFinished() {
        return new TestRunFinished(Instant.EPOCH, new Result(Status.PASSED, Duration.ZERO, null));
    }

    @Test
    void anUndefinedStepFailsTheRunNamingTheScenarioAndTheStep() {
        guard();
        publish(finished("Selecting a mission type", "I PUT \"/a\" with the stored ETag", Status.PASSED));
        publish(finished(
                "Seeding a version 1 document", "the stored file of the activation \"a-1\" holds:", Status.UNDEFINED));

        assertThatThrownBy(() -> publish(runFinished()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Seeding a version 1 document")
                .hasMessageContaining("UNDEFINED")
                .hasMessageContaining("the stored file of the activation \"a-1\" holds:")
                .hasMessageNotContaining("Selecting a mission type");
    }

    @Test
    void aPendingStepFailsTheRunToo() {
        guard();
        publish(finished("A promise", "the service does it later", Status.PENDING));

        assertThatThrownBy(() -> publish(runFinished()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("the service does it later");
    }

    @Test
    void aRunWhoseStepsAllPassedOrFailedIsLeftToItsOwnResult() {
        guard();
        publish(finished("A scenario", "a step", Status.PASSED));
        publish(finished("A failing scenario", "a step that fails", Status.FAILED));
        publish(finished("After a failure", "a step after it", Status.SKIPPED));

        assertThatCode(() -> publish(runFinished())).doesNotThrowAnyException();
        assertThat(registered).containsOnlyKeys(TestStepFinished.class, TestRunFinished.class);
    }
}
