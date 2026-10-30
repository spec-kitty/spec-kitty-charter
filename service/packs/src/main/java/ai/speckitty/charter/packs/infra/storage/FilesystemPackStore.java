package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The filesystem storage of packs, at the persistence-shape level: it maps a {@link PackIdentifier} to a pack
 * directory, reads the directory partial-tolerantly into DSOs, and writes a DSO back atomically and inside the pack.
 * Format is the {@link SerializationStrategy}'s business; this class only moves text, and the bytes of the files of the
 * {@code bytes} content form (asset files and templates), which it reads and writes as they are, with no charset.
 *
 * <p>The identifier's value names the pack directory under the storage root. Every kind bound in {@link KindBindings}
 * is read and written through one path (ADR-0037). An element is located by scanning the pack's files of its kind, as
 * {@link PackFileSet} declares them, for the one whose {@code id} matches. A write is an upsert: a known id replaces
 * its file, an unknown id creates {@code <id>.<kind>.yaml} directly in the pack directory (and never over an existing
 * file). A read never throws on the content of a file (that is a {@link PackElementViolation}); only a missing or unreadable
 * {@code pack.yaml} refuses it.
 *
 * <p>An asset sidecar is held only when its payload is: a sidecar whose {@code path} names no asset file the read found
 * under {@code assets/} is dropped and recorded as a violation ({@code payload assets/<path> does not exist}), and so is
 * a second sidecar naming a file another sidecar already named. A payload that is not UTF-8 is no violation. An asset
 * file or a template reached through a link whose real location is outside the pack is a violation and is not read,
 * and so is an asset file whose name the domain refuses.
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
     * @param assetFiles the asset files, in path order
     * @param templates the templates, in path order
     * @param presets the readable presets whose id is their file stem, in path order (ADR-0042)
     * @param document the pack document ({@code pack.md}) when the pack has a readable one (#260)
     */
    record PackRead(
            PackDescriptorDso descriptor,
            Map<ArtifactKind, List<Stored<?>>> elements,
            List<PackElementViolation> violations,
            List<AssetFile> assetFiles,
            List<TemplateFile> templates,
            List<Stored<PresetDso>> presets,
            Optional<Stored<MarkdownDocument>> document) {
        /**
         * Copies the collections.
         *
         * @param descriptor the descriptor
         * @param elements the readable files of each kind
         * @param violations the unreadable files
         * @param assetFiles the asset files, in path order
         * @param templates the templates, in path order
         * @param presets the readable presets, in path order
         * @param document the readable pack document, if any
         */
        PackRead {
            Objects.requireNonNull(descriptor, "descriptor");
            Map<ArtifactKind, List<Stored<?>>> copy = new EnumMap<>(ArtifactKind.class);
            elements.forEach((kind, list) -> copy.put(kind, List.copyOf(list)));
            elements = Collections.unmodifiableMap(copy);
            violations = List.copyOf(violations);
            assetFiles = List.copyOf(assetFiles);
            templates = List.copyOf(templates);
            presets = List.copyOf(presets);
            Objects.requireNonNull(document, "document");
        }

        /**
         * A read that holds no asset file, no template, no preset and no pack document.
         *
         * @param descriptor the descriptor
         * @param elements the readable files of each kind
         * @param violations the unreadable files
         */
        PackRead(
                PackDescriptorDso descriptor,
                Map<ArtifactKind, List<Stored<?>>> elements,
                List<PackElementViolation> violations) {
            this(descriptor, elements, violations, List.of(), List.of(), List.of(), Optional.empty());
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
     * One template file as read: its bytes and where it sits.
     *
     * @param path the path relative to the pack's {@code templates/} directory, {@code /}-separated: the template's id
     * @param content the file's bytes
     */
    record TemplateFile(String path, Payload content) {
        /**
         * Checks the fields.
         *
         * @param path the path under {@code templates/}
         * @param content the bytes
         */
        TemplateFile {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(content, "content");
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
    private final Companions.Rules companionRules;

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
        this(root, strategy, rename, Companions.Rules.bundled());
    }

    /**
     * A store whose companions are held to the given table and schemas: the seam a test uses to give a companion a
     * structure schema of its own (#194).
     *
     * @param root the directory that holds the pack directories
     * @param strategy the format
     * @param companionRules the table that classifies companion paths and the schemas of Markdown companions
     */
    FilesystemPackStore(Path root, SerializationStrategy strategy, Companions.Rules companionRules) {
        this(root, strategy, ATOMIC, companionRules);
    }

    private FilesystemPackStore(
            Path root, SerializationStrategy strategy, Rename rename, Companions.Rules companionRules) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.rename = Objects.requireNonNull(rename, "rename");
        this.companionRules = Objects.requireNonNull(companionRules, "companionRules");
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
        return read(id, KindBindings.all());
    }

    /**
     * Reads a pack with the given bindings in place of the registry: the seam a test uses to read a binding of its own.
     *
     * @param id the pack
     * @param kinds the bindings to read, in order
     * @return the descriptor, the readable files of each binding's kind and a violation per broken file
     * @throws CharterRefusal when the pack has no readable {@code pack.yaml}
     */
    PackRead read(PackIdentifier id, List<KindBinding<?, ?>> kinds) {
        Path directory = existingDirectory(id);
        PackDescriptorDso descriptor = descriptorOf(directory, id);
        PackFileSet.Listing listing = PackFileSet.listing(directory);
        Map<ArtifactKind, List<Stored<?>>> elements = new EnumMap<>(ArtifactKind.class);
        List<PackElementViolation> violations = new ArrayList<>();
        List<AssetFile> assetFiles = new ArrayList<>();
        for (String file : listing.assetFiles()) {
            readPayload(directory, file, violations).ifPresent(bytes -> {
                // A read never throws on content: a name the domain refuses (a blank one) is a violation, as a
                // template's is.
                try {
                    assetFiles.add(new AssetFile(file.substring(PackFileSet.ASSETS.length()), Payload.of(bytes)));
                } catch (ConstraintBreachException breach) {
                    violations.add(new PackElementViolation(file, "domain", String.valueOf(breach.getMessage())));
                }
            });
        }
        List<TemplateFile> templates = new ArrayList<>();
        for (String file : listing.templates()) {
            readPayload(directory, file, violations)
                    .ifPresent(bytes -> templates.add(
                            new TemplateFile(file.substring(PackFileSet.TEMPLATES.length()), Payload.of(bytes))));
        }
        Set<String> claimed = new HashSet<>();
        for (KindBinding<?, ?> kind : kinds) {
            List<Stored<?>> read = readKind(directory, listing.files(kind.kind()), kind, violations, claimed);
            if (kind == AssetBinding.INSTANCE) {
                read = withPayloads(read, assetFiles, violations);
            }
            elements.put(kind.kind(), read);
        }
        List<Stored<PresetDso>> presets = readPresets(directory, listing.presets(), violations);
        Optional<Stored<MarkdownDocument>> document =
                listing.document().flatMap(file -> readDocument(directory, file, violations));
        return new PackRead(descriptor, elements, violations, assetFiles, templates, presets, document);
    }

    /**
     * The pack document, when it reads (#260): a {@code pack.md} that is not UTF-8, that the Markdown codec refuses or
     * that breaks the {@code pack-document} structure schema is a violation on its file, and the read holds no document.
     */
    private static Optional<Stored<MarkdownDocument>> readDocument(
            Path directory, String file, List<PackElementViolation> violations) {
        return switch (readFile(directory.resolve(file), file, PackDocumentMarkdown::read)) {
            case FileRead.Read<MarkdownDocument> good -> Optional.of(new Stored<>(file, good.value()));
            case FileRead.Unreadable<MarkdownDocument> bad -> {
                violations.addAll(violationsOf(bad));
                yield Optional.empty();
            }
            case FileRead.Absent<MarkdownDocument> absent -> Optional.empty();
        };
    }

    /**
     * The readable presets, in path order. A preset file that is not UTF-8, is not YAML or breaks the {@code preset}
     * schema is a violation on its file, as an artifact file is; so is one whose {@code id} is not its file stem
     * ({@link PresetFiles#stemMismatch}), the rule validate mode applies too.
     */
    private List<Stored<PresetDso>> readPresets(
            Path directory, List<String> files, List<PackElementViolation> violations) {
        List<Stored<PresetDso>> out = new ArrayList<>();
        for (String file : files) {
            switch (readFile(directory.resolve(file), file, text -> strategy.readPreset(text, file))) {
                case FileRead.Read<PresetDso> good -> {
                    Optional<String> mismatch =
                            PresetFiles.stemMismatch(file, good.value().id());
                    if (mismatch.isPresent()) {
                        violations.add(new PackElementViolation(file, "schema", mismatch.get()));
                    } else {
                        out.add(new Stored<>(file, good.value()));
                    }
                }
                case FileRead.Unreadable<PresetDso> bad -> violations.addAll(violationsOf(bad));
                case FileRead.Absent<PresetDso> absent -> {}
            }
        }
        return out;
    }

    /**
     * The asset sidecars whose payload the read holds, in path order. A sidecar whose {@code path} names no asset file
     * (missing, outside {@code assets/}, or a file another rule classifies) is a violation on the sidecar's file, and so
     * is a sidecar naming a file an earlier sidecar named; neither joins the pack.
     */
    private static List<Stored<?>> withPayloads(
            List<Stored<?>> sidecars, List<AssetFile> assetFiles, List<PackElementViolation> violations) {
        Set<String> held = new HashSet<>();
        assetFiles.forEach(file -> held.add(file.path()));
        Map<String, String> namedBy = new HashMap<>();
        List<Stored<?>> kept = new ArrayList<>();
        for (Stored<?> stored : sidecars) {
            AssetDso sidecar = AssetDso.class.cast(stored.dso());
            String payload = PackFileSet.ASSETS + sidecar.path();
            String claimant = namedBy.get(sidecar.path());
            if (!held.contains(sidecar.path())) {
                violations.add(new PackElementViolation(
                        stored.file(), AssetBinding.INSTANCE.kind().token(), "payload " + payload + " does not exist"));
            } else if (claimant != null) {
                violations.add(new PackElementViolation(
                        stored.file(),
                        AssetBinding.INSTANCE.kind().token(),
                        "payload " + payload + " is already named by asset " + claimant));
            } else {
                namedBy.put(sidecar.path(), sidecar.id());
                kept.add(stored);
            }
        }
        return kept;
    }

    /**
     * An asset file's or a template's bytes. The walk follows a file link, so the file passes the containment rule a
     * companion's does ({@link #realPathContained}): a link whose real location is outside the pack is a violation and
     * is not read. A file that cannot be read is a violation too, not a refusal.
     */
    private static Optional<byte[]> readPayload(Path directory, String file, List<PackElementViolation> violations) {
        if (!realPathContained(directory, directory.resolve(file))) {
            violations.add(new PackElementViolation(file, "unreadable", "the file leaves the pack"));
            return Optional.empty();
        }
        Optional<byte[]> bytes = readBytes(directory.resolve(file));
        if (bytes.isEmpty()) {
            violations.add(new PackElementViolation(file, "unreadable", "the file cannot be read"));
        }
        return bytes;
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
        Path directory = existingDirectory(id);
        return readKind(directory, PackFileSet.files(directory, kind.kind()), kind, new ArrayList<>(), new HashSet<>())
                .stream()
                .map(stored -> kind.dsoType().cast(stored.dso()))
                .toList();
    }

    /**
     * Reads one element: only the file that declares the id is parsed as the kind. Its companions pass the companion
     * rule, but whether another artifact also names one is not checked: that needs the whole pack, and {@link #read}
     * refuses it.
     *
     * @param id the pack
     * @param kind the kind's binding
     * @param elementId the element's id
     * @param <D> the kind's persistence shape
     * @return the element, or empty when the pack has no readable element of the kind with that id
     */
    <D> Optional<D> readOne(PackIdentifier id, KindBinding<D, ?> kind, String elementId) {
        Path directory = existingDirectory(id);
        return locate(directory, kind, elementId).flatMap(file -> {
            String name = directory
                    .relativize(file)
                    .toString()
                    .replace(file.getFileSystem().getSeparator(), "/");
            return switch (readFile(file, name, text -> strategy.read(kind, text, name))) {
                case FileRead.Read<D> good ->
                    withCompanions(
                            companionRules, directory, name, kind, good.value(), new ArrayList<>(), new HashSet<>());
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

    private <D> List<Stored<?>> readKind(
            Path directory,
            List<String> files,
            KindBinding<D, ?> kind,
            List<PackElementViolation> violations,
            Set<String> claimed) {
        List<Stored<?>> into = new ArrayList<>();
        for (String file : files) {
            switch (readFile(directory.resolve(file), file, text -> strategy.read(kind, text, file))) {
                case FileRead.Read<D> good -> {
                    // The stem rule validate mode applies too (FileStems): a misnamed file is refused, not loaded.
                    Optional<String> misnamed = FileStems.mismatch(kind, file, kind.idOf(good.value()));
                    if (misnamed.isPresent()) {
                        violations.add(new PackElementViolation(file, "schema", misnamed.get()));
                        continue;
                    }
                    withCompanions(companionRules, directory, file, kind, good.value(), violations, claimed)
                            .ifPresent(dso -> into.add(new Stored<>(file, dso)));
                }
                case FileRead.Unreadable<D> bad -> violations.addAll(violationsOf(bad));
                case FileRead.Absent<D> absent -> {}
            }
        }
        return into;
    }

    /**
     * The shape with its companions read in (ADR-0039), each in its declared form (#194), or empty when one cannot be
     * read: the artifact is then left out and the reason recorded as a {@code companion} violation of its file, naming
     * the companion path and why (a Markdown refusal names its line, a structure breach its keyword), so a whole-pack
     * write refuses the pack rather than drop the companion. A companion another artifact of this read already named, case ignored, is
     * refused for the second artifact: one file has one owner. {@code claimed} collects the companions read so far.
     */
    private static <D> Optional<D> withCompanions(
            Companions.Rules rules,
            Path directory,
            String file,
            KindBinding<D, ?> kind,
            D dso,
            List<PackElementViolation> violations,
            Set<String> claimed) {
        List<KindBinding.Companion> companions = kind.companions(dso);
        if (companions.isEmpty()) {
            return Optional.of(dso);
        }
        Map<String, CompanionContent> contents = new LinkedHashMap<>();
        Set<String> claims = new HashSet<>();
        for (KindBinding.Companion companion : companions) {
            @Nullable String refusal = null;
            switch (Companions.read(rules, directory, file, companion)) {
                case Companions.Refused refused -> refusal = refused.reason();
                case Companions.Found found -> {
                    String claim = found.at().claim();
                    if (claimed.contains(claim) || !claims.add(claim)) {
                        refusal = "named by two artifacts";
                    } else {
                        contents.put(companion.path(), found.content());
                    }
                }
            }
            if (refusal != null) {
                violations.add(new PackElementViolation(file, "companion", companion.path() + ": " + refusal));
                return Optional.empty();
            }
        }
        claimed.addAll(claims);
        return Optional.of(kind.withCompanions(dso, contents));
    }

    /**
     * Replaces a pack's {@code pack.yaml}.
     *
     * @param id the pack
     * @param descriptor what to write
     */
    void writeDescriptor(PackIdentifier id, PackDescriptorDso descriptor) {
        Path directory = existingDirectory(id);
        writeAtomically(
                directory, directory.resolve(PackFileSet.PACK_YAML), utf8(strategy.writeDescriptor(descriptor)));
    }

    /**
     * Writes an activation preset in place at {@code presets/<id>.yaml}, creating the directory, replacing the file
     * when it is there. The file name is the preset's id, so the stem rule holds by construction.
     *
     * @param id the pack
     * @param preset what to write
     * @throws CharterRefusal when the file cannot be written
     */
    void writePreset(PackIdentifier id, PresetDso preset) {
        Path directory = existingDirectory(id);
        writeAtomically(
                directory, directory.resolve(PresetFiles.pathOf(preset.id())), utf8(strategy.writePreset(preset)));
    }

    /**
     * Writes the pack document in place at {@code pack.md}, in the Markdown form's canonical text (#260), replacing the
     * file when it is there.
     *
     * @param id the pack
     * @param document what to write
     * @throws CharterRefusal when the file cannot be written
     * @throws IllegalStateException when the codec cannot write the document faithfully (its own guard)
     */
    void writePackDocument(PackIdentifier id, MarkdownDocument document) {
        Path directory = existingDirectory(id);
        writeAtomically(directory, directory.resolve(PackDocumentMarkdown.FILE), utf8(MarkdownCodec.write(document)));
    }

    /**
     * Upserts an element: the file of the element of this kind with this id is replaced; an unknown id is created as
     * {@link KindBinding#fileName} in the pack directory. Both arms are atomic and contained in the pack.
     *
     * <p>An element's companions (ADR-0039) are all checked before anything is written, then written, each atomically
     * and beside the element's file, then the element's file. A companion never replaces an existing file the element
     * did not already own. A replaced element then removes the companions its old file named and the new one does
     * not, so a changed path leaves no orphan. The companions and
     * the element are atomic together only through the commit path, which writes into a staging copy of the pack and
     * swaps the whole directory ({@link PackCommitStore}); that is the only production writer of kinds with companions.
     *
     * @param id the pack
     * @param kind the kind's binding
     * @param dso what to write
     * @param <D> the kind's persistence shape
     * @throws CharterRefusal when the file resolves outside the pack or a new element's file name is taken by
     *     something that is not an element of the kind with that id ({@code PACK_UNREADABLE}); when a companion has no
     *     content ({@code PACK_UNREADABLE}); when a companion breaks the companion path rule (leaves the pack, is YAML,
     *     is classified by a rule of {@code file-kinds.yaml}) or its structure schema ({@code VALIDATION_FAILED}, a
     *     client error); when a companion is named twice, is named by another artifact, differs only in case from the
     *     companion it replaces, or would replace an existing file the element does not own
     *     ({@code REGISTRATION_CONFLICT}, a client error)
     */
    <D> void write(PackIdentifier id, KindBinding<D, ?> kind, D dso) {
        Path directory = existingDirectory(id);
        String elementId = kind.idOf(dso);
        Optional<Path> existing = locate(directory, kind, elementId);
        Path target = existing.orElseGet(() -> newFile(directory, kind, elementId));
        String file = relative(directory, target);
        List<Companions.Inside> owned =
                existing.isPresent() ? companionsOf(directory, file, kind) : List.<Companions.Inside>of();
        Set<String> others = kind.hasCompanions() ? claimsOfOthers(directory, file, kind) : Set.of();
        Map<Companions.Inside, String> planned =
                planCompanions(companionRules, directory, file, kind, dso, owned, others);
        // writeAtomically creates each companion's directories and refuses, as "cannot write <path> in pack", one it
        // cannot write.
        for (Map.Entry<Companions.Inside, String> companion : planned.entrySet()) {
            writeAtomically(directory, companion.getKey().file(), utf8(companion.getValue()));
        }
        writeAtomically(directory, target, utf8(strategy.write(kind, dso)));
        Set<String> kept = new HashSet<>();
        planned.keySet().forEach(companion -> kept.add(companion.claim()));
        removeDropped(directory, owned, kept);
    }

    /**
     * Checks every companion of {@code dso} before any is written: each has content, passes the companion rule beside
     * {@code file}, stays in the pack with links resolved, is named once, and does not replace an existing file the
     * element does not already own ({@code owned}).
     *
     * @return each companion's place and text, in the order the element names them
     */
    private static <D> Map<Companions.Inside, String> planCompanions(
            Companions.Rules rules,
            Path directory,
            String file,
            KindBinding<D, ?> kind,
            D dso,
            List<Companions.Inside> owned,
            Set<String> others) {
        String what = kind.kind().token() + " '" + kind.idOf(dso) + "'";
        Map<String, String> ownedPaths = new HashMap<>();
        owned.forEach(companion -> ownedPaths.put(companion.claim(), companion.packRelative()));
        Map<Companions.Inside, String> planned = new LinkedHashMap<>();
        Set<String> claims = new HashSet<>();
        for (KindBinding.Companion companion : kind.companions(dso)) {
            String named = what + ": companion " + companion.path();
            CompanionContent content = companion
                    .content()
                    .orElseThrow(() -> new CharterRefusal(RefusalCode.PACK_UNREADABLE, named + " has no content"));
            Companions.Inside at = switch (Companions.resolve(rules, directory, file, companion)) {
                case Companions.Inside inside -> inside;
                case Companions.Refused refused ->
                    throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, named + " " + refused.reason());
            };
            Companions.breach(rules, companion, at.packRelative(), content).ifPresent(breach -> {
                throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, named + " breaks its " + breach);
            });
            if (!claims.add(at.claim())) {
                throw new CharterRefusal(RefusalCode.REGISTRATION_CONFLICT, named + " is named twice");
            }
            // One file has one owner, case ignored, as the read decides it.
            if (others.contains(at.claim())) {
                throw new CharterRefusal(RefusalCode.REGISTRATION_CONFLICT, named + " is named by another artifact");
            }
            @Nullable String replaced = ownedPaths.get(at.claim());
            if (replaced != null && !replaced.equals(at.packRelative())) {
                // A case-only rename would orphan the old file, or replace the new one on a case-insensitive file
                // system.
                throw new CharterRefusal(
                        RefusalCode.REGISTRATION_CONFLICT, named + " differs only in case from " + replaced);
            }
            if (Files.exists(at.file(), LinkOption.NOFOLLOW_LINKS) && replaced == null) {
                throw new CharterRefusal(RefusalCode.REGISTRATION_CONFLICT, named + " is taken");
            }
            if (!realPathContained(directory, at.file())) {
                throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, named + " leaves the pack");
            }
            try {
                planned.put(at, Companions.text(content));
            } catch (IllegalStateException inexpressible) {
                // A Markdown model the canonical form cannot express (#194): the caller's content, refused.
                throw new CharterRefusal(
                        RefusalCode.VALIDATION_FAILED, named + " " + inexpressible.getMessage(), inexpressible);
            }
        }
        return planned;
    }

    /**
     * The claims of the companions every other artifact of the pack names (every file but {@code file} of the written
     * kind and of every registered kind with companions), so an upsert refuses a companion another artifact owns, as
     * the read does.
     */
    private Set<String> claimsOfOthers(Path directory, String file, KindBinding<?, ?> written) {
        Set<String> out = new HashSet<>();
        List<KindBinding<?, ?>> bindings = new ArrayList<>(List.of(written));
        KindBindings.all().stream().filter(b -> b.kind() != written.kind()).forEach(bindings::add);
        for (KindBinding<?, ?> binding : bindings) {
            if (!binding.hasCompanions()) {
                continue;
            }
            for (String other : PackFileSet.files(directory, binding.kind())) {
                if (!other.equals(file)) {
                    companionsOf(directory, other, binding).forEach(companion -> out.add(companion.claim()));
                }
            }
        }
        return out;
    }

    /**
     * The companions an element's file names now, where the companion rule allows them; none when its kind has no
     * companions or the file no longer reads. An element whose file no longer reads therefore owns no companion, and a
     * write that names an existing one is refused as taken: such a file is repaired by hand, not over the top of it.
     */
    private <D> List<Companions.Inside> companionsOf(Path directory, String file, KindBinding<D, ?> kind) {
        if (!kind.hasCompanions()
                || !(readFile(directory.resolve(file), file, text -> strategy.read(kind, text, file))
                        instanceof FileRead.Read<D> read)) {
            return List.of();
        }
        List<Companions.Inside> out = new ArrayList<>();
        for (KindBinding.Companion companion : kind.companions(read.value())) {
            if (Companions.resolve(companionRules, directory, file, companion) instanceof Companions.Inside at) {
                out.add(at);
            }
        }
        return out;
    }

    /**
     * Deletes the companions an element owned before its write and no longer names ({@code kept} holds the ones it
     * names now). It runs after the element's file is written, so a failed write leaves the old companions in place.
     * Only a regular file inside the pack is deleted.
     */
    private static void removeDropped(Path directory, List<Companions.Inside> owned, Set<String> kept) {
        for (Companions.Inside at : owned) {
            if (!kept.contains(at.claim())
                    && Files.isRegularFile(at.file(), LinkOption.NOFOLLOW_LINKS)
                    && realPathContained(directory, at.file())) {
                try {
                    Files.delete(at.file());
                } catch (IOException e) {
                    throw new CharterRefusal(
                            RefusalCode.PACK_UNREADABLE, "cannot remove " + at.packRelative() + " from pack", e);
                }
            }
        }
    }

    private static String relative(Path directory, Path file) {
        return directory
                .relativize(file)
                .toString()
                .replace(file.getFileSystem().getSeparator(), "/");
    }

    /**
     * Writes one file of the {@code bytes} content form (an asset file or a template) at its pack-relative path, byte
     * for byte, creating its directories. A file already there is replaced. The path passes the containment rule a
     * companion's does ({@link Companions#inPack}) and must classify back as {@code kind} (one file, one owner:
     * {@link PackFileSet#notHeldAs}), and the write is the store's one atomic write.
     *
     * @param id the pack
     * @param file the pack-relative, {@code /}-separated path, such as {@code assets/a/logo.png}
     * @param payload the bytes to write
     * @param kind the {@code bytes} kind the file is written as: {@link PackFileKind#ASSET_PAYLOAD} or
     *     {@link PackFileKind#TEMPLATE}
     * @throws CharterRefusal when the path breaks the containment rule, another rule classifies it, the file resolves
     *     outside the pack, or it cannot be written
     */
    void writePayload(PackIdentifier id, String file, Payload payload, String kind) {
        Path directory = existingDirectory(id);
        Companions.Inside at = switch (Companions.inPack(directory, file)) {
            case Companions.Inside inside -> inside;
            case Companions.Refused refused ->
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "payload " + file + " " + refused.reason());
        };
        PackFileSet.notHeldAs(directory, at.packRelative(), kind).ifPresent(reason -> {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "payload " + file + " " + reason);
        });
        writeAtomically(directory, at.file(), payload.bytes());
    }

    // ---- id to path ----------------------------------------------------------------------------------------------

    private Path existingDirectory(PackIdentifier id) {
        Path directory = directoryOf(id);
        if (!Files.isDirectory(directory)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + id.value() + " is not a directory");
        }
        return directory;
    }

    /**
     * The existing file of the kind whose id (under the kind's {@link KindBinding#idKey()}) is {@code elementId}; the
     * first in path order if several do.
     */
    private static Optional<Path> locate(Path directory, KindBinding<?, ?> kind, String elementId) {
        for (String file : PackFileSet.files(directory, kind.kind())) {
            ParsedYaml parsed =
                    ParsedYaml.parse(readText(directory.resolve(file)).orElse(""), file);
            if (parsed.document() instanceof java.util.Map<?, ?> map && elementId.equals(map.get(kind.idKey()))) {
                return Optional.of(directory.resolve(file));
            }
        }
        return Optional.empty();
    }

    /**
     * The file a new element is created at: {@code <id>.<fileToken>.yaml} directly in the pack directory. An id that would
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

    /**
     * The store's one read of a pack file: its bytes, as they are. Text (artifact files, companions) is a strict UTF-8
     * view over these bytes ({@link #readText}); asset files and templates keep them.
     *
     * @param path the file
     * @return the bytes; empty when the file cannot be read
     */
    static Optional<byte[]> readBytes(Path path) {
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** The file's text; empty when its bytes are not UTF-8 or it cannot be read. */
    static Optional<String> readText(Path path) {
        return readBytes(path).flatMap(bytes -> {
            try {
                return Optional.of(StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString());
            } catch (CharacterCodingException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * The violations of an unreadable file, one per schema violation or one for its parse failure: the store's one
     * wording, which the whole-pack seam reuses for the manifest it owns.
     *
     * @param bad the unreadable file
     * @return the violations, in the reader's order
     */
    static List<PackElementViolation> violationsOf(FileRead.Unreadable<?> bad) {
        if (bad.parse() != null) {
            return List.of(new PackElementViolation(
                    bad.file(), "unreadable", bad.parse().firstLine()));
        }
        List<PackElementViolation> out = new ArrayList<>();
        for (SchemaViolation v : bad.violations()) {
            out.add(new PackElementViolation(bad.file(), "schema", schemaReason(v)));
        }
        return out;
    }

    /**
     * A schema violation as a store violation states it: {@code <keyword> violated at <where>}, with an undeclared key
     * named, so the refusal says which field the schema does not declare (FR-006). A Markdown companion's structure
     * breach is stated the same way ({@link Companions#breach}).
     *
     * @param v the violation
     * @return the reason
     */
    static String schemaReason(SchemaViolation v) {
        String where = v.pointer().isEmpty() ? "the document" : "/" + String.join("/", v.pointer());
        String key = "additionalProperties".equals(v.keyword())
                ? v.property().map(p -> ": undeclared key '" + p + "'").orElse("")
                : "";
        return v.keyword() + " violated at " + where + key;
    }

    // ---- writing -------------------------------------------------------------------------------------------------

    /**
     * Writes {@code content} to a temporary file beside {@code target}, then renames it over {@code target} through
     * the {@link Rename} seam. A target outside the pack directory is refused before anything is written; a failure
     * at either step removes the temporary file and leaves {@code target} as it was.
     */
    private void writeAtomically(Path directory, Path target, byte[] content) {
        Path packRoot = directory.toAbsolutePath().normalize();
        Path resolved = target.toAbsolutePath().normalize();
        if (resolved.equals(packRoot) || !resolved.startsWith(packRoot) || !realPathContained(packRoot, resolved)) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "refusing to write outside pack " + directory.getFileName());
        }
        @Nullable Path temp = null;
        try {
            Path parent = Objects.requireNonNull(resolved.getParent(), "a file in the pack has a parent");
            Files.createDirectories(parent);
            temp = Files.createTempFile(parent, ".pack-write-", ".tmp");
            Files.write(temp, content);
            rename.move(temp, resolved);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "cannot write " + packRoot.relativize(resolved) + " in pack", e);
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Whether the target's real location, following symlinks, is inside the pack's real directory. */
    static boolean realPathContained(Path packRoot, Path target) {
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
