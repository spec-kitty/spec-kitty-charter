package ai.speckitty.charter.missions;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.deployable.CharterServiceApplication;
import ai.speckitty.charter.deployable.ConfigRootTestConfiguration;
import ai.speckitty.charter.missions.application.config.MissionsApplicationConfiguration;
import ai.speckitty.charter.missions.infra.activations.config.MissionsActivationsConfiguration;
import ai.speckitty.charter.missions.infra.api.config.MissionsApiConfiguration;
import ai.speckitty.charter.missions.infra.packs.config.MissionsPacksConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * The missions edge module as the composition root wires it: the whole service starts with {@code :missions-edge} on
 * its classpath, and the {@code config} package of each of the slice's four edge rings contributes its configuration.
 * Fails when {@code DeployableConfiguration} stops scanning one of them.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "charter.worktree-token=0123456789abcdef0123456789abcdef0123")
@Import(ConfigRootTestConfiguration.class)
class MissionsEdgeWiringTest {

    @Autowired
    ApplicationContext context;

    @Test
    void theContextStartsWithTheMissionsEdgeOnItsClasspath() {
        assertThat(context.getId()).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }

    @Test
    void eachOfTheFourMissionsEdgeRingsContributesItsConfiguration() {
        assertThat(context.getBeansOfType(MissionsApplicationConfiguration.class))
                .hasSize(1);
        assertThat(context.getBeansOfType(MissionsApiConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(MissionsPacksConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(MissionsActivationsConfiguration.class))
                .hasSize(1);
    }
}
