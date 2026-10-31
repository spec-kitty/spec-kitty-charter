package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Every file of a pack with its gate kind: the universe the whole-pack gate accounts for.
 *
 * <p>A file that {@code contracts/packs/file-kinds.yaml} maps to a schema takes the schema's name ({@code directive},
 * {@code graph-shard}, {@code pack-descriptor}, {@code agent-profile}, …). Any other file takes
 * {@code other:<top-level directory>}, or {@code other:<file name>} at the pack root, so files without a schema are
 * grouped where they live ({@code other:missions}, {@code other:README.md}).
 *
 * <p>A companion file (ADR-0039, #194) that no rule maps takes its owning artifact's gate kind, so it is modelled with
 * that artifact: the gate pairs it with the owner's written companion at the same relative path and compares it in the
 * companion's declared form ({@link #companionsOf}, {@link #formsOf}), never as YAML.
 */
final class WholePackCensus {

    /** The gate kind of a symbolic link: counted, never followed, so "every file once" holds. */
    static final String SYMLINK = "other:symlink";

    /** The prefix of a gate kind for a file no schema covers. */
    static final String OTHER = "other:";

    /**
     * The prefix of a retired file's gate kind, followed by its rule's glob (ADR-0044 decision 6): a bucket of its own, never
     * folded into {@code other:}, so a stale file is red.
     */
    static final String RETIRED = "retired:";

    private WholePackCensus() {}

    /**
     * A companion file of the pack and the artifact that names it.
     *
     * @param owner the owning artifact's file, pack relative
     * @param ownerKind the owner's gate kind, its binding's schema name
     * @param declared the companion path exactly as the owner declares it, relative to the owner's file
     * @param form the companion's declared content form
     */
    record CompanionFile(String owner, String ownerKind, String declared, ContentForm form) {
        /**
         * Checks the fields.
         *
         * @param owner the owner's file
         * @param ownerKind the owner's gate kind
         * @param declared the declared path
         * @param form the declared form
         */
        CompanionFile {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(ownerKind, "ownerKind");
            Objects.requireNonNull(declared, "declared");
            Objects.requireNonNull(form, "form");
        }
    }

    /**
     * Lists every regular file under {@code pack}, and every symbolic link as kind {@link #SYMLINK}, never followed.
     *
     * @param pack the pack directory
     * @return pack-relative path ({@code /}-separated) to gate kind, sorted by path
     */
    static SortedMap<String, String> of(Path pack) {
        String packName = String.valueOf(pack.getFileName());
        SortedMap<String, CompanionFile> companions = companionsOf(pack);
        SortedMap<String, String> census = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(pack)) {
            walk.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))
                    .forEach(file -> {
                        String path = pack.relativize(file)
                                .toString()
                                .replace(file.getFileSystem().getSeparator(), "/");
                        census.put(path, Files.isSymbolicLink(file) ? SYMLINK : kindOf(packName, path, companions));
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
        return kindOf(packName, path, new TreeMap<>());
    }

    /**
     * The owning artifact's gate kind for a companion, else the schema name, else {@code other:<first segment>}. A
     * companion the companion rule admits is unclassified or at its own rule (a toolguide's guide at the
     * {@code toolguide-guide} rule, #194); either way it is modelled with its owner.
     */
    private static String kindOf(String packName, String path, SortedMap<String, CompanionFile> companions) {
        @Nullable CompanionFile companion = companions.get(path);
        return companion != null ? companion.ownerKind() : kindOf(packName, path, PackFileKind.bundled());
    }

    /**
     * The gate kind of one file under {@code table}: the schema name, {@link #RETIRED} and the rule's glob for a retired
     * file, else {@code other:<first segment>}. The seam a test uses before the bundled table has a retired rule.
     *
     * @param packName the pack directory's name
     * @param path the pack-relative path
     * @param table the file-kinds table
     * @return the gate kind
     */
    static String kindOf(String packName, String path, PackFileKind table) {
        PackFileKind.Classification classification = table.classify(packName + "/" + path);
        if (classification instanceof PackFileKind.Mapped mapped) {
            return mapped.kind();
        }
        if (classification instanceof PackFileKind.Retired retired) {
            return RETIRED + retired.match();
        }
        int slash = path.indexOf('/');
        return OTHER + (slash < 0 ? path : path.substring(0, slash));
    }

    /**
     * The companion files the readable artifacts of the pack name, under the registry's bindings and the bundled rules.
     *
     * @param pack the pack directory
     * @return pack-relative companion path to its owner, sorted by path
     */
    static SortedMap<String, CompanionFile> companionsOf(Path pack) {
        return companionsOf(pack, KindBindings.all(), Companions.Rules.bundled());
    }

    /**
     * The companion files the readable artifacts of the pack name, as the companion rule resolves them (whether the
     * file exists is the round trip's to report). Each artifact file is read as YAML through its binding, as the
     * source fingerprint reads it; a companion itself is never read here.
     *
     * @param pack the pack directory
     * @param bindings the bindings whose companions to list; a test passes one of its own
     * @param rules the table that classifies companion paths
     * @return pack-relative companion path to its owner, sorted by path
     */
    static SortedMap<String, CompanionFile> companionsOf(
            Path pack, List<KindBinding<?, ?>> bindings, Companions.Rules rules) {
        Path packDir = pack.toAbsolutePath().normalize();
        SortedMap<String, CompanionFile> out = new TreeMap<>();
        for (KindBinding<?, ?> binding : bindings) {
            if (binding.hasCompanions()) {
                for (String file : PackFileSet.files(packDir, binding.kind())) {
                    companionsOf(packDir, file, binding, rules, out);
                }
            }
        }
        return out;
    }

    private static <D> void companionsOf(
            Path packDir,
            String file,
            KindBinding<D, ?> binding,
            Companions.Rules rules,
            SortedMap<String, CompanionFile> out) {
        Optional<String> text = FilesystemPackStore.readText(packDir.resolve(file));
        if (text.isEmpty()
                || !(new YamlSerializationStrategy().read(binding, text.get(), file)
                        instanceof FileRead.Read<D> read)) {
            return;
        }
        for (KindBinding.Companion companion : binding.companions(read.value())) {
            if (Companions.resolve(rules, packDir, file, companion) instanceof Companions.Inside at) {
                out.putIfAbsent(
                        at.packRelative(),
                        new CompanionFile(file, binding.schemaName(), companion.path(), companion.form()));
            }
        }
    }

    /**
     * How the gate compares each file of a pack: a companion in its declared form, any other file as
     * {@link #contentFormOf} gives it.
     *
     * @param pack the pack directory
     * @return the form of a pack-relative path
     */
    static Function<String, ContentForm> formsOf(Path pack) {
        return formsOf(pack, companionsOf(pack));
    }

    /**
     * How the gate compares each file of a pack, given its companions.
     *
     * @param pack the pack directory
     * @param companions the pack's companions, as {@link #companionsOf} lists them
     * @return the form of a pack-relative path
     */
    static Function<String, ContentForm> formsOf(Path pack, SortedMap<String, CompanionFile> companions) {
        String packName = String.valueOf(pack.getFileName());
        return path -> {
            @Nullable CompanionFile companion = companions.get(path);
            return companion != null ? companion.form() : contentFormOf(packName, path);
        };
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
