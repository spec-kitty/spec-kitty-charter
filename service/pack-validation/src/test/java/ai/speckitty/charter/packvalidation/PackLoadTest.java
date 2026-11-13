package ai.speckitty.charter.packvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackInspector;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** pack-validation reads a pack through the packs slice's own Spring-free load, in full (#553). */
class PackLoadTest {

    static List<String> examplePacks() {
        return ExamplePacks.names();
    }

    private static PackRoot root(String name) {
        return new PackRoot(
                name,
                Optional.of(ExamplePacks.level(name)),
                ExamplePacks.pack(name).toString());
    }

    @ParameterizedTest
    @MethodSource("examplePacks")
    void everyFileOfAnExamplePackIsClassifiedAndNoneIsUnreadable(String name) {
        PackContents contents = new PackLoad(new FileSystemPackInspector()).contents(root(name));

        assertThat(contents.root()).isEqualTo(root(name));
        assertThat(contents.files()).isNotEmpty();
        assertThat(contents.unreadableFiles()).isEmpty();
    }

    @Test
    void theBuiltInExamplePackIsReadInFullWithItsArtifacts() {
        PackContents contents = new PackLoad(new FileSystemPackInspector()).contents(root(ExamplePacks.BUILT_IN));

        // Only a full read fills the artifacts; the load-path scope leaves them empty (PackContents).
        assertThat(contents.artifacts()).isNotEmpty();
    }

    @Test
    void theLoadAsksThePortForAFullReadOfTheRootItIsGiven() {
        List<PackInspector.Scope> scopes = new ArrayList<>();
        PackInspector full = new FileSystemPackInspector();
        PackRoot bare = new PackRoot(
                "bare",
                Optional.empty(),
                ExamplePacks.pack(ExamplePacks.PROJECT).toString());

        PackContents contents = new PackLoad((root, scope) -> {
                    scopes.add(scope);
                    return full.inspect(root, scope);
                })
                .contents(bare);

        assertThat(scopes).containsExactly(PackInspector.Scope.FULL);
        assertThat(contents.root().configuredLevel()).isEmpty();
    }
}
