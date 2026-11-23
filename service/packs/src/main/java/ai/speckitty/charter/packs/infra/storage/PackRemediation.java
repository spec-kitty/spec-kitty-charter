package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.roundtrip.FileOutcome;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.infra.storage.rules.StorageRulesets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The remediation of a pack directory (FR-009, operator ruling R-j, plan D7): the public storage-edge facade
 * {@code pack-doctor remediate} reaches through its composition root. It repairs what the charter repairs mechanically
 * and judges nothing new:
 *
 * <ol>
 *   <li>the precondition: the storage verdict without the manifest gate
 *       ({@link StorageRulesets#verdictWithoutTheManifestGate}); any violation there is {@link Refused}, and nothing is
 *       written;
 *   <li>a work directory under the JVM's temporary directory takes a copy of the pack, every file byte for byte, so a
 *       file the census does not hold (a stray file, an author's note) stays as it was;
 *   <li>each modelled file is rewritten in the copy in the round trip's canonical form (ADR-0036: the same meaning;
 *       formatting and comments may change), the bytes {@link DomainRoundTrip}'s writer writes for it;
 *   <li>then {@code pack-manifest.yaml} is derived and stamped over the copy as every service write does
 *       ({@link ManifestMembership#stamp} with {@link PackManifestDerivation#derivedAt}), so its membership and its
 *       derived hashes are the pack's;
 *   <li>a copy equal to the pack, byte for byte, is {@link Unchanged}: nothing is written in the pack or beside it, so a
 *       canonical pack needs no write access. Otherwise the copy is written beside the pack, in a hidden sibling
 *       {@code .<pack>.remediate-<uuid>} that keeps the pack's file modes, and takes the pack's place ({@link
 *       Rewritten}): the pack is moved aside to {@code .<pack>.aside-<uuid>}, the sibling is moved in, and the aside
 *       copy is deleted. A move that fails puts the pack back.
 * </ol>
 *
 * <p>A pack root that is a symbolic link is remediated in the directory it resolves to, which is swapped; the link is
 * kept. Rewriting needs write access to that directory's parent. An entry that is neither a directory nor a regular file
 * (a FIFO, a socket, a device), which the storage verdict does not judge, fails the copy ({@code is not a regular
 * file}): the pack is then as it was.
 *
 * <p>It adds no serialization and no classifier: the canonical form is the round trip's, the census is the round
 * trip's, and the manifest is the store's derivation and stamp. It takes no lock and no generation and names no commit
 * store: {@code pack-doctor} runs it without Spring ({@code PACK_VALIDATION_IS_FRAMEWORK_FREE}), on a directory it is
 * handed, not a registry the service writes.
 *
 * <p>A failure after the precondition passed (an I/O failure, a modelled file the round trip refused, a manifest the
 * stamp refuses) throws, after the work directory and the sibling are deleted. The pack is then as it was, and the
 * message says so ({@code it is unchanged}), unless the pack was moved aside and could not be put back: the message
 * then names the aside copy that holds it, the failure is the cause, and the failed put-back is suppressed on it. A
 * shutdown hook, held for the run, deletes the work directory and the sibling when the JVM is stopped (SIGINT,
 * SIGTERM) before the sibling is written; it waits for a sibling being written and swapped in, and neither starts
 * once it has run. A JVM
 * killed outright runs no hook and can leave either hidden sibling: {@code .<pack>.remediate-<uuid>} is a copy and can
 * be deleted, and {@code .<pack>.aside-<uuid>} holds the pack as it was.
 */
public final class PackRemediation {

    /** What one remediation did. */
    public sealed interface Outcome permits Rewritten, Unchanged, Refused {}

    /** The pack was rewritten: its modelled files in canonical form and its manifest derived and stamped. */
    public record Rewritten() implements Outcome {}

    /** The pack was already canonical and stamped: nothing was written. */
    public record Unchanged() implements Outcome {}

    /**
     * The pack holds a fault remediation does not repair: nothing was written.
     *
     * @param violations the storage verdict's violations without the manifest gate, in registration's order
     */
    public record Refused(List<Reason> violations) implements Outcome {
        /**
         * Copies the list.
         *
         * @param violations the violations
         */
        public Refused {
            violations = List.copyOf(violations);
        }
    }

    /** A move of a directory to a path that does not exist yet: the seam a test uses to make the swap fail. */
    @FunctionalInterface
    interface Mover {
        /**
         * Moves {@code from} to {@code to}.
         *
         * @param from the directory to move
         * @param to the path it moves to, which does not exist
         * @throws IOException when it cannot be moved
         */
        void move(Path from, Path to) throws IOException;
    }

    /** Where the run's shutdown hook is held: the JVM's own, or a test's, which runs it when it chooses. */
    interface Interruption {
        /**
         * Holds the hook for the run.
         *
         * @param hook the hook, not started
         */
        void register(Thread hook);

        /**
         * Lets the hook go once the run is over.
         *
         * @param hook the hook {@link #register} was given
         */
        void unregister(Thread hook);
    }

    /** The JVM's shutdown hooks. */
    private static final Interruption JVM = new Interruption() {
        @Override
        public void register(Thread hook) {
            Runtime.getRuntime().addShutdownHook(hook);
        }

        @Override
        public void unregister(Thread hook) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException shuttingDown) {
                // The JVM is stopping and runs the hook itself; it waits for the swap, so there is nothing to undo.
            }
        }
    };

    private final Mover mover;
    private final Interruption interruption;

    /** The remediation with the file system's own move, atomic where the platform renames atomically. */
    public PackRemediation() {
        this(PackRemediation::rename);
    }

    /**
     * The remediation with a move of the caller's.
     *
     * @param mover the move the swap makes
     */
    PackRemediation(Mover mover) {
        this(mover, JVM);
    }

    /**
     * The remediation with a move and a place for its shutdown hook of the caller's.
     *
     * @param mover the move the swap makes
     * @param interruption where the run's shutdown hook is held
     */
    PackRemediation(Mover mover, Interruption interruption) {
        this.mover = Objects.requireNonNull(mover, "mover");
        this.interruption = Objects.requireNonNull(interruption, "interruption");
    }

    /**
     * Remediates one pack.
     *
     * @param root the pack, its {@code locator} the directory (or a link to it), configured under its {@code name} and
     *     level as {@code validate} configures it
     * @return {@link Rewritten}, {@link Unchanged} or {@link Refused}
     * @throws UncheckedIOException when the pack cannot be copied, staged or swapped; its message says the pack is
     *     unchanged, or names the aside copy that holds it when it could not be put back
     * @throws IllegalStateException when the round trip refuses a modelled file of a pack the precondition passed; the
     *     pack is then as it was
     */
    public Outcome remediate(PackRoot root) {
        Objects.requireNonNull(root, "root");
        List<Reason> violations = StorageRulesets.verdictWithoutTheManifestGate(
                        new FileSystemPackInspector().readTree(root))
                .violations();
        if (!violations.isEmpty()) {
            return new Refused(violations);
        }
        return new Run(root).remediate();
    }

    /** One remediation past its precondition: its work directory, its sibling and the hook that cleans them up. */
    private final class Run {
        private final PackRoot root;
        private final Object swapping = new Object();
        private boolean stopped;
        private @Nullable Path work;
        private @Nullable Path besides;

        Run(PackRoot root) {
            this.root = root;
        }

        Outcome remediate() {
            Thread hook = new Thread(this::stop, "pack-remediation-cleanup");
            interruption.register(hook);
            try {
                return swapped();
            } catch (IOException failure) {
                throw new UncheckedIOException("cannot remediate " + root.locator() + "; it is unchanged", failure);
            } finally {
                cleanUp();
                interruption.unregister(hook);
            }
        }

        private Outcome swapped() throws IOException {
            // A root that is a link is remediated in the directory it names, which is swapped; the link stays.
            Path live = Path.of(root.locator()).toRealPath();
            PackIdentifier id = new PackIdentifier(root.name());
            Path staged = workDirectory().resolve("pack");
            stage(id, live, staged, workDirectory().resolve("canonical"));
            if (sameTree(live, staged)) {
                return new Unchanged();
            }
            // The sibling is written and swapped in under the lock the shutdown hook takes, so a stop either comes
            // first, and nothing is written beside the pack, or waits for the swap to end.
            synchronized (swapping) {
                requireRunning();
                Path sibling = sibling(live, "remediate");
                besides = sibling;
                materialize(staged, live, sibling);
                swap(sibling, live);
            }
            return new Rewritten();
        }

        private Path workDirectory() throws IOException {
            synchronized (swapping) {
                requireRunning();
                if (work == null) {
                    work = Files.createTempDirectory("pack-remediation-");
                }
                return work;
            }
        }

        private void requireRunning() throws IOException {
            if (stopped) {
                throw new IOException("the JVM is stopping");
            }
        }

        /** The shutdown hook: once a swap under way has ended, no swap starts, and the copies are deleted. */
        private void stop() {
            synchronized (swapping) {
                stopped = true;
            }
            cleanUp();
        }

        private void cleanUp() {
            Path workCopy;
            Path siblingCopy;
            synchronized (swapping) {
                workCopy = work;
                siblingCopy = besides;
            }
            if (workCopy != null) {
                deleteTree(workCopy);
            }
            if (siblingCopy != null) {
                deleteTree(siblingCopy); // gone already when the swap moved it in
            }
        }

        /** The live pack aside, the sibling in its place, the aside copy deleted; a failed move puts the pack back. */
        private void swap(Path sibling, Path live) throws IOException {
            Path aside = sibling(live, "aside");
            mover.move(live, aside);
            try {
                mover.move(sibling, live);
            } catch (IOException | RuntimeException failure) {
                try {
                    mover.move(aside, live);
                } catch (IOException | RuntimeException restore) {
                    failure.addSuppressed(restore);
                    throw new UncheckedIOException(
                            "cannot remediate " + root.locator() + ", and it could not be put back: the pack as it was"
                                    + " is at " + aside,
                            failure instanceof IOException io ? io : new IOException(failure));
                }
                throw failure;
            }
            deleteTree(aside);
        }
    }

    /**
     * Writes the remediated pack into {@code staged}: a copy of the pack, each modelled file replaced by the bytes the
     * round trip writes for it, then the manifest derived and stamped over the staged files.
     */
    private static void stage(PackIdentifier id, Path live, Path staged, Path canonical) throws IOException {
        copyTree(live, staged);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        PackRoundTripResult result = PackRoundTrip.run(new DomainRoundTrip(strategy), id, live, canonical);
        for (Map.Entry<String, FileOutcome> entry : result.outcomes().entrySet()) {
            String path = entry.getKey();
            if (PackFileSet.MANIFEST.equals(path)) {
                continue; // derived and stamped below, from the staged files, whatever the pack's manifest listed
            }
            switch (entry.getValue()) {
                case FileOutcome.Written written ->
                    Files.write(
                            staged.resolve(path), PackFileSet.noFollowBytes(canonical.resolve(written.writtenPath())));
                case FileOutcome.Refused refused ->
                    throw new IllegalStateException(
                            "the round trip refused " + path + ": " + refused.reason() + "; " + live + " is unchanged");
            }
        }
        ManifestMembership.stamp(
                staged, directory -> PackManifestDerivation.derivedAt(id, directory, strategy), strategy);
    }

    /** A hidden path beside the pack that does not exist yet, on the pack's file system so the swap is a rename. */
    private static Path sibling(Path live, String purpose) {
        Path parent = Objects.requireNonNull(live.getParent(), "a pack directory has a parent");
        return parent.resolve("." + live.getFileName() + "." + purpose + "-" + UUID.randomUUID());
    }

    static void rename(Path from, Path to) throws IOException {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * A copy of the tree, its contents only: links refused (the precondition refused a pack that holds one), each file
     * byte for byte, every directory and file writable in the copy whatever the pack's modes are.
     */
    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Files.createDirectory(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile()) {
                    throw new IOException(source.relativize(file) + " is not a regular file");
                }
                Files.copy(file, target.resolve(source.relativize(file).toString()), LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * The staged tree written at {@code target} beside the pack, each directory and file with the pack's own mode where
     * the pack has it, and a file whose bytes are the pack's with its modification time too. A directory's mode is set after its contents are written, so a read-only one
     * still takes them.
     */
    private static void materialize(Path staged, Path live, Path target) throws IOException {
        Files.walkFileTree(staged, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Files.createDirectory(target.resolve(staged.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                String relative = staged.relativize(file).toString();
                Path written = target.resolve(relative);
                Files.copy(file, written, LinkOption.NOFOLLOW_LINKS);
                Path original = live.resolve(relative);
                if (Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS)) {
                    keepAttributes(original, written, sameBytes(original, file));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Path original = live.resolve(staged.relativize(dir).toString());
                if (Files.isDirectory(original, LinkOption.NOFOLLOW_LINKS)) {
                    keepAttributes(
                            original, target.resolve(staged.relativize(dir).toString()), false);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * The pack's own POSIX mode carried to the copy, as a copy with its attributes carries it, where both have a POSIX
     * view, and its modification time when asked. No mode is built here (OwnerOnly owns that, #510): on a file system
     * with no POSIX view nothing is carried, and the copy keeps the mode it was made with.
     */
    private static void keepAttributes(Path original, Path copy, boolean time) throws IOException {
        if (time) {
            Files.setLastModifiedTime(copy, Files.getLastModifiedTime(original, LinkOption.NOFOLLOW_LINKS));
        }
        PosixFileAttributeView from =
                Files.getFileAttributeView(original, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributeView to =
                Files.getFileAttributeView(copy, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (from != null && to != null) {
            to.setPermissions(from.readAttributes().permissions());
        }
    }

    private static boolean sameBytes(Path left, Path right) throws IOException {
        return Arrays.equals(PackFileSet.noFollowBytes(left), PackFileSet.noFollowBytes(right));
    }

    /** Whether the two trees hold the same directories and files, each file with the same bytes. */
    private static boolean sameTree(Path left, Path right) throws IOException {
        SortedMap<String, byte[]> leftTree = tree(left);
        SortedMap<String, byte[]> rightTree = tree(right);
        if (!leftTree.keySet().equals(rightTree.keySet())) {
            return false;
        }
        return leftTree.entrySet().stream()
                .allMatch(entry -> Arrays.equals(entry.getValue(), rightTree.get(entry.getKey())));
    }

    /**
     * Every path under {@code root}, root-relative and keyed by what it is ({@code d:} a directory, {@code f:} a file),
     * with a file's bytes; a directory maps to no bytes.
     */
    private static SortedMap<String, byte[]> tree(Path root) throws IOException {
        SortedMap<String, byte[]> tree = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                String relative = root.relativize(path)
                        .toString()
                        .replace(path.getFileSystem().getSeparator(), "/");
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    tree.put("d:" + relative, new byte[0]);
                } else {
                    tree.put("f:" + relative, PackFileSet.noFollowBytes(path));
                }
            }
        }
        return tree;
    }

    /** Deletes a tree if it is there; a failure is left for the caller's outcome, never masking it. */
    private static void deleteTree(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | UncheckedIOException ignored) {
            // A copy left behind is hidden beside the pack; it never changes what the remediation answered.
        }
    }
}
