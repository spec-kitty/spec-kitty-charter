package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The example packs (ADR-0070) are sound: each one validates with no finding, the same check validate mode runs over a
 * real pack, and removing a file it needs is caught, so the check reads the examples.
 */
class ExamplePacksTest {

    private static final FileSystemPackInspector INSPECTOR = new FileSystemPackInspector();

    private static PackVerdict judge(String name, Path dir) {
        PackLevel level = ExamplePacks.packs().stream()
                .filter(location -> location.name().equals(name))
                .findFirst()
                .orElseThrow()
                .level();
        return PackPolicy.judge(INSPECTOR.inspect(new PackRoot(name, Optional.of(level), dir.toString()), Scope.FULL));
    }

    @ParameterizedTest
    @ValueSource(strings = {ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT})
    void everyExamplePackValidatesWithNoFinding(String name) {
        assertThat(judge(name, ExamplePacks.pack(name)).findings()).as(name).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT})
    void aPackWithoutItsDescriptorIsCaught(String name, @TempDir Path dir) throws Exception {
        Path copy = ExamplePacks.copyOf(name, dir);
        Files.delete(copy.resolve("pack.yaml"));

        assertThat(judge(name, copy).findings()).as("the control: the check reads the example").isNotEmpty();
    }
}
