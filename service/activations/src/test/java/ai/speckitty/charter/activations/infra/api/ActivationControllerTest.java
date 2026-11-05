package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The activation set is reserved one operation at a time (ADR-0020 D8): the controller carries no mark of its own, and
 * each of the fourteen operations it overrides carries one, so an operation is built by removing its mark alone.
 */
class ActivationControllerTest {

    private static List<Method> operations() {
        return Arrays.stream(ActivationApi.class.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .toList();
    }

    @Test
    void theControllerIsNotReservedWhole() {
        assertThat(ActivationController.class.isAnnotationPresent(ReservedOperation.class))
                .isFalse();
    }

    @Test
    void everyOneOfTheFourteenOperationsIsOverriddenAndMarked() throws NoSuchMethodException {
        assertThat(operations()).hasSize(14);
        for (Method operation : operations()) {
            Method overridden =
                    ActivationController.class.getMethod(operation.getName(), operation.getParameterTypes());
            assertThat(overridden.getDeclaringClass()).isEqualTo(ActivationController.class);
            assertThat(overridden.isAnnotationPresent(ReservedOperation.class))
                    .as("%s is marked reserved", operation.getName())
                    .isTrue();
        }
    }

    @Test
    void anOperationReachedPastTheInterceptorAnswers501() {
        assertThatThrownBy(() -> new ActivationController().getActivation("a1", null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        notBuilt -> assertThat(notBuilt.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED));
    }
}
