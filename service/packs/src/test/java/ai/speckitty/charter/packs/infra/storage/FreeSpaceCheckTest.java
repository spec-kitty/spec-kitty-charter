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

/** The room a registration needs on the pack volume (NFR-009): twice the 32 MiB archive bound, judged by a fake store. */
class FreeSpaceCheckTest {

    private static final long MIB = 1024L * 1024L;

    @Test
    void lessThanTwiceTheArchiveBoundIsRefusedStorageFull() {
        FreeSpaceCheck check = new FreeSpaceCheck(() -> storeWith(63 * MIB));

        assertThatThrownBy(check::require).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_STORAGE_FULL);
            assertThat(refusal.getMessage()).contains("64 MiB").contains("63 MiB");
        });
    }

    @Test
    void twiceTheArchiveBoundOrMoreIsAccepted() {
        assertThatCode(new FreeSpaceCheck(() -> storeWith(65 * MIB))::require).doesNotThrowAnyException();
        assertThatCode(new FreeSpaceCheck(() -> storeWith(64 * MIB))::require)
                .as("exactly the bound is room enough: only less than it is refused")
                .doesNotThrowAnyException();
    }

    @Test
    void aVolumeWhoseSpaceCannotBeReadRefusesRatherThanGuesses() {
        FreeSpaceCheck check = new FreeSpaceCheck(() -> new FakeStore(-1));

        assertThatThrownBy(check::require)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
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
