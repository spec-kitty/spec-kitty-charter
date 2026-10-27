package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The filesystem storage of packs, at the persistence-shape level: it maps a {@link PackIdentifier} to a pack
 * directory, reads the directory partial-tolerantly into DSOs, and writes a DSO back atomically and inside the pack.
 * Format is the {@link SerializationStrategy}'s business; this class only moves text.
 *
 * <p>The identifier's value names the pack directory under the storage root. Every kind bound in {@link KindBindings}
 * is read and written through one path (ADR-0036). An element is located by scanning the pack's files of its kind, as
 * {@link PackFileSet} declares them, for the one whose {@code id} matches. A write is an upsert: a known id replaces
 * its file, an unknown id creates {@code <id>.<kind>.yaml} directly in the pack directory (and never over an existing
 * file). A read never throws on the content of a file (that is a {@link PackElementViolation}); only a missing or unreadable
 * {@code pack.yaml} refuses it.
 *
 * <p>A write, replace or create, serializes from scratch, writes a temporary file beside the target and renames it
 * into place, so a failure leaves the original untouched (or the new file absent) and removes the temporary file. A target that resolves, symlinks followed,
 * outside the pack directory is refused before any byte is written.
 */
final class FilesystemPackStore {

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

    /**
     * One readable artifact file: where it sits in the pack and what it held.
     *
     * @param file the pack-relative, {@code /}-separated path
     * @param dso what the file held
     * @param <D> the kind's persistence shape
     */
    record Stored<D>(String file, D dso) {
        /**
         * Checks the fields.
         *
         * @param file the pack-relative path
         * @param dso what the file held
         */
        Stored {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(dso, "dso");
        }
    }

    /**
     * What a pack directory held: the descriptor, the readable files of every bound kind and a violation per file
     * that could not be read.
     *
     * @param descriptor the descriptor
     * @param elements the readable files of each bound kind, in path order; a kind with none may be absent
     * @param violations the files that could not be read, in kind then path order
     */
    record PackRead(
            PackDescriptorDso descriptor,
            Map<ArtifactKind, List<Stored<?>>> elements,
            List<PackElementViolation> violations) {
        /**
         * Copies the collections.
         *
         * @param descriptor the descriptor
         * @param elements the readable files of each kind
         * @param violations the unreadable files
         */
        PackRead {
            Objects.requireNonNull(descriptor, "descriptor");
            Map<ArtifactKind, List<Stored<?>>> copy = new EnumMap<>(ArtifactKind.class);
            elements.forEach((kind, list) -> copy.put(kind, List.copyOf(list)));
            elements = Collections.unmodifiableMap(copy);
            violations = List.copyOf(violations);
        }

        /**
         * The readable files of one kind, typed by its binding.
         *
         * @param kind the kind's binding
         * @param <D> the kind's persistence shape
         * @return the files, in path order
         */
        <D> List<Stored<D>> elements(KindBinding<D, ?> kind) {
            return elements.getOrDefault(kind.kind(), List.of()).stream()
                    .map(stored -> new Stored<>(stored.file(), kind.dsoType().cast(stored.dso())))
                    .toList();
        }

        /**
         * The readable persistence shapes of one kind.
         *
         * @param kind the kind's binding
         * @param <D> the kind's persistence shape
         * @return the shapes, in path order
         */
        <D> List<D> dsos(KindBinding<D, ?> kind) {
            return elements(kind).stream().map(Stored::dso).toList();
        }
    }

    /**
     * The DRG documents of a pack directory and a violation per document that could not be read.
     *
     * @param documents the readable documents: the root shards by name, then the fragment
     * @param violations the unreadable documents
     */
    record GraphRead(List<GraphDocument> documents, List<PackElementViolation> violations) {
        /**
         * Copies the lists.
         *
         * @param documents the readable documents
         * @param violations the unreadable documents
         */
        GraphRead {
            documents = List.copyOf(documents);
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
     * @return the descriptor, the readable files of every bound kind and a violation per broken file
     * @throws CharterRefusal when the pack has no readable {@code pack.yaml}
     */
    PackRead read(PackIdentifier id) {
        Path directory = existingDirectory(id);
        PackDescriptorDso descriptor = descriptorOf(directory, id);
        Map<ArtifactKind, List<Stored<?>>> elements = new EnumMap<>(ArtifactKind.class);
        List<PackElementViolation> violations = new ArrayList<>();
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            elements.put(kind.kind(), readKind(directory, kind, violations));
        }
        return new PackRead(descriptor, elements, violations);
    }

    /**
     * Reads only a pack's {@code pack.yaml}: no element file is opened. This is how an identifier is matched to a pack
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
     * Reads the readable elements of one kind of a pack, without its descriptor.
     *
     * @param id the pack
     * @param kind the kind's binding
     * @param <D> the kind's persistence shape
     * @return the readable elements, in path order
     */
    <D> List<D> readAll(PackIdentifier id, KindBinding<D, ?> kind) {
        return readKind(existingDirectory(id), kind, new ArrayList<>()).stream()
                .map(stored -> kind.dsoType().cast(stored.dso()))
                .toList();
    }

    /**
     * Reads one element: only the file that declares the id is parsed as the kind.
     *
     * @param id the pack
     * @param kind the kind's binding
     * @param elementId the element's id
     * @param <D> the kind's persistence shape
     * @return the element, or empty when the pack has no readable element of the kind with that id
     */
    <D> Optional<D> readOne(PackIdentifier id, KindBinding<D, ?> kind, String elementId) {
        Path directory = existingDirectory(id);
        return locate(directory, kind.kind(), elementId).flatMap(file -> {
            String name = directory
                    .relativize(file)
                    .toString()
                    .replace(file.getFileSystem().getSeparator(), "/");
            return switch (readFile(file, name, text -> strategy.read(kind, text, name))) {
                case FileRead.Read<D> good -> Optional.of(good.value());
                case FileRead.Unreadable<D> bad -> Optional.empty();
                case FileRead.Absent<D> absent -> Optional.empty();
            };
        });
    }

    /**
     * Reads a pack's DRG documents with every field: the root {@code *.graph.yaml} shards by name, then
     * {@code drg/fragment.yaml} when the pack has one. A document that cannot be read is a violation, not a refusal.
     *
     * @param id the pack
     * @return the readable documents and a violation per unreadable one
     */
    GraphRead readGraphs(PackIdentifier id) {
        Path directory = existingDirectory(id);
        List<GraphDocument> documents = new ArrayList<>();
        List<PackElementViolation> violations = new ArrayList<>();
        List<String> files = new ArrayList<>();
        for (Path shard : PackFileSet.rootShards(directory, id.value())) {
            files.add(String.valueOf(shard.getFileName()));
        }
        boolean hasFragment = Files.isRegularFile(directory.resolve(PackFileSet.FRAGMENT));
        if (hasFragment) {
            files.add(PackFileSet.FRAGMENT);
        }
        for (String file : files) {
            Shape shape = file.equals(PackFileSet.FRAGMENT) ? Shape.FRAGMENT : Shape.SHARD;
            switch (readFile(directory.resolve(file), file, text -> strategy.readGraph(text, shape, file))) {
                case FileRead.Read<GraphDocument> good -> documents.add(good.value());
                case FileRead.Unreadable<GraphDocument> bad -> violations.addAll(violationsOf(bad));
                case FileRead.Absent<GraphDocument> absent -> {}
            }
        }
        return new GraphRead(documents, violations);
    }

    private PackDescriptorDso descriptorOf(Path directory, PackIdentifier id) {
        Path packYaml = directory.resolve(PackFileSet.PACK_YAML);
        if (!Files.isRegularFile(packYaml)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + id.value() + " has no pack.yaml");
        }
        return switch (readFile(packYaml, PackFileSet.PACK_YAML, strategy::readDescriptor)) {
            case FileRead.Read<PackDescriptorDso> good -> good.value();
            case FileRead.Unreadable<PackDescriptorDso> bad ->
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of " + id.value() + " cannot be read");
            case FileRead.Absent<PackDescriptorDso> absent ->
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + id.value() + " has no pack.yaml");
        };
    }

    private <D> List<Stored<?>> readKind(Path directory, KindBinding<D, ?> kind, List<PackElementViolation> violations) {
        List<Stored<?>> into = new ArrayList<>();
        for (String file : PackFileSet.files(directory, kind.kind())) {
            switch (readFile(directory.resolve(file), file, text -> strategy.read(kind, text, file))) {
                case FileRead.Read<D> good -> into.add(new Stored<>(file, good.value()));
                case FileRead.Unreadable<D> bad -> violations.addAll(violationsOf(bad));
                case FileRead.Absent<D> absent -> {}
            }
        }
        return into;
    }

    /**
     * Replaces a pack's {@code pack.yaml}.
     *
     * @param id the pack
     * @param descriptor what to write
     */
    void writeDescriptor(PackIdentifier id, PackDescriptorDso descriptor) {
        Path directory = existingDirectory(id);
        writeAtomically(directory, directory.resolve(PackFileSet.PACK_YAML), strategy.writeDescriptor(descriptor));
    }

    /**
     * Upserts an element: the file of the element of this kind with this id is replaced; an unknown id is created as
     * {@link KindBinding#fileName} in the pack directory. Both arms are atomic and contained in the pack.
     *
     * @param id the pack
     * @param kind the kind's binding
     * @param dso what to write
     * @param <D> the kind's persistence shape
     * @throws CharterRefusal when the file resolves outside the pack, or a new element's file name is taken by
     *     something that is not an element of the kind with that id
     */
    <D> void write(PackIdentifier id, KindBinding<D, ?> kind, D dso) {
        Path directory = existingDirectory(id);
        String elementId = kind.idOf(dso);
        Path target = locate(directory, kind.kind(), elementId)
                .orElseGet(() -> newFile(directory, kind, elementId));
        writeAtomically(directory, target, strategy.write(kind, dso));
    }

    // ---- id to path ----------------------------------------------------------------------------------------------

    private Path existingDirectory(PackIdentifier id) {
        Path directory = directoryOf(id);
        if (!Files.isDirectory(directory)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + id.value() + " is not a directory");
        }
        return directory;
    }

    /** The existing file of the kind whose {@code id} is {@code elementId}; the first in path order if several do. */
    private static Optional<Path> locate(Path directory, ArtifactKind kind, String elementId) {
        for (String file : PackFileSet.files(directory, kind)) {
            ParsedYaml parsed =
                    ParsedYaml.parse(readText(directory.resolve(file)).orElse(""), file);
            if (parsed.document() instanceof java.util.Map<?, ?> map && elementId.equals(map.get("id"))) {
                return Optional.of(directory.resolve(file));
            }
        }
        return Optional.empty();
    }

    /**
     * The file a new element is created at: {@code <id>.<kind>.yaml} directly in the pack directory. An id that would
     * place the file anywhere else (a separator, a climb) is refused, and so is a name already taken, links included,
     * so a create never overwrites a file or follows a planted link.
     */
    private static Path newFile(Path directory, KindBinding<?, ?> kind, String elementId) {
        String token = kind.kind().token();
        Path target = directory.resolve(kind.fileName(elementId)).normalize();
        if (!directory.equals(target.getParent())) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, token + " id '" + elementId + "' does not name a file in the pack");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "cannot create " + token + " '" + elementId + "': its file name is taken");
        }
        return target;
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
