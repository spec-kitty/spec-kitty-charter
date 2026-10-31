package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.AssetFacts;
import ai.speckitty.charter.packs.domain.PackContents.ClassifiedFile;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackContents.StepContractFacts;
import ai.speckitty.charter.packs.domain.PackInspector;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Reads a pack directory as facts, in one of two scopes. It decides nothing: {@code PackPolicy} judges what it finds.
 *
 * <p>{@link Scope#LOAD_PATH} reads {@code pack.yaml}, the root {@code *.graph.yaml} shards (sorted by file name) and
 * {@code drg/fragment.yaml}, the places core's runtime reads, each as {@link FileRead.Absent},
 * {@link FileRead.Unreadable} or {@link FileRead.Read}.
 *
 * <p>{@link Scope#FULL} reads those the same way and then every other {@code *.yaml} file of the pack, in path order:
 * each is classified by {@link PackFileKind} (mapped to a schema, or excluded for a reviewed reason; a file no rule
 * covers is left out of the classification), and each mapped file that has a schema is parsed and validated through
 * {@link PackSchemas}; a schema-less payload (a {@code payloads} rule's kind, such as an asset payload that happens to
 * be YAML) is listed with its kind and not read. The step ids, artifact ids and asset declarations of the files that passed their schema are the
 * facts the validate-only rules judge; a file that did not is {@link PackContents#unreadableFiles()}.
 *
 * <p>{@link Scope#FULL} also reads every {@code *.md} file that a {@code files} rule maps to a bound kind's Markdown
 * companion structure (a toolguide's guide, #194), as the store's load does: parsed with {@link MarkdownCodec},
 * validated against the structure schema, then held to the rules the schema cannot express
 * ({@link KindBinding#checkCompanion}). A guide that breaks one is {@link PackContents#unreadableFiles()}, so validate
 * mode and the load refuse the same guide. It reads the pack document ({@code pack.md}, #260) the same way, through
 * the store's own read ({@link PackDocumentMarkdown#read}). Other Markdown files are not read, nor listed in the
 * classification.
 *
 * <p>{@link Scope#FULL} reports every retired file (ADR-0044 decision 6), YAML and Markdown alike, as
 * {@link PackContents#unreadableFiles()} naming its rule's reason, from the store's own listing
 * ({@link PackFileSet.Listing#retired()}), so validate mode and the load refuse the same files by name.
 *
 * <p>Every call reads the files again, every file through {@link ParsedYaml} and never through {@link Yaml} directly,
 * so that a document nested too deeply or holding a scalar no validator can judge is a parse failure. A file whose
 * bytes are not UTF-8 is a parse failure too, and the code of its finding follows its kind like every other. The
 * content of a file never throws.
 */
public final class FileSystemPackInspector implements PackInspector {

    private static final String RESERVED_SHARD = RelationshipShard.SHARD_PATH
            + " is reserved for registered relationships and cannot be a source graph shard; rename the shard";

    private static final String MARKDOWN = ".md";

    /** The fixed length of a chain of symlinks that is followed; as long as the kernel's, which refuses past 40. */
    private static final int MAX_LINKS = 40;

    /**
     * The schema kinds a {@code files} rule of {@code file-kinds.yaml} maps whose schema declares an {@code id} (an
     * agent profile declares {@code profile-id}, so it is not one), less the pack-level {@code preset}: a preset
     * declares an id but is no artifact (#195, ADR-0042). Derived from the table and the schemas, so a kind added to
     * either is one here too, and built on first use: it needs the schemas, which are built lazily.
     */
    private static final class ArtifactKinds {
        /** File kinds with an {@code id} that are pack-level files, not artifacts; declared first, as derive reads it. */
        private static final Set<String> PACK_LEVEL = Set.of(PresetYaml.SCHEMA);

        static final Set<String> KINDS = derive();

        private static Set<String> derive() {
            PackSchemas schemas = PackSchemas.bundled();
            return PackFileKind.bundled().fileRules().stream()
                    .map(PackFileKind.FileRule::schema)
                    .distinct()
                    .filter(kind -> !PACK_LEVEL.contains(kind))
                    .filter(kind -> schemas.declaredProperties(kind).contains("id"))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /** The artifact schema kinds, for the tests. */
    static Set<String> artifactKinds() {
        return ArtifactKinds.KINDS;
    }

    private final PackFileKind table;

    /** An inspector over the bundled file-kinds table; it keeps no state. */
    public FileSystemPackInspector() {
        this(PackFileKind.bundled());
    }

    /**
     * An inspector that classifies with {@code table} in place of the bundled one: the seam a test uses to classify
     * with rules of its own, retired ones included (#194).
     *
     * @param table the file-kinds table
     */
    FileSystemPackInspector(PackFileKind table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    /**
     * Reads a pack directory.
     *
     * @param root the pack to read; its {@code locator} is the directory
     * @param scope how much of it to read
     * @return the facts of {@code pack.yaml}, the shards and the fragment, and for {@link Scope#FULL} the
     *     classification of every YAML file with the facts of the validate-only rules
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the directory does not exist, cannot be listed or a
     *     file cannot be read; the content of a file never throws
     */
    @Override
    public PackContents inspect(PackRoot root, Scope scope) {
        Path directory = Path.of(root.locator());
        String name = root.name();
        if (!Files.isDirectory(directory)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + name + " is not a directory");
        }
        FileRead<PackDescriptor> descriptor = new FileRead.Absent<>(PackFileSet.PACK_YAML);
        Path packYaml = directory.resolve(PackFileSet.PACK_YAML);
        if (Files.isRegularFile(packYaml)) {
            descriptor = read(packYaml, PackFileSet.PACK_YAML, name, PackDescriptorReader::read);
        }
        List<FileRead<RawGraph>> shards = new ArrayList<>();
        for (Path shard : shards(directory, name)) {
            String file = directory.relativize(shard).toString();
            shards.add(read(shard, file, name, text -> shard(text, file)));
        }
        FileRead<RawGraph> fragment = new FileRead.Absent<>(PackFileSet.FRAGMENT);
        Path fragmentYaml = directory.resolve("drg").resolve("fragment.yaml");
        if (Files.isRegularFile(fragmentYaml)) {
            fragment = read(
                    fragmentYaml,
                    PackFileSet.FRAGMENT,
                    name,
                    text -> GraphDocumentReader.read(text, GraphDocumentReader.Shape.FRAGMENT, PackFileSet.FRAGMENT));
        }
        if (scope == Scope.LOAD_PATH) {
            return new PackContents(
                    root, descriptor, shards, fragment, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        FullRead full = new FullRead(
                directory, name, shards.stream().map(FileRead::file).toList(), table);
        full.readAll();
        return new PackContents(
                root,
                descriptor,
                shards,
                fragment,
                full.files,
                full.stepContracts,
                full.artifacts,
                full.assets,
                full.unreadable);
    }

    // ---- the full scope ------------------------------------------------------------------------------------------

    /**
     * Whether validate mode reads a mapped file as YAML: a {@code yaml} file with a schema. A payload (a
     * {@code bytes} file, no schema) has nothing to validate: it is listed, not read. A {@code markdown} or
     * {@code text} file is refused loudly, never read as YAML. (This walk lists {@code *.yaml} files only; a Markdown
     * companion is read by its own walk, as Markdown.)
     *
     * @param mapped the file's classification
     * @param file the file, pack-relative, named in the failure
     * @return whether the file is read and validated as YAML
     * @throws IllegalStateException for a {@code markdown} file
     */
    static boolean readsAsYaml(PackFileKind.Mapped mapped, String file) {
        return switch (mapped.contentForm()) {
            case YAML -> mapped.schema().isPresent();
            case BYTES -> false;
            case MARKDOWN, TEXT -> throw PackFileSet.notStored(file, mapped.contentForm());
        };
    }

    /** The state of one full read: every YAML file of the pack, in path order, and what they yielded. */
    private static final class FullRead {
        private final Path directory;
        private final String name;
        private final Set<String> loadPath = new HashSet<>();
        final List<ClassifiedFile> files = new ArrayList<>();
        final List<FileRead.Unreadable<?>> unreadable = new ArrayList<>();
        final List<StepContractFacts> stepContracts = new ArrayList<>();
        final List<ArtifactFacts> artifacts = new ArrayList<>();
        final List<AssetFacts> assets = new ArrayList<>();
        private final Set<String> assetIds = new HashSet<>();
        private final PackFileKind table;

        FullRead(Path directory, String name, List<String> shards, PackFileKind table) {
            this.directory = directory;
            this.name = name;
            this.table = table;
            loadPath.add(PackFileSet.PACK_YAML);
            loadPath.add(PackFileSet.FRAGMENT);
            loadPath.addAll(shards);
        }

        void readAll() {
            // The classification globs are relative to the directory that holds the pack directories.
            Path last = directory.toAbsolutePath().normalize().getFileName();
            String prefix = (last == null ? "pack" : last.toString()) + "/";
            for (Path path : yamlFiles(directory, name)) {
                String file = slashed(directory.relativize(path));
                switch (table.classify(prefix + file)) {
                    case PackFileKind.Excluded excluded ->
                        files.add(new ClassifiedFile(file, Optional.empty(), Optional.of(excluded.exclusionClass())));
                    case PackFileKind.Mapped mapped -> {
                        files.add(new ClassifiedFile(file, Optional.of(mapped.kind()), Optional.empty()));
                        if (readsAsYaml(mapped, file) && !loadPath.contains(file)) {
                            readMapped(path, file, mapped.kind());
                        }
                    }
                    // Reported below from the store's listing, with the retired Markdown files.
                    case PackFileKind.Retired retired -> {}
                    case PackFileKind.Unclassified unclassified -> {}
                }
            }
            for (Path path : packFiles(directory, name, MARKDOWN)) {
                String file = slashed(directory.relativize(path));
                if (!(table.classify(prefix + file) instanceof PackFileKind.Mapped mapped)
                        || mapped.contentForm() != ContentForm.MARKDOWN) {
                    continue;
                }
                if (KindBindings.isCompanionStructure(mapped.kind())) {
                    readCompanion(path, file, mapped.kind());
                } else if (PackDocumentMarkdown.KIND.equals(mapped.kind())) {
                    readDocument(path, file);
                } else {
                    // A future Markdown kind must fail loud in validate mode too, as the store's
                    // listing walk does (PackFileSet#loaded) — never silently skipped. Unreachable
                    // with today's markdown rules; keeps load and validate symmetric by construction.
                    throw PackFileSet.notStored(file, mapped.contentForm());
                }
            }
            PackFileSet.listing(directory, table)
                    .retired()
                    .forEach((file, reason) -> unreadable.add(
                            new FileRead.Unreadable<>(file, ParseFailure.retired(file, reason), List.of())));
        }

        /** The pack document (#260), read as the store's load reads it, so the two refuse the same {@code pack.md}. */
        private void readDocument(Path path, String file) {
            if (read(path, file, name, PackDocumentMarkdown::read) instanceof FileRead.Unreadable<?> bad) {
                unreadable.add(bad);
            }
        }

        private void readCompanion(Path path, String file, String structure) {
            if (read(path, file, name, text -> companion(text, file, structure))
                    instanceof FileRead.Unreadable<?> bad) {
                unreadable.add(bad);
            }
        }

        private void readMapped(Path path, String file, String kind) {
            FileRead<Object> read = read(path, file, name, text -> parseAndValidate(text, file, kind));
            switch (read) {
                case FileRead.Unreadable<Object> bad -> unreadable.add(bad);
                case FileRead.Read<Object> good -> {
                    factsOf(file, kind, good.value());
                    MissionTypeChecks.refusal(directory, file, kind, good.value(), table)
                            .ifPresent(refusal -> unreadable.add(
                                    new FileRead.Unreadable<>(file, ParseFailure.refused(file, refusal), List.of())));
                }
                case FileRead.Absent<Object> absent -> {}
            }
        }

        private void factsOf(String file, String kind, Object document) {
            if (!ArtifactKinds.KINDS.contains(kind) || !(document instanceof Map<?, ?> map)) {
                return;
            }
            if ("mission-step-contract".equals(kind)) {
                stepContracts.add(new StepContractFacts(file, stepIds(map)));
            }
            if (map.get("id") instanceof String id && !id.isEmpty()) {
                artifacts.add(new ArtifactFacts(file, kind.replace('-', '_'), id));
                if ("asset".equals(kind) && assetIds.add(id)) {
                    assets.add(assetFacts(file, id, map));
                }
            }
        }

        private AssetFacts assetFacts(String file, String id, Map<?, ?> manifest) {
            // The schema has required both as non-empty strings; the first owner of an id is the only one core checks.
            String path = String.valueOf(manifest.get("path"));
            String mime = String.valueOf(manifest.get("mime"));
            return new AssetFacts(file, id, path, mime, containedInAssetsRoot(directory.resolve("assets"), path));
        }

        private static List<String> stepIds(Map<?, ?> contract) {
            List<String> ids = new ArrayList<>();
            if (contract.get("steps") instanceof List<?> steps) {
                for (Object step : steps) {
                    if (step instanceof Map<?, ?> s && s.get("id") instanceof String id) {
                        ids.add(id);
                    }
                }
            }
            return ids;
        }
    }

    /**
     * Reads a Markdown companion as the store's load does: parsed, validated against its structure schema, then held
     * to its binding's own rules. A Markdown refusal and a broken rule are parse failures naming the line or the rule;
     * a structure breach is a schema violation.
     */
    private static FileRead<Object> companion(String text, String file, String structure) {
        return switch (MarkdownCodec.parse(text)) {
            case MarkdownRefusal refusal ->
                new FileRead.Unreadable<>(file, new ParseFailure(file, refusal.message()), List.of());
            case MarkdownCodec.Parsed parsed -> {
                List<SchemaViolation> violations =
                        PackSchemas.bundled().validateMarkdown(structure, file, parsed.document());
                if (!violations.isEmpty()) {
                    yield new FileRead.Unreadable<>(file, null, violations);
                }
                try {
                    KindBindings.byCompanionStructure(structure)
                            .orElseThrow()
                            .checkCompanion(structure, parsed.document());
                } catch (IllegalArgumentException broken) {
                    yield new FileRead.Unreadable<>(
                            file, new ParseFailure(file, String.valueOf(broken.getMessage())), List.of());
                }
                yield new FileRead.Read<>(file, parsed.document());
            }
        };
    }

    /** Parses and validates a mapped file that is not read for the load path. */
    private static FileRead<Object> parseAndValidate(String text, String file, String kind) {
        if ("graph-shard".equals(kind)) {
            // The graph reader adds the sibling facts the detail vocabulary needs; the graph itself is not kept.
            return switch (GraphDocumentReader.read(text, GraphDocumentReader.Shape.SHARD, file)) {
                case FileRead.Unreadable<RawGraph> bad ->
                    new FileRead.Unreadable<>(file, bad.parse(), bad.violations());
                case FileRead.Read<RawGraph> good -> new FileRead.Read<>(file, good.value());
                case FileRead.Absent<RawGraph> absent -> new FileRead.Absent<>(file);
            };
        }
        ParsedYaml parsed = ParsedYaml.parse(text, file);
        if (parsed.failure() != null) {
            return new FileRead.Unreadable<>(file, parsed.failure(), List.of());
        }
        Object document = parsed.document();
        List<SchemaViolation> violations = PackSchemas.bundled().validate(kind, file, document);
        if (!violations.isEmpty()) {
            return new FileRead.Unreadable<>(file, null, violations);
        }
        if (PresetYaml.SCHEMA.equals(kind)
                && document instanceof Map<?, ?> preset
                && preset.get("id") instanceof String id
                && PresetFiles.stemMismatch(file, id).isPresent()) {
            // The store's stem rule, checked here too so validate mode and the load refuse the same files (ADR-0042):
            // the id must equal one value, the file stem, so it reads as a const violation at /id.
            return new FileRead.Unreadable<>(
                    file,
                    null,
                    List.of(new SchemaViolation(
                            file, List.of("id"), "const", Optional.empty(), Optional.of(id), Map.of())));
        }
        Optional<SchemaViolation> misnamed = misnamedArtifact(file, kind, document);
        if (misnamed.isPresent()) {
            return new FileRead.Unreadable<>(file, null, List.of(misnamed.get()));
        }
        if (PackManifestYaml.SCHEMA.equals(kind) && document instanceof Map<?, ?> manifest) {
            List<SchemaViolation> misplaced = misplacedPresetEntries(file, manifest);
            if (!misplaced.isEmpty()) {
                return new FileRead.Unreadable<>(file, null, misplaced);
            }
        }
        // An empty document passes no schema that asks for an object, so a document that passed is a value.
        return new FileRead.Read<>(file, Objects.requireNonNullElse(document, Map.of()));
    }

    /**
     * The store's stem rule for an artifact whose binding declares it ({@link FileStems}, a procedure), checked here
     * too so validate mode and the load refuse the same files: the id must equal one value, the file stem, so it reads
     * as a const violation at the id.
     */
    private static Optional<SchemaViolation> misnamedArtifact(String file, String kind, @Nullable Object document) {
        Optional<KindBinding<?, ?>> binding = KindBindings.bySchema(kind);
        if (binding.isEmpty()
                || !(document instanceof Map<?, ?> artifact)
                || !(artifact.get(binding.get().idKey()) instanceof String id)
                || FileStems.mismatch(binding.get(), file, id).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SchemaViolation(
                file, List.of(binding.get().idKey()), "const", Optional.empty(), Optional.of(id), Map.of()));
    }

    /**
     * The store's rule that a manifest's preset entry names {@code presets/<id>.yaml} ({@link
     * PresetFiles#entryPathMismatch}), checked here too so validate mode and the load refuse the same files: the path
     * must equal one value, so each miss reads as a const violation at {@code /presets/<i>/path}.
     */
    private static List<SchemaViolation> misplacedPresetEntries(String file, Map<?, ?> manifest) {
        List<SchemaViolation> out = new ArrayList<>();
        if (manifest.get("presets") instanceof List<?> entries) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i) instanceof Map<?, ?> entry
                        && entry.get("id") instanceof String id
                        && entry.get("path") instanceof String path
                        && PresetFiles.entryPathMismatch(id, path).isPresent()) {
                    out.add(new SchemaViolation(
                            file,
                            List.of("presets", String.valueOf(i), "path"),
                            "const",
                            Optional.empty(),
                            Optional.of(path),
                            Map.of()));
                }
            }
        }
        return out;
    }

    /**
     * Every {@code *.yaml} regular file under the pack directory, in the order of Python's {@code PurePath}: segment by
     * segment, a directory before a sibling that extends its name. A symlinked directory is not entered.
     *
     * <p>{@code PackFileSet}'s walk, which the store and the fingerprint share, orders by {@link String} instead and
     * skips vanished entries; this validate-mode walk mirrors core's pathlib order (#115).
     */
    private static List<Path> yamlFiles(Path directory, String packName) {
        return packFiles(directory, packName, ".yaml");
    }

    /**
     * Every regular file under the pack directory whose name ends in {@code suffix}, in {@link #yamlFiles}' order. The
     * walk starts at the directory's real path, so a pack root that is a symbolic link is entered (#284), and each file
     * is named under {@code directory} again; a directory link below the root is not entered.
     */
    private static List<Path> packFiles(Path directory, String packName, String suffix) {
        Path root;
        try {
            root = directory.toRealPath();
        } catch (IOException e) {
            throw PackFileSet.unlistable(packName, e);
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(suffix) && Files.isRegularFile(p))
                    .map(p -> directory.resolve(root.relativize(p)))
                    .sorted(Comparator.comparing(
                            (Path p) -> slashed(directory.relativize(p)), FileSystemPackInspector::comparePaths))
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            throw PackFileSet.unlistable(packName, e);
        }
    }

    private static String slashed(Path relative) {
        return relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
    }

    /** Segment by segment, each by code point: how pathlib orders paths, so a path is not sorted by its separators. */
    private static int comparePaths(String a, String b) {
        String[] left = a.split("/", -1);
        String[] right = b.split("/", -1);
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int order = Arrays.compare(
                    left[i].codePoints().toArray(), right[i].codePoints().toArray());
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    // ---- asset containment ---------------------------------------------------------------------------------------

    /**
     * Whether a declared asset path stays inside the pack's assets root, following symlinks, as core's
     * {@code resolve_relative_path_within_root} judges it from the resolved paths. The path is resolved the way
     * {@code Path.resolve(strict=False)} does: each existing component is resolved, a symlink is followed to where it
     * points (a dangling one too), and a component that does not exist is kept as written. A path that cannot be
     * resolved is an escape: a symlink loop, or more links than the kernel follows. A path with a NUL cannot be a name
     * in any directory: core raises {@code ValueError} for it, uncaught, so there is no verdict to match; here it is
     * not contained, which makes it an {@code asset_path_escape} finding and never an exception.
     *
     * <p>The absolute and {@code ..} forms, including the Windows ones, are judged by the domain from the raw path.
     *
     * @param assetsRoot the pack's {@code assets/} directory, which need not exist
     * @param declared the path as the manifest wrote it
     * @return {@code true} when the path resolves inside the resolved assets root
     */
    static boolean containedInAssetsRoot(Path assetsRoot, String declared) {
        try {
            Path resolvedRoot = resolve(assetsRoot);
            return resolve(assetsRoot.resolve(declared)).startsWith(resolvedRoot);
        } catch (IOException | InvalidPathException e) {
            return false;
        }
    }

    private static Path resolve(Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        Deque<String> pending = new ArrayDeque<>();
        absolute.forEach(part -> pending.addLast(part.toString()));
        Path current = Objects.requireNonNull(absolute.getRoot(), "an absolute path has a root");
        int links = 0;
        while (!pending.isEmpty()) {
            String part = pending.removeFirst();
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                Path parent = current.getParent();
                current = parent == null ? current : parent;
                continue;
            }
            Path next = current.resolve(part);
            if (!Files.isSymbolicLink(next)) {
                current = next;
                continue;
            }
            if (++links > MAX_LINKS) {
                throw new IOException("too many levels of symbolic links");
            }
            Path target = Files.readSymbolicLink(next);
            List<String> names = new ArrayList<>();
            target.forEach(name -> names.add(name.toString()));
            for (int i = names.size() - 1; i >= 0; i--) {
                pending.addFirst(names.get(i));
            }
            Path targetRoot = target.getRoot();
            if (targetRoot != null) {
                current = targetRoot;
            }
        }
        return current;
    }

    // ---- reading one file ----------------------------------------------------------------------------------------

    /**
     * Reads a file's bytes as UTF-8 and hands the text to {@code reader}. Bytes that are not UTF-8 are not YAML: the
     * file is {@link FileRead.Unreadable} with a parse failure, so its finding takes the code of its kind.
     */
    private static <T> FileRead<T> read(Path path, String file, String packName, Function<String, FileRead<T>> reader) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, packName + "/" + file + " cannot be read", e);
        }
        return FilesystemPackStore.decodeUtf8(bytes)
                .map(reader)
                .orElseGet(() -> new FileRead.Unreadable<>(
                        file, new ParseFailure(file, FilesystemPackStore.NOT_UTF8), List.of()));
    }

    /** A root shard; the name the registration store writes to is reserved, so a source shard under it is refused. */
    private static FileRead<RawGraph> shard(String text, String file) {
        if (file.equals(RelationshipShard.SHARD_PATH) && !RelationshipShard.isWrittenByRegistration(text)) {
            return new FileRead.Unreadable<>(file, new ParseFailure(file, RESERVED_SHARD), List.of());
        }
        return GraphDocumentReader.read(text, GraphDocumentReader.Shape.SHARD, file);
    }

    private static List<Path> shards(Path directory, String name) {
        return PackFileSet.rootShards(directory, name);
    }
}
