package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Every file of a pack with its gate kind: the universe the whole-pack gate accounts for.
 *
 * <p>A file that {@code contracts/packs/file-kinds.yaml} maps to a schema takes the schema's name ({@code directive},
 * {@code graph-shard}, {@code pack-descriptor}, {@code agent-profile}, …). Any other file takes
 * {@code other:<top-level directory>}, or {@code other:<file name>} at the pack root, so files without a schema are
 * grouped where they live ({@code other:missions}, {@code other:pack.md}).
 */
final class WholePackCensus {

    /** The gate kind of a symbolic link: counted, never followed, so "every file once" holds. */
    static final String SYMLINK = "other:symlink";

    /** The prefix of a gate kind for a file no schema covers. */
    static final String OTHER = "other:";

    private WholePackCensus() {}

    /**
     * Lists every regular file under {@code pack}, and every symbolic link as kind {@link #SYMLINK}, never followed.
     *
     * @param pack the pack directory
     * @return pack-relative path ({@code /}-separated) to gate kind, sorted by path
     */
    static SortedMap<String, String> of(Path pack) {
        String packName = String.valueOf(pack.getFileName());
        SortedMap<String, String> census = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(pack)) {
            walk.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))
                    .forEach(file -> {
                        String path = pack.relativize(file)
                                .toString()
                                .replace(file.getFileSystem().getSeparator(), "/");
                        census.put(path, Files.isSymbolicLink(file) ? SYMLINK : kindOf(packName, path));
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return census;
    }

    /**
     * The gate kind of one file.
     *
     * @param packName the pack directory's name ({@code file-kinds.yaml} globs start at the directory holding packs)
     * @param path the pack-relative path
     * @return the schema name, or {@code other:<first path segment>}
     */
    static String kindOf(String packName, String path) {
        if (PackFileKind.bundled().classify(packName + "/" + path) instanceof PackFileKind.Mapped mapped) {
            return mapped.kind();
        }
        int slash = path.indexOf('/');
        return OTHER + (slash < 0 ? path : path.substring(0, slash));
    }

    /**
     * How the gate reads and compares one file (#193): the content form its {@code file-kinds.yaml} rule declares, so a
     * {@link ContentForm#BYTES} file is compared byte for byte and never read as text, and a
     * {@link ContentForm#MARKDOWN} file is compared by its Markdown model and never read as YAML (#194). A file no rule
     * maps keeps today's treatment, {@link ContentForm#YAML}.
     *
     * @param packName the pack directory's name
     * @param path the pack-relative path
     * @return the file's content form
     * @throws IllegalStateException for a form the gate does not compare yet ({@link ContentForm#TEXT}), which the table
     *     refuses anyway: the dispatch is exhaustive, so a newly built form must be decided here
     */
    static ContentForm contentFormOf(String packName, String path) {
        if (!(PackFileKind.bundled().classify(packName + "/" + path) instanceof PackFileKind.Mapped mapped)) {
            return ContentForm.YAML;
        }
        return switch (mapped.contentForm()) {
            case YAML, MARKDOWN, BYTES -> mapped.contentForm();
            case TEXT ->
                throw new IllegalStateException(packName + "/" + path + ": content form '" + ContentForm.TEXT.token()
                        + "' is not built; the gate has no comparison for it");
        };
    }
}
