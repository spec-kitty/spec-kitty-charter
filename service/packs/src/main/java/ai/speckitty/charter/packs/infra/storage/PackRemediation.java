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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The remediation of a pack directory (FR-009, operator ruling R-j, plan D7): the public storage-edge facade
 * {@code pack-doctor remediate} reaches through its composition root. It repairs what the charter repairs mechanically
 * and judges nothing new:
 *
 * <ol>
 *   <li>the precondition: the storage verdict without the manifest gate
 *       ({@link StorageRulesets#verdictWithoutTheManifestGate}); any violation there is {@link Refused}, and nothing is
 *       written;
 *   <li>a staged sibling directory takes a copy of the pack, every file byte for byte, so a file the census does not
 *       hold (a stray file, an author's note) stays as it was;
 *   <li>each modelled file is rewritten in place in the round trip's canonical form (ADR-0036: the same meaning;
 *       formatting and comments may change), the bytes {@link DomainRoundTrip}'s writer writes for it;
 *   <li>then {@code pack-manifest.yaml} is derived and stamped over the staged files as every service write does
 *       ({@link ManifestMembership#stamp} with {@link PackManifestDerivation#derivedAt}), so its membership and its
 *       derived hashes are the pack's;
 *   <li>a staged tree equal to the pack, byte for byte, is {@link Unchanged} and dropped; otherwise the staged directory
 *       takes the pack's place ({@link Rewritten}): the pack is moved aside, the staged directory is moved in, and the
 *       aside copy is deleted. A move that fails puts the pack back.
 * </ol>
 *
 * <p>It adds no serialization and no classifier: the canonical form is the round trip's, the census is the round
 * trip's, and the manifest is the store's derivation and stamp. It takes no lock and no generation and names no commit
 * store: {@code pack-doctor} runs it without Spring ({@code PACK_VALIDATION_IS_FRAMEWORK_FREE}), on a directory it is
 * handed, not a registry the service writes.
 *
 * <p>A failure after the precondition passed (an I/O failure, a modelled file the round trip refused, a manifest the
 * stamp refuses) throws, after the staged directory is deleted and with the pack untouched: it is not a fault of the
 * pack the precondition can name.
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

    private final Mover mover;

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
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    /**
     * Remediates one pack.
     *
     * @param root the pack, its {@code locator} the directory, configured under its {@code name} and level as
     *     {@code validate} configures it
     * @return {@link Rewritten}, {@link Unchanged} or {@link Refused}
     * @throws UncheckedIOException when a staged file cannot be written or the swap fails; the pack is then as it was
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
        Path live = Path.of(root.locator()).toAbsolutePath().normalize();
        PackIdentifier id = new PackIdentifier(root.name());
        Path staged = sibling(live, "remediate");
        Path canonical = sibling(live, "canonical");
        try {
            stage(id, live, staged, canonical);
            if (sameTree(live, staged)) {
                return new Unchanged();
            }
            swap(staged, live);
            return new Rewritten();
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot remediate " + root.locator() + "; it is unchanged", failure);
        } finally {
            deleteTree(canonical);
            deleteTree(staged);
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

    /** The live pack aside, the staged pack in its place, the aside copy deleted; a failed move puts the pack back. */
    private void swap(Path staged, Path live) throws IOException {
        Path aside = sibling(live, "aside");
        mover.move(live, aside);
        try {
            mover.move(staged, live);
        } catch (IOException | RuntimeException failure) {
            try {
                mover.move(aside, live);
            } catch (IOException | RuntimeException restore) {
                failure.addSuppressed(restore);
            }
            throw failure;
        }
        deleteTree(aside);
    }

    /** A hidden path beside the pack that does not exist yet, on the pack's file system so the swap is a rename. */
    private static Path sibling(Path live, String purpose) {
        Path parent = Objects.requireNonNull(live.getParent(), "a pack directory has a parent");
        return parent.resolve("." + live.getFileName() + "." + purpose + "-" + UUID.randomUUID());
    }

    private static void rename(Path from, Path to) throws IOException {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    }

    /** A copy of the tree, links refused (the precondition refused a pack that holds one), each file byte for byte. */
    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                // The directory entry with its attributes, its mode included, so the swapped pack keeps the pack's
                // own; no POSIX mode is built here (OwnerOnly owns that, #510).
                Files.copy(
                        dir,
                        target.resolve(source.relativize(dir).toString()),
                        StandardCopyOption.COPY_ATTRIBUTES,
                        LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile()) {
                    throw new IOException(source.relativize(file) + " is not a regular file");
                }
                Files.copy(
                        file,
                        target.resolve(source.relativize(file).toString()),
                        StandardCopyOption.COPY_ATTRIBUTES,
                        LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
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
