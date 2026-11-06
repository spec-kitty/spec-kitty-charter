package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.FakeAvailablePacks;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The activation set is reserved one operation at a time (ADR-0020 D8): the controller carries no mark of its own, and
 * each of the fourteen operations it overrides carries one until it is built, so an operation is built by removing
 * its mark alone. The register, its closure read, the four per-artifact writes and the preset write are built; the four
 * effective reads stay reserved.
 */
class ActivationControllerTest {

    /** The operations the activation register (#125, #360), its per-artifact writes (#291) and the preset write build. */
    private static final Set<String> BUILT = Set.of(
            "createActivation",
            "getActivation",
            "replaceActivation",
            "deleteActivation",
            "listActivationPacks",
            "selectActivationArtifact",
            "unselectActivationArtifact",
            "deactivateActivationArtifact",
            "reactivateActivationArtifact",
            "applyActivationPreset");

    private static ActivationController controller() {
        return StandaloneActivationMvc.controller(
                new InMemoryActivationRepository(),
                new FakeAvailablePacks(ClosureSnapshots.snapshot().build()));
    }

    private static List<Method> operations() {
        return Arrays.stream(ActivationApi.class.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .toList();
    }

    @Test
    void everyOneOfTheFourteenOperationsIsOverriddenAndOnlyTheFourUnbuiltAreMarked() throws NoSuchMethodException {
        assertThat(operations()).hasSize(14);
        assertThat(operations()).extracting(Method::getName).containsAll(BUILT);
        for (Method operation : operations()) {
            Method overridden =
                    ActivationController.class.getMethod(operation.getName(), operation.getParameterTypes());
            assertThat(overridden.getDeclaringClass()).isEqualTo(ActivationController.class);
            assertThat(overridden.isAnnotationPresent(ReservedOperation.class))
                    .as("%s is marked reserved", operation.getName())
                    .isEqualTo(!BUILT.contains(operation.getName()));
        }
    }

    @Test
    void anOperationReachedPastTheInterceptorAnswers501() {
        assertThatThrownBy(() -> controller().getEffectiveGraph("a1", null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        notBuilt -> assertThat(notBuilt.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED));
    }
}
