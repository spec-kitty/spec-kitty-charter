package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
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
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
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
        return Toolguide.builder()
                .id(new ToolguideIdentifier(toolguideId))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep")
                .guidePath("guides/" + toolguideId + ".md")
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
                                Stream.of("ripgrep.toolguide.yaml", RelationshipShard.SHARD_PATH))
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
    @DisplayName("a swap a crash interrupted between its two renames is restored from the aside copy on the next write")
    void anInterruptedSwapIsRestoredBeforeTheNextWrite(@TempDir Path host) throws IOException {
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
        Files.writeString(packDir.resolve(PackFileSet.PACK_YAML), "pack_id: [\n", StandardCharsets.UTF_8);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(
                locations(packDir), strategy, new PackCommitStore(locations(packDir), strategy));
        ToolguideRegistrationService registrations = new ToolguideRegistrationService(
                new DomainRepositoryService(locations(packDir), strategy), writeStore, new PermissiveWritePolicyGate());

        assertThatThrownBy(() -> registrations.registerToolguide(PACK, toolguide("ripgrep"), true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        assertThatThrownBy(() ->
                        registrations.registerToolguide(new PackIdentifier("ghost"), toolguide("ripgrep"), true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    /** Whether the packs-root holds no staging ({@code .pack-commit-}) or aside ({@code .pack-old-}) residue. */
    private static boolean noCommitResidue(Path packsRoot) throws IOException {
        try (var entries = Files.list(packsRoot)) {
            return entries.map(p -> String.valueOf(p.getFileName()))
                    .noneMatch(name -> name.startsWith(".pack-commit-") || name.contains(".pack-old-"));
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
