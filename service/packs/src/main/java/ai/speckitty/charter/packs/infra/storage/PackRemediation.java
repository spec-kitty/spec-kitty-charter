package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.application.OwnerOnly;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.roundtrip.FileOutcome;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.domain.rules.Findings;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 *       written, except a quoted number in a number field, the one fault of a file's content it repairs (#651,
 *       {@link QuotedNumbers});
 *   <li>a work directory under the JVM's temporary directory takes a copy of the pack, every file byte for byte, so a
 *       file the census does not hold (a stray file, an author's note) stays as it was;
 *   <li>when the precondition found quoted numbers, each is written plain in the copy, its value in place of its whole
 *       node (quotes, a tag, escapes, a block scalar's lines), and the copy is judged again: any violation left is
 *       {@link Refused}, and nothing is written. So a quoted value that is no number of the field's type, one it cannot
 *       write plain (an anchor on it, an alias or a second document in its file), any other fault beside a quoted
 *       number, and a number the field's range refuses once it is plain all refuse the pack; a quoted number's detail
 *       then says it is repaired once the rest is fixed, or why it cannot be, never to run remediate. A file a finding
 *       names that is gone from the copy is the pack changed during remediation;
 *   <li>each modelled file is rewritten in the copy in the round trip's canonical form (ADR-0036: the same meaning;
 *       formatting and comments may change), the bytes {@link DomainRoundTrip}'s writer writes for it. A modelled file
 *       the round trip refuses (its canonical form past the code-point cap, say) cannot be rewritten: the pack is
 *       {@link NotRoundTripped}, each such file named with the round trip's reason, and nothing is written (Tier R B4);
 *   <li>then {@code pack-manifest.yaml} is derived and stamped over the copy as every service write does
 *       ({@link ManifestMembership#stamp} with {@link PackManifestDerivation#derivedAt}), so its membership and its
 *       derived hashes are the pack's;
 *   <li>a copy equal to the pack, byte for byte, is {@link Unchanged}: nothing is written in the pack or beside it, so a
 *       canonical pack needs no write access. Otherwise the copy is written beside the pack, in a hidden sibling
 *       {@code .<pack>.remediate-<uuid>} that keeps the pack's file modes, and takes the pack's place ({@link
 *       Rewritten}): the pack is moved aside to {@code .<pack>.aside-<uuid>}, the sibling is moved in, and the aside
 *       copy is deleted, each of its directories made writable by its owner first, since it carries the pack's own
 *       modes. When it still cannot be deleted, the swap stands and the outcome names the copy left behind
 *       ({@link Rewritten#oldCopyLeft}). A move that fails puts the pack back;
 *   <li>the round trip reads the work directory's copy, never the pack, and just before the pack is moved aside, under
 *       the swapping lock, the pack is compared with the copy it was made from: a pack edited meanwhile is
 *       {@link Refused} ("the pack changed during remediation"), nothing written, so the swap never takes an edit
 *       away. Only the instant between that comparison and the rename is left open, which no lock a pack author's tools
 *       honour could close.
 * </ol>
 *
 * <p>A pack root that is a symbolic link is remediated in the directory it resolves to, which is swapped; the link is
 * kept. Rewriting needs write access to that directory's parent. An entry that is neither a directory nor a regular file
 * (a FIFO, a socket, a device) is a violation of the storage verdict ({@code unreadable}, operator ruling R-ad), so the
 * precondition refuses the pack; the copy still refuses such an entry ({@code is not a regular file}), as a second line
 * of defense, should one appear after the precondition: the pack is then as it was.
 *
 * <p>It adds no serialization and no classifier: the canonical form is the round trip's, the census is the round
 * trip's, and the manifest is the store's derivation and stamp. It takes no lock and no generation and names no commit
 * store: {@code pack-doctor} runs it without Spring ({@code PACK_VALIDATION_IS_FRAMEWORK_FREE}), on a directory it is
 * handed, not a registry the service writes.
 *
 * <p>A failure after the precondition passed (an I/O failure, a manifest the stamp refuses) throws, after the work directory and the sibling are deleted. The pack is then as it was, and the
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
    public sealed interface Outcome permits Rewritten, Unchanged, Refused, NotRoundTripped {}

    /**
     * The pack was rewritten: its quoted numbers written plain, its modelled files in canonical form and its manifest
     * derived and stamped.
     *
     * @param oldCopyLeft the hidden copy of the pack as it was, {@code .<pack>.aside-<uuid>}, when it could not be
     *     deleted after the swap; the swap stands, so it is the caller's to report and the user's to delete
     * @param repaired each quoted number written plain, in the order the verdict found them; empty when there was none
     */
    public record Rewritten(Optional<Path> oldCopyLeft, List<Repaired> repaired) implements Outcome {
        /**
         * Checks the fields and copies the list.
         *
         * @param oldCopyLeft the copy left behind, if any
         * @param repaired the quoted numbers written plain
         */
        public Rewritten {
            Objects.requireNonNull(oldCopyLeft, "oldCopyLeft");
            repaired = List.copyOf(repaired);
        }
    }

    /**
     * A quoted number in a number field, written plain (#651).
     *
     * @param file the pack-relative path of the file that held it
     * @param pointer the JSON pointer of the value, as the verdict's finding names it
     * @param value the text that was inside the quotes
     * @param integer whether the field is typed {@code integer}, else {@code number}
     */
    public record Repaired(String file, String pointer, String value, boolean integer) {
        /**
         * Checks the fields.
         *
         * @param file the file
         * @param pointer the pointer
         * @param value the value
         * @param integer whether the field takes integers only
         */
        public Repaired {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(pointer, "pointer");
            Objects.requireNonNull(value, "value");
        }
    }

    /** The pack was already canonical and stamped: nothing was written. */
    public record Unchanged() implements Outcome {}

    /**
     * The pack holds a fault remediation does not repair, or changed while it was remediated: nothing was written.
     *
     * @param violations the storage verdict's violations without the manifest gate, in registration's order; when the
     *     pack held quoted numbers, those and the violations its copy held once they were written plain, each quoted
     *     number worded for the refusal; or the one reason that the pack changed during remediation
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

    /**
     * The pack passed the precondition, but the round trip refuses some of its modelled files, so it cannot be written
     * in canonical form: nothing was written. It is a fault of the pack's content, as {@link Refused} is.
     *
     * @param refused each refused file, pack-relative, with the round trip's reason, sorted by path
     */
    public record NotRoundTripped(SortedMap<String, String> refused) implements Outcome {
        /**
         * Copies the map.
         *
         * @param refused the refused files
         */
        public NotRoundTripped {
            refused = Collections.unmodifiableSortedMap(new TreeMap<>(refused));
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

    /** A deletion of a whole tree: the seam a test uses to make the old copy's deletion fail. */
    @FunctionalInterface
    interface Remover {
        /**
         * Deletes {@code root} and everything under it, if it is there.
         *
         * @param root the tree
         * @throws IOException when it cannot be deleted whole
         */
        void remove(Path root) throws IOException;
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

    /** A point of the run a test stops at: after the sibling is written, before the pack is checked and moved aside. */
    @FunctionalInterface
    interface Checkpoint {
        /**
         * Reached once the sibling is written.
         *
         * @throws IOException when the test's step fails
         */
        void reached() throws IOException;
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
    private final Remover remover;
    private final Checkpoint beforeSwap;

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
        this(mover, interruption, PackRemediation::removeTree);
    }

    /**
     * The remediation with a move, a place for its shutdown hook and a deletion of the caller's.
     *
     * @param mover the move the swap makes
     * @param interruption where the run's shutdown hook is held
     * @param remover the deletion of a copy the run no longer needs
     */
    PackRemediation(Mover mover, Interruption interruption, Remover remover) {
        this(mover, interruption, remover, () -> {});
    }

    /**
     * The remediation with a move, a place for its shutdown hook, a deletion and a checkpoint of the caller's.
     *
     * @param mover the move the swap makes
     * @param interruption where the run's shutdown hook is held
     * @param remover the deletion of a copy the run no longer needs
     * @param beforeSwap what runs once the sibling is written, before the pack is checked for a change and moved aside
     */
    PackRemediation(Mover mover, Interruption interruption, Remover remover, Checkpoint beforeSwap) {
        this.mover = Objects.requireNonNull(mover, "mover");
        this.interruption = Objects.requireNonNull(interruption, "interruption");
        this.remover = Objects.requireNonNull(remover, "remover");
        this.beforeSwap = Objects.requireNonNull(beforeSwap, "beforeSwap");
    }

    /**
     * Remediates one pack.
     *
     * @param root the pack, its {@code locator} the directory (or a link to it), configured under its {@code name} and
     *     level as {@code validate} configures it
     * @return {@link Rewritten}, {@link Unchanged}, {@link Refused} or {@link NotRoundTripped}
     * @throws UncheckedIOException when the pack cannot be copied, staged or swapped; its message says the pack is
     *     unchanged, or names the aside copy that holds it when it could not be put back
     */
    public Outcome remediate(PackRoot root) {
        Objects.requireNonNull(root, "root");
        if (!PackIdentifier.isPackId(root.name())) {
            // A pack is remediated under its name, which the round trip needs as a pack id: say how to give it one,
            // before anything is read, so the run goes on to the packs after it.
            return new Refused(List.of(Findings.at(
                    StorageCodes.PACK_ID_INVALID,
                    FileSystemPackInspector.ROOT,
                    List.of(),
                    StoreRead.notAPackId(root.name()) + " Name the pack with `=<id>`: write `" + root.locator()
                            + "=<id>`, a pack id after the =.")));
        }
        List<Reason> violations = StorageRulesets.verdictWithoutTheManifestGate(
                        new FileSystemPackInspector().readTree(root))
                .violations();
        if (!violations.isEmpty() && violations.stream().noneMatch(QuotedNumbers::namesTheFix)) {
            return new Refused(violations);
        }
        return new Run(root, violations).remediate();
    }

    /** One remediation past its precondition: its work directory, its sibling and the hook that cleans them up. */
    private final class Run {
        private final PackRoot root;
        private final List<Reason> faults;
        private final Object swapping = new Object();
        private boolean stopped;
        private @Nullable Path work;
        private @Nullable Path besides;

        /**
         * The run of one pack.
         *
         * @param root the pack
         * @param faults the precondition's violations: none, or some that name a quoted number's fix
         */
        Run(PackRoot root, List<Reason> faults) {
            this.root = root;
            this.faults = List.copyOf(faults);
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
            copyTree(live, staged);
            // The pack as it was copied: the snapshot the swap is checked against.
            SortedMap<String, byte[]> copied = tree(staged);
            List<Repaired> repaired = List.of();
            if (!faults.isEmpty()) {
                QuotedNumbers.Repairs repairs = QuotedNumbers.repair(staged, faults);
                if (repairs.gone().isPresent()) {
                    return changed();
                }
                List<Reason> left = StorageRulesets.verdictWithoutTheManifestGate(new FileSystemPackInspector()
                                .readTree(new PackRoot(root.name(), root.configuredLevel(), staged.toString())))
                        .violations();
                if (!left.isEmpty()) {
                    return new Refused(refusal(faults, repairs, left));
                }
                repaired = repairs.repaired();
            }
            SortedMap<String, String> refused =
                    stage(id, staged, workDirectory().resolve("canonical"));
            if (!refused.isEmpty()) {
                return new NotRoundTripped(refused);
            }
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
                beforeSwap.reached();
                // Everything written came from the copy; were the pack edited meanwhile, the swap would take the edit
                // away. A change in the instant between this check and the move is the one window left: the move is
                // one rename, and no lock a pack author's editor honours exists to close it.
                if (!sameTree(tree(live), copied)) {
                    return changed();
                }
                return new Rewritten(swap(sibling, live), repaired);
            }
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
                quietlyRemove(workCopy);
            }
            if (siblingCopy != null) {
                quietlyRemove(siblingCopy); // gone already when the swap moved it in
            }
        }

        /** Deletes a copy the run no longer needs; a failure never changes what the remediation answered. */
        private void quietlyRemove(Path copy) {
            try {
                remover.remove(copy);
            } catch (IOException | RuntimeException ignored) {
                // A copy left behind is hidden beside the pack or in the temporary directory.
            }
        }

        /**
         * The live pack aside, the sibling in its place, the aside copy deleted; a failed move puts the pack back. A
         * move's failure, checked or not, is an {@link IOException}, so the caller words it as any failed move, and a
         * failed put-back is suppressed on it, where pack-doctor prints it.
         *
         * @return the aside copy when it could not be deleted once the swap stood; empty when it is gone
         */
        private Optional<Path> swap(Path sibling, Path live) throws IOException {
            Path aside = sibling(live, "aside");
            move(live, aside);
            try {
                move(sibling, live);
            } catch (IOException failure) {
                try {
                    move(aside, live);
                } catch (IOException restore) {
                    failure.addSuppressed(restore);
                    throw new UncheckedIOException(
                            "cannot remediate " + root.locator() + ", and it could not be put back: the pack as it was"
                                    + " is at " + aside,
                            failure);
                }
                throw failure;
            }
            try {
                remover.remove(aside);
                return Optional.empty();
            } catch (IOException | RuntimeException leftBehind) {
                return Optional.of(aside);
            }
        }

        private void move(Path from, Path to) throws IOException {
            try {
                mover.move(from, to);
            } catch (RuntimeException unchecked) {
                throw new IOException(unchecked.getMessage(), unchecked);
            }
        }
    }

    /** The pack changed while it was remediated: nothing was written. */
    private static Refused changed() {
        return new Refused(List.of(Reason.failed(
                "the pack is not changed while it is remediated",
                "the pack changed during remediation: nothing was written; run remediate again")));
    }

    /**
     * What a pack whose copy still holds a fault once its quoted numbers were written plain is refused with, in the
     * precondition's order and then the faults the plain numbers brought: each fault the copy still holds; a quoted
     * number it wrote plain, unless the copy holds a fault at its place (a range, say), worded as one remediate repairs
     * once the other faults are fixed; and a quoted number it could not write plain, worded with why. No detail of the
     * refusal tells the author to run remediate.
     */
    private static List<Reason> refusal(List<Reason> faults, QuotedNumbers.Repairs repairs, List<Reason> left) {
        Set<Location> written = new HashSet<>();
        repairs.repaired().forEach(each -> written.add(new Location(each.file(), each.pointer())));
        Set<Location> stillWrong = new HashSet<>();
        left.forEach(fault -> stillWrong.add(fault.location()));
        Set<Reason> said = new LinkedHashSet<>();
        for (Reason fault : faults) {
            if (written.contains(fault.location()) && QuotedNumbers.namesTheFix(fault)) {
                if (!stillWrong.contains(fault.location())) {
                    said.add(QuotedNumbers.blocked(fault));
                }
            } else if (left.contains(fault)) {
                said.add(worded(fault, repairs));
            }
        }
        left.forEach(fault -> said.add(worded(fault, repairs)));
        return List.copyOf(said);
    }

    /** A fault the copy still holds; a quoted number left as it was says why remediate did not write it plain. */
    private static Reason worded(Reason fault, QuotedNumbers.Repairs repairs) {
        return QuotedNumbers.namesTheFix(fault)
                ? QuotedNumbers.unrepaired(
                        fault, repairs.unrepaired().getOrDefault(fault.location(), "it was left as it was"))
                : fault;
    }

    /**
     * Writes the remediated pack into {@code staged}, which holds a copy of the pack: each modelled file replaced by the
     * bytes the round trip writes for it (reading the copy, never the pack, so a pack edited meanwhile cannot make the
     * read and the copy disagree), then the manifest derived and stamped over the staged files.
     *
     * @return each modelled file the round trip refused, with its reason; when there is one, nothing is staged further
     *     and the staged copy is left to the clean-up
     */
    private static SortedMap<String, String> stage(PackIdentifier id, Path staged, Path canonical) throws IOException {
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        PackRoundTripResult result = PackRoundTrip.run(new DomainRoundTrip(strategy), id, staged, canonical);
        SortedMap<String, String> refused = new TreeMap<>();
        result.outcomes().forEach((path, outcome) -> {
            if (!PackFileSet.MANIFEST.equals(path) && outcome instanceof FileOutcome.Refused refusal) {
                refused.put(path, refusal.reason());
            }
        });
        if (!refused.isEmpty()) {
            return refused;
        }
        // Every file but the manifest is written now; the manifest is derived and stamped below, from the staged files,
        // whatever the pack's manifest listed.
        for (Map.Entry<String, FileOutcome> entry : result.outcomes().entrySet()) {
            String path = entry.getKey();
            if (!PackFileSet.MANIFEST.equals(path) && entry.getValue() instanceof FileOutcome.Written written) {
                Files.write(staged.resolve(path), PackFileSet.noFollowBytes(canonical.resolve(written.writtenPath())));
            }
        }
        ManifestMembership.stamp(
                staged, directory -> PackManifestDerivation.derivedAt(id, directory, strategy), strategy);
        return refused;
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
                Path copy = target.resolve(source.relativize(file).toString());
                Files.copy(file, copy, LinkOption.NOFOLLOW_LINKS);
                // The copy takes the file's mode; a read-only file is rewritten in it, and its mode carried back after.
                OwnerOnly.ownerReadWrite(copy);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * The staged tree written at {@code target} beside the pack, each directory and file with the pack's own mode where
     * the pack has it (a file it never had, with the mode of the pack's directory it is in, without search bits), and a file whose bytes are the pack's with its modification time too. A directory's mode is set after its contents are written, so a read-only one
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
                } else {
                    // A file the pack never had (the derived manifest) is no wider than the directory it is in.
                    OwnerOnly.carryFileModeOf(Objects.requireNonNull(original.getParent()), written);
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
     * The pack's own POSIX mode carried to the copy ({@link OwnerOnly#carryMode}, the one place a mode is set, #510),
     * and its modification time when asked. On a file system with no POSIX view nothing is carried, and the copy keeps
     * the mode it was made with.
     */
    private static void keepAttributes(Path original, Path copy, boolean time) throws IOException {
        if (time) {
            Files.setLastModifiedTime(copy, Files.getLastModifiedTime(original, LinkOption.NOFOLLOW_LINKS));
        }
        OwnerOnly.carryMode(original, copy);
    }

    private static boolean sameBytes(Path left, Path right) throws IOException {
        return Arrays.equals(PackFileSet.noFollowBytes(left), PackFileSet.noFollowBytes(right));
    }

    /** Whether the two trees hold the same directories and files, each file with the same bytes. */
    private static boolean sameTree(Path left, Path right) throws IOException {
        return sameTree(tree(left), tree(right));
    }

    private static boolean sameTree(SortedMap<String, byte[]> leftTree, SortedMap<String, byte[]> rightTree) {
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

    /**
     * Deletes a tree if it is there. Each directory is made writable by its owner first ({@link OwnerOnly#ownerWritable}):
     * the copy carries the pack's own modes, and a directory the pack keeps read-only could not be emptied otherwise.
     * Its mode no longer matters, since it is about to go.
     */
    static void removeTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                OwnerOnly.ownerWritable(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
