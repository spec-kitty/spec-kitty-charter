package ai.speckitty.charter.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.artifacts.application.AssetReads;
import ai.speckitty.charter.artifacts.application.config.ArtifactsApplicationConfiguration;
import ai.speckitty.charter.artifacts.infra.activations.config.ArtifactsActivationsConfiguration;
import ai.speckitty.charter.artifacts.infra.api.AssetBodies;
import ai.speckitty.charter.artifacts.infra.api.AssetsController;
import ai.speckitty.charter.artifacts.infra.api.config.ArtifactsApiConfiguration;
import ai.speckitty.charter.artifacts.infra.packs.PacksAssets;
import ai.speckitty.charter.artifacts.infra.packs.config.ArtifactsPacksConfiguration;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The edge module's rings: each ring's configuration is a Spring configuration that starts with the others in a slice
 * context, each once, over the packs slice's read the packs adapter asks for (a stand-in that is never called). The
 * asset reads are beans: the packs adapter is the application ring's port, and the controller and its body mapper are
 * the inbound adapter's.
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
                });
    }
}
