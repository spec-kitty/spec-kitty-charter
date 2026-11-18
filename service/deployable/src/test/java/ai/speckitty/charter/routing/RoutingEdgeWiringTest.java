package ai.speckitty.charter.routing;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.deployable.CharterServiceApplication;
import ai.speckitty.charter.deployable.ConfigRootTestConfiguration;
import ai.speckitty.charter.routing.application.config.RoutingApplicationConfiguration;
import ai.speckitty.charter.routing.infra.activations.config.RoutingActivationsConfiguration;
import ai.speckitty.charter.routing.infra.api.config.RoutingApiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * The routing edge module as the composition root wires it: the whole service starts with {@code :routing-edge} on its
 * classpath, and the {@code config} package of each of the slice's three edge rings contributes its configuration.
 * Fails when {@code DeployableConfiguration} stops scanning one of them.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "charter.worktree-token=0123456789abcdef0123456789abcdef0123")
@Import(ConfigRootTestConfiguration.class)
class RoutingEdgeWiringTest {

    @Autowired
    ApplicationContext context;

    @Test
    void eachOfTheThreeRoutingEdgeRingsContributesItsConfiguration() {
        assertThat(context.getBeansOfType(RoutingApplicationConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(RoutingApiConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(RoutingActivationsConfiguration.class)).hasSize(1);
    }
}
