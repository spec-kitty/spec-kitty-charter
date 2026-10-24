package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The single declaration of which files of a pack the service loads and which it digests. The store reads the files
 * of each {@link LoadedKind}; the source fingerprint digests {@code pack.yaml}, every file of every {@link LoadedKind}
 * and the declared graph inputs (the root {@code *.graph.yaml} shards and {@code drg/fragment.yaml}). Both take their
 * list from here, so a file the service loads cannot escape the fingerprint. Adding a kind is adding a
 * {@link LoadedKind}, which the digest then covers.
 *
 * <p>A pack file's kind is the {@link PackFileKind} classification of {@code <pack directory name>/<relative path>}.
 * The walk is the store's: {@link Files#walk} (hidden directories are entered, a directory link is not followed),
 * regular files only ({@link Files#isRegularFile} follows a file link), names ending {@code .yaml}, in {@link String}
 * order. Every entry point normalizes the directory first, so a configured root such as {@code packs/foo/.}
 * classifies like the store's own normalized directory.
 */
final class PackFileSet {

    /** The pack descriptor's file name. */
    static final String PACK_YAML = "pack.yaml";

    private static final String FRAGMENT = "drg/fragment.yaml";

    /** The kinds of pack file the service loads, each spelled as in a file name and in {@code file-kinds.yaml}. */
    enum LoadedKind {
        /** {@code *.tactic.yaml}. */
        TACTIC("tactic"),
        /** {@code *.directive.yaml}. */
        DIRECTIVE("directive"),
        /** {@code *.styleguide.yaml}. */
        STYLEGUIDE("styleguide"),
        /** {@code *.paradigm.yaml}. */
        PARADIGM("paradigm");

        private final String token;

        LoadedKind(String token) {
            this.token = token;
        }

        /**
         * The kind's spelling in a file name ({@code <id>.<token>.yaml}) and as the schema kind.
         *
         * @return the token
         */
        String token() {
            return token;
        }

        private static Optional<LoadedKind> ofToken(String token) {
            for (LoadedKind kind : values()) {
                if (kind.token.equals(token)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    private PackFileSet() {}

    /**
     * The files of one kind in a pack.
     *
     * @param packDir the pack directory
     * @param kind the kind
     * @return the pack-relative, {@code /}-separated paths, in {@link String} order
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when the directory cannot
     *     be listed
     */
    static List<String> files(Path packDir, LoadedKind kind) {
        return loaded(normalized(packDir)).getOrDefault(kind, List.of());
    }

    /**
     * Everything the fingerprint digests for a pack: {@code pack.yaml}, the files of every {@link LoadedKind}, the
     * root graph shards and {@code drg/fragment.yaml}.
     *
     * @param packDir the pack directory
     * @return the pack-relative paths, in digest order
     */
    static List<String> digested(Path packDir) {
        return digested(packDir, EnumSet.allOf(LoadedKind.class));
    }

    /**
     * The digested files when only {@code kinds} are loaded: the seam a test uses to plant an omission.
     *
     * @param packDir the pack directory
     * @param kinds the loaded kinds to include
     * @return the pack-relative paths: {@code pack.yaml} when a regular file; the files of each kind in declaration
     *     order; the root {@code *.graph.yaml} regular files by name; {@code drg/fragment.yaml} when a regular file
     */
    static List<String> digested(Path packDir, Set<LoadedKind> kinds) {
        Path directory = normalized(packDir);
        List<String> out = new ArrayList<>();
        if (Files.isRegularFile(directory.resolve(PACK_YAML))) {
            out.add(PACK_YAML);
        }
        Map<LoadedKind, List<String>> loaded = loaded(directory);
        for (LoadedKind kind : LoadedKind.values()) {
            if (kinds.contains(kind)) {
                out.addAll(loaded.getOrDefault(kind, List.of()));
            }
        }
        for (Path shard : rootShards(directory, nameOf(directory))) {
            out.add(directory.relativize(shard).toString());
        }
        if (Files.isRegularFile(directory.resolve(FRAGMENT))) {
            out.add(FRAGMENT);
        }
        return out;
    }

    /**
     * The graph shards in a pack's root directory: its {@code *.graph.yaml} regular files, sorted by name.
     *
     * @param directory the pack directory
     * @param packName the pack, named in a refusal
     * @return the shard paths
     */
    static List<Path> rootShards(Path directory, String packName) {
        try (Stream<Path> files = Files.list(directory)) {
            return FileSystemPackInspector.graphShards(files, packName, Files::isRegularFile);
        } catch (IOException e) {
            throw FileSystemPackInspector.unlistable(packName, e);
        }
    }

    /** One walk of the pack, every loaded kind's files grouped. */
    private static Map<LoadedKind, List<String>> loaded(Path directory) {
        // The classification globs are relative to the directory that holds the pack directories.
        String prefix = nameOf(directory) + "/";
        Map<LoadedKind, List<String>> byKind = new EnumMap<>(LoadedKind.class);
        for (String file : yamlFiles(directory)) {
            if (PackFileKind.bundled().classify(prefix + file) instanceof PackFileKind.Mapped mapped) {
                LoadedKind.ofToken(mapped.kind())
                        .ifPresent(kind -> byKind.computeIfAbsent(kind, k -> new ArrayList<>())
                                .add(file));
            }
        }
        return byKind;
    }

    /** The pack-relative, {@code /}-separated paths of every {@code *.yaml} regular file, in {@link String} order. */
    private static List<String> yamlFiles(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".yaml") && Files.isRegularFile(p))
                    .map(p -> directory
                            .relativize(p)
                            .toString()
                            .replace(p.getFileSystem().getSeparator(), "/"))
                    .sorted()
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            throw FileSystemPackInspector.unlistable(nameOf(directory), e);
        }
    }

    private static Path normalized(Path packDir) {
        return packDir.toAbsolutePath().normalize();
    }

    private static String nameOf(Path directory) {
        return String.valueOf(directory.getFileName());
    }
}
