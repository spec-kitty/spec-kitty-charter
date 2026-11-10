package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The activation set is reserved one operation at a time (ADR-0020 D8): the controller carries no mark of its own, and an
 * operation that is not built carries one, so an operation is built by removing its mark alone. Every one of the fifteen
 * operations is built now (PR C built the effective graph and the effective action context, WP06 the read of one
 * artifact), so none is marked; the
 * interceptor stays for a reservation to come, which {@code ReservedOperationInterceptorTest} and the architecture rules
 * keep honest.
 */
class ActivationControllerTest {

    /** The operations the activation set builds: the register (#125, #360), the writes (#291) and the six reads. */
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
            "applyActivationPreset",
            "listEffectiveArtifacts",
            "getActivationSkippedReport",
            "getEffectiveGraph",
            "getEffectiveActionContext",
            "getEffectiveArtifact");

    private static List<Method> operations() {
        return Arrays.stream(ActivationApi.class.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .toList();
    }

    @Test
    void everyOneOfTheFifteenOperationsIsOverriddenAndNoneIsMarkedReserved() throws NoSuchMethodException {
        assertThat(operations()).hasSize(15);
        assertThat(operations()).extracting(Method::getName).containsExactlyInAnyOrderElementsOf(BUILT);
        for (Method operation : operations()) {
            Method overridden =
                    ActivationController.class.getMethod(operation.getName(), operation.getParameterTypes());
            assertThat(overridden.getDeclaringClass()).isEqualTo(ActivationController.class);
            assertThat(overridden.isAnnotationPresent(ReservedOperation.class))
                    .as("%s is marked reserved", operation.getName())
                    .isFalse();
        }
        assertThat(ActivationController.class.isAnnotationPresent(ReservedOperation.class))
                .as("the controller carries no mark of its own")
                .isFalse();
    }
}
