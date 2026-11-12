package ai.speckitty.charter.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.artifacts.application.config.ArtifactsApplicationConfiguration;
import ai.speckitty.charter.artifacts.infra.activations.config.ArtifactsActivationsConfiguration;
import ai.speckitty.charter.artifacts.infra.api.config.ArtifactsApiConfiguration;
import ai.speckitty.charter.artifacts.infra.packs.config.ArtifactsPacksConfiguration;
import ai.speckitty.charter.deployable.CharterServiceApplication;
import ai.speckitty.charter.deployable.ConfigRootTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * The artifacts edge module as the composition root wires it: the whole service starts with {@code :artifacts-edge} on
 * its classpath, and the {@code config} package of each of the edge's four rings contributes its configuration. Fails
 * when {@code DeployableConfiguration} stops scanning one of them.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "charter.worktree-token=0123456789abcdef0123456789abcdef0123")
@Import(ConfigRootTestConfiguration.class)
class ArtifactsEdgeDeployableWiringTest {

    @Autowired
    ApplicationContext context;

    @Test
    void eachOfTheFourArtifactsEdgeRingsContributesItsConfiguration() {
        assertThat(context.getBeansOfType(ArtifactsApplicationConfiguration.class))
                .hasSize(1);
        assertThat(context.getBeansOfType(ArtifactsApiConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(ArtifactsPacksConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(ArtifactsActivationsConfiguration.class))
                .hasSize(1);
    }
}
