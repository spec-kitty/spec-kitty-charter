package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A seeded generator of small pack trees whose expected verdicts are written down here, from the generator's own
 * grammar. It never asks {@code PackFileKind} or {@code PackFileSet} what a path is: that would make the test that
 * compares the service against these sets circular.
 *
 * <p>The name-shape table. Every planted path has exactly one verdict:
 *
 * <table>
 * <caption>Name shapes and their verdicts</caption>
 * <tr><th>Shape</th><th>Verdict</th></tr>
 * <tr><td>{@code <stem>.<kind>.yaml}, depth 0-3, kind in tactic, directive, styleguide, toolguide, paradigm, agent</td><td>loaded</td></tr>
 * <tr><td>{@code .<stem>.<kind>.yaml}, a hidden stem</td><td>loaded</td></tr>
 * <tr><td>{@code .h/<stem>.<kind>.yaml}, a hidden directory</td><td>loaded</td></tr>
 * <tr><td>{@code presets/<stem>.<kind>.yaml} (a files rule wins over the presets exclusion)</td><td>loaded</td></tr>
 * <tr><td>{@code <stem>.config.<kind>.yaml}</td><td>loaded</td></tr>
 * <tr><td>a file symlink inside the pack to an external valid tactic</td><td>loaded</td></tr>
 * <tr><td>root {@code a.graph.yaml}, hidden root {@code .b.graph.yaml}, {@code drg/fragment.yaml}</td>
 *     <td>graph input</td></tr>
 * <tr><td>{@code x.tactic.yml}, {@code x.tactic.YAML}, {@code x.TACTIC.yaml}, {@code x.tactic.yaml.bak},
 *     {@code x.tactic.config.yaml}</td><td>decoy</td></tr>
 * <tr><td>{@code notes.yaml}, {@code sub/r.yaml}</td><td>decoy</td></tr>
 * <tr><td>procedure and asset files</td><td>decoy</td></tr>
 * <tr><td>root {@code org-charter.yaml}, {@code sub/charter.yaml}, nested {@code sub/pack.yaml}</td>
 *     <td>decoy</td></tr>
 * <tr><td>nested {@code sub/y.graph.yaml}, nested {@code sub/drg/fragment.yaml}</td><td>decoy</td></tr>
 * <tr><td>a directory named {@code d.tactic.yaml}</td><td>decoy (not a file)</td></tr>
 * <tr><td>a directory symlink to an external directory that holds a valid tactic</td><td>decoy</td></tr>
 * </table>
 *
 * <p>Directory names that are both a files rule and an exclusion ({@code mission_types}, {@code missions},
 * {@code actions}) are kept out of the alphabet, so a verdict never depends on which rule comes first.
 */
final class GeneratedPackTree {

    /** What the service must do with a planted path. */
    enum Verdict {
        /** A pack file the load reads. */
        LOADED,
        /** A declared graph input: digested, never loaded. */
        GRAPH_INPUT,
        /** A file or directory the service neither loads nor digests. */
        DECOY
    }

    /** The loaded kinds, spelled as the file names spell them. */
    enum Kind {
        TACTIC("tactic"),
        DIRECTIVE("directive"),
        STYLEGUIDE("styleguide"),
        TOOLGUIDE("toolguide"),
        PARADIGM("paradigm"),
        AGENT_PROFILE("agent"),
        MISSION_STEP_CONTRACT("step-contract"),
        GLOSSARY_PACK("glossary-pack"),
        SKILL("skill");

        private final String token;

        Kind(String token) {
            this.token = token;
        }

        String token() {
            return token;
        }
    }

    /** The rows of the name-shape table. */
    enum Shape {
        LOADED_PLAIN(Verdict.LOADED),
        LOADED_HIDDEN_STEM(Verdict.LOADED),
        LOADED_HIDDEN_DIR(Verdict.LOADED),
        LOADED_PRESETS(Verdict.LOADED),
        LOADED_CONFIG_STEM(Verdict.LOADED),
        LOADED_FILE_SYMLINK(Verdict.LOADED),
        GRAPH_ROOT(Verdict.GRAPH_INPUT),
        GRAPH_HIDDEN_ROOT(Verdict.GRAPH_INPUT),
        GRAPH_FRAGMENT(Verdict.GRAPH_INPUT),
        DECOY_YML(Verdict.DECOY),
        DECOY_UPPER_EXTENSION(Verdict.DECOY),
        DECOY_UPPER_KIND(Verdict.DECOY),
        DECOY_BAK(Verdict.DECOY),
        DECOY_KIND_THEN_CONFIG(Verdict.DECOY),
        DECOY_NOTES(Verdict.DECOY),
        DECOY_SUB_R(Verdict.DECOY),
        DECOY_PROCEDURE(Verdict.DECOY),
        DECOY_ASSET(Verdict.DECOY),
        DECOY_ORG_CHARTER(Verdict.DECOY),
        DECOY_SUB_CHARTER(Verdict.DECOY),
        DECOY_SUB_PACK(Verdict.DECOY),
        DECOY_NESTED_SHARD(Verdict.DECOY),
        DECOY_NESTED_FRAGMENT(Verdict.DECOY),
        DECOY_TACTIC_DIRECTORY(Verdict.DECOY),
        DECOY_DIRECTORY_SYMLINK(Verdict.DECOY);

        private final Verdict verdict;

        Shape(Verdict verdict) {
            this.verdict = verdict;
        }

        Verdict verdict() {
            return verdict;
        }
    }

    /** The shapes whose path is made from a fresh stem, so a new one can be added at any time. */
    static final List<Shape> STEM_SHAPES = List.of(
            Shape.LOADED_PLAIN,
            Shape.LOADED_HIDDEN_STEM,
            Shape.LOADED_HIDDEN_DIR,
            Shape.LOADED_PRESETS,
            Shape.LOADED_CONFIG_STEM,
            Shape.DECOY_YML,
            Shape.DECOY_UPPER_EXTENSION,
            Shape.DECOY_UPPER_KIND,
            Shape.DECOY_BAK,
            Shape.DECOY_KIND_THEN_CONFIG,
            Shape.DECOY_PROCEDURE,
            Shape.DECOY_ASSET);

    private static final List<String> DIRECTORIES = List.of("a", "b", "c", "sub", "d-1", "drg");
    private static final String DECOY_CONTENT = "a: 1\n";
    private static final String MALFORMED = "id: [\n";
    private static final int LATE_COUNTER = 1_000_000;
    private static final String PACK_YAML = packYaml();

    /**
     * One planted path.
     *
     * @param path the path relative to the pack directory, with forward slashes
     * @param verdict what the service must do with it
     * @param kind the loaded kind, or null when the file is not loaded
     * @param id the planted element id, or null when the content is malformed or the file is not loaded
     * @param file where the file is on disk (through the link, for a symlink)
     * @param target the external file a symlink points to, or null
     */
    record Planted(
            String path,
            Verdict verdict,
            @Nullable Kind kind,
            @Nullable String id,
            Path file,
            @Nullable Path target) {
        boolean valid() {
            return id != null;
        }
    }

    /**
     * A file the digest test appends to.
     *
     * @param label the relative path, or the external path of a link target, for the failure message
     * @param file the file to append to
     * @param digested whether the service must digest it
     */
    record Target(String label, Path file, boolean digested) {}

    /** One generated pack. */
    static final class Pack {
        private final String name;
        private final String directoryName;
        private final Path directory;
        private final PackLocation location;
        private final Map<String, Planted> planted = new LinkedHashMap<>();
        private final List<Target> externalDecoys = new ArrayList<>();

        private Pack(String name, String directoryName, Path directory, Path configuredRoot) {
            this.name = name;
            this.directoryName = directoryName;
            this.directory = directory;
            this.location = new PackLocation(name, PackLevel.ORG, configuredRoot);
        }

        /** The name the pack is configured under. */
        String name() {
            return name;
        }

        /** The name of the pack directory, which the store addresses the pack by. */
        String directoryName() {
            return directoryName;
        }

        /** The pack directory, absolute and normalized. */
        Path directory() {
            return directory;
        }

        /** The configured location; its root may be written as {@code <dir>/.}. */
        PackLocation location() {
            return location;
        }

        /** Every planted path of the pack, in planting order. */
        Map<String, Planted> planted() {
            return planted;
        }

        /** The relative paths of the planted files of one verdict. */
        Set<String> paths(Verdict verdict) {
            Set<String> out = new LinkedHashSet<>();
            planted.values().stream().filter(p -> p.verdict() == verdict).forEach(p -> out.add(p.path()));
            return out;
        }

        /** The relative paths of the planted files of one loaded kind. */
        Set<String> paths(Kind kind) {
            Set<String> out = new LinkedHashSet<>();
            planted.values().stream().filter(p -> p.kind() == kind).forEach(p -> out.add(p.path()));
            return out;
        }

        /** Every regular file the digest test appends to, with whether the service must digest it. */
        List<Target> targets() {
            List<Target> out = new ArrayList<>();
            out.add(new Target("pack.yaml", directory.resolve("pack.yaml"), true));
            for (Planted p : planted.values()) {
                if (Files.isRegularFile(p.file())) {
                    out.add(new Target(p.path(), p.file(), p.verdict() != Verdict.DECOY));
                }
                Path target = p.target();
                if (target != null) {
                    out.add(new Target(p.path() + " -> " + target.getFileName(), target, true));
                }
            }
            out.addAll(externalDecoys);
            return out;
        }
    }

    private final long seed;
    private final int index;
    private final List<Pack> packs = new ArrayList<>();
    private final Set<Shape> shapes = EnumSet.noneOf(Shape.class);
    private final Path home;
    private Random random;
    private int counter;

    private GeneratedPackTree(long seed, int index, Path home) {
        this.seed = seed;
        this.index = index;
        this.home = home;
        this.random = new Random(seed);
    }

    /**
     * Generates one tree of 1-3 packs.
     *
     * @param seed the tree's own seed
     * @param index the tree's number, for failure messages
     * @param home an empty directory the tree is written under
     * @param exhaustive whether every shape of the table is planted in every pack
     * @return the tree
     * @throws IOException when a file cannot be written
     */
    static GeneratedPackTree generate(long seed, int index, Path home, boolean exhaustive) throws IOException {
        GeneratedPackTree tree = new GeneratedPackTree(seed, index, home);
        int count = exhaustive ? 3 : tree.packCount();
        for (int i = 0; i < count; i++) {
            tree.packs.add(tree.plantPack(i, exhaustive));
        }
        return tree;
    }

    /** Smaller trees are likelier (60% one pack, 25% two, 15% three): a run of 200 trees stays fast. */
    private int packCount() {
        int roll = random.nextInt(20);
        return roll < 12 ? 1 : roll < 17 ? 2 : 3;
    }

    long seed() {
        return seed;
    }

    int index() {
        return index;
    }

    List<Pack> packs() {
        return packs;
    }

    /** The shapes that were planted in this tree. */
    Set<Shape> shapes() {
        return shapes;
    }

    /** The configured locations, in pack order. */
    List<PackLocation> locations() {
        return packs.stream().map(Pack::location).toList();
    }

    /** The directory that holds the pack directories. */
    Path home() {
        return home;
    }

    // ---- planting ------------------------------------------------------------------------------------------------

    private Pack plantPack(int i, boolean exhaustive) throws IOException {
        String directoryName = "pk" + i + (random.nextBoolean() ? "-x" : "");
        Path directory = home.resolve(directoryName).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String name = (random.nextBoolean() || (exhaustive && i == 0)) ? "cfg-" + directoryName : directoryName;
        boolean rawRoot = (exhaustive && i == 0) || random.nextInt(4) == 0;
        Pack pack = new Pack(name, directoryName, directory, rawRoot ? directory.resolve(".") : directory);
        Files.writeString(directory.resolve("pack.yaml"), PACK_YAML, StandardCharsets.UTF_8);
        for (Kind kind : Kind.values()) {
            plantLoaded(pack, Shape.LOADED_PLAIN, kind);
        }
        for (Shape shape : Shape.values()) {
            int copies = copiesOf(shape, exhaustive);
            for (int c = 0; c < copies; c++) {
                plant(pack, shape);
            }
        }
        return pack;
    }

    /** How many files of a shape one pack gets: small packs keep 200 trees fast, the table is covered across trees. */
    private int copiesOf(Shape shape, boolean exhaustive) {
        if (exhaustive) {
            return 1;
        }
        return switch (shape) {
            case GRAPH_ROOT, GRAPH_HIDDEN_ROOT, GRAPH_FRAGMENT -> chance(1, 2);
            case LOADED_PLAIN -> chance(1, 4);
            case LOADED_HIDDEN_STEM, LOADED_HIDDEN_DIR, LOADED_PRESETS, LOADED_CONFIG_STEM, LOADED_FILE_SYMLINK ->
                chance(1, 5);
            default -> chance(1, 10);
        };
    }

    private int chance(int in, int of) {
        return random.nextInt(of) < in ? 1 : 0;
    }

    /** Plants one file or directory of the shape; a fixed-path shape that is already there is left alone. */
    private void plant(Pack pack, Shape shape) throws IOException {
        switch (shape) {
            case LOADED_PLAIN, LOADED_HIDDEN_STEM, LOADED_HIDDEN_DIR, LOADED_PRESETS, LOADED_CONFIG_STEM ->
                plantLoaded(pack, shape, randomKind());
            case LOADED_FILE_SYMLINK -> plantSymlink(pack);
            case DECOY_TACTIC_DIRECTORY -> plantDirectory(pack);
            case DECOY_DIRECTORY_SYMLINK -> plantDirectorySymlink(pack);
            default -> plantFile(pack, shape);
        }
    }

    /**
     * Adds one new file to a pack, for the "a new file appears" check. The shape and the name depend only on the tree's
     * seed and the pack, so a failure replays.
     *
     * @param pack the pack to add to
     * @return the planted file
     * @throws IOException when the file cannot be written
     */
    Planted addFile(Pack pack) throws IOException {
        random = new Random(seed * 31 + pack.name().hashCode());
        counter = LATE_COUNTER;
        Shape shape = STEM_SHAPES.get(random.nextInt(STEM_SHAPES.size()));
        return shape.verdict() == Verdict.LOADED ? plantLoaded(pack, shape, randomKind()) : plantFile(pack, shape);
    }

    private Kind randomKind() {
        return Kind.values()[random.nextInt(Kind.values().length)];
    }

    private Planted plantLoaded(Pack pack, Shape shape, Kind kind) throws IOException {
        int n = ++counter;
        String stem = "e" + n;
        String path = loadedPath(shape, stem, kind);
        boolean valid =
                pack.paths(kind).stream().map(pack.planted::get).noneMatch(Planted::valid) || random.nextInt(5) != 0;
        String id = idOf(kind, n);
        Path file = write(pack, path, valid ? contentOf(kind, id) : MALFORMED);
        Planted planted = new Planted(path, Verdict.LOADED, kind, valid ? id : null, file, null);
        remember(pack, shape, planted);
        return planted;
    }

    private String loadedPath(Shape shape, String stem, Kind kind) {
        String leaf = stem + "." + kind.token() + ".yaml";
        return switch (shape) {
            case LOADED_HIDDEN_STEM -> directories(3) + "." + leaf;
            case LOADED_HIDDEN_DIR -> ".h/" + directories(2) + leaf;
            case LOADED_PRESETS -> "presets/" + leaf;
            case LOADED_CONFIG_STEM -> directories(2) + stem + ".config." + kind.token() + ".yaml";
            default -> directories(3) + leaf;
        };
    }

    private void plantSymlink(Pack pack) throws IOException {
        int n = ++counter;
        String id = idOf(Kind.TACTIC, n);
        Path target = external(pack, "link-target-" + n + ".yaml");
        Files.writeString(target, contentOf(Kind.TACTIC, id), StandardCharsets.UTF_8);
        String path = directories(1) + "l" + n + ".tactic.yaml";
        Path link = pack.directory().resolve(path);
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, target);
        remember(pack, Shape.LOADED_FILE_SYMLINK, new Planted(path, Verdict.LOADED, Kind.TACTIC, id, link, target));
    }

    private void plantDirectory(Pack pack) throws IOException {
        Files.createDirectories(pack.directory().resolve("d.tactic.yaml"));
        remember(
                pack,
                Shape.DECOY_TACTIC_DIRECTORY,
                new Planted(
                        "d.tactic.yaml",
                        Verdict.DECOY,
                        null,
                        null,
                        pack.directory().resolve("d.tactic.yaml"),
                        null));
    }

    private void plantDirectorySymlink(Pack pack) throws IOException {
        Path outside = external(pack, "linked-directory");
        Files.createDirectories(outside);
        Path file = outside.resolve("v.tactic.yaml");
        Files.writeString(file, contentOf(Kind.TACTIC, "t-linked"), StandardCharsets.UTF_8);
        Path link = pack.directory().resolve("linkdir");
        Files.createSymbolicLink(link, outside);
        pack.externalDecoys.add(new Target("linkdir/v.tactic.yaml (external)", file, false));
        remember(pack, Shape.DECOY_DIRECTORY_SYMLINK, new Planted("linkdir", Verdict.DECOY, null, null, link, null));
    }

    private Planted plantFile(Pack pack, Shape shape) throws IOException {
        counter++;
        String path = decoyPath(shape, "e" + counter);
        Path file = pack.directory().resolve(path);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return pack.planted.get(path);
        }
        write(pack, path, DECOY_CONTENT);
        Planted planted = new Planted(path, shape.verdict(), null, null, file, null);
        remember(pack, shape, planted);
        return planted;
    }

    private String decoyPath(Shape shape, String stem) {
        return switch (shape) {
            case GRAPH_ROOT -> "a.graph.yaml";
            case GRAPH_HIDDEN_ROOT -> ".b.graph.yaml";
            case GRAPH_FRAGMENT -> "drg/fragment.yaml";
            case DECOY_YML -> directories(2) + stem + ".tactic.yml";
            case DECOY_UPPER_EXTENSION -> directories(2) + stem + ".tactic.YAML";
            case DECOY_UPPER_KIND -> directories(2) + stem + ".TACTIC.yaml";
            case DECOY_BAK -> directories(2) + stem + ".tactic.yaml.bak";
            case DECOY_KIND_THEN_CONFIG -> directories(2) + stem + ".tactic.config.yaml";
            case DECOY_NOTES -> "notes.yaml";
            case DECOY_SUB_R -> "sub/r.yaml";
            case DECOY_PROCEDURE -> directories(2) + stem + ".procedure.yaml";
            case DECOY_ASSET -> directories(2) + stem + ".asset.yaml";
            case DECOY_ORG_CHARTER -> "org-charter.yaml";
            case DECOY_SUB_CHARTER -> "sub/charter.yaml";
            case DECOY_SUB_PACK -> "sub/pack.yaml";
            case DECOY_NESTED_SHARD -> "sub/y.graph.yaml";
            case DECOY_NESTED_FRAGMENT -> "sub/drg/fragment.yaml";
            default -> throw new IllegalArgumentException(shape + " has no file path");
        };
    }

    private void remember(Pack pack, Shape shape, Planted planted) {
        pack.planted.put(planted.path(), planted);
        shapes.add(shape);
    }

    private Path write(Pack pack, String path, String content) throws IOException {
        Path file = pack.directory().resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private Path external(Pack pack, String name) throws IOException {
        Path directory = home.resolve("external").resolve(pack.directoryName());
        Files.createDirectories(directory);
        return directory.resolve(name);
    }

    /** Zero to {@code max} directory segments, each with a trailing slash. */
    private String directories(int max) {
        StringBuilder out = new StringBuilder();
        int depth = random.nextInt(max + 1);
        for (int i = 0; i < depth; i++) {
            out.append(DIRECTORIES.get(random.nextInt(DIRECTORIES.size()))).append('/');
        }
        return out.toString();
    }

    // ---- content -------------------------------------------------------------------------------------------------

    private static String idOf(Kind kind, int n) {
        return switch (kind) {
            case TACTIC -> "t-" + n;
            case DIRECTIVE -> "D-" + n;
            case STYLEGUIDE -> "s-" + n;
            case TOOLGUIDE -> "tg-" + n;
            case PARADIGM -> "p-" + n;
            case AGENT_PROFILE -> "ap-" + n;
            case MISSION_STEP_CONTRACT -> "msc-" + n;
            case GLOSSARY_PACK -> "gp-" + n;
            case SKILL -> "sk-" + n;
        };
    }

    private static String contentOf(Kind kind, String id) {
        return switch (kind) {
            case TACTIC -> "id: " + id + "\nschema_version: '1.0'\nname: N\nsteps:\n  - title: S\n";
            case DIRECTIVE -> "id: " + id + "\nschema_version: '1.0'\ntitle: T\nintent: I\nenforcement: advisory\n";
            case STYLEGUIDE -> "id: " + id + "\nschema_version: '1.0'\ntitle: T\nscope: code\nprinciples: [p]\n";
            case TOOLGUIDE ->
                "id: " + id + "\nschema_version: '1.0'\ntool: T\ntitle: T\nguide_path: g.md\nsummary: S\n";
            case PARADIGM -> "id: " + id + "\nschema_version: '1.0'\nname: N\nsummary: S\n";
            case AGENT_PROFILE ->
                "profile-id: " + id + "\nname: N\nroles: [r]\npurpose: P\nspecialization:\n  primary-focus: F\n";
            case MISSION_STEP_CONTRACT ->
                "id: " + id + "\nschema_version: '1.0'\naction: a\nmission: m\nsteps:\n  - id: s\n    description: D\n";
            case GLOSSARY_PACK ->
                "id: " + id
                        + "\nprovenance: org\nterms:\n  - surface: s\n    definition: D\n    confidence: 0.9\n    status: active\n";
            // A wrapper skill names no companion, so the tree plants the skill file alone.
            case SKILL ->
                "id: " + id
                        + "\nschema_version: '1.0'\ntitle: T\ndescription: D\nform: wrapper\nexpands_to:\n  target: builtin:x\n";
        };
    }

    private static String packYaml() {
        try (InputStream in = GeneratedPackTree.class.getResourceAsStream("/roundtrip-fixtures/pack-a/pack.yaml")) {
            if (in == null) {
                throw new IllegalStateException("the pack-a fixture is missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
