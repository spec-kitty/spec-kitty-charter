package ai.speckitty.charter.missions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.speckitty.charter.missions.application.config.MissionsApplicationConfiguration;
import ai.speckitty.charter.missions.infra.activations.config.MissionsActivationsConfiguration;
import ai.speckitty.charter.missions.infra.api.config.MissionsApiConfiguration;
import ai.speckitty.charter.missions.infra.packs.config.MissionsPacksConfiguration;
import ai.speckitty.charter.packs.application.MissionTypeHoldingRead;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The edge module's rings: each ring's configuration is a Spring configuration that starts with the others, given the
 * one bean of the packs slice the packs adapter asks (its composite read, which the deployable composes).
 */
class MissionsEdgeConfigurationsTest {

    @Test
    void eachRingsConfigurationStartsAsOneBeanAndHoldsNoOther() {
        new ApplicationContextRunner()
                .withBean(MissionTypeHoldingRead.class, () -> mock(MissionTypeHoldingRead.class))
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
