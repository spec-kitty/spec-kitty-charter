package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackContents;
import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.RawGraph;
import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackInspector.Scope;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.PackRoot;
import ai.speckitty.charter.domain.RefusalCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The load-path inspector reads {@code pack.yaml}, the root shards and the fragment as facts, and nothing else. */
class FileSystemPackInspectorTest {

    private static final FileSystemPackInspector INSPECTOR = new FileSystemPackInspector();

    private static PackRoot root(Path dir) {
        return new PackRoot("qa", Optional.of(PackLevel.ORG), dir.toString());
    }

    private static <T> FileRead.Read<T> read(FileRead<T> file) {
        if (file instanceof FileRead.Read<T> read) {
            return read;
        }
        throw new AssertionError("expected a Read but was " + file);
    }

    private static <T> FileRead.Unreadable<T> unreadable(FileRead<T> file) {
        if (file instanceof FileRead.Unreadable<T> unreadable) {
            return unreadable;
        }
        throw new AssertionError("expected an Unreadable but was " + file);
    }

    private static void write(Path dir, String relative, String text) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    void aPackIsReadAsItsDescriptorItsShardsInNameOrderAndItsFragment(@TempDir Path dir) throws IOException {
        write(dir, "pack.yaml", "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        write(dir, "b.graph.yaml", "schema_version: '1.0'\ngenerated_at: S\nnodes: []\nedges: []\n");
        write(dir, "a.graph.yaml", "schema_version: '1.0'\ngenerated_at: S\nnodes: []\nedges: []\n");
        write(dir, "readme.md", "# not read\n");
        write(dir, "tactics/x.tactic.yaml", "id: x\n");
        write(dir, "drg/fragment.yaml", "nodes: []\nedges: []\n");

        PackContents contents = INSPECTOR.inspect(root(dir), Scope.LOAD_PATH);

        assertThat(contents.root()).isEqualTo(root(dir));
        assertThat(read(contents.descriptor()).file()).isEqualTo("pack.yaml");
        assertThat(read(contents.descriptor()).value().declaredLevel()).contains(PackLevel.ORG);
        assertThat(contents.shards()).extracting(FileRead::file).containsExactly("a.graph.yaml", "b.graph.yaml");
        assertThat(contents.shards()).allSatisfy(s -> assertThat(s).isInstanceOf(FileRead.Read.class));
        assertThat(read(contents.fragment()).file()).isEqualTo("drg/fragment.yaml");
        assertThat(read(contents.fragment()).value().shape()).isEqualTo(RawGraph.Shape.FRAGMENT);
        assertThat(contents.files()).isEmpty();
        assertThat(contents.stepContracts()).isEmpty();
        assertThat(contents.artifacts()).isEmpty();
        assertThat(contents.assets()).isEmpty();
    }

    @Test
    void aFileThatIsNotThereIsAbsent(@TempDir Path dir) {
        PackContents contents = INSPECTOR.inspect(root(dir), Scope.LOAD_PATH);

        assertThat(contents.descriptor()).isEqualTo(new FileRead.Absent<PackDescriptor>("pack.yaml"));
        assertThat(contents.shards()).isEmpty();
        assertThat(contents.fragment()).isEqualTo(new FileRead.Absent<RawGraph>("drg/fragment.yaml"));
    }

    @Test
    void aFileThatIsNotYamlOrBreaksItsSchemaIsUnreadableAndNeverThrows(@TempDir Path dir) throws IOException {
        write(dir, "pack.yaml", "pack_id: [\n");
        write(dir, "a.graph.yaml", "nodes: [\n");
        write(dir, "b.graph.yaml", "surprise: true\n");
        write(dir, "drg/fragment.yaml", "- a\n");

        PackContents contents = INSPECTOR.inspect(root(dir), Scope.LOAD_PATH);

        assertThat(unreadable(contents.descriptor()).parse()).isNotNull();
        assertThat(unreadable(contents.shards().get(0)).file()).isEqualTo("a.graph.yaml");
        assertThat(unreadable(contents.shards().get(0)).parse()).isNotNull();
        assertThat(unreadable(contents.shards().get(1)).file()).isEqualTo("b.graph.yaml");
        assertThat(unreadable(contents.shards().get(1)).violations()).isNotEmpty();
        assertThat(unreadable(contents.fragment()).violations()).isNotEmpty();
    }

    @Test
    void aRootThatIsNoDirectoryIsRefusedAsUnreadable(@TempDir Path dir) {
        assertThatThrownBy(() -> INSPECTOR.inspect(root(dir.resolve("missing")), Scope.LOAD_PATH))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(r.getMessage()).isEqualTo("pack qa is not a directory");
                });
    }

    @Test
    void aFileThatCannotBeReadIsAnIoFailureAndRefusesNamingItRelativeToThePack(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("tactic.graph.yaml"), new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00});

        assertThatThrownBy(() -> INSPECTOR.inspect(root(dir), Scope.LOAD_PATH))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(r.getMessage()).isEqualTo("qa/tactic.graph.yaml cannot be read");
                    assertThat(r.getMessage()).doesNotContain(dir.toString());
                });
    }

    @Test
    void theFullScopeIsNotThereYet(@TempDir Path dir) {
        assertThatThrownBy(() -> INSPECTOR.inspect(root(dir), Scope.FULL))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("FULL scope lands in WP08");
    }

    // ---- the listing of shards ---------------------------------------------------------------------

    @Test
    void theGraphShardsOfAListingAreItsGraphYamlFilesSortedByName() {
        assertThat(FileSystemPackInspector.graphShards(
                        Stream.of(Path.of("qa/b.graph.yaml"), Path.of("qa/pack.yaml"), Path.of("qa/a.graph.yaml")),
                        "qa",
                        p -> true))
                .containsExactly(Path.of("qa/a.graph.yaml"), Path.of("qa/b.graph.yaml"));
    }

    @Test
    void aListingThatFailsWhileItIsReadIsRefusedAsAnUnreadablePack() {
        IOException cause = new IOException("gone");
        Stream<Path> failing = Stream.of(Path.of("qa/a.graph.yaml")).peek(p -> {
            throw new UncheckedIOException(cause);
        });

        assertThatThrownBy(() -> FileSystemPackInspector.graphShards(failing, "qa", p -> true))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage("pack qa cannot be listed")
                .hasCause(cause);
    }

    @Test
    void aDirectoryThatEndsInGraphYamlIsNotAShard(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("odd.graph.yaml"));
        write(dir, "real.graph.yaml", "nodes: []\n");

        assertThat(INSPECTOR.inspect(root(dir), Scope.LOAD_PATH).shards())
                .extracting(FileRead::file)
                .containsExactly("real.graph.yaml");
    }

    @Test
    void anInspectedRootThatCannotBeListedIsRefused(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("a-file");
        Files.writeString(file, "x");

        assertThatThrownBy(() -> INSPECTOR.inspect(root(file), Scope.LOAD_PATH))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.getMessage()).isEqualTo("pack qa is not a directory"));
    }
}
