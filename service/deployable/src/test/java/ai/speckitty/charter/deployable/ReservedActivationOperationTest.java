package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.activations.application.ActivationExclusionService;
import ai.speckitty.charter.activations.application.ActivationPresetService;
import ai.speckitty.charter.activations.application.ActivationSelectionService;
import ai.speckitty.charter.activations.application.ActivationService;
import ai.speckitty.charter.activations.infra.api.ActivationChangeMapper;
import ai.speckitty.charter.activations.infra.api.ActivationController;
import ai.speckitty.charter.activations.infra.api.ActivationMapper;
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
 * although the interface method that carries the mapping is unmarked. The interceptor answers before the request is
 * bound, so a request the operation would refuse is answered {@code 501}, not {@code 400}. No reserved operation takes
 * a body any more ({@code ReservedOperationCoverageTest.noReservedOperationTakesABody}), so the request the binding
 * would refuse is a query parameter of the wrong type.
 */
class ReservedActivationOperationTest {

    /** The reserved effective context, with a depth that cannot be bound to its integer parameter. */
    private static final String UNBINDABLE_CONTEXT =
            "/api/v1/activations/a1/mission-types/software-dev/actions/implement/context?depth=deep";

    /** A controller whose use cases a reserved operation never reaches. */
    private static ActivationController controller() {
        ActivationMapper mapper = new ActivationMapper("/api/v1");
        return new ActivationController(
                mock(ActivationService.class),
                mock(ActivationSelectionService.class),
                mock(ActivationExclusionService.class),
                mock(ActivationPresetService.class),
                mapper,
                new ActivationChangeMapper(mapper));
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
    void aReservedActivationOperationAnswers501BeforeItsRequestIsBound() throws Exception {
        mvc.perform(get(UNBINDABLE_CONTEXT).accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotImplemented());
    }

    @Test
    void aReservedActivationReadAnswers501() throws Exception {
        mvc.perform(get("/api/v1/activations/a1/skipped").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void withoutTheInterceptorTheSameRequestIsRefusedWhenItIsBound() throws Exception {
        MockMvcBuilders.standaloneSetup(controller())
                .build()
                .perform(get(UNBINDABLE_CONTEXT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
