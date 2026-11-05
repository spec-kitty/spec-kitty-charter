package ai.speckitty.charter.activations.infra.storage;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The activation documents on disk (FR-019): {@code <root>/<id>/activation.json}, holding exactly the canonical bytes
 * of {@link ActivationFileCodec}. Decision {@code DM-01M6P92QKYFZW9KDGGQGX9H9VS}: an atomic move under an in-process
 * per-id lock, for one replica (ADR-0060); several processes over one root are out of scope (#349).
 *
 * <p><b>A write</b> creates the id's directory, writes {@code activation.json.tmp-<uuid>} beside the target through a
 * {@link FileChannel} forced to the device, moves it over the target with {@code ATOMIC_MOVE} and
 * {@code REPLACE_EXISTING} through the {@link Rename} seam, then forces the directory. A failure at any step deletes
 * the temporary file and leaves the previous document as it was; it is thrown as an {@link ActivationStorageException}.
 * A temporary file a crash left behind is never read and is deleted by the id's next write.
 *
 * <p><b>Locking:</b> every operation, {@link #find} included, holds the id's {@link ReentrantLock} across its
 * read-compare-write, which makes each id linearisable. The lock map grows by one lock per id ever touched, which is
 * acceptable at one activation per project; a lock is never removed, since removing one on delete would race a writer
 * that already holds it.
 *
 * <p><b>Platform notes:</b> on POSIX the atomic move alone guarantees that a reader sees a whole file. On Windows a
 * move over a target another thread holds open fails with {@link AccessDeniedException}, which is why {@link #find}
 * takes the lock too: no reader of this process holds the target open during a move. As a second guard (a virus
 * scanner or an indexer may hold it), a denied move is retried up to {@value #MOVE_ATTEMPTS} times, pausing at most
 * {@value #RETRY_PAUSE_MILLIS} ms. Forcing a directory is best-effort: Windows cannot open a directory as a channel
 * and some file systems refuse to force one, and the write does not fail for it.
 */
public final class FileActivationRepository implements ActivationRepository {

    /** The document's file name in the id's directory. */
    static final String FILE = "activation.json";

    /** The infix of a temporary file: {@code activation.json.tmp-<uuid>}. */
    static final String TEMP_PREFIX = FILE + ".tmp-";

    /** How many times a move the platform denies is attempted. */
    static final int MOVE_ATTEMPTS = 3;

    /** The pause before a denied move is attempted again. */
    static final long RETRY_PAUSE_MILLIS = 5;

    /**
     * The failure-injection seam: the second step of a write, moving the fully written temporary file over the
     * target. The default is an {@code ATOMIC_MOVE}; a test supplies one that throws.
     */
    @FunctionalInterface
    interface Rename {
        /**
         * Moves {@code temp} over {@code target}.
         *
         * @param temp the fully written temporary file
         * @param target the file to replace
         * @throws IOException when the move fails
         */
        void move(Path temp, Path target) throws IOException;
    }

    private static final Rename ATOMIC = (temp, target) ->
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

    private final Path root;
    private final Rename rename;
    private final ConcurrentMap<ActivationId, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * A store over a directory, which the first write creates.
     *
     * @param root the directory of the activation documents
     */
    public FileActivationRepository(Path root) {
        this(root, ATOMIC);
    }

    /**
     * A store whose move is the given one, for the failure tests.
     *
     * @param root the directory of the activation documents
     * @param rename the move of a written temporary file over its target
     */
    FileActivationRepository(Path root, Rename rename) {
        this.root = root.toAbsolutePath().normalize();
        this.rename = rename;
    }

    @Override
    public Optional<StoredActivation> find(ActivationId id) {
        return underLock(id, () -> read(id));
    }

    @Override
    public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
        return underLock(id, () -> {
            if (read(id).isPresent()) {
                return Optional.empty();
            }
            StoredActivation fresh = StoredActivation.of(id, document);
            writeAtomically(fresh);
            return Optional.of(fresh);
        });
    }

    @Override
    public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
        return underLock(id, () -> {
            CasResult decided = Cas.replace(read(id), expected, StoredActivation.of(id, document));
            if (decided instanceof CasResult.Written written) {
                writeAtomically(written.stored());
            }
            return decided;
        });
    }

    @Override
    public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
        return underLock(id, () -> {
            DeleteResult decided = Cas.delete(read(id), expected);
            if (decided instanceof DeleteResult.Deleted) {
                remove(id);
            }
            return decided;
        });
    }

    private <T> T underLock(ActivationId id, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(id, key -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /** The id's directory, never outside the root (the id grammar already forbids it; this is defence in depth). */
    private Path directory(ActivationId id) {
        Path directory = root.resolve(id.value()).normalize();
        if (directory.equals(root) || !directory.startsWith(root)) {
            throw new ActivationStorageException(id, "resolves outside the store");
        }
        return directory;
    }

    private Optional<StoredActivation> read(ActivationId id) {
        Path file = directory(id).resolve(FILE);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new ActivationStorageException(id, "cannot be read", e);
        }
        return Optional.of(StoredActivation.of(id, ActivationFileCodec.decode(id, bytes)));
    }

    private void writeAtomically(StoredActivation stored) {
        ActivationId id = stored.id();
        Path directory = directory(id);
        @Nullable Path temp = null;
        try {
            Files.createDirectories(directory);
            deleteTemporaryFiles(directory);
            temp = directory.resolve(TEMP_PREFIX + UUID.randomUUID());
            writeDurably(temp, ActivationFileCodec.encode(stored.document()));
            move(temp, directory.resolve(FILE));
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw new ActivationStorageException(id, "cannot be written", e);
        }
        forceDirectory(directory);
    }

    private static void writeDurably(Path temp, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    /** The move through the seam, retried a bounded number of times when the platform denies it (Windows). */
    private void move(Path temp, Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                rename.move(temp, target);
                return;
            } catch (AccessDeniedException e) {
                if (attempt >= MOVE_ATTEMPTS) {
                    throw e;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(RETRY_PAUSE_MILLIS));
            }
        }
    }

    /**
     * Forces the directory, so the move itself survives a power cut. Best-effort: Windows cannot open a directory as
     * a channel ({@link IOException}) and some file systems refuse to force one
     * ({@link UnsupportedOperationException}); the document's own bytes are already forced, so the write stands.
     */
    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Not every platform can force a directory (see above); the document's bytes are forced already.
        }
    }

    private void remove(ActivationId id) {
        Path directory = directory(id);
        try {
            Files.deleteIfExists(directory.resolve(FILE));
            deleteTemporaryFiles(directory);
            Files.deleteIfExists(directory);
        } catch (DirectoryNotEmptyException ignored) {
            // Something other than the store's own files is in the directory: the document is gone, the rest stays.
        } catch (IOException e) {
            throw new ActivationStorageException(id, "cannot be deleted", e);
        }
    }

    /** Deletes what an interrupted write left; called under the id's lock, so no write of this process is in flight. */
    private static void deleteTemporaryFiles(Path directory) throws IOException {
        List<Path> leftovers;
        try (Stream<Path> listed = Files.list(directory)) {
            leftovers = listed.filter(p -> p.getFileName().toString().startsWith(TEMP_PREFIX))
                    .toList();
        }
        for (Path leftover : leftovers) {
            Files.deleteIfExists(leftover);
        }
    }

    private static void deleteQuietly(@Nullable Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // The write already failed and is reported; a temporary file left here is deleted by the next write.
        }
    }
}
