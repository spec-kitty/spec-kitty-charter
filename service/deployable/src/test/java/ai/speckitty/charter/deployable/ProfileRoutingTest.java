package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.packs.infra.api.ArtifactController;
import ai.speckitty.charter.profiles.infra.api.AgentProfilesController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * The routing of the literal profile path beside the generic artifact paths, through the whole service (fold A5). The
 * contract has the literal {@code /packs/{packId}/artifacts/agent_profile/{artifactId}} (a GET) and the templated
 * {@code /packs/{packId}/artifacts/{kind}/{artifactId}} (a GET and a DELETE) over the same URLs, and Spring prefers the
 * literal pattern for the method it maps. So a GET on a profile must reach the profile controller, a GET on another kind
 * must still reach the generic controller, a DELETE and the {@code /references} of a profile stay on the generic
 * handlers, and a method no path maps is a 405, not a read by the wrong handler.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ProfileRoutingTest.TOKEN)
@AutoConfigureMockMvc
class ProfileRoutingTest {

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String BEARER = "Bearer " + TOKEN;
    private static final String PROFILES = "/api/v1/packs/built-in/artifacts/agent_profile/java-jenny";
    private static @Nullable Path root;

    @DynamicPropertySource
    static void packWithAProfileAndADirective(DynamicPropertyRegistry registry) throws IOException {
        Path created = Files.createTempDirectory("charter-profile-routing");
        root = created;
        write(created, "packs/built-in/pack.yaml", """
                pack_id: 01ARWG13C000000000000000FG
                pack_version: 1.0.0
                parent_pack: null
                name: built-in
                pack_level: built-in
                """);
        write(created, "packs/built-in/agent_profiles/java-jenny.agent.yaml", """
                profile-id: java-jenny
                name: Java Jenny
                role: implementer
                purpose: Deliver Java.
                specialization:
                  primary-focus: Java
                """);
        write(created, "packs/built-in/directives/X.directive.yaml", """
                schema_version: '1.0'
                id: X
                title: X
                intent: X
                enforcement: required
                """);
        registry.add("charter.config-root", created::toString);
    }

    private static void write(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @AfterAll
    static void deletePacks() throws IOException {
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
    void aGetOnTheLiteralProfilePathReachesTheProfileControllerAndAnswersTheProfile() throws Exception {
        assertThat(handler("GET", PROFILES).getBeanType()).isEqualTo(AgentProfilesController.class);

        mvc.perform(get(PROFILES).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(header().exists("Charter-Source-Fingerprint"))
                .andExpect(header().exists("ETag"));
    }

    @Test
    void aGetOnAnotherKindStillReachesTheGenericArtifactController() throws Exception {
        HandlerMethod routed = handler("GET", "/api/v1/packs/built-in/artifacts/directive/X");

        assertThat(routed.getBeanType()).isEqualTo(ArtifactController.class);
        assertThat(routed.getMethod().getName()).isEqualTo("getPackArtifact");
    }

    @Test
    void theReferencesOfAProfileStayOnTheGenericReferencesRead() throws Exception {
        HandlerMethod routed = handler("GET", PROFILES + "/references");

        assertThat(routed.getBeanType()).isEqualTo(ArtifactController.class);
        assertThat(routed.getMethod().getName()).isEqualTo("listArtifactReferences");
    }

    @Test
    void aDeleteOnAProfileStaysOnTheGenericRemoval() throws Exception {
        HandlerMethod routed = handler("DELETE", PROFILES);

        assertThat(routed.getBeanType().getSimpleName()).isEqualTo("PackRegistrationController");
        assertThat(routed.getMethod().getName()).isEqualTo("deletePackArtifact");
    }

    @Test
    void aPutOnAProfileIsAMethodNotAllowedAndNeverReachesTheProfileController() throws Exception {
        mvc.perform(put(PROFILES)
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")));
    }
}
