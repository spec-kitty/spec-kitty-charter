package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The single declaration of which files of a pack the service loads and which it digests. The store reads the files
 * of each kind bound in {@link KindBindings}, the asset files and the templates; the source fingerprint digests
 * {@code pack.yaml}, every file of every bound kind, each followed by the companions it names (ADR-0039), the declared
 * graph inputs (the root {@code *.graph.yaml} shards and {@code drg/fragment.yaml}), then the asset files, then the
 * templates. Both take their list from here, so a file the service loads cannot escape the fingerprint. Binding a kind
 * is all it takes for the digest to cover it. The asset files and templates come last, so a pack that holds none keeps
 * its exact digest list.
 *
 * <p>A pack file's kind is the {@link PackFileKind} classification of {@code <pack directory name>/<relative path>}:
 * a {@code files} rule of a bound kind makes a file of that kind; the {@code payloads} rule {@value #ASSET_PAYLOAD}
 * makes an asset file (under {@code assets/}) and the payload rule {@code template} a template (under the top-level
 * {@code templates/}), both of the {@code bytes} content form. The walk is the store's: {@link Files#walkFileTree}
 * (hidden directories are entered, a directory link is not followed, an entry that vanishes mid-walk is not listed),
 * every regular file ({@link Files#isRegularFile} follows a file link) classified once, in {@link String} order. Every
 * entry point normalizes the directory first, so a configured root such as {@code packs/foo/.} classifies like the
 * store's own normalized directory.
 */
final class PackFileSet {

    /** The pack descriptor's file name. */
    static final String PACK_YAML = "pack.yaml";

    /** The DRG fragment's pack-relative path. */
    static final String FRAGMENT = "drg/fragment.yaml";

    /** Reads an artifact's file to name its companions; see {@link #companions}. */
    private static final SerializationStrategy COMPANION_READER = new YamlSerializationStrategy();

    /** The directory that holds a pack's asset sidecars and asset files, with its trailing slash. */
    static final String ASSETS = "assets/";

    /** The pack's top-level template directory, with its trailing slash. */
    static final String TEMPLATES = "templates/";

    /** The {@code payloads} rule kind of an asset file. */
    static final String ASSET_PAYLOAD = "asset-payload";

    /**
     * One walk of a pack: the files of every bound kind, the asset files and the templates, each list in
     * {@link String} order of the pack-relative, {@code /}-separated path.
     *
     * @param byKind the files of each bound kind; a kind with none may be absent
     * @param assetFiles the asset files, pack-relative (they start with {@code assets/})
     * @param templates the templates, pack-relative (they start with {@code templates/})
     */
    record Listing(Map<ArtifactKind, List<String>> byKind, List<String> assetFiles, List<String> templates) {
        /**
         * Copies the collections.
         *
         * @param byKind the files of each bound kind
         * @param assetFiles the asset files
         * @param templates the templates
         */
        Listing {
            Map<ArtifactKind, List<String>> copy = new EnumMap<>(ArtifactKind.class);
            byKind.forEach((kind, files) -> copy.put(kind, List.copyOf(files)));
            byKind = Collections.unmodifiableMap(copy);
            assetFiles = List.copyOf(assetFiles);
            templates = List.copyOf(templates);
        }

        /**
         * The files of one kind.
         *
         * @param kind the kind
         * @return the pack-relative paths, in {@link String} order
         */
        List<String> files(ArtifactKind kind) {
            return byKind.getOrDefault(kind, List.of());
        }
    }

    private PackFileSet() {}

    /**
     * Walks a pack once.
     *
     * @param packDir the pack directory
     * @return the files of every bound kind, the asset files and the templates
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when the directory cannot
     *     be listed
     */
    static Listing listing(Path packDir) {
        Path directory = normalized(packDir);
        return loaded(directory, nameOf(directory));
    }

    /**
     * The files of one kind in a pack.
     *
     * @param packDir the pack directory
     * @param kind the kind
     * @return the pack-relative, {@code /}-separated paths, in {@link String} order
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when the directory cannot
     *     be listed
     */
    static List<String> files(Path packDir, ArtifactKind kind) {
        return listing(packDir).files(kind);
    }

    /**
     * The asset files of a pack: every file the {@value #ASSET_PAYLOAD} payload rule classifies, whether or not a
     * sidecar names it. A {@code README.md} under {@code assets/} is excluded by the table and is not one.
     *
     * @param packDir the pack directory
     * @return the pack-relative paths (each starts with {@code assets/}), in {@link String} order
     */
    static List<String> assetFiles(Path packDir) {
        return listing(packDir).assetFiles();
    }

    /**
     * The templates of a pack: every file under its top-level {@code templates/} that the {@code template} payload rule
     * classifies. Mission templates ({@code missions/<m>/templates/}) are not among them.
     *
     * @param packDir the pack directory
     * @return the pack-relative paths (each starts with {@code templates/}), in {@link String} order
     */
    static List<String> templates(Path packDir) {
        return listing(packDir).templates();
    }

    /**
     * Everything the fingerprint digests for a pack: {@code pack.yaml}, the files of every bound kind, the
     * root graph shards, {@code drg/fragment.yaml}, the asset files and the templates.
     *
     * @param packDir the pack directory
     * @return the pack-relative paths, in digest order
     */
    static List<String> digested(Path packDir) {
        return digested(packDir, Set.copyOf(KindBindings.kinds()));
    }

    /**
     * The digested files when only {@code kinds} are loaded: the seam a test uses to plant an omission.
     *
     * @param packDir the pack directory
     * @param kinds the loaded kinds to include
     * @return the pack-relative paths: {@code pack.yaml} when a regular file; the files of each kind in declaration
     *     order; the root {@code *.graph.yaml} regular files by name; {@code drg/fragment.yaml} when a regular file;
     *     the asset files; the templates. The asset files and templates are not a bound kind and are always included.
     */
    static List<String> digested(Path packDir, Set<ArtifactKind> kinds) {
        return digested(packDir, kinds, nameOf(normalized(packDir)));
    }

    /**
     * Everything the fingerprint digests for a configured pack, a refusal to list it naming {@code packName}.
     *
     * @param packDir the pack directory
     * @param packName the configured pack's name, which may differ from the directory's name
     * @return the pack-relative paths, in digest order
     */
    static List<String> digested(Path packDir, String packName) {
        return digested(packDir, Set.copyOf(KindBindings.kinds()), packName);
    }

    private static List<String> digested(Path packDir, Set<ArtifactKind> kinds, String packName) {
        Path directory = normalized(packDir);
        List<String> out = new ArrayList<>();
        if (Files.isRegularFile(directory.resolve(PACK_YAML))) {
            out.add(PACK_YAML);
        }
        Listing loaded = loaded(directory, packName);
        Set<String> listed = new HashSet<>(out);
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            if (!kinds.contains(binding.kind())) {
                continue;
            }
            for (String file : loaded.files(binding.kind())) {
                out.add(file);
                listed.add(file);
                if (binding.hasCompanions()) {
                    for (String companion : companions(directory, file, binding)) {
                        if (listed.add(companion)) {
                            out.add(companion);
                        }
                    }
                }
            }
        }
        for (Path shard : rootShards(directory, packName)) {
            out.add(directory.relativize(shard).toString());
        }
        if (Files.isRegularFile(directory.resolve(FRAGMENT))) {
            out.add(FRAGMENT);
        }
        // Appended after every YAML input, so a pack without asset files or templates keeps its digest (NFR-002).
        // A file already listed as a companion is listed once (ADR-0039 decision 7).
        for (String payload : loaded.assetFiles()) {
            if (listed.add(payload)) {
                out.add(payload);
            }
        }
        for (String template : loaded.templates()) {
            if (listed.add(template)) {
                out.add(template);
            }
        }
        return out;
    }

    /**
     * The companions an artifact's file names that the companion rule allows and that read as text (ADR-0039), pack
     * relative. The names come from the binding itself ({@link KindBinding#companions}) on the store's own
     * schema-valid read of the file, so the digest and the load agree on them. The read is static, as the
     * classification and the store's id lookup already parse YAML statically: YAML is the store's one format, and the
     * digest's callers need no strategy. A file that does not read names none; the load reports it.
     */
    private static <D> List<String> companions(Path directory, String file, KindBinding<D, ?> binding) {
        Optional<String> text = FilesystemPackStore.readText(directory.resolve(file));
        if (text.isEmpty() || !(COMPANION_READER.read(binding, text.get(), file) instanceof FileRead.Read<D> read)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (KindBinding.Companion companion : binding.companions(read.value())) {
            if (Companions.read(directory, file, companion.path()) instanceof Companions.Found found) {
                out.add(found.at().packRelative());
            }
        }
        return out;
    }

    /**
     * The graph shards in a pack's root directory: its {@code *.graph.yaml} regular files, sorted by the file name as a
     * {@link String}, which is the same order on every OS.
     *
     * @param directory the pack directory
     * @param packName the pack, named in a refusal
     * @return the shard paths
     */
    static List<Path> rootShards(Path directory, String packName) {
        try (Stream<Path> files = Files.list(directory)) {
            return graphShards(files, packName, Files::isRegularFile);
        } catch (IOException e) {
            throw unlistable(packName, e);
        }
    }

    /** One walk of the pack, each file classified once: every bound kind's files grouped, the payload files apart. */
    private static Listing loaded(Path directory, String packName) {
        // The classification globs are relative to the directory that holds the pack directories.
        String prefix = nameOf(directory) + "/";
        Map<ArtifactKind, List<String>> byKind = new EnumMap<>(ArtifactKind.class);
        List<String> assetFiles = new ArrayList<>();
        List<String> templates = new ArrayList<>();
        for (String file : regularFiles(directory, packName)) {
            if (!(PackFileKind.bundled().classify(prefix + file) instanceof PackFileKind.Mapped mapped)) {
                continue;
            }
            if (mapped.schema().isEmpty()) {
                if (ASSET_PAYLOAD.equals(mapped.kind())) {
                    assetFiles.add(file);
                } else if (ArtifactKind.TEMPLATE.token().equals(mapped.kind())) {
                    templates.add(file);
                }
                continue;
            }
            KindBindings.bySchema(mapped.kind())
                    .ifPresent(binding -> byKind.computeIfAbsent(binding.kind(), k -> new ArrayList<>())
                            .add(file));
        }
        return new Listing(byKind, assetFiles, templates);
    }

    /**
     * The pack-relative, {@code /}-separated paths of every regular file, in {@link String} order. An
     * entry that vanishes while the tree is walked (the temp file of an atomic save, an editor's scratch file) is not
     * listed; any other failure to list refuses.
     *
     * <p>Validate mode walks the pack too ({@code FileSystemPackInspector.yamlFiles}); it orders by path segment like
     * core's pathlib (#115), where this walk orders by {@link String}, the order the store has always loaded in; the two
     * differ on purpose.
     */
    private static List<String> regularFiles(Path directory, String packName) {
        List<String> found = new ArrayList<>();
        try {
            Files.walkFileTree(directory, new FileCollector(directory, found));
        } catch (IOException | UncheckedIOException e) {
            throw unlistable(packName, e);
        }
        Collections.sort(found);
        return found;
    }

    /** Collects the regular files; an entry that is gone by the time it is visited is skipped. */
    private static final class FileCollector extends SimpleFileVisitor<Path> {
        private final Path root;
        private final List<String> found;

        FileCollector(Path root, List<String> found) {
            this.root = root;
            this.found = found;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            // isRegularFile follows a file link and is false for an entry that has just vanished.
            if (Files.isRegularFile(file)) {
                found.add(root.relativize(file)
                        .toString()
                        .replace(file.getFileSystem().getSeparator(), "/"));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
            if (failure instanceof NoSuchFileException && !file.equals(root)) {
                return FileVisitResult.CONTINUE;
            }
            throw failure;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException failure) throws IOException {
            if (failure instanceof NoSuchFileException && !dir.equals(root)) {
                return FileVisitResult.CONTINUE;
            }
            if (failure != null) {
                throw failure;
            }
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * The graph shards of a pack directory listing: its {@code *.graph.yaml} regular files, sorted by the file name as a
     * {@link String}.
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
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()))
                    .toList();
        } catch (UncheckedIOException e) {
            // The JDK always sets the cause; the fallback only satisfies the nullness checker.
            throw unlistable(packName, Objects.<Exception>requireNonNullElse(e.getCause(), e));
        }
    }

    static CharterRefusal unlistable(String packName, Exception cause) {
        return new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + packName + " cannot be listed", cause);
    }

    private static Path normalized(Path packDir) {
        return packDir.toAbsolutePath().normalize();
    }

    private static String nameOf(Path directory) {
        return String.valueOf(directory.getFileName());
    }
}
