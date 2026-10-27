package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileSystemPackSourcesTest {

    @Test
    void theSourceFingerprintFollowsTheFilesAndNotWhereThePacksAre(@TempDir Path dir) throws IOException {
        Path first = Files.createDirectories(dir.resolve("one/qa"));
        Path second = Files.createDirectories(dir.resolve("two/qa"));
        for (Path root : List.of(first, second)) {
            Files.writeString(root.resolve("a.graph.yaml"), "nodes: []\n");
            Files.createDirectories(root.resolve("drg"));
            Files.writeString(root.resolve("drg/fragment.yaml"), "nodes: []\n");
        }
        String fingerprint = sources(first).fingerprint();
        assertThat(fingerprint).matches("sha256:[0-9a-f]{64}");
        assertThat(sources(second).fingerprint()).as("same files elsewhere").isEqualTo(fingerprint);

        Files.writeString(second.resolve("a.graph.yaml"), "nodes: []\nedges: []\n");
        assertThat(sources(second).fingerprint()).as("a changed file").isNotEqualTo(fingerprint);
    }

    @Test
    void aPackThatIsNoDirectoryHasNoSourceFingerprint(@TempDir Path dir) {
        assertThatThrownBy(() -> sources(dir.resolve("missing")).fingerprint())
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tactic", "directive", "styleguide", "paradigm"})
    void aLoadedFileOfEachKindChangesTheFingerprintWhileADecoyDoesNot(String kind, @TempDir Path dir)
            throws IOException {
        Path root = Files.createDirectories(dir.resolve("qa"));
        Files.writeString(root.resolve("pack.yaml"), "name: qa\n");
        Files.writeString(root.resolve("a.graph.yaml"), "nodes: []\n");
        Path loaded = Files.createDirectories(root.resolve("nested/deeper")).resolve("e." + kind + ".yaml");
        Files.writeString(loaded, "id: e\n");
        Path decoy = root.resolve("nested/e." + kind + ".yml");
        Files.writeString(decoy, "id: e\n");
        String baseline = sources(root).fingerprint();

        Files.writeString(decoy, "id: changed\n");
        assertThat(sources(root).fingerprint()).as("a decoy").isEqualTo(baseline);

        Files.writeString(loaded, "id: changed\n");
        assertThat(sources(root).fingerprint()).as("the %s file", kind).isNotEqualTo(baseline);
    }

    @Test
    void aRootWrittenWithADotDigestsTheSameAsTheNormalizedRoot(@TempDir Path dir) throws IOException {
        Path root = Files.createDirectories(dir.resolve("qa"));
        Files.writeString(root.resolve("pack.yaml"), "name: qa\n");
        Files.writeString(root.resolve("presets-free.tactic.yaml"), "id: t\n");
        Files.createDirectories(root.resolve("presets"));
        Files.writeString(root.resolve("presets/p.tactic.yaml"), "id: p\n");

        assertThat(sources(root.resolve(".")).fingerprint())
                .isEqualTo(sources(root).fingerprint());
    }

    /** The order and labels of a pack without loaded-kind files are what the fingerprint always digested. */
    @Test
    void aPackWithoutLoadedKindFilesDigestsAsItAlwaysDid(@TempDir Path dir) throws IOException {
        Path root = Files.createDirectories(dir.resolve("qa"));
        Files.createDirectories(root.resolve("drg"));
        Files.writeString(root.resolve("drg/fragment.yaml"), "frag\n");
        Files.writeString(root.resolve("b.graph.yaml"), "b\n");
        Files.writeString(root.resolve("a.graph.yaml"), "a\n");
        Files.writeString(root.resolve("pack.yaml"), "pack\n");
        Files.writeString(root.resolve("notes.yaml"), "ignored\n");

        Fingerprint expected = Fingerprint.sha256();
        for (String file : List.of("pack.yaml", "a.graph.yaml", "b.graph.yaml", "drg/fragment.yaml")) {
            expected.add("qa/" + file).add(Files.readAllBytes(root.resolve(file)));
        }

        assertThat(sources(root).fingerprint()).isEqualTo(expected.value());
    }

    @Test
    void aLoadedFileThatCannotBeReadRefusesTheFingerprint(@TempDir Path dir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")), "permissions do not bite for root");
        Path root = Files.createDirectories(dir.resolve("qa"));
        Path unreadable = Files.createDirectories(root.resolve("deep/er")).resolve("e.tactic.yaml");
        Files.writeString(unreadable, "id: e\n");
        assumeTrue(unreadable.toFile().setReadable(false), "the file system cannot revoke read access");

        assertThatThrownBy(() -> sources(root).fingerprint()).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(r.getMessage()).isEqualTo("qa/deep/er/e.tactic.yaml cannot be read");
        });
    }

    @Test
    void aNestedDirectoryThatCannotBeListedRefusesTheFingerprint(@TempDir Path dir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")), "permissions do not bite for root");
        Path root = Files.createDirectories(dir.resolve("qa"));
        Path nested = Files.createDirectories(root.resolve("deep/er"));
        assumeTrue(nested.toFile().setReadable(false), "the file system cannot revoke read access");

        try {
            assertThatThrownBy(() -> sources(root).fingerprint())
                    .isInstanceOfSatisfying(
                            CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        } finally {
            assertThat(nested.toFile().setReadable(true)).isTrue();
        }
    }

    /** Where permissions do not bite (root), a loaded file whose read fails with an I/O error still refuses. */
    @Test
    void aLoadedFileWhoseReadFailsRefusesTheFingerprintEvenForRoot(@TempDir Path dir) throws IOException {
        Path unreadable = Path.of("/proc/self/mem");
        assumeTrue(Files.isRegularFile(unreadable), "no /proc/self/mem on this system");
        Path root = Files.createDirectories(dir.resolve("qa"));
        Files.createSymbolicLink(root.resolve("e.tactic.yaml"), unreadable);

        assertThatThrownBy(() -> sources(root).fingerprint()).isInstanceOfSatisfying(CharterRefusal.class, r -> {
            assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(r.getMessage()).isEqualTo("qa/e.tactic.yaml cannot be read");
        });
    }

    /** An atomic save creates and renames a temp file in the pack while it is walked; the walk must not refuse. */
    @Test
    void aTempFileThatVanishesWhileThePackIsWalkedDoesNotRefuseTheFingerprintOrTheFileSet(@TempDir Path dir)
            throws Exception {
        Path root = Files.createDirectories(dir.resolve("qa"));
        Path tactic = root.resolve("a.tactic.yaml");
        Files.writeString(tactic, "id: a\n");
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                while (!stop.get()) {
                    Path temp = Files.createTempFile(root, ".pack-write-", ".tmp");
                    Files.writeString(temp, "id: a\n");
                    Files.move(temp, tactic, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException e) {
                writerFailure.set(e);
            }
        });
        int refused = 0;
        String first = null;
        long deadline = System.nanoTime() + 20_000_000_000L;
        writer.start();
        try {
            for (int i = 0; i < 2000 && System.nanoTime() < deadline; i++) {
                try {
                    sources(root).fingerprint();
                    PackFileSet.files(root, ArtifactKind.TACTIC);
                } catch (CharterRefusal refusal) {
                    refused++;
                    first = first == null ? refusal.getMessage() : first;
                }
            }
        } finally {
            stop.set(true);
            writer.join();
        }

        assertThat(writerFailure.get()).isNull();
        assertThat(refused).as("refusals; first: %s", first).isZero();
    }

    @Test
    void aRefusalToListAPackNamesTheConfiguredNameNotTheDirectoryName(@TempDir Path dir) throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")), "permissions do not bite for root");
        Path root = Files.createDirectories(dir.resolve("directory-name"));
        Path nested = Files.createDirectories(root.resolve("deep/er"));
        assumeTrue(nested.toFile().setReadable(false), "the file system cannot revoke read access");
        FileSystemPackSources sources =
                new FileSystemPackSources(() -> List.of(new PackLocation("configured-name", PackLevel.ORG, root)));

        try {
            assertThatThrownBy(sources::fingerprint).isInstanceOfSatisfying(CharterRefusal.class, r -> {
                assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                assertThat(r.getMessage()).isEqualTo("pack configured-name cannot be listed");
            });
        } finally {
            assertThat(nested.toFile().setReadable(true)).isTrue();
        }
    }

    // ---- NFR-002 / FR-011: the fingerprint of packs without asset files or templates is the one main had -----------

    /**
     * The fixture packs without asset files or templates, by location name: the digested list and the fingerprint each
     * had before this mission. Captured on {@code main} at 90de8f9 with a throwaway test in a scratch {@code git
     * worktree} of that commit: it printed {@code PackFileSet.digested(dir)} and
     * {@code new FileSystemPackSources(() -> List.of(new PackLocation(name, PackLevel.ORG, dir))).fingerprint()} for
     * each fixture below, and the fingerprint of all three locations in this order. The fixtures are byte-identical on
     * 90de8f9 and on this branch.
     */
    private static final List<PinnedPack> PINNED = List.of(
            new PinnedPack(
                    "pack-a",
                    "/roundtrip-fixtures/pack-a",
                    List.of(
                            "pack.yaml",
                            "alpha-tactic.tactic.yaml",
                            "beta-tactic.tactic.yaml",
                            "broken-tactic.tactic.yaml"),
                    "sha256:b1b6a8c40896b5f5a769e37c41c90d4fb9185062e6ea0da9cf74a727128f9a58"),
            new PinnedPack(
                    "pack-descriptor-only",
                    "/roundtrip-fixtures/pack-descriptor-only",
                    List.of("pack.yaml"),
                    "sha256:ded97480fbaeab435de330a315be5c06c60544d7d61aa4b876cbfd490c65b47b"),
            new PinnedPack(
                    "shards-and-fragment",
                    "/load-fixtures/shards-and-fragment-merge-their-nodes/files",
                    List.of("pack.yaml", "a.graph.yaml", "b.graph.yaml", "drg/fragment.yaml"),
                    "sha256:731c25459783e328fb650111f46747f0b9bdc84f80f96388a9e6cfff682ffa44"));

    /** The fingerprint of the three {@link #PINNED} locations together, in that order (captured as above). */
    private static final String PINNED_ALL = "sha256:f3c3fc76bb4d5b7b2a319e41d6b507b9fbb0f44fc109ee131ee6cb22ec7c3aa7";

    private record PinnedPack(String name, String resource, List<String> digested, String fingerprint) {}

    @Test
    void fixturePacksWithoutAssetFilesOrTemplatesDigestAndFingerprintExactlyAsOnMain(@TempDir Path host)
            throws IOException {
        List<PackLocation> all = new ArrayList<>();
        for (PinnedPack pinned : PINNED) {
            Path dir = copyOfResource(pinned.resource(), host.resolve(pinned.name()));
            assertThat(PackFileSet.assetFiles(dir))
                    .as("%s has no asset files", pinned.name())
                    .isEmpty();
            assertThat(PackFileSet.templates(dir))
                    .as("%s has no templates", pinned.name())
                    .isEmpty();
            PackLocation location = new PackLocation(pinned.name(), PackLevel.ORG, dir);
            all.add(location);

            assertThat(PackFileSet.digested(dir))
                    .as("%s digested", pinned.name())
                    .containsExactlyElementsOf(pinned.digested());
            assertThat(new FileSystemPackSources(() -> List.of(location)).fingerprint())
                    .as("%s fingerprint", pinned.name())
                    .isEqualTo(pinned.fingerprint());
        }
        assertThat(new FileSystemPackSources(() -> List.copyOf(all)).fingerprint())
                .isEqualTo(PINNED_ALL);
    }

    /**
     * FR-011, the positive controls on a pinned fixture through the production path: an asset file, a byte of it, a
     * template and a byte of it each move the fingerprint, and each step is a fingerprint not seen before.
     */
    @Test
    void anAssetFileATemplateAndOneByteOfEitherEachChangeThePinnedFingerprint(@TempDir Path host) throws IOException {
        PinnedPack pinned = PINNED.getFirst();
        Path dir = copyOfResource(pinned.resource(), host.resolve(pinned.name()));
        FileSystemPackSources sources =
                new FileSystemPackSources(() -> List.of(new PackLocation(pinned.name(), PackLevel.ORG, dir)));
        List<String> seen = new ArrayList<>(List.of(sources.fingerprint()));
        assertThat(seen.getFirst()).as("the unchanged fixture").isEqualTo(pinned.fingerprint());

        Path payload = Files.createDirectories(dir.resolve("assets/debrief")).resolve("logo.png");
        Files.write(payload, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0, (byte) 0xff});
        assertThat(PackFileSet.assetFiles(dir)).containsExactly("assets/debrief/logo.png");
        assertNewFingerprint(sources, seen, "an added asset file");

        flipByte(payload, 6);
        assertNewFingerprint(sources, seen, "one flipped byte of the asset file");

        Path template =
                Files.createDirectories(dir.resolve("templates/writing")).resolve("minutes.md");
        Files.write(template, "# Minutes\n".getBytes(StandardCharsets.UTF_8));
        assertThat(PackFileSet.templates(dir)).containsExactly("templates/writing/minutes.md");
        assertNewFingerprint(sources, seen, "an added template");

        flipByte(template, 2);
        assertNewFingerprint(sources, seen, "one flipped byte of the template");
    }

    private static void assertNewFingerprint(FileSystemPackSources sources, List<String> seen, String step) {
        String fingerprint = sources.fingerprint();
        assertThat(seen).as("after %s", step).doesNotContain(fingerprint);
        seen.add(fingerprint);
    }

    private static void flipByte(Path file, int offset) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        bytes[offset] ^= 0x01;
        Files.write(file, bytes);
    }

    private static Path copyOfResource(String resource, Path target) throws IOException {
        Path source;
        try {
            source = Path.of(Objects.requireNonNull(FileSystemPackSourcesTest.class.getResource(resource), resource)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted().toList()) {
                Path to = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(path, to);
                }
            }
        }
        return target;
    }

    private static FileSystemPackSources sources(Path root) {
        return new FileSystemPackSources(() -> List.of(new PackLocation("qa", PackLevel.ORG, root)));
    }
}
