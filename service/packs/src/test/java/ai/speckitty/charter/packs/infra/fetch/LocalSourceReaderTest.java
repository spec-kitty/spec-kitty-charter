package ai.speckitty.charter.packs.infra.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.infra.archive.ArchiveLimits;
import ai.speckitty.charter.packs.infra.archive.BoundedTreeStager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The local path source (T086, FR-004, NFR-005, NFR-012, squad finding 10): a directory under the configured root is
 * copied through the archive rules; a path that leaves the root, a link, a file with another hard link, a path that is
 * not there and a source nobody configured are all refused the same way, so the answer does not reveal what exists.
 */
class LocalSourceReaderTest {

    @TempDir
    Path base;

    private Path root;
    private Path outside;
    private Path staging;
    private final Set<String> refusals = new LinkedHashSet<>();

    @BeforeEach
    void layOut() throws IOException {
        Path real = base.toRealPath();
        root = Files.createDirectories(real.resolve("sources"));
        outside = Files.createDirectories(real.resolve("outside"));
        staging = Files.createDirectories(real.resolve("job"));
        write(root.resolve("packs/acme/pack.yaml"), "name: acme\n");
        write(root.resolve("packs/acme/directives/a.directive.yaml"), "id: A\n");
        write(outside.resolve("secret.yaml"), "secret: yes\n");
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private LocalSourceReader reader() {
        return new LocalSourceReader(root, null, new BoundedTreeStager());
    }

    private static PackSourceRequest path(@Nullable String path) {
        return new PackSourceRequest(PackSourceKind.FILESYSTEM, null, null, null, null, path);
    }

    private String refusedMessage(LocalSourceReader reader, @Nullable String path) {
        try {
            reader.fetch(path(path), staging);
        } catch (CharterRefusal refusal) {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SOURCE_REFUSED);
            return refusal.getMessage();
        }
        throw new AssertionError("the path was not refused: " + path);
    }

    private void assertPathRefused(String path) {
        String message = refusedMessage(reader(), path);
        assertThat(message).startsWith("path_refused: ");
        refusals.add(message);
        assertThat(refusals).as("every refusal reads the same").hasSize(1);
        if (path.chars().anyMatch(Character::isLetter)) {
            assertThat(message).doesNotContain(path);
        }
        assertThat(staging).as("nothing is left in the job's directory").isEmptyDirectory();
    }

    @Test
    void aDirectoryUnderTheRootIsStagedOwnerOnlyAndRecordedAsSent() throws Exception {
        Staged staged = reader().fetch(path("packs/acme"), staging);

        assertThat(staged.source())
                .containsExactlyInAnyOrderEntriesOf(Map.of("kind", "filesystem", "path", "packs/acme"));
        Path pack = staged.pack().directory();
        assertThat(Files.readString(pack.resolve("pack.yaml"))).isEqualTo("name: acme\n");
        assertThat(Files.readString(pack.resolve("directives/a.directive.yaml")))
                .isEqualTo("id: A\n");
        assertThat(Files.getPosixFilePermissions(pack.resolve("pack.yaml")))
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.getPosixFilePermissions(pack.resolve("directives")))
                .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        assertThat(pack).startsWith(staging);
        assertThat(Files.getPosixFilePermissions(staging.resolve("extract")))
                .as("the directory the tree is staged in")
                .isEqualTo(PosixFilePermissions.fromString("rwx------"));
    }

    @Test
    void theSourceIsNotChangedByBeingRead() throws Exception {
        reader().fetch(path("packs/acme"), staging);

        assertThat(Files.readString(root.resolve("packs/acme/pack.yaml"))).isEqualTo("name: acme\n");
        try (Stream<Path> tree = Files.walk(root.resolve("packs/acme"))) {
            assertThat(tree.count()).isEqualTo(4);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"packs/missing", "missing", "packs/acme/pack.yaml", "packs/acme/directives/missing"})
    void aPathThatIsNotADirectoryHereIsRefusedLikeEveryOtherRefusal(String path) {
        assertPathRefused(path);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../outside",
                "packs/../../outside",
                "/etc",
                "/etc/passwd",
                "packs\\acme",
                "packs//acme",
                "",
                ".",
                "packs/acme/.",
                "packs/acme/",
                "packs/acme\0"
            })
    void aPathThatIsNotRelativeAndPlainIsRefusedEvenThoughTheSchemaAlreadyHoldsItBack(String path) {
        assertPathRefused(path);
    }

    @Test
    void aSymlinkAsTheFinalSegmentIsRefusedWhereverItPoints() throws Exception {
        Files.createSymbolicLink(root.resolve("packs/link"), outside);
        Files.createSymbolicLink(root.resolve("packs/inside"), root.resolve("packs/acme"));

        assertPathRefused("packs/link");
        assertPathRefused("packs/inside");
    }

    @Test
    void aSymlinkInTheMiddleOfThePathIsRefused() throws Exception {
        Files.createSymbolicLink(root.resolve("escape"), base.toRealPath());
        write(base.resolve("sources-sibling/pack.yaml"), "x: y\n");

        assertPathRefused("escape/outside");
        assertPathRefused("escape/sources/packs/acme");
    }

    @Test
    void aSymlinkInsideThePackIsRefusedWhetherItIsAFileOrADirectory() throws Exception {
        Files.createSymbolicLink(root.resolve("packs/acme/loot.yaml"), outside.resolve("secret.yaml"));
        assertPathRefused("packs/acme");
        Files.delete(root.resolve("packs/acme/loot.yaml"));

        Files.createSymbolicLink(root.resolve("packs/acme/loot"), outside);
        assertPathRefused("packs/acme");
    }

    @Test
    void aFileWithAnotherHardLinkIsRefusedWhetherTheOtherLinkIsInsideTheRootOrNot() throws Exception {
        Files.createLink(root.resolve("packs/acme/twin.yaml"), root.resolve("packs/acme/pack.yaml"));
        assertPathRefused("packs/acme");
        Files.delete(root.resolve("packs/acme/twin.yaml"));

        Files.createLink(root.resolve("packs/acme/loot.yaml"), outside.resolve("secret.yaml"));
        assertPathRefused("packs/acme");
        Files.delete(root.resolve("packs/acme/loot.yaml"));

        assertThat(reader().fetch(path("packs/acme"), staging).pack()).isNotNull();
    }

    @Test
    void aTreeThatBreaksAnArchiveRuleIsRefusedTheSameWay() throws Exception {
        write(root.resolve("packs/acme/bad name.yaml"), "x: y\n");

        assertPathRefused("packs/acme");
    }

    @Test
    void aTreeOverTheEntryLimitIsRefusedTheSameWay() {
        LocalSourceReader small =
                new LocalSourceReader(root, null, new BoundedTreeStager(new ArchiveLimits(1024, 1024, 2, 0, 4096)));

        assertThat(refusedMessage(small, "packs/acme")).startsWith("path_refused: ");
        assertThat(staging).isEmptyDirectory();
    }

    @Test
    void aSourceNobodyConfiguredRefusesEveryPath() {
        LocalSourceReader unset = new LocalSourceReader(null, null, new BoundedTreeStager());

        assertThat(refusedMessage(unset, "packs/acme")).startsWith("path_refused: ");
    }

    @Test
    void aMemberForeignToTheKindOrNoPathIsMalformed() {
        PackSourceRequest[] malformed = {
            path(null),
            new PackSourceRequest(PackSourceKind.FILESYSTEM, "https://x.example/a", null, null, null, "packs/acme"),
            new PackSourceRequest(PackSourceKind.FILESYSTEM, null, "refs/tags/v1", null, null, "packs/acme"),
            new PackSourceRequest(PackSourceKind.FILESYSTEM, null, null, "a".repeat(40), null, "packs/acme"),
            new PackSourceRequest(PackSourceKind.FILESYSTEM, null, null, null, "0".repeat(64), "packs/acme")
        };
        for (PackSourceRequest request : malformed) {
            assertThatThrownBy(() -> reader().fetch(request, staging))
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.getMessage()).startsWith("source_malformed: "));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/proc", "/proc/self", "/sys", "/sys/kernel", "/dev", "/dev/shm"})
    void aRootUnderAKernelOrDeviceTreeIsRefusedAtStartup(String forbidden) {
        assertThatThrownBy(() -> new LocalSourceReader(Path.of(forbidden), null, new BoundedTreeStager()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("charter.pack-sources.filesystem.root");
    }

    @Test
    void aRootThatIsALinkToAKernelTreeIsRefusedAtStartup() throws Exception {
        Path link = Files.createSymbolicLink(base.toRealPath().resolve("proc-link"), Path.of("/proc"));

        assertThatThrownBy(() -> new LocalSourceReader(link, null, new BoundedTreeStager()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRootThatOverlapsTheConfigRootIsRefusedAtStartup() throws Exception {
        Path config = Files.createDirectories(root.resolve("config"));

        assertThatThrownBy(() -> new LocalSourceReader(root, config, new BoundedTreeStager()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("charter.config-root");
        assertThatThrownBy(() -> new LocalSourceReader(config, root, new BoundedTreeStager()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalSourceReader(root, root, new BoundedTreeStager()))
                .isInstanceOf(IllegalArgumentException.class);
        Path elsewhere = Files.createDirectories(base.toRealPath().resolve("config-elsewhere"));
        assertThat(new LocalSourceReader(root, elsewhere, new BoundedTreeStager()).kind())
                .isEqualTo(PackSourceKind.FILESYSTEM);
    }

    @Test
    void aRootThatIsNotThereYetIsAcceptedAndRefusesUntilItIs() {
        LocalSourceReader later = new LocalSourceReader(base.resolve("not-yet"), null, new BoundedTreeStager());

        assertThat(refusedMessage(later, "packs/acme")).startsWith("path_refused: ");
    }
}
