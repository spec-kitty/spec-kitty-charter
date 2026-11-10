package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.activations.infra.api.ActivationController;
import ai.speckitty.charter.profiles.infra.api.EffectiveAgentProfilesController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The routing of the activation-scoped reads of an artifact, through the whole service (fold A5). The contract maps
 * {@code /activations/{activationId}/artifacts/{kind}/{artifactId}} for a GET only, and the literal
 * {@code /activations/{activationId}/artifacts/agent_profile/{artifactId}} of the profiles slice maps the same URLs of a
 * profile, which Spring answers from the literal pattern, as {@link ProfileRoutingTest} pins for the pack route.
 *
 * <p>A GET on an {@code agent_profile} reaches the profiles slice's controller, which answers the winner with its body and
 * its governance; the generic handler carries no body and is not reached for that kind. A write on the path is a 405 and
 * the list route stays where it is.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ActivationRoutingTest.TOKEN)
@AutoConfigureMockMvc
class ActivationRoutingTest {

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String BEARER = "Bearer " + TOKEN;
    private static final String READ = "/api/v1/activations/a-1/artifacts/";
    private static @Nullable Path root;

    @DynamicPropertySource
    static void anEmptyConfigRoot(DynamicPropertyRegistry registry) throws IOException {
        Path created = Files.createTempDirectory("charter-activation-routing");
        root = created;
        registry.add("charter.config-root", created::toString);
    }

    @AfterAll
    static void deleteRoot() throws IOException {
        // Only the temporary directory this class created is ever removed: nothing is deleted when it was never made.
        if (root == null || !root.startsWith(Path.of(System.getProperty("java.io.tmpdir")))) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    /** The handler Spring's request mapping picks, asked of the mapping so that no filter or interceptor answers first. */
    private HandlerMethod handler(String method, String path) throws Exception {
        HandlerExecutionChain chain = mappings.getHandler(new MockHttpServletRequest(method, path));
        assertThat(chain).as("a handler for %s %s", method, path).isNotNull();
        assertThat(chain.getHandler()).isInstanceOf(HandlerMethod.class);
        return (HandlerMethod) chain.getHandler();
    }

    @Test
    void aGetOnAServedKindReachesTheGenericRead() throws Exception {
        for (String kind : new String[] {"directive", "tactic", "styleguide", "paradigm", "toolguide"}) {
            HandlerMethod routed = handler("GET", READ + kind + "/X");

            assertThat(routed.getBeanType()).as(kind).isEqualTo(ActivationController.class);
            assertThat(routed.getMethod().getName()).as(kind).isEqualTo("getEffectiveArtifact");
        }
    }

    @Test
    void aGetOnAnAgentProfileReachesTheProfileControllerAndNotTheGenericRead() throws Exception {
        HandlerMethod routed = handler("GET", READ + "agent_profile/java-jenny");

        assertThat(routed.getBeanType())
                .as("the literal path is preferred to the templated one")
                .isEqualTo(EffectiveAgentProfilesController.class);
        assertThat(routed.getMethod().getName()).isEqualTo("getEffectiveAgentProfile");
    }

    @Test
    void theListRouteStaysWhereItWasAndAWriteOnTheReadPathIsAMethodNotAllowed() throws Exception {
        HandlerMethod list = handler("GET", "/api/v1/activations/a-1/artifacts");

        assertThat(list.getMethod().getName()).isEqualTo("listEffectiveArtifacts");
        for (String kind : new String[] {"directive", "agent_profile"}) {
            mvc.perform(put(READ + kind + "/X")
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")));
            mvc.perform(delete(READ + kind + "/X").header("Authorization", BEARER))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
