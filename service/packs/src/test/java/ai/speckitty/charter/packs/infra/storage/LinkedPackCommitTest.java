package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PreconditionException;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A commit to a pack whose directory under {@code <config-root>/packs} is a symbolic link (#174, ADR-0038 as amended
 * 2026-10-31): for a target inside the configuration root, the commit stages beside the link's real target and swaps
 * there, so the link stays a link and its target holds the new content; a pack linked from outside the configuration
 * root is read, but read-only through the service. Read and written through the production
 * {@link StorageRootLocations}.
 */
class LinkedPackCommitTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final String SHARD =
            "schema_version: '1.0'\ngenerated_at: '2026-01-01T00:00:00Z'\nnodes: []\nedges: []\n";

    @TempDir
    Path host;

    private Path packs;
    private Path link;
    private Path target;
    private StorageRootLocations locations;
    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @BeforeEach
    void aPackLinkedFromInsideTheConfigRoot() throws IOException {
        packs = Files.createDirectories(host.resolve("config/packs"));
        // A hidden directory of the pack domain is no pack itself, so a link into it overlaps none.
        linkTo(Files.createDirectories(packs.resolve(".vendor")));
    }

    /** Links {@code packs/pack-a} to a copy of the pack, under another name, in {@code checkout}. */
    private void linkTo(Path checkout) throws IOException {
        // The target has another name than the link, so a write that confuses them shows.
        target = Files.move(PackFixtures.copyOf("pack-a", checkout), checkout.resolve("real-pack-a"))
                .toRealPath();
        link = packs.resolve("pack-a");
        Files.deleteIfExists(link);
        SymbolicLinks.create(link, target);
        locations = new StorageRootLocations(packs);
    }

    @Test
    void aPackLinkedFromOutsideTheConfigRootIsReadButACommitToItIsRefusedAndChangesNothing() throws IOException {
        linkTo(Files.createDirectories(host.resolve("checkout")));
        Map<String, String> before = snapshot(target);
        PackCommitStore store = new PackCommitStore(locations, strategy);

        assertThat(new DomainRepositoryService(locations, strategy).load(PACK).tactics())
                .as("read and served through the link")
                .isNotEmpty();
        assertThatThrownBy(() -> store.commit(PACK, null, toolguide("ripgrep"), SHARD))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessageContaining("pack-a")
                .hasMessageContaining("read-only through the service");

        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(snapshot(target)).as("the target is unchanged").isEqualTo(before);
        assertThat(residue(target.getParent()))
                .as("nothing was staged beside the target")
                .isEmpty();
    }

    @Test
    void aPackDomainLinkedFromOutsideTheConfigRootIsReadButEveryCommitToItIsRefused() throws IOException {
        Path config = Files.createDirectories(host.resolve("linked-domain-config"));
        Path shared = Files.createDirectories(host.resolve("shared-packs"));
        Path real = PackFixtures.copyOf("pack-a", shared).toRealPath();
        Path domain = SymbolicLinks.create(config.resolve("packs"), shared);
        StorageRootLocations linkedDomain = new StorageRootLocations(domain);
        Map<String, String> before = snapshot(real);

        assertThat(new DomainRepositoryService(linkedDomain, strategy)
                        .load(PACK)
                        .tactics())
                .as("read and served through the linked domain (#285)")
                .isNotEmpty();
        assertThatThrownBy(() ->
                        new PackCommitStore(linkedDomain, strategy).commit(PACK, null, toolguide("ripgrep"), SHARD))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage("pack pack-a is linked from outside the configuration root; it is read-only through the"
                        + " service");

        assertThat(snapshot(real)).as("the pack is unchanged").isEqualTo(before);
        assertThat(residue(shared)).as("nothing was staged beside it").isEmpty();
    }

    @Test
    void aCommitKeepsTheLinkAndWritesTheNewContentIntoItsTarget() throws IOException {
        Map<String, String> before = snapshot(target);
        PackCommitStore store = new PackCommitStore(locations, strategy);

        store.commit(PACK, null, toolguide("ripgrep"), SHARD);

        assertThat(Files.isSymbolicLink(link)).as("the link is still a link").isTrue();
        assertThat(Files.readSymbolicLink(link))
                .as("pointing at the same target")
                .isEqualTo(target);
        Map<String, String> after = snapshot(target);
        assertThat(after).containsKeys("ripgrep.toolguide.yaml", "ripgrep.guide.md", RelationshipShard.SHARD_PATH);
        before.forEach((file, content) ->
                assertThat(after.get(file)).as("%s is byte-unchanged", file).isEqualTo(content));
        assertThat(new DomainRepositoryService(locations, strategy).load(PACK).toolguides())
                .extracting(guide -> guide.id().value())
                .contains("ripgrep");
        assertThat(residue(packs))
                .as("no staging or aside copy in the pack domain")
                .isEmpty();
        assertThat(residue(target.getParent()))
                .as("no staging or aside copy beside the target")
                .isEmpty();
    }

    @Test
    void theSwapRunsOnTheRealTargetWithItsStagingCopyBesideIt() throws IOException {
        List<Path[]> swaps = new ArrayList<>();
        PackCommitStore.DirectoryRename recording = (staging, live) -> {
            swaps.add(new Path[] {staging, live});
            throw new IOException("stop before the swap");
        };
        Map<String, String> before = snapshot(target);

        assertThatThrownBy(() -> new PackCommitStore(locations, strategy, recording)
                        .commit(PACK, null, toolguide("ripgrep"), SHARD))
                .isInstanceOf(CharterRefusal.class);

        assertThat(swaps).hasSize(1);
        assertThat(swaps.getFirst()[1])
                .as("the live directory swapped is the real target")
                .isEqualTo(target);
        assertThat(swaps.getFirst()[0].getParent())
                .as("staged on the target's own file system, so the rename is atomic")
                .isEqualTo(target.getParent());
        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(snapshot(target)).as("a refused swap changes no file").isEqualTo(before);
        assertThat(residue(target.getParent())).isEmpty();
    }

    @Test
    void theWriteStoreHoldsTheLinkedPacksIfMatchAndReportsTheRevisionTheCommitAnswered() {
        PackCommitStore commits = new PackCommitStore(locations, strategy);
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations, strategy, commits);
        String ifMatch = writeStore.currentRevision(PACK).token();

        PackRevision committed =
                writeStore.exclusively(PACK, () -> commits.commit(PACK, ifMatch, toolguide("ripgrep"), null));

        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(target.resolve("ripgrep.toolguide.yaml")).isRegularFile();
        assertThat(writeStore.currentRevision(PACK)).isEqualTo(committed);
        assertThatThrownBy(() -> commits.commit(PACK, ifMatch, toolguide("fd"), null))
                .as("the old If-Match is stale after the commit through the link")
                .isInstanceOf(PreconditionException.class);
    }

    private static Toolguide toolguide(String id) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep")
                .guide(Guide.of("How to use the tool."))
                .summary("How to search.")
                .build();
    }

    private static List<String> residue(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> String.valueOf(entry.getFileName()))
                    .filter(name -> name.startsWith(".pack-commit-") || name.contains(".pack-old-"))
                    .toList();
        }
    }

    private static Map<String, String> snapshot(Path directory) {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                out.put(directory.relativize(file).toString(), Files.readString(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
