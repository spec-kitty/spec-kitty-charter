package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Asset sidecars and asset files through {@link FilesystemPackStore} (US1, FR-002 to FR-006): sidecars round-trip by
 * meaning, asset files byte for byte, and a sidecar the pack cannot back is refused while a clean one beside it loads.
 */
@DisplayName("Asset sidecars and asset files in the per-pack store")
class AssetStoreTest {

    private static final PackIdentifier SOURCE = new PackIdentifier("source");
    private static final PackIdentifier TARGET = new PackIdentifier("target");

    /** A PNG signature, a NUL, CR/LF byte pairs, a byte that is no UTF-8, and no final newline. */
    static final byte[] BINARY = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, '\r', '\n', (byte) 0xff, (byte) 0xfe, 'e', 'n', 'd'
    };

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    private static Path pack(Path host, String name) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Path renamed = host.resolve(name);
        Files.move(pack, renamed);
        return renamed;
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        write(pack, relative, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path pack, String relative, byte[] bytes) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static String sidecar(String id, String path) {
        return "id: " + id + "\nmime: text/markdown\npath: " + path + "\n";
    }

    private static List<String> ids(FilesystemPackStore.PackRead read) {
        return read.dsos(AssetBinding.INSTANCE).stream().map(AssetDso::id).toList();
    }

    private static String reasonFor(FilesystemPackStore.PackRead read, String file) {
        return read.violations().stream()
                .filter(v -> v.file().equals(file))
                .map(PackElementViolation::reason)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no violation on " + file + " in " + read.violations()));
    }

    // ---- sidecars by meaning (FR-002) ---------------------------------------------------------------------------

    /** US1 scenario 2: an absent title is written absent, and the written sidecar means what the source did. */
    @Test
    void aSidecarWithoutATitleIsWrittenWithoutOneAndMeansTheSame(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        String text = sidecar("notes", "notes.md");
        write(source, "assets/notes.md.asset.yaml", text);
        write(source, "assets/notes.md", "# Notes\n");
        Path target = pack(host, "target");
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);

        AssetDso dso = store.read(SOURCE).dsos(AssetBinding.INSTANCE).getFirst();
        store.write(TARGET, AssetBinding.INSTANCE, dso);

        assertThat(dso.title()).isNull();
        String written = Files.readString(target.resolve("notes.asset.yaml"));
        assertThat(written).doesNotContain("title");
        assertThat(MeaningComparator.adr0036().compareTexts("asset", text, written))
                .isEmpty();
    }

    /** US1 scenario 2's pair: a title survives; {@code title: null} reads as absent and is not written. */
    @Test
    void aTitleSurvivesAndANullTitleReadsAsAbsent(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        String titled = "id: logo\nmime: image/png\npath: logo.png\ntitle: 'Logo: the cat'\n";
        write(source, "assets/logo.png.asset.yaml", titled);
        write(source, "assets/logo.png", BINARY);
        write(source, "assets/plain.md.asset.yaml", sidecar("plain", "plain.md") + "title: null\n");
        write(source, "assets/plain.md", "plain\n");
        Path target = pack(host, "target");
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);

        FilesystemPackStore.PackRead read = store.read(SOURCE);
        assertThat(read.violations()).isEmpty();
        AssetDso logo = read.dsos(AssetBinding.INSTANCE).getFirst();
        AssetDso plain = read.dsos(AssetBinding.INSTANCE).get(1);
        store.write(TARGET, AssetBinding.INSTANCE, logo);
        store.write(TARGET, AssetBinding.INSTANCE, plain);

        assertThat(logo.title()).isEqualTo("Logo: the cat");
        assertThat(MeaningComparator.adr0036()
                        .compareTexts("asset", titled, Files.readString(target.resolve("logo.asset.yaml"))))
                .isEmpty();
        assertThat(plain.title()).isNull();
        assertThat(Files.readString(target.resolve("plain.asset.yaml"))).doesNotContain("title");
        assertThat(AssetBinding.INSTANCE.toDso(AssetBinding.INSTANCE.toEntity(SOURCE, logo)))
                .isEqualTo(logo);
    }

    // ---- asset files byte for byte (FR-003, FR-004) -------------------------------------------------------------

    /** A binary payload, a sidecar-less payload and a 0-byte file are read and written with the same SHA-256. */
    @Test
    void assetFilesRoundTripByteForByte(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        write(source, "assets/debrief/logo.png.asset.yaml", sidecar("logo", "debrief/logo.png"));
        write(source, "assets/debrief/logo.png", BINARY);
        write(source, "assets/lint.config.yaml", "rules:\r\n  - a\r\n");
        write(source, "assets/empty.txt", new byte[0]);
        Path target = pack(host, "target");
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);

        FilesystemPackStore.PackRead read = store.read(SOURCE);
        for (AssetFile file : read.assetFiles()) {
            store.writePayload(TARGET, PackFileSet.ASSETS + file.path(), file.payload());
        }

        assertThat(read.violations()).isEmpty();
        assertThat(read.assetFiles())
                .extracting(AssetFile::path)
                .containsExactly("debrief/logo.png", "empty.txt", "lint.config.yaml");
        for (String file : List.of("assets/debrief/logo.png", "assets/lint.config.yaml", "assets/empty.txt")) {
            assertThat(sha256(target.resolve(file))).as(file).isEqualTo(sha256(source.resolve(file)));
        }
        assertThat(Files.size(target.resolve("assets/empty.txt"))).isZero();
        assertThat(Files.readAllBytes(target.resolve("assets/debrief/logo.png")))
                .isEqualTo(BINARY);
    }

    /** A payload whose bytes are not UTF-8 is no violation: the bytes form has no charset. */
    @Test
    void aPayloadThatIsNotUtf8IsNoViolation(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        write(source, "assets/font.otf.asset.yaml", sidecar("font", "font.otf"));
        write(source, "assets/font.otf", new byte[] {(byte) 0xc3, (byte) 0x28, 0, (byte) 0xff});

        FilesystemPackStore.PackRead read = new FilesystemPackStore(host, strategy).read(SOURCE);

        assertThat(read.violations()).isEmpty();
        assertThat(ids(read)).containsExactly("font");
        assertThat(read.assetFiles().getFirst().payload())
                .isEqualTo(Payload.of(new byte[] {(byte) 0xc3, (byte) 0x28, 0, (byte) 0xff}));
    }

    // ---- refusals (FR-005, FR-006, R3) ---------------------------------------------------------------------------

    /** US1 scenario 3: an undeclared key refuses that sidecar, naming the key; the clean one beside it loads. */
    @Test
    void anUndeclaredKeyRefusesTheSidecarNamingItWhileACleanOneLoads(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        write(source, "assets/a.md.asset.yaml", sidecar("a", "a.md") + "surprise: 1\n");
        write(source, "assets/a.md", "a\n");
        write(source, "assets/b.md.asset.yaml", sidecar("b", "b.md"));
        write(source, "assets/b.md", "b\n");

        FilesystemPackStore.PackRead read = new FilesystemPackStore(host, strategy).read(SOURCE);

        assertThat(ids(read)).containsExactly("b");
        assertThat(read.violations()).singleElement().satisfies(v -> {
            assertThat(v.file()).isEqualTo("assets/a.md.asset.yaml");
            assertThat(v.kind()).isEqualTo("schema");
            assertThat(v.reason())
                    .isEqualTo("additionalProperties violated at the document: undeclared key 'surprise'");
        });
    }

    /** US1 scenario 4: a sidecar whose payload is missing is refused with the exact reason; its sibling loads. */
    @Test
    void aMissingPayloadRefusesTheSidecarWithTheExactReasonWhileAPresentOneLoads(@TempDir Path host)
            throws IOException {
        Path source = pack(host, "source");
        write(source, "assets/gone.md.asset.yaml", sidecar("gone", "gone.md"));
        write(source, "assets/here.md.asset.yaml", sidecar("here", "here.md"));
        write(source, "assets/here.md", "here\n");

        FilesystemPackStore.PackRead read = new FilesystemPackStore(host, strategy).read(SOURCE);

        assertThat(ids(read)).containsExactly("here");
        assertThat(read.violations())
                .containsExactly(new PackElementViolation(
                        "assets/gone.md.asset.yaml", "asset", "payload assets/gone.md does not exist"));
    }

    /** The built-in shape: a {@code path} anchored at the pack root resolves to {@code assets/assets/...}. */
    @Test
    void aPathAnchoredAtThePackRootIsRefusedWithThePinnedReason(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        write(
                source,
                "assets/audiences/line_manager.md.asset.yaml",
                sidecar("writing-audience-line-manager", "assets/audiences/line_manager.md"));
        write(source, "assets/audiences/line_manager.md", "# Line manager\n");

        FilesystemPackStore.PackRead read = new FilesystemPackStore(host, strategy).read(SOURCE);

        assertThat(ids(read)).isEmpty();
        assertThat(reasonFor(read, "assets/audiences/line_manager.md.asset.yaml"))
                .isEqualTo("payload assets/assets/audiences/line_manager.md does not exist");
        assertThat(read.assetFiles()).extracting(AssetFile::path).containsExactly("audiences/line_manager.md");
    }

    /** A second sidecar naming a file the first one named is refused; the first keeps it. */
    @Test
    void aSecondSidecarNamingAClaimedFileIsRefused(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        write(source, "assets/a.asset.yaml", sidecar("first", "shared.md"));
        write(source, "assets/b.asset.yaml", sidecar("second", "shared.md"));
        write(source, "assets/shared.md", "shared\n");

        FilesystemPackStore.PackRead read = new FilesystemPackStore(host, strategy).read(SOURCE);

        assertThat(ids(read)).containsExactly("first");
        assertThat(reasonFor(read, "assets/b.asset.yaml"))
                .isEqualTo("payload assets/shared.md is already named by asset first");
    }

    /**
     * Analysis U1: a {@code path} naming a file another rule classifies (a directive under {@code assets/}) names no
     * asset file, so the sidecar is refused naming that path; the payload rules are a fallback, with no precedence
     * logic.
     */
    @Test
    void aPathNamingAFileOfAnotherKindIsRefusedNamingThePath(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        write(source, "assets/rule.asset.yaml", sidecar("rule", "x.directive.yaml"));
        write(source, "assets/x.directive.yaml", """
                id: DIRECTIVE_900
                schema_version: '1.0'
                title: T
                intent: I
                enforcement: advisory
                """);
        write(source, "assets/ok.md.asset.yaml", sidecar("ok", "ok.md"));
        write(source, "assets/ok.md", "ok\n");

        FilesystemPackStore.PackRead read = new FilesystemPackStore(host, strategy).read(SOURCE);

        assertThat(ids(read)).containsExactly("ok");
        assertThat(read.dsos(DirectiveBinding.INSTANCE))
                .extracting(DirectiveDso::id)
                .containsExactly("DIRECTIVE_900");
        assertThat(reasonFor(read, "assets/rule.asset.yaml"))
                .isEqualTo("payload assets/x.directive.yaml does not exist");
    }

    /**
     * Analysis U2: a {@code path} that climbs out of {@code assets/} or is absolute names no asset file, even when a
     * file sits where it points: refused, naming the path.
     */
    @Test
    void aPathThatEscapesTheAssetsDirectoryIsRefusedNamingThePath(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        write(source, "outside.md", "outside\n");
        write(source, "assets/climb.asset.yaml", sidecar("climb", "../outside.md"));
        Path absolute = source.resolve("outside.md").toAbsolutePath();
        write(source, "assets/absolute.asset.yaml", sidecar("absolute", absolute.toString()));
        write(source, "assets/in.md.asset.yaml", sidecar("in", "in.md"));
        write(source, "assets/in.md", "in\n");

        FilesystemPackStore.PackRead read = new FilesystemPackStore(host, strategy).read(SOURCE);

        assertThat(ids(read)).containsExactly("in");
        assertThat(reasonFor(read, "assets/climb.asset.yaml")).isEqualTo("payload assets/../outside.md does not exist");
        assertThat(reasonFor(read, "assets/absolute.asset.yaml"))
                .isEqualTo("payload assets/" + absolute + " does not exist");
    }

    static String sha256(Path file) throws IOException {
        return Payload.of(Files.readAllBytes(file)).sha256();
    }

    /**
     * WP09 (T037): an asset file's path passes the companions' containment rule ({@link Companions#inPack}), the one
     * path rule owner, while a YAML payload, which a companion may not be, is written.
     */
    @Test
    void aPayloadPathTheContainmentRuleRefusesIsNotWritten(@TempDir Path host) throws IOException {
        Path target = pack(host, "target");
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);
        Payload payload = Payload.of(BINARY);

        store.writePayload(TARGET, "assets/tools/lint.config.yaml", payload);

        assertThat(Files.readAllBytes(target.resolve("assets/tools/lint.config.yaml")))
                .isEqualTo(BINARY);
        assertThatThrownBy(() -> store.writePayload(TARGET, "assets/../../outside.bin", payload))
                .isInstanceOf(CharterRefusal.class)
                .hasMessage("payload assets/../../outside.bin leaves the pack");
        assertThatThrownBy(() -> store.writePayload(TARGET, "assets\\x.bin", payload))
                .isInstanceOf(CharterRefusal.class)
                .hasMessage("payload assets\\x.bin is not a valid path");
        assertThat(host.resolve("outside.bin")).doesNotExist();
    }
}
