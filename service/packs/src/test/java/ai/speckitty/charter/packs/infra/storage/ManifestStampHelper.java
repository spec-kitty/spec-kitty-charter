package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The test-scope tool that restamps the example packs' {@code pack-manifest.yaml} through the service's own write path
 * ({@link ManifestStamps#stamp}, which runs {@link ManifestMembership#stamp}; #630, plan D4). Since a registration and
 * validate mode refuse a stale manifest ({@code manifest_stale}), an example pack whose files change needs its manifest
 * restamped in the same change. Restamp every example pack's manifest in the source tree with
 *
 * <pre>
 * CHARTER_RESTAMP_EXAMPLE_MANIFESTS=true ./gradlew :packs:test --tests '*ManifestStampHelper*' --rerun
 * </pre>
 *
 * <p>Without the variable, the tool checks that the stamp leaves every example manifest byte for byte as it is: the
 * restamp's control.
 */
@DisplayName("ManifestStampHelper: the example manifests are what the service's stamp writes (#630)")
class ManifestStampHelper {

    /** The environment variable that makes the tool restamp the example packs' manifests in the source tree. */
    static final String RESTAMP = "CHARTER_RESTAMP_EXAMPLE_MANIFESTS";

    /** The example packs in the source tree, relative to the module directory the test task runs in. */
    private static final Path SOURCE = Path.of("src/testFixtures/resources/example-packs");

    @Test
    @DisplayName("the stamp leaves every example manifest as it is: each is fresh, and none needs a restamp")
    void theStampLeavesEveryExampleManifestAsItIs(@TempDir Path temp) throws IOException {
        for (String name : ExamplePacks.names()) {
            Path copy = ExamplePacks.copyOf(name, temp);
            String before = Files.readString(copy.resolve(PackFileSet.MANIFEST), StandardCharsets.UTF_8);

            ManifestStamps.stamp(copy, name);

            assertThat(Files.readString(copy.resolve(PackFileSet.MANIFEST), StandardCharsets.UTF_8))
                    .as("%s: restamp with %s=true", name, RESTAMP)
                    .isEqualTo(before);
        }
    }

    @Test
    @DisplayName("restamps every example pack's manifest in the source tree")
    @EnabledIfEnvironmentVariable(named = RESTAMP, matches = "true")
    void restampTheExampleManifests() {
        for (String name : ExamplePacks.names()) {
            ManifestStamps.stamp(SOURCE.resolve(name), name);
        }
    }
}
