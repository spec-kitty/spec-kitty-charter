package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteStore.Change;
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
import ai.speckitty.charter.packs.application.PreconditionException;
import ai.speckitty.charter.packs.application.RelationshipRegistrationService;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The aggregate-commit store (NFR-002): a commit is whole-pack atomic. The load-bearing case is the injected pre-swap
 * failure, which must leave the live pack byte-for-byte unchanged and no staging residue; it is paired with the
 * accepted-commit control that proves the same mutation does change the pack when the swap runs.
 */
@DisplayName("PackCommitStore atomic commit")
class PackCommitStoreTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final String SHARD =
            "schema_version: '1.0'\ngenerated_at: '2026-01-01T00:00:00Z'\nnodes: []\nedges: []\n";

    private static DomainRepositoryService.Locations locations(Path packDir) {
        return () -> List.of(new PackLocation("pack-a", PackLevel.ORG, packDir));
    }

    private static Pack mutatedWithToolguide(Pack baseline, String toolguideId) {
        Pack.Builder builder = Pack.builder().id(baseline.id()).descriptor(baseline.descriptor());
        baseline.tactics().forEach(builder::tactic);
        baseline.directives().forEach(builder::directive);
        baseline.styleguides().forEach(builder::styleguide);
        baseline.toolguides().forEach(builder::toolguide);
        baseline.paradigms().forEach(builder::paradigm);
        baseline.violations().forEach(builder::violation);
        builder.toolguide(toolguide(toolguideId));
        return builder.build();
    }

    private static Toolguide toolguide(String toolguideId) {
        return toolguide(toolguideId, Guide.of("How to use the tool."));
    }

    private static Toolguide toolguide(String toolguideId, Guide guide) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(toolguideId))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep")
                .guide(guide)
                .summary("How to search.")
                .build();
    }

    @Test
    @DisplayName("a successful commit adds only the new artefact and the shard, leaves every other file byte-identical,"
            + " and returns the revision the store then reports")
    void aSuccessfulCommitReplacesContentAndReturnsARevision(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        Path packYaml = packDir.resolve(PackFileSet.PACK_YAML);
        Files.writeString(
                packYaml,
                "# Authored by hand; never machine-rewritten.\n" + Files.readString(packYaml, StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        Files.createDirectories(packDir.resolve("guides"));
        Files.writeString(packDir.resolve("guides/keep.md"), "KEEP", StandardCharsets.UTF_8);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService service = new DomainRepositoryService(locations(packDir), strategy);
        PackCommitStore store = new PackCommitStore(locations(packDir), strategy);
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations(packDir), strategy, store);
        Map<String, String> before = snapshot(packDir);

        PackRevision revision = store.commit(PACK, null, toolguide("ripgrep"), SHARD);

        assertThat(revision)
                .as("the ETag a commit answers is the revision the store then reports")
                .isEqualTo(writeStore.currentRevision(PACK));
        assertThat(service.load(PACK).toolguides())
                .extracting(tg -> tg.id().value())
                .contains("ripgrep");
        Map<String, String> after = snapshot(packDir);
        assertThat(after.keySet())
                .containsExactlyInAnyOrderElementsOf(Stream.concat(
                                before.keySet().stream(),
                                Stream.of("ripgrep.toolguide.yaml", "ripgrep.guide.md", RelationshipShard.SHARD_PATH))
                        .toList());
        before.forEach((file, content) ->
                assertThat(after.get(file)).as("%s is byte-unchanged", file).isEqualTo(content));
        assertThat(after.get(RelationshipShard.SHARD_PATH)).isEqualTo(SHARD);
        assertThat(noCommitResidue(host)).as("no staging residue").isTrue();
    }

    @Test
    @DisplayName(
            "a failure injected before the swap leaves the live pack byte-for-byte unchanged and no residue (NFR-002)")
    void aPreSwapFailureChangesZeroFiles(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        Files.createDirectories(packDir.resolve("guides"));
        Files.writeString(packDir.resolve("guides/keep.md"), "KEEP", StandardCharsets.UTF_8);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService service = new DomainRepositoryService(locations(packDir), strategy);
        PackCommitStore.DirectoryRename failingSwap = (staging, live) -> {
            throw new IOException("injected pre-swap failure");
        };
        PackCommitStore store = new PackCommitStore(locations(packDir), strategy, failingSwap);

        Map<String, String> before = snapshot(packDir);

        assertThatThrownBy(() -> store.commit(PACK, null, toolguide("ripgrep"), SHARD))
                .isInstanceOf(CharterRefusal.class);

        assertThat(snapshot(packDir))
                .as("the live pack is byte-for-byte unchanged")
                .isEqualTo(before);
        assertThat(service.load(PACK).toolguides()).isEmpty();
        assertThat(noCommitResidue(host)).as("no staging or aside residue").isTrue();
    }

    @Test
    @DisplayName("a commit whose If-Match went stale before the swap is refused (412) and changes zero files")
    void aCommitWithAStaleIfMatchIsRefusedAndChangesNothing(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService service = new DomainRepositoryService(locations(packDir), strategy);
        PackCommitStore store = new PackCommitStore(locations(packDir), strategy);
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations(packDir), strategy, store);
        String etag = writeStore.currentRevision(PACK).token();
        writeStore.commit(
                PACK,
                etag,
                Change.artifact(
                        mutatedWithToolguide(service.load(PACK), "fd"), toolguide("fd"))); // another writer lands
        Map<String, String> before = snapshot(packDir);

        assertThatThrownBy(() -> writeStore.commit(
                        PACK,
                        etag,
                        Change.artifact(
                                mutatedWithToolguide(service.load(PACK), "ripgrep"), toolguide("ripgrep"), List.of())))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));

        assertThat(snapshot(packDir))
                .as("the live pack is byte-for-byte unchanged")
                .isEqualTo(before);
        assertThat(noCommitResidue(host)).as("no staging residue").isTrue();
    }

    @Test
    @DisplayName("the non-listing fixed-Locations fallback restores an interrupted swap on the next write")
    void anInterruptedSwapIsRestoredBeforeTheNextWrite(@TempDir Path host) throws IOException {
        // Covers CommitStorePackWriteStore.exclusively's own restoreInterruptedSwap fallback for a fixed Locations
        // that never lists. This is NOT end-to-end reachability coverage: pre-#281 it passed falsely because the
        // fixed locations(packDir) lambda resolves pack-a without the production listing where the bug lived
        // (kitty-ops/research/issue-281-crash-repair.md). The listing path is covered by InterruptedCommitRepairTest.
        Path packDir = PackFixtures.copyOf("pack-a", host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(
                locations(packDir), strategy, new PackCommitStore(locations(packDir), strategy));
        Map<String, String> committed = snapshot(packDir);
        PackRevision revision = writeStore.currentRevision(PACK);
        // The state a crash after "move live aside" and before "move staging into place" leaves behind.
        Files.move(packDir, host.resolve("pack-a.pack-old-crashed"));

        PackRevision seen = writeStore.exclusively(PACK, () -> writeStore.currentRevision(PACK));

        assertThat(seen).isEqualTo(revision);
        assertThat(snapshot(packDir)).isEqualTo(committed);
        assertThat(noCommitResidue(host))
                .as("the aside copy was moved back, not copied")
                .isTrue();
    }

    @Test
    @DisplayName("a second commit is atomic too: both toolguides land and the revision changes")
    void aSecondCommitIsAtomicToo(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService service = new DomainRepositoryService(locations(packDir), strategy);
        PackCommitStore store = new PackCommitStore(locations(packDir), strategy);

        PackRevision first = store.commit(PACK, null, toolguide("ripgrep"), null);
        PackRevision second = store.commit(PACK, null, toolguide("fd"), null);

        assertThat(second).isNotEqualTo(first);
        assertThat(service.load(PACK).toolguides())
                .extracting(tg -> tg.id().value())
                .contains("ripgrep", "fd");
        assertThat(noCommitResidue(host)).as("no staging residue").isTrue();
    }

    @Test
    @DisplayName("a registration over a partly invalid relationship shard is refused and leaves the shard"
            + " byte-unchanged, while one into a pack without a shard lands")
    void aRegistrationOverAnInvalidShardIsRefusedAndKeepsTheShard(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        String partlyInvalid = """
                schema_version: '1.0'
                generated_at: STATIC
                generated_by: charter-registration
                nodes: []
                edges:
                - {source: 'tactic:alpha-tactic', target: 'directive:kept', relation: requires}
                - {source: QA_404, target: 'directive:kept', relation: requires}
                """;
        Path shard = packDir.resolve(RelationshipShard.SHARD_PATH);
        Files.writeString(shard, partlyInvalid, StandardCharsets.UTF_8);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService service = new DomainRepositoryService(locations(packDir), strategy);
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(
                locations(packDir), strategy, new PackCommitStore(locations(packDir), strategy));
        RelationshipRegistrationService registrations =
                new RelationshipRegistrationService(service, writeStore, new PermissiveWritePolicyGate());
        Relationship edge = Relationship.of(
                new Urn(NodeKind.TACTIC, "alpha-tactic"), new Urn(NodeKind.DIRECTIVE, "other"), RelationType.SUGGESTS);

        assertThatThrownBy(() -> registrations.registerRelationship(
                        PACK, edge, writeStore.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));

        assertThat(Files.readString(shard, StandardCharsets.UTF_8)).isEqualTo(partlyInvalid);

        // Positive control on the same fixture: a pack nothing was registered into yet has no shard, which is no
        // fault; the same registration then lands.
        Files.delete(shard);
        registrations.registerRelationship(
                PACK, edge, writeStore.currentRevision(PACK).token());
        assertThat(writeStore.registeredRelationships(PACK)).containsExactly(edge);
    }

    @Test
    @DisplayName("a write to a configured but broken pack is pack_unreadable, while an unconfigured pack is not found")
    void aBrokenConfiguredPackIsUnreadableNotNotFoundOnAWrite(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        String descriptor = Files.readString(packDir.resolve(PackFileSet.PACK_YAML), StandardCharsets.UTF_8);
        Files.writeString(packDir.resolve(PackFileSet.PACK_YAML), "pack_id: [\n", StandardCharsets.UTF_8);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(
                locations(packDir), strategy, new PackCommitStore(locations(packDir), strategy));
        ToolguideRegistrationService registrations = new ToolguideRegistrationService(
                new DomainRepositoryService(locations(packDir), strategy),
                writeStore,
                new PermissiveWritePolicyGate(),
                uri -> {
                    throw new AssertionError("no guide is fetched: the guide is sent as content");
                },
                new MarkdownGuideFormat());
        ToolguideRegistrationService.GuideSource guide =
                new ToolguideRegistrationService.GuideSource.Content("## Overview\n\nHow to search.");

        assertThatThrownBy(() -> registrations.registerToolguide(
                        PACK,
                        new ToolguideIdentifier("ripgrep"),
                        "Use ripgrep",
                        guide,
                        resolved -> toolguide("ripgrep"),
                        true,
                        null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        assertThatThrownBy(() -> registrations.registerToolguide(
                        new PackIdentifier("ghost"),
                        new ToolguideIdentifier("ripgrep"),
                        "Use ripgrep",
                        guide,
                        resolved -> toolguide("ripgrep"),
                        true,
                        null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));

        // Positive control: the pack repaired, the same registration lands, and the stored guide is the one sent.
        Files.writeString(packDir.resolve(PackFileSet.PACK_YAML), descriptor, StandardCharsets.UTF_8);
        registrations.registerToolguide(
                PACK,
                new ToolguideIdentifier("ripgrep"),
                "Use ripgrep",
                guide,
                resolved -> toolguide("ripgrep", resolved),
                true,
                null);
        assertThat(new DomainRepositoryService(locations(packDir), strategy)
                        .load(PACK).toolguides().stream()
                                .filter(t -> t.id().value().equals("ripgrep"))
                                .findFirst()
                                .orElseThrow()
                                .guide())
                .isEqualTo(Guide.of("How to search."));
    }

    /**
     * The revision {@code CommitStorePackWriteStore.currentRevision} reported for the unmodified {@code pack-a}
     * fixture on {@code main} at 90de8f9, when it still read every digested file as UTF-8 text. Captured by a
     * throwaway test printing {@code currentRevision} on this lane's base before the change (its revision readers
     * and the fixture were byte-identical to 90de8f9's); pinned so reading bytes does not move an existing ETag.
     */
    private static final String PACK_A_REVISION_ON_MAIN =
            "sha256:b220d430031fc948dbdd597b9ab9d88297a68b6588537e87fadc68f4d648c711";

    /** A root graph shard, which {@link PackFileSet#digested} covers, written with bytes that are not UTF-8. */
    private static final String NON_UTF8_SHARD = "binary.graph.yaml";

    private static final byte[] NON_UTF8_BYTES = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00, 0x00, (byte) 0xFF, (byte) 0xFE
    };

    @Test
    @DisplayName("the revision of a pack of UTF-8 files is the value main reported, read over bytes")
    void aUtf8PackKeepsTheRevisionMainReported(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(
                locations(packDir), strategy, new PackCommitStore(locations(packDir), strategy));

        assertThat(writeStore.currentRevision(PACK).token()).isEqualTo(PACK_A_REVISION_ON_MAIN);
    }

    @Test
    @DisplayName("a pack whose digested set holds a non-UTF-8 file reports a revision and commits, not pack_unreadable")
    void aPackWithANonUtf8DigestedFileCommitsAndReportsARevision(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        Files.write(packDir.resolve(NON_UTF8_SHARD), NON_UTF8_BYTES);
        assertThat(PackFileSet.digested(packDir))
                .as("the planted file is digested")
                .contains(NON_UTF8_SHARD);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        PackCommitStore store = new PackCommitStore(locations(packDir), strategy);
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations(packDir), strategy, store);

        PackRevision before = writeStore.currentRevision(PACK);
        PackRevision committed = store.commit(PACK, null, null, SHARD);

        assertThat(before.token()).isNotEqualTo(PACK_A_REVISION_ON_MAIN);
        assertThat(committed).isNotEqualTo(before).isEqualTo(writeStore.currentRevision(PACK));
        assertThat(Files.readAllBytes(packDir.resolve(NON_UTF8_SHARD))).isEqualTo(NON_UTF8_BYTES);
        assertThat(noCommitResidue(host)).as("no staging residue").isTrue();
    }

    @Test
    @DisplayName("If-Match with the reported revision passes over a non-UTF-8 file; after a one-byte edit of it the"
            + " same If-Match is refused as stale and nothing changes")
    void ifMatchHoldsOverBytesAndAOneByteEditMakesItStale(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        Path binary = packDir.resolve(NON_UTF8_SHARD);
        Files.write(binary, NON_UTF8_BYTES);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        PackCommitStore store = new PackCommitStore(locations(packDir), strategy);
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations(packDir), strategy, store);

        // The ETag comes from currentRevision; the commit store matches it against its own revisionOf the live pack.
        PackRevision committed =
                store.commit(PACK, writeStore.currentRevision(PACK).token(), null, SHARD);
        assertThat(committed).isEqualTo(writeStore.currentRevision(PACK));

        byte[] edited = NON_UTF8_BYTES.clone();
        edited[edited.length - 1] = (byte) 0xFD;
        Files.write(binary, edited);
        Map<String, byte[]> before = byteSnapshot(packDir);

        assertThatThrownBy(() -> store.commit(PACK, committed.token(), null, SHARD))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(byteSnapshot(packDir))
                .as("the live pack is byte-for-byte unchanged")
                .containsOnlyKeys(before.keySet())
                .allSatisfy((file, bytes) -> assertThat(bytes).isEqualTo(before.get(file)));
        assertThat(noCommitResidue(host)).as("no staging residue").isTrue();
    }

    /** Every regular file under {@code dir}, relative path to raw bytes, for files that are not UTF-8 text. */
    private static Map<String, byte[]> byteSnapshot(Path dir) {
        Map<String, byte[]> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                out.put(dir.relativize(file).toString(), Files.readAllBytes(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** Whether the packs-root holds no staging ({@code .pack-commit-}) or aside ({@code .pack-old-}) residue. */
    private static boolean noCommitResidue(Path packsRoot) throws IOException {
        try (var entries = Files.list(packsRoot)) {
            return entries.map(p -> String.valueOf(p.getFileName()))
                    .noneMatch(name -> name.startsWith(".pack-commit-")
                            || name.startsWith(".pack-discard-")
                            || name.contains(".pack-old-"));
        }
    }

    /** Every regular file under {@code dir}, relative path to UTF-8 content, for a byte-for-byte comparison. */
    private static Map<String, String> snapshot(Path dir) {
        Map<String, String> out = new TreeMap<>();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    out.put(dir.relativize(file).toString(), Files.readString(file, StandardCharsets.UTF_8));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
