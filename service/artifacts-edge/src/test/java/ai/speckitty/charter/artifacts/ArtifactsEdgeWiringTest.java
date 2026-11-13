package ai.speckitty.charter.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.artifacts.application.AssetReads;
import ai.speckitty.charter.artifacts.application.SkillReads;
import ai.speckitty.charter.artifacts.application.config.ArtifactsApplicationConfiguration;
import ai.speckitty.charter.artifacts.infra.activations.config.ArtifactsActivationsConfiguration;
import ai.speckitty.charter.artifacts.infra.api.AssetBodies;
import ai.speckitty.charter.artifacts.infra.api.AssetsController;
import ai.speckitty.charter.artifacts.infra.api.SkillBodies;
import ai.speckitty.charter.artifacts.infra.api.SkillsController;
import ai.speckitty.charter.artifacts.infra.api.config.ArtifactsApiConfiguration;
import ai.speckitty.charter.artifacts.infra.packs.PacksAssets;
import ai.speckitty.charter.artifacts.infra.packs.PacksSkills;
import ai.speckitty.charter.artifacts.infra.packs.config.ArtifactsPacksConfiguration;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The edge module's rings: each ring's configuration is a Spring configuration that starts with the others in a slice
 * context, each once, over the packs slice's read the packs adapters ask for (a stand-in that is never called). The
 * asset and skill reads are beans: the packs adapters are the application ring's ports, and the controllers and their
 * body mappers are the inbound adapter's.
 */
class ArtifactsEdgeWiringTest {

    @Test
    void theFourRingConfigurationsStartTogetherEachOnce() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        ArtifactsApplicationConfiguration.class,
                        ArtifactsApiConfiguration.class,
                        ArtifactsPacksConfiguration.class,
                        ArtifactsActivationsConfiguration.class)
                .withBean(ForeignArtifactRead.class, () -> Mockito.mock(ForeignArtifactRead.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ArtifactsApplicationConfiguration.class);
                    assertThat(context).hasSingleBean(ArtifactsApiConfiguration.class);
                    assertThat(context).hasSingleBean(ArtifactsPacksConfiguration.class);
                    assertThat(context).hasSingleBean(ArtifactsActivationsConfiguration.class);
                    assertThat(context.getBean(AssetReads.class)).isInstanceOf(PacksAssets.class);
                    assertThat(context).hasSingleBean(AssetsController.class);
                    assertThat(context).hasSingleBean(AssetBodies.class);
                    assertThat(context.getBean(SkillReads.class)).isInstanceOf(PacksSkills.class);
                    assertThat(context).hasSingleBean(SkillsController.class);
                    assertThat(context).hasSingleBean(SkillBodies.class);
                });
    }
}
