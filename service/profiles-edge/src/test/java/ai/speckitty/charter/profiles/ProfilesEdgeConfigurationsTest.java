package ai.speckitty.charter.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.profiles.application.EffectiveProfiles;
import ai.speckitty.charter.profiles.application.ProfileFixtures;
import ai.speckitty.charter.profiles.application.ProfileRead;
import ai.speckitty.charter.profiles.application.ProfileReadService;
import ai.speckitty.charter.profiles.application.ProfileSource;
import ai.speckitty.charter.profiles.application.config.ProfilesApplicationConfiguration;
import ai.speckitty.charter.profiles.infra.activations.ActivationsEffectiveProfiles;
import ai.speckitty.charter.profiles.infra.activations.config.ProfilesActivationsConfiguration;
import ai.speckitty.charter.profiles.infra.api.AgentProfilesController;
import ai.speckitty.charter.profiles.infra.api.EffectiveAgentProfilesController;
import ai.speckitty.charter.profiles.infra.api.EffectiveProfileDtos;
import ai.speckitty.charter.profiles.infra.api.EffectiveProfileProblems;
import ai.speckitty.charter.profiles.infra.api.ProfileDtos;
import ai.speckitty.charter.profiles.infra.api.config.ProfilesApiConfiguration;
import ai.speckitty.charter.profiles.infra.packs.PacksProfileSource;
import ai.speckitty.charter.profiles.infra.packs.config.ProfilesPacksConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JacksonModule;

/** Each ring's configuration is a Spring configuration that starts on its own, over the bean its ring asks for. */
class ProfilesEdgeConfigurationsTest {

    private static final ProfileSource SOURCE = (pack, profileId, depth) ->
            new ProfileRead(ProfileFixtures.leanProfile(pack.value(), profileId), "r", "f", List.of(), List.of());

    @Test
    void theApplicationRingMakesItsServiceABeanOverThePort() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProfilesApplicationConfiguration.class)
                .withBean(ProfileSource.class, () -> SOURCE)
                .withBean(EffectiveProfiles.class, () -> ProfileFixtures.NO_ACTIVATIONS)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProfileReadService.class);
                });
    }

    @Test
    void theInboundAdapterMakesItsControllerItsWireMapperAndItsJsonModuleBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProfilesApiConfiguration.class)
                .withBean(
                        ProfileReadService.class, () -> new ProfileReadService(SOURCE, ProfileFixtures.NO_ACTIVATIONS))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AgentProfilesController.class);
                    assertThat(context).hasSingleBean(EffectiveAgentProfilesController.class);
                    assertThat(context).hasSingleBean(EffectiveProfileDtos.class);
                    assertThat(context).hasSingleBean(EffectiveProfileProblems.class);
                    assertThat(context).hasSingleBean(ProfileDtos.class);
                    assertThat(context).hasSingleBean(JacksonModule.class);
                });
    }

    @Test
    void thePacksAdapterMakesItsSourceABeanOverThePacksRead() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProfilesPacksConfiguration.class)
                // Only the wiring is under test: the bean the adapter asks for is a stand-in that is never called.
                .withBean(ForeignArtifactRead.class, () -> Mockito.mock(ForeignArtifactRead.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PacksProfileSource.class);
                    assertThat(context.getBean(ProfileSource.class)).isInstanceOf(PacksProfileSource.class);
                });
    }

    @Test
    void theActivationsAdapterMakesItsPortABeanOverTheEffectiveRead() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProfilesActivationsConfiguration.class)
                // Only the wiring is under test: the bean the adapter asks for is a stand-in that is never called.
                .withBean(EffectiveReadService.class, () -> Mockito.mock(EffectiveReadService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ActivationsEffectiveProfiles.class);
                    assertThat(context.getBean(EffectiveProfiles.class))
                            .isInstanceOf(ActivationsEffectiveProfiles.class);
                });
    }
}
