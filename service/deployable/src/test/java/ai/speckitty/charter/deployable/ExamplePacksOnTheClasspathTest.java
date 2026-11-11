package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import org.junit.jupiter.api.Test;

/**
 * The example packs (ADR-0070) reach this module as a test-fixtures jar; {@link ExamplePacks} copies them out of it, so
 * every location is a real directory holding its descriptor.
 */
class ExamplePacksOnTheClasspathTest {

    @Test
    void theThreePacksAreRealDirectoriesOutsideTheJar() {
        assertThat(ExamplePacks.packs())
                .extracting(PackLocation::name)
                .containsExactly(ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT);
        assertThat(ExamplePacks.packs())
                .allSatisfy(location ->
                        assertThat(location.root().resolve("pack.yaml")).isRegularFile());
    }
}
