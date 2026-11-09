package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.transport.ReservedOperation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

/**
 * A mark on a controller method that overrides a generated API interface method is honoured (ADR-0020 D8): Spring routes
 * the request to the controller's own method, whose {@link ReservedOperation} the interceptor reads, although the
 * interface method that carries the mapping is unmarked. The interceptor answers before the request is bound, so a request
 * the operation would refuse is answered {@code 501}, not {@code 400}.
 *
 * <p>No operation of the activation set is reserved any more (PR C built the last two, IC-11), so the property is shown on
 * a planted interface and controller shaped like the generated ones: an interface that carries the mapping and a controller
 * that overrides it with a mark. It stays for a reservation to come, as the interceptor does.
 */
class ReservedInterfaceOverrideTest {

    private static final String UNBINDABLE = "/api/v1/things/a1?count=deep";

    /** The generated API's shape: the interface carries the mapping, and carries no mark. */
    interface ThingApi {

        @GetMapping("/api/v1/things/{id}")
        ResponseEntity<String> read(
                @PathVariable("id") String id, @RequestParam(value = "count", defaultValue = "0") Integer count);
    }

    /** A controller that reserves its one operation by marking the override. */
    @RestController
    static final class ReservedThingController implements ThingApi {

        @Override
        @ReservedOperation
        public ResponseEntity<String> read(String id, Integer count) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReservedThingController())
            .addInterceptors(new ReservedOperationInterceptor())
            .build();

    @Test
    void theRoutedHandlerIsTheControllersMarkedOverrideNotTheUnmarkedInterfaceMethod() throws Exception {
        Object routed = MockMvcBuilders.standaloneSetup(new ReservedThingController())
                .build()
                .perform(get("/api/v1/things/a1").accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getHandler();

        assertThat(routed).isInstanceOf(HandlerMethod.class);
        HandlerMethod handler = (HandlerMethod) routed;
        assertThat(handler.getMethod())
                .isEqualTo(ReservedThingController.class.getMethod("read", String.class, Integer.class));
        assertThat(ThingApi.class
                        .getMethod("read", String.class, Integer.class)
                        .isAnnotationPresent(ReservedOperation.class))
                .as("the interface method that carries the mapping is unmarked")
                .isFalse();
        assertThat(ReservedThingController.class.isAnnotationPresent(ReservedOperation.class))
                .as("the controller carries no mark of its own")
                .isFalse();
        assertThatThrownBy(() -> new ReservedOperationInterceptor()
                        .preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        notBuilt -> assertThat(notBuilt.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED));
    }

    @Test
    void aReservedOperationAnswers501BeforeItsRequestIsBound() throws Exception {
        mvc.perform(get(UNBINDABLE).accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotImplemented());
    }

    @Test
    void aReservedReadAnswers501() throws Exception {
        mvc.perform(get("/api/v1/things/a1").accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotImplemented());
    }

    @Test
    void withoutTheInterceptorTheSameRequestIsRefusedWhenItIsBound() throws Exception {
        MockMvcBuilders.standaloneSetup(new ReservedThingController())
                .build()
                .perform(get(UNBINDABLE).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
