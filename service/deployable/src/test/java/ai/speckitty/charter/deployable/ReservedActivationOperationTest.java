package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.activations.infra.api.ActivationController;
import ai.speckitty.charter.api.ActivationApi;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
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

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ActivationController())
            .addInterceptors(new ReservedOperationInterceptor())
            .build();

    @Test
    void theRoutedHandlerIsTheControllersMarkedOverrideNotTheUnmarkedInterfaceMethod() throws Exception {
        Object routed = MockMvcBuilders.standaloneSetup(new ActivationController())
                .build()
                .perform(get("/api/v1/activations/a1").accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getHandler();

        assertThat(routed).isInstanceOf(HandlerMethod.class);
        HandlerMethod handler = (HandlerMethod) routed;
        assertThat(handler.getMethod()).isEqualTo(ActivationController.class.getMethod("getActivation", String.class));
        assertThat(ActivationApi.class
                        .getMethod("getActivation", String.class)
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
        mvc.perform(post("/api/v1/activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void aReservedActivationReadAnswers501() throws Exception {
        mvc.perform(get("/api/v1/activations/a1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void withoutTheInterceptorTheSameBodyIsRefusedWhenItIsBound() throws Exception {
        MockMvcBuilders.standaloneSetup(new ActivationController())
                .build()
                .perform(post("/api/v1/activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest());
    }
}
