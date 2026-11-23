package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.infra.storage.rules.LinkRules;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * No read follows a link (D6, PR #608 review MEDIUM-2): a file the walk listed and that is swapped for a link before
 * it is read is refused in the link rule's words ({@link LinkRules#LINKED}) and never read through. Each read opens the
 * file with {@code NOFOLLOW_LINKS} ({@code O_NOFOLLOW} on Linux), so the refusal comes from the open itself, not from a
 * check before it.
 *
 * <p>The swap is staged deterministically between the walk and the read through the seam each surface has: the
 * inspector's walker, and the store's {@link FilesystemPackStore.PayloadRead}. The commit store's revision and the
 * fingerprint's digest have no seam between their walk and their read, so their read half is driven directly over a
 * path that is a link, as a walk that listed the file before the swap hands it.
 */
@DisplayName("D6: a file swapped for a link after the walk is refused, never read through (PR #608 MEDIUM-2)")
class LinkSwapReadTest {

    private static final String PACK = "qa";

    private static final String DIRECTIVE = "directives/A.directive.yaml";

    private static final String DESCRIPTOR =
            "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n";

    /** A text no pack file holds: if a read went through the link, it would surface. */
    private static final String MARKER = "zqswapmarker9c1e";

    private static String directive(String title) {
        return "schema_version: \"1.0\"\nid: A\ntitle: " + title + "\nintent: Intent of A\nenforcement: required\n";
    }

    /** A valid org pack named {@value #PACK} holding one directive, in {@code host/packs/qa}. */
    private static Path pack(Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("packs").resolve(PACK));
        write(pack.resolve("pack.yaml"), DESCRIPTOR);
        write(pack.resolve(DIRECTIVE), directive("A"));
        return pack;
    }

    /** A valid directive whose title is the marker, outside every pack. */
    private static Path marker(Path host) throws IOException {
        Path marker = host.resolve("marker.directive.yaml");
        write(marker, directive(MARKER));
        return marker;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** Replaces the regular file {@code file} with a link to {@code target}: the swap a race would make. */
    private static void swap(Path file, Path target) {
        try {
            Files.delete(file);
            SymbolicLinks.create(file, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- the one helper ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the no-follow read refuses a link in the link rule's words, and reads a regular file whole")
    void theNoFollowReadRefusesALink(@TempDir Path host) throws IOException {
        Path marker = marker(host);
        Path link = SymbolicLinks.create(host.resolve("link.yaml"), marker);

        assertThatThrownBy(() -> PackFileSet.noFollowBytes(link))
                .isInstanceOf(PackFileSet.LinkedFile.class)
                .hasMessage(LinkRules.detail(link.toString()));
        assertThatThrownBy(() -> PackFileSet.noFollowText(link)).isInstanceOf(PackFileSet.LinkedFile.class);
        assertThat(new String(PackFileSet.noFollowBytes(marker), StandardCharsets.UTF_8))
                .contains(MARKER);
        assertThat(PackFileSet.noFollowText(marker)).contains(MARKER);
    }

    // ---- validate mode: FileSystemPackInspector#read -------------------------------------------------------------

    @Test
    @DisplayName("validate mode: a directive swapped for a link after the walk refuses the pack, naming the link")
    void theInspectorRefusesAFileSwappedAfterTheWalk(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path marker = marker(host);
        FileSystemPackInspector inspector = new FileSystemPackInspector(
                PackFileKind.bundled(),
                new YamlSerializationStrategy(),
                KindBindings.all(),
                Companions.Rules.bundled(),
                (directory, name) -> {
                    PackFileSet.Walk walk = PackFileSet.walk(directory, name);
                    swap(directory.resolve(DIRECTIVE), marker);
                    return walk;
                });

        PackReadout readout = inspector.read(new PackRoot(PACK, Optional.empty(), pack.toString()));

        assertThat(Files.isSymbolicLink(pack.resolve(DIRECTIVE)))
                .as("the swap happened")
                .isTrue();
        List<Reason> details = readout.rationale().details();
        assertThat(details)
                .filteredOn(reason -> StorageCodes.PACK_UNREADABLE.equals(reason.code()))
                .singleElement()
                .satisfies(reason -> assertThat(reason.detail()).isEqualTo(LinkRules.detail(PACK + "/" + DIRECTIVE)));
        assertThat(readout.toString()).as("the marker is never read").doesNotContain(MARKER);
    }

    // ---- the store's load: FilesystemPackStore#FILES -------------------------------------------------------------

    @Test
    @DisplayName("the load: an asset file swapped for a link after the walk is a violation in the link rule's words")
    void theLoadRefusesAnAssetFileSwappedAfterTheWalk(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path asset = pack.resolve("assets/logo.bin");
        Files.createDirectories(asset.getParent());
        Files.write(asset, new byte[] {1, 2, 3});
        Path marker = host.resolve("marker.bin");
        Files.writeString(marker, MARKER, StandardCharsets.UTF_8);
        // The store's own read, after the swap: the seam stages the race, the read under test is the store's.
        FilesystemPackStore.PayloadRead swapping = file -> {
            if (file.equals(asset)) {
                swap(asset, marker);
            }
            return FilesystemPackStore.FILES.read(file);
        };

        FilesystemPackStore.PackRead read = new FilesystemPackStore(
                        host.resolve("packs"), new YamlSerializationStrategy(), swapping)
                .read(PackDirectory.of(new PackIdentifier(PACK)));

        assertThat(Files.isSymbolicLink(asset)).as("the swap happened").isTrue();
        assertThat(read.assetFiles()).extracting(AssetFile::path).doesNotContain("logo.bin");
        assertThat(read.assetFiles())
                .as("the marker is never read")
                .noneSatisfy(file -> assertThat(new String(file.payload().bytes(), StandardCharsets.UTF_8))
                        .contains(MARKER));
        assertThat(read.violations())
                .contains(PackElementViolation.wholeFile("assets/logo.bin", StorageCodes.UNREADABLE, LinkRules.LINKED));
    }

    @Test
    @DisplayName("the load's text read of a file that is a link gives no text, never the link's target")
    void theStoreTextReadRefusesALink(@TempDir Path host) throws IOException {
        Path link = SymbolicLinks.create(host.resolve("A.directive.yaml"), marker(host));

        assertThat(FilesystemPackStore.readBytes(link)).isEmpty();
        assertThat(FilesystemPackStore.readText(link)).isEmpty();
    }

    // ---- the commit store's revision: PackCommitStore#revisionOf -------------------------------------------------

    @Test
    @DisplayName("the revision: a listed file that is a link at the read refuses the pack, naming the link")
    void theRevisionRefusesAFileSwappedAfterTheWalk(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        List<String> listed = PackFileSet.digested(pack, PACK);
        assertThat(listed).contains(DIRECTIVE);
        swap(pack.resolve(DIRECTIVE), marker(host));

        assertThatThrownBy(() -> PackCommitStore.revisionOver(pack, PACK, listed))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage(LinkRules.detail(PACK + "/" + DIRECTIVE));
    }

    // ---- the fingerprint's digest: FileSystemPackSources#bytes ---------------------------------------------------

    @Test
    @DisplayName("the digest: a listed file that is a link at the read refuses the pack, naming the link")
    void theDigestRefusesAFileSwappedAfterTheWalk(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        swap(pack.resolve(DIRECTIVE), marker(host));

        assertThatThrownBy(() -> FileSystemPackSources.bytes(pack.resolve(DIRECTIVE), PACK, DIRECTIVE))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage(LinkRules.detail(PACK + "/" + DIRECTIVE));
    }
}
