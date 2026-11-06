package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.Revision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A write replaces {@code activation.json} whole, never in place. It replaces the old
 * {@code readersRacingWritersOnlyEverSeeAWholeWrittenDocument}, which read through {@code find}: {@code find} takes the
 * id's lock, as every writer does, so no reader ever ran during a write and that test passed with a non-atomic write.
 *
 * <p>Option (b) of the work package is the proof, chosen because it is deterministic: a probing {@link
 * FileActivationRepository.Durability} records, at the moment of each move, the file the target is before and after it.
 * An atomic move leaves the target as the temporary file itself (its file key is the temporary file's), and the bytes
 * it held up to the move are the old document, untouched. A planted non-atomic move (copy the bytes into the target,
 * then delete the temporary file) keeps the target's old file key, and the probe fails it; {@link
 * #thePlantedNonAtomicRenameFailsTheProbe} shows that, so the probe cannot pass either way. The test of option (a), lockless
 * readers straight from disk, runs too, as further evidence on a real file system.
 */
class FileActivationRepositoryAtomicityTest {

    private static final ActivationId ID = new ActivationId("atomic");
    private static final int ROUNDS = 100;

    @TempDir
    Path root;

    /** A non-atomic move: the target's own file is truncated and written over, then the temporary file deleted. */
    private static final FileActivationRepository.Durability IN_PLACE = (temp, target) -> {
        Files.write(
                target,
                Files.readAllBytes(temp),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.delete(temp);
    };

    /** A move that records whether the target became the temporary file, and whether it was written before the move. */
    private static final class Probe implements FileActivationRepository.Durability {

        private final FileActivationRepository.Durability delegate;
        private final List<String> faults = new ArrayList<>();
        private byte @Nullable [] lastWritten;

        Probe(FileActivationRepository.Durability delegate) {
            this.delegate = delegate;
        }

        @Override
        public void move(Path temp, Path target) throws IOException {
            Object tempKey = keyOf(temp);
            assumeThat(tempKey)
                    .as("the file system reports file keys (every POSIX one does)")
                    .isNotNull();
            byte[] before = bytesOrNull(target);
            if (lastWritten != null && !Arrays.equals(before, lastWritten)) {
                faults.add("the target was written before its move");
            }
            byte[] written = Files.readAllBytes(temp);
            delegate.move(temp, target);
            if (!tempKey.equals(keyOf(target))) {
                faults.add("the target was written in place, not replaced by the temporary file");
            }
            lastWritten = written;
        }

        void assertEveryMoveReplacedTheTargetWhole() {
            assertThat(faults).as("faults of the moves").isEmpty();
        }

        private static @Nullable Object keyOf(Path path) throws IOException {
            return Files.readAttributes(path, BasicFileAttributes.class).fileKey();
        }

        private static byte @Nullable [] bytesOrNull(Path path) throws IOException {
            try {
                return Files.readAllBytes(path);
            } catch (NoSuchFileException absent) {
                return null;
            }
        }
    }

    private void writeEveryRound(FileActivationRepository store) {
        store.create(ID, ActivationRepositoryContract.document(0));
        for (int n = 1; n <= ROUNDS; n++) {
            store.replace(ID, Optional.empty(), ActivationRepositoryContract.document(n));
        }
    }

    @Test
    void everyWriteReplacesTheTargetByTheWrittenTemporaryFile() {
        Probe probe = new Probe(FileActivationRepository.DISK);

        writeEveryRound(new FileActivationRepository(root, probe));

        probe.assertEveryMoveReplacedTheTargetWhole();
    }

    @Test
    void thePlantedNonAtomicRenameFailsTheProbe() {
        Probe probe = new Probe(IN_PLACE);

        writeEveryRound(new FileActivationRepository(root, probe));

        assertThatThrownBy(probe::assertEveryMoveReplacedTheTargetWhole)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("written in place");
    }

    @Test
    void readersStraightFromDiskWithoutTheLockOnlyEverSeeAWholeWrittenDocument() throws Exception {
        FileActivationRepository store = new FileActivationRepository(root);
        store.create(ID, ActivationRepositoryContract.document(0));
        Path file = root.resolve(ActivationDirectoryName.encode(ID)).resolve(FileActivationRepository.FILE);
        Set<Revision> written = IntStream.rangeClosed(0, ROUNDS)
                .mapToObj(n -> ActivationRepositoryContract.document(n).revision())
                .collect(Collectors.toSet());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<List<Revision>>> readers = new ArrayList<>();
            for (int r = 0; r < 3; r++) {
                readers.add(pool.submit(() -> readFromDisk(file, gate)));
            }
            Future<?> writer = pool.submit(() -> {
                gate.await();
                for (int n = 1; n <= ROUNDS; n++) {
                    store.replace(ID, Optional.empty(), ActivationRepositoryContract.document(n));
                }
                return null;
            });
            gate.countDown();
            writer.get(60, TimeUnit.SECONDS);
            for (Future<List<Revision>> reader : readers) {
                assertThat(reader.get(60, TimeUnit.SECONDS)).isNotEmpty().allMatch(written::contains);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Reads the file with no lock at all, as another reader of the disk would, and parses each read whole. */
    private static List<Revision> readFromDisk(Path file, CountDownLatch gate) throws Exception {
        gate.await();
        List<Revision> seen = new ArrayList<>();
        for (int i = 0; i < ROUNDS * 5; i++) {
            ActivationDocument document = ActivationFileCodec.decode(ID, Files.readAllBytes(file));
            seen.add(document.revision());
        }
        return seen;
    }
}
