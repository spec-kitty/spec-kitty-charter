package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Problem parity of the profile's activation route with the activation routes (fold A6). The activation set answers a
 * request that Spring refuses before the controller runs through advice scoped to its own controller
 * ({@code ActivationProblems}); the profile route is another controller, so its refusals come from the shared web
 * transport. A client must not be able to tell the two routes apart by the problem they answer: the same request fault
 * on {@code /activations/{id}/artifacts/directive/X} (the activation set's generic read) and on
 * {@code /activations/{id}/artifacts/agent_profile/X} (the profile route) answers the same status, the same content
 * type and the same body, and a refusal the service throws, the same.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ProfileProblemParityTest.TOKEN)
@AutoConfigureMockMvc
class ProfileProblemParityTest {

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String BEARER = "Bearer " + TOKEN;
    private static @Nullable Path root;

    @DynamicPropertySource
    static void anEmptyConfigRoot(DynamicPropertyRegistry registry) throws IOException {
        Path created = Files.createTempDirectory("charter-profile-problem-parity");
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

    private MvcResult answer(String path) throws Exception {
        return mvc.perform(get("/api/v1" + path).header("Authorization", BEARER))
                .andReturn();
    }

    /** The two routes' answers to the same fault, which must be the same in status, content type and body. */
    private void assertParity(String activations, String tail) throws Exception {
        MvcResult generic = answer("/activations/" + activations + "/artifacts/directive/X" + tail);
        MvcResult profile = answer("/activations/" + activations + "/artifacts/agent_profile/X" + tail);

        assertThat(profile.getResponse().getStatus())
                .as("the status of %s%s", activations, tail)
                .isEqualTo(generic.getResponse().getStatus());
        assertThat(profile.getResponse().getContentType())
                .as("the content type of %s%s", activations, tail)
                .isEqualTo(generic.getResponse().getContentType());
        assertThat(profile.getResponse().getContentAsString())
                .as("the body of %s%s", activations, tail)
                .isEqualTo(generic.getResponse().getContentAsString());
    }

    @Test
    void aDepthAboveTheMaximumIsTheSameBadRequestAsTheActivationRoutesAnswer() throws Exception {
        assertThat(answer("/activations/a-1/artifacts/agent_profile/X?depth=2")
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);

        assertParity("a-1", "?depth=2");
    }

    @Test
    void aNegativeDepthIsTheSameBadRequest() throws Exception {
        assertThat(answer("/activations/a-1/artifacts/agent_profile/X?depth=-1")
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);

        assertParity("a-1", "?depth=-1");
    }

    @Test
    void aDepthThatIsNoIntegerIsTheSameBadRequest() throws Exception {
        assertThat(answer("/activations/a-1/artifacts/agent_profile/X?depth=one")
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);

        assertParity("a-1", "?depth=one");
    }

    @Test
    void anActivationIdThatBreaksItsPatternIsTheSameBadRequest() throws Exception {
        assertThat(answer("/activations/-bad/artifacts/agent_profile/X")
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);

        assertParity("-bad", "");
    }

    @Test
    void anActivationThatIsNotStoredIsTheSameNotFoundRefusal() throws Exception {
        MvcResult profile = answer("/activations/nope/artifacts/agent_profile/X");

        assertThat(profile.getResponse().getStatus()).isEqualTo(404);
        assertThat(profile.getResponse().getContentAsString()).contains("\"code\":\"activation_not_found\"");
        assertParity("nope", "");
    }
}
