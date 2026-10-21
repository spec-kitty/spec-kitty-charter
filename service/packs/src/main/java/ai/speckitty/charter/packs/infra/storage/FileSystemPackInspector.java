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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a pack directory as facts, in one of two scopes. It decides nothing: {@code PackPolicy} judges what it finds.
 *
 * <p>{@link Scope#LOAD_PATH} reads {@code pack.yaml}, the root {@code *.graph.yaml} shards (sorted by file name) and
 * {@code drg/fragment.yaml}, the places core's runtime reads, each as {@link FileRead.Absent},
 * {@link FileRead.Unreadable} or {@link FileRead.Read}.
 *
 * <p>{@link Scope#FULL} reads those the same way and then every other {@code *.yaml} file of the pack, in path order:
 * each is classified by {@link PackFileKind} (mapped to a schema, or excluded for a reviewed reason; a file no rule
 * covers is left out of the classification), and each mapped file is parsed and validated through
 * {@link PackSchemas}. The step ids, artifact ids and asset declarations of the files that passed their schema are the
 * facts the validate-only rules judge; a file that did not is {@link PackContents#unreadableFiles()}.
 *
 * <p>Every call reads the files again, every file through {@link ParsedYaml} and never through {@link Yaml} directly,
 * so that a document nested too deeply or holding a scalar no validator can judge is a parse failure. A file whose
 * bytes are not UTF-8 is a parse failure too, and the code of its finding follows its kind like every other. The
 * content of a file never throws.
 */
public final class FileSystemPackInspector implements PackInspector {

    private static final String PACK_YAML = "pack.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";
    private static final String NOT_UTF8 = "the file is not valid UTF-8";

    /** The fixed length of a chain of symlinks that is followed; as long as the kernel's, which refuses past 40. */
    private static final int MAX_LINKS = 40;

    /**
     * The schema kinds a {@code files} rule of {@code file-kinds.yaml} maps whose schema declares an {@code id} (an
     * agent profile declares {@code profile-id}, so it is not one). Derived from the table and the schemas, so a kind
     * added to either is one here too, and built on first use: it needs the schemas, which are built lazily.
     */
    private static final class ArtifactKinds {
        static final Set<String> KINDS = derive();

        private static Set<String> derive() {
            PackSchemas schemas = PackSchemas.bundled();
            return PackFileKind.bundled().fileRules().stream()
                    .map(PackFileKind.FileRule::schema)
                    .distinct()
                    .filter(kind -> schemas.declaredProperties(kind).contains("id"))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /** The artifact schema kinds, for the tests. */
    static Set<String> artifactKinds() {
        return ArtifactKinds.KINDS;
    }

    /** An inspector; it keeps no state. */
    public FileSystemPackInspector() {}

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
        FileRead<PackDescriptor> descriptor = new FileRead.Absent<>(PACK_YAML);
        Path packYaml = directory.resolve(PACK_YAML);
        if (Files.isRegularFile(packYaml)) {
            descriptor = read(packYaml, PACK_YAML, name, PackDescriptorReader::read);
        }
        List<FileRead<RawGraph>> shards = new ArrayList<>();
        for (Path shard : shards(directory, name)) {
            String file = directory.relativize(shard).toString();
            shards.add(read(
                    shard, file, name, text -> GraphDocumentReader.read(text, GraphDocumentReader.Shape.SHARD, file)));
        }
        FileRead<RawGraph> fragment = new FileRead.Absent<>(FRAGMENT);
        Path fragmentYaml = directory.resolve("drg").resolve("fragment.yaml");
        if (Files.isRegularFile(fragmentYaml)) {
            fragment = read(
                    fragmentYaml,
                    FRAGMENT,
                    name,
                    text -> GraphDocumentReader.read(text, GraphDocumentReader.Shape.FRAGMENT, FRAGMENT));
        }
        if (scope == Scope.LOAD_PATH) {
            return new PackContents(
                    root, descriptor, shards, fragment, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        FullRead full = new FullRead(
                directory, name, shards.stream().map(FileRead::file).toList());
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

        FullRead(Path directory, String name, List<String> shards) {
            this.directory = directory;
            this.name = name;
            loadPath.add(PACK_YAML);
            loadPath.add(FRAGMENT);
            loadPath.addAll(shards);
        }

        void readAll() {
            // The classification globs are relative to the directory that holds the pack directories.
            Path last = directory.toAbsolutePath().normalize().getFileName();
            String prefix = (last == null ? "pack" : last.toString()) + "/";
            for (Path path : yamlFiles(directory, name)) {
                String file = slashed(directory.relativize(path));
                switch (PackFileKind.bundled().classify(prefix + file)) {
                    case PackFileKind.Excluded excluded ->
                        files.add(new ClassifiedFile(file, Optional.empty(), Optional.of(excluded.exclusionClass())));
                    case PackFileKind.Mapped mapped -> {
                        files.add(new ClassifiedFile(file, Optional.of(mapped.kind()), Optional.empty()));
                        if (!loadPath.contains(file)) {
                            readMapped(path, file, mapped.kind());
                        }
                    }
                    case PackFileKind.Unclassified unclassified -> {}
                }
            }
        }

        private void readMapped(Path path, String file, String kind) {
            FileRead<Object> read = read(path, file, name, text -> parseAndValidate(text, file, kind));
            switch (read) {
                case FileRead.Unreadable<Object> bad -> unreadable.add(bad);
                case FileRead.Read<Object> good -> factsOf(file, kind, good.value());
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
        // An empty document passes no schema that asks for an object, so a document that passed is a value.
        return new FileRead.Read<>(file, Objects.requireNonNullElse(document, Map.of()));
    }

    /**
     * Every {@code *.yaml} regular file under the pack directory, in the order of Python's {@code PurePath}: segment by
     * segment, a directory before a sibling that extends its name. A symlinked directory is not entered.
     */
    private static List<Path> yamlFiles(Path directory, String packName) {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".yaml") && Files.isRegularFile(p))
                    .sorted(Comparator.comparing(
                            (Path p) -> slashed(directory.relativize(p)), FileSystemPackInspector::comparePaths))
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            throw unlistable(packName, e);
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
        String text;
        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new FileRead.Unreadable<>(file, new ParseFailure(file, NOT_UTF8), List.of());
        }
        return reader.apply(text);
    }

    private static List<Path> shards(Path directory, String name) {
        try (Stream<Path> files = Files.list(directory)) {
            return graphShards(files, name, Files::isRegularFile);
        } catch (IOException e) {
            throw unlistable(name, e);
        }
    }

    /**
     * The graph shards of a pack directory listing: its {@code *.graph.yaml} regular files, sorted by name.
     *
     * @param files the directory listing; reading it may fail with an {@link UncheckedIOException}
     * @param packName the pack the listing belongs to, named in a refusal
     * @param regularFile whether a listed path is a regular file
     * @return the shards, sorted
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when reading the listing fails, as when opening it fails
     */
    static List<Path> graphShards(Stream<Path> files, String packName, Predicate<Path> regularFile) {
        try {
            return files.filter(p -> p.getFileName().toString().endsWith(".graph.yaml") && regularFile.test(p))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException e) {
            // The JDK always sets the cause; the fallback only satisfies the nullness checker.
            throw unlistable(packName, Objects.<Exception>requireNonNullElse(e.getCause(), e));
        }
    }

    static CharterRefusal unlistable(String packName, Exception cause) {
        return new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + packName + " cannot be listed", cause);
    }
}
