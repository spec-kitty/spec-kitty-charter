package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.activations.application.ActivationIdMint;
import ai.speckitty.charter.activations.application.ActivationService;
import ai.speckitty.charter.activations.application.AvailablePacks;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.infra.api.ActivationController;
import ai.speckitty.charter.activations.infra.api.ActivationMapper;
import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

/**
 * A mark on a controller method that overrides a generated {@link ActivationApi} method is honoured (ADR-0020 D8):
 * Spring routes the request to the controller's own method, whose {@link ReservedOperation} the interceptor reads,
 * although the interface method that carries the mapping is unmarked. The interceptor answers before the body is
 * bound, so a body the operation would refuse is answered {@code 501}, not {@code 400}.
 */
class ReservedActivationOperationTest {

    private static final String SELECTION = "/api/v1/activations/a1/selections/directive/D1";

    /** A controller whose use cases a reserved operation never reaches: every port they use fails when called. */
    private static ActivationController controller() {
        ActivationService service = new ActivationService(
                untouched(ActivationRepository.class), untouched(AvailablePacks.class), new ActivationIdMint());
        return new ActivationController(service, new ActivationMapper("/api/v1"));
    }

    /** A port that fails the test when any of its methods is called: a reserved operation reaches no use case. */
    private static <T> T untouched(Class<T> port) {
        return port.cast(
                Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[] {port}, (proxy, method, arguments) -> {
                    throw new AssertionError(
                            "A reserved operation reached " + port.getSimpleName() + "." + method.getName());
                }));
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller())
            .addInterceptors(new ReservedOperationInterceptor())
            .build();

    @Test
    void theRoutedHandlerIsTheControllersMarkedOverrideNotTheUnmarkedInterfaceMethod() throws Exception {
        Object routed = MockMvcBuilders.standaloneSetup(controller())
                .build()
                .perform(get("/api/v1/activations/a1/graph").accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getHandler();

        assertThat(routed).isInstanceOf(HandlerMethod.class);
        HandlerMethod handler = (HandlerMethod) routed;
        assertThat(handler.getMethod())
                .isEqualTo(ActivationController.class.getMethod("getEffectiveGraph", String.class, String.class));
        assertThat(ActivationApi.class
                        .getMethod("getEffectiveGraph", String.class, String.class)
                        .isAnnotationPresent(ReservedOperation.class))
                .as("the generated interface method that carries the mapping is unmarked")
                .isFalse();
        assertThat(ActivationController.class.isAnnotationPresent(ReservedOperation.class))
                .as("the controller carries no mark of its own")
                .isFalse();
        assertThatThrownBy(() -> new ReservedOperationInterceptor()
                        .preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        notBuilt -> assertThat(notBuilt.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED));
    }

    @Test
    void aReservedActivationOperationAnswers501BeforeItsBodyIsRead() throws Exception {
        mvc.perform(put(SELECTION)
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void aReservedActivationReadAnswers501() throws Exception {
        mvc.perform(get("/api/v1/activations/a1/skipped").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void withoutTheInterceptorTheSameBodyIsRefusedWhenItIsBound() throws Exception {
        MockMvcBuilders.standaloneSetup(controller())
                .build()
                .perform(put(SELECTION)
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest());
    }
}
