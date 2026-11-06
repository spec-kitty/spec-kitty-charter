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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The activation documents on disk (FR-019): {@code <root>/<encoded id>/activation.json}, holding exactly the
 * canonical bytes of {@link ActivationFileCodec}. The directory is named by {@link ActivationDirectoryName}, a
 * reversible encoding that keeps ids differing only in case apart on a case-insensitive file system. The file
 * holds the document alone, not its id, since its bytes are the bytes its revision hashes (FR-005); a read checks
 * instead that the directory's name decodes to the id asked for ({@link #checkedName}). No register was released
 * before this layout, so nothing is migrated. Decision {@code DM-01M6P92QKYFZW9KDGGQGX9H9VS}: an atomic move under an in-process
 * per-id lock, for one replica (ADR-0061); several processes over one root are out of scope (#349).
 *
 * <p><b>A write</b> creates the id's directory, writes {@code activation.json.tmp-<uuid>} beside the target through a
 * {@link FileChannel} forced to the device, moves it over the target with {@code ATOMIC_MOVE} and
 * {@code REPLACE_EXISTING} through the {@link Durability} seam, then forces the id's directory, as it forces the parent
 * of every directory the write created. A failure at any step deletes the temporary file and leaves the previous document as it was; it is thrown as an {@link ActivationStorageException}.
 * A temporary file a crash left behind is never read and is deleted by the id's next write.
 *
 * <p><b>Locking:</b> every operation, {@link #find} included, holds its id's lock across its read-compare-write, which
 * makes each id linearisable. The locks are striped: a fixed array of {@value #LOCK_STRIPES}
 * {@link ReentrantLock}s, an id's stripe chosen by the hash of its encoded directory name, so the structure never grows
 * with the ids written and case variants never share a key by accident (they may share a stripe, which only serialises
 * them). Striping is preferred to removing a lock on release, which would race a writer that already holds it.
 *
 * <p><b>Platform notes:</b> on POSIX the atomic move alone guarantees that a reader sees a whole file. On Windows a
 * move over a target another thread holds open fails with {@link AccessDeniedException}, which is why {@link #find}
 * takes the lock too: no reader of this process holds the target open during a move. As a second guard (a virus
 * scanner or an indexer may hold it), a denied move is retried up to {@value #MOVE_ATTEMPTS} times, pausing at most
 * {@value #RETRY_PAUSE_MILLIS} ms. Forcing a directory tolerates only what a platform cannot do: Windows cannot
 * open a directory as a channel and some file systems refuse to force one. A force that fails is a storage failure.
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

    /** How many locks the ids are striped over. */
    static final int LOCK_STRIPES = 64;

    /**
     * The durability seam, and the failure-injection seam of the tests: the move of a fully written temporary file over
     * its target, and the forcing of a directory to the device. The default is an {@code ATOMIC_MOVE}; a test supplies
     * one that throws, or one that records what was forced.
     */
    @FunctionalInterface
    interface Durability {
        /**
         * Moves {@code temp} over {@code target}.
         *
         * @param temp the fully written temporary file
         * @param target the file to replace
         * @throws IOException when the move fails
         */
        void move(Path temp, Path target) throws IOException;

        /**
         * Forces a directory to the device, so a file created, moved or removed in it, or a directory created in it,
         * survives a power cut. A directory that cannot be opened as a channel (Windows) or that the file system
         * refuses to force ({@link UnsupportedOperationException}) is tolerated; a force that fails is not.
         *
         * @param directory the directory
         * @throws IOException when the device fails the force
         */
        default void forceDirectory(Path directory) throws IOException {
            FileChannel channel;
            try {
                channel = FileChannel.open(directory, StandardOpenOption.READ);
            } catch (IOException | UnsupportedOperationException cannotOpen) {
                return; // Not every platform can open a directory (see above); there is nothing to force.
            }
            try (channel) {
                channel.force(true);
            } catch (UnsupportedOperationException unsupported) {
                // Some file systems refuse to force a directory; the document's bytes are forced already.
            }
        }
    }

    /** The production seam: an atomic replace, and the default force of a directory. */
    static final Durability DISK = (temp, target) ->
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

    private final Path root;
    private final Durability durability;
    private final ReentrantLock[] locks = stripes();

    /**
     * A store over a directory, which the first write creates.
     *
     * @param root the directory of the activation documents
     */
    public FileActivationRepository(Path root) {
        this(root, DISK);
    }

    /**
     * A store whose move and directory force are the given ones, for the failure and durability tests.
     *
     * @param root the directory of the activation documents
     * @param durability the move of a written temporary file over its target, and the force of a directory
     */
    FileActivationRepository(Path root, Durability durability) {
        this.root = root.toAbsolutePath().normalize();
        this.durability = durability;
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

    private static ReentrantLock[] stripes() {
        ReentrantLock[] stripes = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            stripes[i] = new ReentrantLock();
        }
        return stripes;
    }

    /** The stripe of an id: the hash of its encoded directory name, the same key its directory is found by. */
    static int stripeOf(ActivationId id) {
        return Math.floorMod(ActivationDirectoryName.encode(id).hashCode(), LOCK_STRIPES);
    }

    private <T> T underLock(ActivationId id, Supplier<T> action) {
        ReentrantLock lock = locks[stripeOf(id)];
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /** The id's directory, never outside the root (the id grammar already forbids it; this is defence in depth). */
    private Path directory(ActivationId id) {
        Path directory = root.resolve(ActivationDirectoryName.encode(id)).normalize();
        if (directory.equals(root) || !directory.startsWith(root)) {
            throw new ActivationStorageException(id, "resolves outside the store");
        }
        return directory;
    }

    /**
     * Checks that a directory holds the id's document: its name decodes to the id.
     *
     * @param id the id asked for
     * @param name the directory's name
     * @throws ActivationStorageException when the name decodes to another id, or to none
     */
    static void checkedName(ActivationId id, String name) {
        if (!ActivationDirectoryName.tryDecode(name).filter(id::equals).isPresent()) {
            throw new ActivationStorageException(
                    id, "is stored under a directory named for another id, '" + name + "'");
        }
    }

    private Optional<StoredActivation> read(ActivationId id) {
        Path directory = directory(id);
        checkedName(id, directory.getFileName().toString());
        Path file = directory.resolve(FILE);
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
            createDurably(directory);
            deleteTemporaryFiles(directory);
            temp = directory.resolve(TEMP_PREFIX + UUID.randomUUID());
            writeDurably(temp, ActivationFileCodec.encode(stored.document()));
            move(temp, directory.resolve(FILE));
            durability.forceDirectory(directory);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw new ActivationStorageException(id, "cannot be written", e);
        }
    }

    /**
     * Creates the directory and every missing ancestor, and forces the parent of each one created, outermost first, so
     * the entry that names it survives a power cut as the document inside it must.
     */
    private void createDurably(Path directory) throws IOException {
        Deque<Path> missing = new ArrayDeque<>();
        for (Path at = directory; at != null && !Files.exists(at); at = at.getParent()) {
            missing.addFirst(at);
        }
        Files.createDirectories(directory);
        for (Path created : missing) {
            durability.forceDirectory(Objects.requireNonNull(created.getParent(), "a missing directory has a parent"));
        }
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
                durability.move(temp, target);
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
     * Removes the document, then its directory. The removal of the document is what is reported; the temporary files
     * a crash left are cleaned best-effort. The id's directory is forced once the files are gone, and the root once
     * the directory is, so the removal survives a power cut; a force that fails is a storage failure.
     */
    private void remove(ActivationId id) {
        Path directory = directory(id);
        try {
            Files.deleteIfExists(directory.resolve(FILE));
            deleteTemporaryFilesQuietly(directory);
            durability.forceDirectory(directory);
            Files.deleteIfExists(directory);
            durability.forceDirectory(root);
        } catch (DirectoryNotEmptyException ignored) {
            // Something other than the store's own files is in the directory: the document is gone, the rest stays.
        } catch (IOException e) {
            throw new ActivationStorageException(id, "cannot be deleted", e);
        }
    }

    private static void deleteTemporaryFilesQuietly(Path directory) {
        try {
            deleteTemporaryFiles(directory);
        } catch (IOException ignored) {
            // The document is gone and the delete is reported; a temporary file left here is deleted by the next write.
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
