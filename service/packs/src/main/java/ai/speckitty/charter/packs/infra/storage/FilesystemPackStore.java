package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The filesystem storage of packs, at the persistence-shape level: it maps a {@link PackIdentifier} to a pack
 * directory, reads the directory partial-tolerantly into DSOs, and writes a DSO back atomically and inside the pack.
 * Format is the {@link SerializationStrategy}'s business; this class only moves text.
 *
 * <p>The identifier's value names the pack directory under the storage root. A tactic is located by scanning the
 * pack's {@code *.yaml} files that {@link PackFileKind} classifies as {@code tactic} for the one whose {@code id}
 * matches. A tactic write is an upsert: a known id replaces its file, an unknown id creates {@code <id>.tactic.yaml}
 * directly in the pack directory (and never over an existing file). A read never throws on the content of a file (that is a {@link PackElementViolation}); only a missing or unreadable
 * {@code pack.yaml} refuses it.
 *
 * <p>A write, replace or create, serializes from scratch, writes a temporary file beside the target and renames it
 * into place, so a failure leaves the original untouched (or the new file absent) and removes the temporary file. A target that resolves, symlinks followed,
 * outside the pack directory is refused before any byte is written.
 */
final class FilesystemPackStore {

    private static final String PACK_YAML = "pack.yaml";
    private static final String TACTIC_KIND = "tactic";

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

    /** What a pack directory held. */
    record PackRead(PackDescriptorDso descriptor, List<TacticDso> tactics, List<PackElementViolation> violations) {
        /**
         * Copies the lists.
         *
         * @param descriptor the descriptor
         * @param tactics the readable tactics, in path order
         * @param violations the files that could not be read, in path order
         */
        PackRead {
            Objects.requireNonNull(descriptor, "descriptor");
            tactics = List.copyOf(tactics);
            violations = List.copyOf(violations);
        }
    }

    private static final Rename ATOMIC = (temp, target) -> Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);

    private final Path root;
    private final SerializationStrategy strategy;
    private final Rename rename;

    /**
     * A store that renames atomically.
     *
     * @param root the directory that holds the pack directories
     * @param strategy the format
     */
    FilesystemPackStore(Path root, SerializationStrategy strategy) {
        this(root, strategy, ATOMIC);
    }

    /**
     * A store with an injected rename step; the seam for tests of write atomicity.
     *
     * @param root the directory that holds the pack directories
     * @param strategy the format
     * @param rename the move step
     */
    FilesystemPackStore(Path root, SerializationStrategy strategy, Rename rename) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.rename = Objects.requireNonNull(rename, "rename");
    }

    /**
     * The directory a pack lives in.
     *
     * @param id the pack
     * @return the pack directory under the storage root
     * @throws CharterRefusal when the identifier does not name a directory directly under the root
     */
    Path directoryOf(PackIdentifier id) {
        Path directory = root.resolve(id.value()).normalize();
        if (!root.equals(directory.getParent())) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack identifier does not name a pack directory");
        }
        return directory;
    }

    /**
     * Reads a pack, tolerating broken element files.
     *
     * @param id the pack
     * @return the descriptor, the readable tactics and a violation per broken file
     * @throws CharterRefusal when the pack has no readable {@code pack.yaml}
     */
    PackRead read(PackIdentifier id) {
        Path directory = existingDirectory(id);
        PackDescriptorDso descriptor = descriptorOf(directory, id);
        List<TacticDso> tactics = new ArrayList<>();
        List<PackElementViolation> violations = new ArrayList<>();
        readTactics(directory, tactics, violations);
        return new PackRead(descriptor, tactics, violations);
    }

    /**
     * Reads only a pack's {@code pack.yaml}: no tactic file is opened. This is how an identifier is matched to a pack
     * without reading every pack's elements.
     *
     * @param id the pack
     * @return the descriptor
     * @throws CharterRefusal when the pack has no readable {@code pack.yaml}
     */
    PackDescriptorDso readDescriptor(PackIdentifier id) {
        return descriptorOf(existingDirectory(id), id);
    }

    /**
     * Reads the readable tactics of a pack, without its descriptor.
     *
     * @param id the pack
     * @return the readable tactics, in path order
     */
    List<TacticDso> readTactics(PackIdentifier id) {
        List<TacticDso> tactics = new ArrayList<>();
        readTactics(existingDirectory(id), tactics, new ArrayList<>());
        return tactics;
    }

    /**
     * Reads one tactic: only the file that declares the id is parsed as a tactic.
     *
     * @param id the pack
     * @param tacticId the tactic's id
     * @return the tactic, or empty when the pack has no readable tactic with that id
     */
    Optional<TacticDso> readTactic(PackIdentifier id, String tacticId) {
        Path directory = existingDirectory(id);
        return locateTactic(directory, tacticId).flatMap(file -> {
            String name = directory
                    .relativize(file)
                    .toString()
                    .replace(file.getFileSystem().getSeparator(), "/");
            return switch (readFile(file, name, text -> strategy.readTactic(text, name))) {
                case FileRead.Read<TacticDso> good -> Optional.of(good.value());
                case FileRead.Unreadable<TacticDso> bad -> Optional.empty();
                case FileRead.Absent<TacticDso> absent -> Optional.empty();
            };
        });
    }

    private PackDescriptorDso descriptorOf(Path directory, PackIdentifier id) {
        Path packYaml = directory.resolve(PACK_YAML);
        if (!Files.isRegularFile(packYaml)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + id.value() + " has no pack.yaml");
        }
        return switch (readFile(packYaml, PACK_YAML, strategy::readDescriptor)) {
            case FileRead.Read<PackDescriptorDso> good -> good.value();
            case FileRead.Unreadable<PackDescriptorDso> bad ->
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of " + id.value() + " cannot be read");
            case FileRead.Absent<PackDescriptorDso> absent ->
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + id.value() + " has no pack.yaml");
        };
    }

    private void readTactics(Path directory, List<TacticDso> tactics, List<PackElementViolation> violations) {
        for (String file : tacticFiles(directory)) {
            switch (readFile(directory.resolve(file), file, text -> strategy.readTactic(text, file))) {
                case FileRead.Read<TacticDso> good -> tactics.add(good.value());
                case FileRead.Unreadable<TacticDso> bad -> violations.addAll(violationsOf(bad));
                case FileRead.Absent<TacticDso> absent -> {}
            }
        }
    }

    /**
     * Replaces a pack's {@code pack.yaml}.
     *
     * @param id the pack
     * @param descriptor what to write
     */
    void writeDescriptor(PackIdentifier id, PackDescriptorDso descriptor) {
        Path directory = existingDirectory(id);
        writeAtomically(directory, directory.resolve(PACK_YAML), strategy.writeDescriptor(descriptor));
    }

    /**
     * Upserts a tactic: the file of the tactic with this id is replaced; an unknown id is created as
     * {@code <id>.tactic.yaml} in the pack directory. Both arms are atomic and contained in the pack.
     *
     * @param id the pack
     * @param tactic what to write
     * @throws CharterRefusal when the file resolves outside the pack, or a new tactic's file name is taken by
     *     something that is not a tactic with that id
     */
    void writeTactic(PackIdentifier id, TacticDso tactic) {
        Path directory = existingDirectory(id);
        Path target = locateTactic(directory, tactic.id()).orElseGet(() -> newTacticFile(directory, tactic.id()));
        writeAtomically(directory, target, strategy.writeTactic(tactic));
    }

    // ---- id to path ----------------------------------------------------------------------------------------------

    private Path existingDirectory(PackIdentifier id) {
        Path directory = directoryOf(id);
        if (!Files.isDirectory(directory)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + id.value() + " is not a directory");
        }
        return directory;
    }

    /** The existing tactic file whose {@code id} is {@code tacticId}; the first in path order if several declare it. */
    private static Optional<Path> locateTactic(Path directory, String tacticId) {
        for (String file : tacticFiles(directory)) {
            ParsedYaml parsed =
                    ParsedYaml.parse(readText(directory.resolve(file)).orElse(""), file);
            if (parsed.document() instanceof java.util.Map<?, ?> map && tacticId.equals(map.get("id"))) {
                return Optional.of(directory.resolve(file));
            }
        }
        return Optional.empty();
    }

    /**
     * The file a new tactic is created at: {@code <id>.tactic.yaml} directly in the pack directory. An id that would
     * place the file anywhere else (a separator, a climb) is refused, and so is a name already taken, links included,
     * so a create never overwrites a file or follows a planted link.
     */
    private static Path newTacticFile(Path directory, String tacticId) {
        Path target = directory.resolve(tacticId + ".tactic.yaml").normalize();
        if (!directory.equals(target.getParent())) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "tactic id '" + tacticId + "' does not name a file in the pack");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot create tactic '" + tacticId + "': its file name is taken");
        }
        return target;
    }

    /** The pack-relative, slash-separated paths of the pack's tactic files, in path order. */
    private static List<String> tacticFiles(Path directory) {
        // The classification globs are relative to the directory that holds the pack directories.
        String prefix = directory.getFileName() + "/";
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".yaml") && Files.isRegularFile(p))
                    .map(p -> directory
                            .relativize(p)
                            .toString()
                            .replace(p.getFileSystem().getSeparator(), "/"))
                    .filter(file -> PackFileKind.bundled().classify(prefix + file) instanceof PackFileKind.Mapped mapped
                            && TACTIC_KIND.equals(mapped.kind()))
                    .sorted()
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            throw FileSystemPackInspector.unlistable(String.valueOf(directory.getFileName()), e);
        }
    }

    // ---- reading -------------------------------------------------------------------------------------------------

    private static <T> FileRead<T> readFile(Path path, String file, Function<String, FileRead<T>> reader) {
        Optional<String> text = readText(path);
        if (text.isEmpty()) {
            return new FileRead.Unreadable<>(
                    file, new ParseFailure(file, "the file cannot be read as UTF-8 text"), List.of());
        }
        return reader.apply(text.get());
    }

    /** The file's text; empty when its bytes are not UTF-8 or it cannot be read. */
    private static Optional<String> readText(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return Optional.of(StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static List<PackElementViolation> violationsOf(FileRead.Unreadable<?> bad) {
        if (bad.parse() != null) {
            return List.of(new PackElementViolation(
                    bad.file(), "unreadable", bad.parse().firstLine()));
        }
        List<PackElementViolation> out = new ArrayList<>();
        for (SchemaViolation v : bad.violations()) {
            String where = v.pointer().isEmpty() ? "the document" : "/" + String.join("/", v.pointer());
            out.add(new PackElementViolation(bad.file(), "schema", v.keyword() + " violated at " + where));
        }
        return out;
    }

    // ---- writing -------------------------------------------------------------------------------------------------

    /**
     * Writes {@code content} to a temporary file beside {@code target}, then renames it over {@code target} through
     * the {@link Rename} seam. A target outside the pack directory is refused before anything is written; a failure
     * at either step removes the temporary file and leaves {@code target} as it was.
     */
    private void writeAtomically(Path directory, Path target, String content) {
        Path packRoot = directory.toAbsolutePath().normalize();
        Path resolved = target.toAbsolutePath().normalize();
        if (!resolved.startsWith(packRoot) || !realPathContained(packRoot, resolved)) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "refusing to write outside pack " + directory.getFileName());
        }
        @Nullable Path temp = null;
        try {
            temp = Files.createTempFile(resolved.getParent(), ".pack-write-", ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            rename.move(temp, resolved);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot write " + packRoot.relativize(resolved) + " in pack", e);
        }
    }

    /** Whether the target's real location, following symlinks, is inside the pack's real directory. */
    private static boolean realPathContained(Path packRoot, Path target) {
        try {
            Path realRoot = packRoot.toRealPath();
            Path existing = target;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            return existing != null && existing.toRealPath().startsWith(realRoot);
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteQuietly(@Nullable Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // the original failure is the one worth reporting
        }
    }
}
