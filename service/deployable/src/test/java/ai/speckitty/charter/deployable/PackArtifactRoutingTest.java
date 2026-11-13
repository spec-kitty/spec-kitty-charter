package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.artifacts.infra.api.AssetsController;
import ai.speckitty.charter.artifacts.infra.api.SkillsController;
import ai.speckitty.charter.packs.infra.api.ArtifactController;
import ai.speckitty.charter.packs.infra.api.PackRegistrationController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The routing of the pack tree's literal asset and skill paths beside the templated artifact paths, through the whole
 * service (slice A2, ADR-0074 decisions 10 and 11). The contract maps the literal {@code …/artifacts/asset},
 * {@code …/asset/{artifactId}}, {@code …/asset/{artifactId}/payload}, {@code …/artifacts/skill} and
 * {@code …/skill/{artifactId}} (GETs of the artifacts edge) over URLs the templated {@code …/{kind}/{artifactId}} and
 * {@code …/{kind}/{artifactId}/references} of the packs slice also match. Spring prefers the literal pattern for the
 * method it maps, so:
 *
 * <ul>
 *   <li>a GET on an asset or a skill reaches its literal read, never the templated {@code getPackArtifact}, which
 *       refuses both kinds;
 *   <li>the {@code /references} of every kind, an asset and a skill included, stays on {@code listArtifactReferences}:
 *       no literal path ends in {@code /references};
 *   <li>a payload and a references read are told apart by their last segment, so an asset whose id is
 *       {@code payload} or {@code references} is read like any other;
 *   <li>a DELETE on an asset or a skill stays on the generic removal, which keeps its own kind set (research R14).
 * </ul>
 *
 * <p>The handler is asked of the request mapping, so no filter, interceptor or empty configuration answers first. The
 * activation tree's twin is {@link ActivationRoutingTest}.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + PackArtifactRoutingTest.TOKEN)
class PackArtifactRoutingTest {

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String PACK = "/api/v1/packs/built-in/artifacts/";
    private static @Nullable Path root;

    /** One expected route: the request path under the pack's artifacts, the controller and the operation it reaches. */
    private record Route(String path, Class<?> controller, String operation) {}

    @DynamicPropertySource
    static void anEmptyConfigRoot(DynamicPropertyRegistry registry) throws IOException {
        Path created = Files.createTempDirectory("charter-pack-artifact-routing");
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
    @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    /** The handler Spring's request mapping picks for a request. */
    private HandlerMethod handler(String method, String path) throws Exception {
        HandlerExecutionChain chain = mappings.getHandler(new MockHttpServletRequest(method, path));
        assertThat(chain).as("a handler for %s %s", method, path).isNotNull();
        assertThat(chain.getHandler()).isInstanceOf(HandlerMethod.class);
        return (HandlerMethod) chain.getHandler();
    }

    private void assertRoutes(String method, List<Route> routes) throws Exception {
        for (Route route : routes) {
            HandlerMethod routed = handler(method, PACK + route.path());

            assertThat(routed.getBeanType()).as("%s %s", method, route.path()).isEqualTo(route.controller());
            assertThat(routed.getMethod().getName())
                    .as("%s %s", method, route.path())
                    .isEqualTo(route.operation());
        }
    }

    @Test
    void aGetOnAnAssetOrASkillReachesItsLiteralReadAndNotTheGenericRead() throws Exception {
        assertRoutes(
                "GET",
                List.of(
                        new Route("asset", AssetsController.class, "listPackAssets"),
                        new Route("asset/run", AssetsController.class, "getPackAsset"),
                        new Route("asset/run/payload", AssetsController.class, "getPackAssetPayload"),
                        new Route("skill", SkillsController.class, "listPackSkills"),
                        new Route("skill/land-pr", SkillsController.class, "getPackSkill")));
    }

    @Test
    void aGetOnAnotherKindStillReachesTheGenericRead() throws Exception {
        assertRoutes(
                "GET",
                List.of(
                        new Route("directive/X", ArtifactController.class, "getPackArtifact"),
                        new Route("procedure/P", ArtifactController.class, "getPackArtifact"),
                        new Route("mission_step_contract/implement", ArtifactController.class, "getPackArtifact")));
    }

    @Test
    void theReferencesOfEveryKindReachTheReferencesReadAndNoLiteralPathShadowsThem() throws Exception {
        assertRoutes(
                "GET",
                List.of(
                        new Route("directive/D/references", ArtifactController.class, "listArtifactReferences"),
                        new Route(
                                "agent_profile/java-jenny/references",
                                ArtifactController.class,
                                "listArtifactReferences"),
                        new Route("asset/run/references", ArtifactController.class, "listArtifactReferences"),
                        new Route("skill/land-pr/references", ArtifactController.class, "listArtifactReferences"),
                        new Route(
                                "mission_type/software-dev/references",
                                ArtifactController.class,
                                "listArtifactReferences")));
    }

    @Test
    void anAssetWhoseIdIsPayloadOrReferencesIsReadLikeAnyOther() throws Exception {
        assertRoutes(
                "GET",
                List.of(
                        new Route("asset/payload", AssetsController.class, "getPackAsset"),
                        new Route("asset/references", AssetsController.class, "getPackAsset"),
                        new Route("asset/payload/payload", AssetsController.class, "getPackAssetPayload"),
                        new Route("asset/references/payload", AssetsController.class, "getPackAssetPayload"),
                        new Route("asset/payload/references", ArtifactController.class, "listArtifactReferences"),
                        new Route("asset/references/references", ArtifactController.class, "listArtifactReferences")));
    }

    @Test
    void aDeleteOnAnAssetOrASkillStaysOnTheGenericRemoval() throws Exception {
        assertRoutes(
                "DELETE",
                List.of(
                        new Route("asset/run", PackRegistrationController.class, "deletePackArtifact"),
                        new Route("skill/land-pr", PackRegistrationController.class, "deletePackArtifact")));
    }
}
