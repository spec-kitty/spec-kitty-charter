package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.PackRevision;
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
        builder.toolguide(Toolguide.builder()
                .id(new ToolguideIdentifier(toolguideId))
                .packId(baseline.id())
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep")
                .guidePath("guides/" + toolguideId + ".md")
                .summary("How to search.")
                .build());
        return builder.build();
    }

    @Test
    @DisplayName(
            "a successful commit replaces the pack content, keeps non-child files, and returns the current revision")
    void aSuccessfulCommitReplacesContentAndReturnsARevision(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        Files.createDirectories(packDir.resolve("guides"));
        Files.writeString(packDir.resolve("guides/keep.md"), "KEEP", StandardCharsets.UTF_8);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService service = new DomainRepositoryService(locations(packDir), strategy);
        PackCommitStore store = new PackCommitStore(locations(packDir), strategy);
        CommitStorePackWriteStore writeStore = new CommitStorePackWriteStore(locations(packDir), strategy, store);

        Pack mutated = mutatedWithToolguide(service.load(PACK), "ripgrep");
        PackRevision revision = store.commit(PACK, mutated, SHARD);

        assertThat(revision)
                .as("the ETag a commit answers is the revision the store then reports")
                .isEqualTo(writeStore.currentRevision(PACK));
        assertThat(service.load(PACK).toolguides())
                .extracting(tg -> tg.id().value())
                .contains("ripgrep");
        assertThat(packDir.resolve("guides/keep.md")).exists();
        assertThat(Files.readString(packDir.resolve("guides/keep.md"))).isEqualTo("KEEP");
        assertThat(packDir.resolve(RelationshipShard.SHARD_PATH)).exists();
        assertThat(Files.readString(packDir.resolve(RelationshipShard.SHARD_PATH)))
                .isEqualTo(SHARD);
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
        Pack mutated = mutatedWithToolguide(service.load(PACK), "ripgrep");

        assertThatThrownBy(() -> store.commit(PACK, mutated, SHARD)).isInstanceOf(CharterRefusal.class);

        assertThat(snapshot(packDir))
                .as("the live pack is byte-for-byte unchanged")
                .isEqualTo(before);
        assertThat(service.load(PACK).toolguides()).isEmpty();
        assertThat(noCommitResidue(host)).as("no staging or aside residue").isTrue();
    }

    @Test
    @DisplayName("a second commit is atomic too: both toolguides land and the revision changes")
    void aSecondCommitIsAtomicToo(@TempDir Path host) throws IOException {
        Path packDir = PackFixtures.copyOf("pack-a", host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService service = new DomainRepositoryService(locations(packDir), strategy);
        PackCommitStore store = new PackCommitStore(locations(packDir), strategy);

        PackRevision first = store.commit(PACK, mutatedWithToolguide(service.load(PACK), "ripgrep"), null);
        PackRevision second = store.commit(PACK, mutatedWithToolguide(service.load(PACK), "fd"), null);

        assertThat(second).isNotEqualTo(first);
        assertThat(service.load(PACK).toolguides())
                .extracting(tg -> tg.id().value())
                .contains("ripgrep", "fd");
        assertThat(noCommitResidue(host)).as("no staging residue").isTrue();
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
