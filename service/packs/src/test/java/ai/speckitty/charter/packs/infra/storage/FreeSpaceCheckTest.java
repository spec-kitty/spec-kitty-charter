package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The room a registration needs (NFR-009, #418 A6): the per-job peak, which holds the archive, the extracted tree and the
 * commit copy, each at most the 32 MiB bound. On one volume that is three times the bound for an archive of the bound; on
 * two, the staging volume holds the archive and the tree and the pack volume the commit copy. Judged by fake stores.
 */
class FreeSpaceCheckTest {

    private static final long MIB = 1024L * 1024L;

    @Test
    void lessThanThePeakOfAFullSizeArchiveOnOneVolumeIsRefusedStorageFull() {
        FreeSpaceCheck check = new FreeSpaceCheck(() -> storeWith(95 * MIB));

        assertThatThrownBy(check::require).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_STORAGE_FULL);
            assertThat(refusal.getMessage()).contains("96 MiB").contains("95 MiB");
        });
    }

    @Test
    void thePeakOrMoreOnOneVolumeIsAccepted() {
        assertThatCode(new FreeSpaceCheck(() -> storeWith(97 * MIB))::require).doesNotThrowAnyException();
        assertThatCode(new FreeSpaceCheck(() -> storeWith(96 * MIB))::require)
                .as("exactly the peak is room enough: only less than it is refused")
                .doesNotThrowAnyException();
    }

    @Test
    void anArchiveOfUnknownSizeNeedsTheRoomOfAFullSizeOne() {
        assertThatThrownBy(() -> new FreeSpaceCheck(() -> storeWith(95 * MIB)).require(-1))
                .isInstanceOf(CharterRefusal.class);
        assertThatCode(() -> new FreeSpaceCheck(() -> storeWith(96 * MIB)).require(-1))
                .doesNotThrowAnyException();
    }

    @Test
    void aSmallerArchiveNeedsItsOwnSizePlusTheTwoBoundsOfTheTreeAndTheCopy() {
        assertThatThrownBy(() -> new FreeSpaceCheck(() -> storeWith(64 * MIB)).require(1 * MIB))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_STORAGE_FULL);
                    assertThat(refusal.getMessage()).contains("65 MiB");
                });
        assertThatCode(() -> new FreeSpaceCheck(() -> storeWith(65 * MIB)).require(1 * MIB))
                .doesNotThrowAnyException();
    }

    @Test
    void anArchiveLargerThanTheBoundIsJudgedAsTheBound() {
        assertThatCode(() -> new FreeSpaceCheck(() -> storeWith(96 * MIB)).require(500 * MIB))
                .as("a body over the bound is a 413 before this check; the check never asks for more than the peak")
                .doesNotThrowAnyException();
    }

    @Test
    void onTwoVolumesTheStagingVolumeHoldsTheArchiveAndTheTreeAndThePackVolumeTheCommitCopy() {
        FileStore packs = storeWith(1024 * MIB);
        FileStore staging = storeWith(63 * MIB);
        assertThatThrownBy(() -> new FreeSpaceCheck(() -> packs, () -> staging).require())
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_STORAGE_FULL);
                    assertThat(refusal.getMessage())
                            .contains("staging volume")
                            .contains("63 MiB")
                            .contains("64 MiB");
                });

        FileStore tightPacks = storeWith(31 * MIB);
        FileStore roomyStaging = storeWith(1024 * MIB);
        assertThatThrownBy(() -> new FreeSpaceCheck(() -> tightPacks, () -> roomyStaging).require())
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_STORAGE_FULL);
                    assertThat(refusal.getMessage())
                            .contains("pack volume")
                            .contains("31 MiB")
                            .contains("32 MiB");
                });

        assertThatCode(() -> new FreeSpaceCheck(() -> storeWith(32 * MIB), () -> storeWith(64 * MIB)).require())
                .doesNotThrowAnyException();
    }

    @Test
    void aVolumeWhoseSpaceCannotBeReadRefusesRatherThanGuesses() {
        FreeSpaceCheck check = new FreeSpaceCheck(() -> new FakeStore(-1));
        FreeSpaceCheck stagingUnreadable = new FreeSpaceCheck(() -> storeWith(1024 * MIB), () -> new FakeStore(-1));

        for (FreeSpaceCheck unreadable : new FreeSpaceCheck[] {check, stagingUnreadable}) {
            assertThatThrownBy(unreadable::require)
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
        }
    }

    @Test
    void theVolumeOfAPackDomainThatIsNotCreatedYetIsThatOfItsNearestExistingAncestor(@TempDir java.nio.file.Path host)
            throws IOException {
        java.nio.file.Path missing = host.resolve("config").resolve("packs");

        assertThat(FreeSpaceCheck.volumeOf(missing)).isEqualTo(java.nio.file.Files.getFileStore(host));
        java.nio.file.Files.createDirectories(missing);
        assertThat(FreeSpaceCheck.volumeOf(missing)).isEqualTo(java.nio.file.Files.getFileStore(missing));
    }

    private static FileStore storeWith(long usable) {
        return new FakeStore(usable);
    }

    /** A file store that reports one usable size, or fails when it is negative. */
    private static final class FakeStore extends FileStore {
        private final long usable;

        FakeStore(long usable) {
            this.usable = usable;
        }

        @Override
        public long getUsableSpace() throws IOException {
            if (usable < 0) {
                throw new IOException("no such volume");
            }
            return usable;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public String type() {
            return "fake";
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public long getTotalSpace() {
            return Long.MAX_VALUE;
        }

        @Override
        public long getUnallocatedSpace() {
            return usable;
        }

        @Override
        public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
            return false;
        }

        @Override
        public boolean supportsFileAttributeView(String name) {
            return false;
        }

        @Override
        public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
            return null;
        }

        @Override
        public Object getAttribute(String attribute) {
            return null;
        }
    }
}
