package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import org.junit.jupiter.api.Test;

/**
 * The example packs (ADR-0070) reach this module as a test-fixtures jar; {@link ExamplePacks} copies them out of it, so
 * every location is a real directory holding its descriptor.
 */
class ExamplePacksOnTheClasspathTest {

    @Test
    void theThreePacksAreRealDirectoriesOutsideTheJar() {
        assertThat(ExamplePacks.names()).containsExactly(ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT);
        assertThat(ExamplePacks.names())
                .allSatisfy(name ->
                        assertThat(ExamplePacks.pack(name).resolve("pack.yaml")).isRegularFile());
    }
}
