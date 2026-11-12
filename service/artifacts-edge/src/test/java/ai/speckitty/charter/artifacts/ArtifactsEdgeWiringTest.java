package ai.speckitty.charter.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.artifacts.application.config.ArtifactsApplicationConfiguration;
import ai.speckitty.charter.artifacts.infra.activations.config.ArtifactsActivationsConfiguration;
import ai.speckitty.charter.artifacts.infra.api.config.ArtifactsApiConfiguration;
import ai.speckitty.charter.artifacts.infra.packs.config.ArtifactsPacksConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The edge module's rings: each ring's configuration is a Spring configuration that starts with the others in a slice
 * context, each once. The rings hold no bean of their own yet, so the context asks nothing of the packs or activations
 * slices; the reads that land in the rings add the beans they ask for here.
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
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ArtifactsApplicationConfiguration.class);
                    assertThat(context).hasSingleBean(ArtifactsApiConfiguration.class);
                    assertThat(context).hasSingleBean(ArtifactsPacksConfiguration.class);
                    assertThat(context).hasSingleBean(ArtifactsActivationsConfiguration.class);
                });
    }
}
