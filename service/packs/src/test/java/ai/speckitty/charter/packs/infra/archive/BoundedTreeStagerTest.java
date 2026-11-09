package ai.speckitty.charter.packs.infra.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import ai.speckitty.charter.packs.application.PackTreeReader.TreeWalk;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The stager of a walked tree (IC-05, NFR-004, NFR-005): the path rules and limits of the archive extractor, applied to
 * entries a source walked, with nothing left behind when it refuses.
 */
class BoundedTreeStagerTest {

    private static final ArchiveLimits SMALL = new ArchiveLimits(1024, 100, 3, 0, 4096);

    @TempDir
    Path staging;

    private static InputStream text(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static TreeWalk walk(Map<String, String> files) {
        return sink -> {
            for (Map.Entry<String, String> file : files.entrySet()) {
                sink.file(file.getKey(), file.getValue().length(), text(file.getValue()));
            }
        };
    }

    private Code refusedWith(TreeWalk walk, ArchiveLimits limits) {
        try {
            new BoundedTreeStager(limits).stage(walk, staging);
        } catch (ArchiveRefusal refusal) {
            assertThat(ArchiveFixtures.listing(staging))
                    .as("nothing is left in staging")
                    .isEmpty();
            return refusal.violations().getFirst().code();
        } catch (IOException unexpected) {
            throw new AssertionError(unexpected);
        }
        throw new AssertionError("the tree was not refused");
    }

    @Test
    void aWalkedTreeIsStagedOwnerOnlyUnderThePackDirectory() throws Exception {
        TreeWalk tree = sink -> {
            sink.directory("directives");
            sink.file("pack.yaml", 5, text("hello"));
            sink.file("directives/a.directive.yaml", 3, text("abc"));
        };

        StagedPack staged = new BoundedTreeStager().stage(tree, staging);

        assertThat(staged.directory()).isEqualTo(staging.resolve("pack"));
        assertThat(ArchiveFixtures.tree(staged.directory()))
                .containsOnly(
                        Map.entry("pack.yaml", "hello"),
                        Map.entry("directives/", ""),
                        Map.entry("directives/a.directive.yaml", "abc"));
        ArchiveFixtures.assertOwnerOnly(staged.directory());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "../outside.yaml|PATH_TRAVERSAL",
                "a/../b.yaml|PATH_TRAVERSAL",
                "/etc/passwd|PATH_ABSOLUTE",
                "a\\b.yaml|PATH_BACKSLASH",
                "C:/x.yaml|PATH_DRIVE_LETTER",
                "a//b.yaml|PATH_EMPTY_SEGMENT",
                "bad name.yaml|PATH_CHARACTER",
                "caf\u00e9.yaml|PATH_CHARACTER"
            })
    void aPathThatBreaksARuleIsRefusedWithItsViolation(String path, Code expected) {
        assertThat(refusedWith(walk(Map.of(path, "x")), ArchiveLimits.DEFAULTS)).isEqualTo(expected);
    }

    @Test
    void twoPathsThatMeetAfterCaseFoldingCollide() {
        TreeWalk tree = sink -> {
            sink.file("Pack.yaml", 1, text("a"));
            sink.file("pack.yaml", 1, text("b"));
        };

        assertThat(refusedWith(tree, ArchiveLimits.DEFAULTS)).isEqualTo(Code.PATH_COLLISION);
    }

    @Test
    void moreEntriesThanTheLimitAreRefused() {
        TreeWalk tree = sink -> {
            for (int i = 0; i < 4; i++) {
                sink.file("f" + i + ".yaml", 1, text("x"));
            }
        };

        assertThat(refusedWith(tree, SMALL)).isEqualTo(Code.TOO_MANY_ENTRIES);
    }

    @Test
    void declaredSizesOverTheExpandedLimitAreRefusedBeforeABodyByteIsRead() {
        TreeWalk tree = sink -> sink.file("big.yaml", 101, new FailingStream("a byte was read"));

        assertThat(refusedWith(tree, SMALL)).isEqualTo(Code.EXPANDED_TOO_LARGE);
    }

    @Test
    void aFileThatGrowsPastItsDeclaredSizeWhileItIsReadIsRefused() {
        TreeWalk tree = sink -> sink.file("grows.yaml", 2, text("far more than two bytes"));

        assertThat(refusedWith(tree, SMALL)).isEqualTo(Code.EXPANDED_TOO_LARGE);
    }

    @Test
    void aTreeThatCannotBeReadLeavesNothingBehind() {
        TreeWalk tree = sink -> {
            sink.file("a.yaml", 1, text("x"));
            throw new IOException("the disk went away");
        };

        assertThatThrownBy(() -> new BoundedTreeStager().stage(tree, staging))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("disk");
        assertThat(ArchiveFixtures.listing(staging)).isEmpty();
    }

    @Test
    void aFileWhoseDataFailsToReadLeavesNothingBehind() {
        TreeWalk tree = sink -> sink.file("a.yaml", 5, new FailingStream("unreadable"));

        assertThatThrownBy(() -> new BoundedTreeStager().stage(tree, staging)).isInstanceOf(IOException.class);
        assertThat(ArchiveFixtures.listing(staging)).isEmpty();
    }

    @Test
    void theViolationsAreListedAndTheRestCounted() {
        TreeWalk tree = sink -> {
            for (int i = 0; i < ArchiveRefusal.MAX_LISTED + 5; i++) {
                sink.file("bad name " + i + ".yaml", 1, text("x"));
            }
        };

        assertThatThrownBy(() -> new BoundedTreeStager().stage(tree, staging))
                .isInstanceOfSatisfying(ArchiveRefusal.class, refusal -> {
                    assertThat(refusal.violations()).hasSize(ArchiveRefusal.MAX_LISTED);
                    assertThat(refusal.omitted()).isEqualTo(5);
                });
    }

    /** A stream that fails every read with an {@link IOException}. */
    private static final class FailingStream extends InputStream {
        private final String message;

        FailingStream(String message) {
            this.message = message;
        }

        @Override
        public int read() throws IOException {
            throw new IOException(message);
        }

        @Override
        public int read(byte[] into, int offset, int length) throws IOException {
            throw new IOException(message);
        }
    }
}
