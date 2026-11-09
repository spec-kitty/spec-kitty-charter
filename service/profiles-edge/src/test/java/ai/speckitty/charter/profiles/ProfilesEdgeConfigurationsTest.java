package ai.speckitty.charter.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.profiles.application.config.ProfilesApplicationConfiguration;
import ai.speckitty.charter.profiles.infra.activations.config.ProfilesActivationsConfiguration;
import ai.speckitty.charter.profiles.infra.api.config.ProfilesApiConfiguration;
import ai.speckitty.charter.profiles.infra.packs.config.ProfilesPacksConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The skeleton of the edge module: each ring's configuration is a Spring configuration that starts on its own. */
class ProfilesEdgeConfigurationsTest {

    @Test
    void eachRingsConfigurationStartsAsOneBeanAndHoldsNoOther() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        ProfilesApplicationConfiguration.class,
                        ProfilesApiConfiguration.class,
                        ProfilesPacksConfiguration.class,
                        ProfilesActivationsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProfilesApplicationConfiguration.class);
                    assertThat(context).hasSingleBean(ProfilesApiConfiguration.class);
                    assertThat(context).hasSingleBean(ProfilesPacksConfiguration.class);
                    assertThat(context).hasSingleBean(ProfilesActivationsConfiguration.class);
                });
    }
}
