package ai.speckitty.charter.packvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackValidation;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** pack-validation reads a pack through the packs slice's own Spring-free load, the one the service runs (#553). */
class PackLoadTest {

    static List<String> examplePacks() {
        return ExamplePacks.names();
    }

    private static PackRoot root(String name) {
        Path directory = ExamplePacks.pack(name);
        return new PackRoot(name, Optional.of(ExamplePacks.level(name)), directory.toString());
    }

    @ParameterizedTest
    @MethodSource("examplePacks")
    void theLoadReadsEveryFileOfAnExamplePackAndNoneIsUnreadable(String name) {
        PackContents contents = new PackLoad(new FileSystemPackInspector()).contents(root(name));

        assertThat(contents.root().name()).isEqualTo(name);
        assertThat(contents.files()).isNotEmpty();
        assertThat(contents.unreadableFiles()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("examplePacks")
    void theVerdictIsTheServicesValidateModeVerdict(String name) {
        PackRoot root = root(name);

        assertThat(new PackLoad(new FileSystemPackInspector()).verdict(root))
                .isEqualTo(new PackValidation(new FileSystemPackInspector()).validate(root));
    }

    @ParameterizedTest
    @MethodSource("examplePacks")
    void aBarePathIsJudgedWithoutALevel(String name) {
        PackRoot bare =
                new PackRoot(name, Optional.empty(), ExamplePacks.pack(name).toString());

        assertThat(new PackLoad(new FileSystemPackInspector()).verdict(bare))
                .isEqualTo(new PackValidation(new FileSystemPackInspector()).validate(bare));
    }
}
