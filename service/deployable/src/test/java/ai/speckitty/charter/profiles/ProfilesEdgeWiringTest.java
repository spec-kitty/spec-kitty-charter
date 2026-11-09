package ai.speckitty.charter.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.deployable.CharterServiceApplication;
import ai.speckitty.charter.deployable.ConfigRootTestConfiguration;
import ai.speckitty.charter.profiles.application.config.ProfilesApplicationConfiguration;
import ai.speckitty.charter.profiles.infra.activations.config.ProfilesActivationsConfiguration;
import ai.speckitty.charter.profiles.infra.api.config.ProfilesApiConfiguration;
import ai.speckitty.charter.profiles.infra.packs.config.ProfilesPacksConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * The profiles edge module as the composition root wires it: the whole service starts with {@code :profiles-edge} on
 * its classpath, and the {@code config} package of each of the slice's four edge rings contributes its configuration.
 * Fails when {@code DeployableConfiguration} stops scanning one of them.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "charter.worktree-token=0123456789abcdef0123456789abcdef0123")
@Import(ConfigRootTestConfiguration.class)
class ProfilesEdgeWiringTest {

    @Autowired
    ApplicationContext context;

    @Test
    void theContextStartsWithTheProfilesEdgeOnItsClasspath() {
        assertThat(context.getId()).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }

    @Test
    void eachOfTheFourProfilesEdgeRingsContributesItsConfiguration() {
        assertThat(context.getBeansOfType(ProfilesApplicationConfiguration.class))
                .hasSize(1);
        assertThat(context.getBeansOfType(ProfilesApiConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(ProfilesPacksConfiguration.class)).hasSize(1);
        assertThat(context.getBeansOfType(ProfilesActivationsConfiguration.class))
                .hasSize(1);
    }
}
