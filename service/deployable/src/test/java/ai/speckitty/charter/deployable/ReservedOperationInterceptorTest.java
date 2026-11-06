package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.transport.ReservedOperation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/**
 * The reserved-operation answer is decided per handler method (ADR-0020 D8): a method marked
 * {@link ReservedOperation} answers {@code 501}, and so does every method of a marked type; anything else passes on
 * to argument resolution.
 */
class ReservedOperationInterceptorTest {

    private final ReservedOperationInterceptor interceptor = new ReservedOperationInterceptor();

    @Test
    void everyMethodOfAMarkedTypeAnswers501() {
        assertThatThrownBy(() -> preHandle(new ReservedController(), "read"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        notBuilt -> assertThat(notBuilt.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED));
    }

    @Test
    void onAnUnmarkedTypeOnlyTheMarkedMethodAnswers501() throws NoSuchMethodException {
        assertThatThrownBy(() -> preHandle(new PartlyReservedController(), "reserved"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        notBuilt -> assertThat(notBuilt.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED));
        assertThat(preHandle(new PartlyReservedController(), "built"))
                .as("the other operation of the same controller is served")
                .isTrue();
    }

    @Test
    void anUnmarkedTypeWithNoMarkedMethodPasses() throws NoSuchMethodException {
        assertThat(preHandle(new BuiltController(), "read")).isTrue();
    }

    @Test
    void aHandlerThatIsNoHandlerMethodPasses() {
        assertThat(interceptor.preHandle(
                        new MockHttpServletRequest(), new MockHttpServletResponse(), new ResourceHttpRequestHandler()))
                .isTrue();
    }

    private boolean preHandle(Object controller, String method) throws NoSuchMethodException {
        HandlerMethod handler =
                new HandlerMethod(controller, controller.getClass().getMethod(method));
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);
    }

    /** A controller reserved whole. */
    @ReservedOperation
    static final class ReservedController {
        public String read() {
            return "read";
        }
    }

    /** A controller that serves one operation and reserves another. */
    static final class PartlyReservedController {
        public String built() {
            return "built";
        }

        @ReservedOperation
        public String reserved() {
            return "reserved";
        }
    }

    /** A controller with no reserved operation. */
    static final class BuiltController {
        public String read() {
            return "read";
        }
    }
}
