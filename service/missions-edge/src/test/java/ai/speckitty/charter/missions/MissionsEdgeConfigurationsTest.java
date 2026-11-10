package ai.speckitty.charter.missions;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.missions.application.config.MissionsApplicationConfiguration;
import ai.speckitty.charter.missions.infra.activations.config.MissionsActivationsConfiguration;
import ai.speckitty.charter.missions.infra.api.config.MissionsApiConfiguration;
import ai.speckitty.charter.missions.infra.packs.config.MissionsPacksConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The skeleton of the edge module: each ring's configuration is a Spring configuration that starts on its own. */
class MissionsEdgeConfigurationsTest {

    @Test
    void eachRingsConfigurationStartsAsOneBeanAndHoldsNoOther() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        MissionsApplicationConfiguration.class,
                        MissionsApiConfiguration.class,
                        MissionsPacksConfiguration.class,
                        MissionsActivationsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MissionsApplicationConfiguration.class);
                    assertThat(context).hasSingleBean(MissionsApiConfiguration.class);
                    assertThat(context).hasSingleBean(MissionsPacksConfiguration.class);
                    assertThat(context).hasSingleBean(MissionsActivationsConfiguration.class);
                });
    }
}
