package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.infra.storage.GeneratedPackTree.Kind;
import ai.speckitty.charter.packs.infra.storage.GeneratedPackTree.Pack;
import ai.speckitty.charter.packs.infra.storage.GeneratedPackTree.Planted;
import ai.speckitty.charter.packs.infra.storage.GeneratedPackTree.RootForm;
import ai.speckitty.charter.packs.infra.storage.GeneratedPackTree.Shape;
import ai.speckitty.charter.packs.infra.storage.GeneratedPackTree.Target;
import ai.speckitty.charter.packs.infra.storage.GeneratedPackTree.Verdict;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The service loads exactly the files it digests (issue 120): over generated pack trees, the files the store reads
 * are exactly the planted loaded files and payloads (asset files and templates), and the source fingerprint changes if
 * and only if a file is {@code pack.yaml}, a loaded file, a payload or a declared graph input. An asset sidecar whose
 * payload does not exist is digested and refused by the load.
 *
 * <p>The expected sets come from {@link GeneratedPackTree}'s own name-shape table. The load is observed only through
 * {@code FilesystemPackStore.read}, {@code DomainRepositoryService.tacticsOf} and {@code DomainRepositoryService.load};
 * the digest is observed only by appending bytes to a file and digesting again, through the production
 * {@code FileSystemPackSources.fingerprint()}. Nothing here asks {@code PackFileKind} what a path is.
 *
 * <p>Replay a failure with {@code -DpackFileSet.seed=<seed>} where the assertion message says {@code seed}; tree
 * <em>i</em> uses the base seed plus <em>i</em>.
 */
class PackFileSetExactnessTest {

    private static final long BASE_SEED = Long.getLong("packFileSet.seed", 20_261_024L);
    private static final int TREES = 200;
    private static final byte[] MARK = "\n# m\n".getBytes(StandardCharsets.UTF_8);
    private static final String PACK_YAML = "pack.yaml";

    @TempDir
    static Path home;

    /** NFR-001: 200 trees in under 10 s of CPU time, measured apart from the wall clock, which a starved runner skews. */
    private static final long CPU_BUDGET_NANOS = 10_000_000_000L;

    /** Only a hang cap: the budget is CPU time. */
    private static final int HANG_CAP_SECONDS = 60;

    private static final Map<Integer, GeneratedPackTree> TREES_BY_INDEX = new HashMap<>();
    private static long generationCpuNanos = -1;

    /** Writes the trees once, so that no check is charged for them; {@link #generatingTheTreesIsWithinBudget()}. */
    @BeforeAll
    static void generateTheTrees() throws IOException {
        if (!SymbolicLinks.supported(home)) {
            return;
        }
        long start = cpuNanos();
        for (int i = 0; i < TREES; i++) {
            tree(i);
        }
        generationCpuNanos = cpuNanos() - start;
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void generatingTheTreesIsWithinBudget() {
        assumeTrue(generationCpuNanos >= 0, "no trees were generated, or CPU time cannot be measured");
        assertThat(generationCpuNanos)
                .as("CPU time to generate %d trees", TREES)
                .isLessThan(CPU_BUDGET_NANOS);
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void theFingerprintChangesIfAndOnlyIfAFileIsPackYamlLoadedAPayloadOrAGraphInput() throws IOException {
        SymbolicLinks.requireSupport(home);
        Set<Shape> seen = EnumSet.noneOf(Shape.class);
        Set<RootForm> roots = EnumSet.noneOf(RootForm.class);
        long start = cpuNanos();
        for (int i = 0; i < TREES; i++) {
            GeneratedPackTree tree = tree(i);
            assertDigestIsExact(tree, PackFileSetExactnessTest::fingerprintOf);
            seen.addAll(tree.shapes());
            roots.addAll(tree.rootForms());
        }
        assertWithinCpuBudget(start, "digest checks over " + TREES + " trees");
        assertThat(seen)
                .as("every row of the name-shape table was planted at least once")
                .containsExactlyInAnyOrder(Shape.values());
        assertThat(roots)
                .as("every form of a configured root, a symbolic link among them (#174), was planted at least once")
                .containsExactlyInAnyOrder(RootForm.values());
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void theStoreLoadsExactlyThePlantedFilesOfEveryKind() throws IOException {
        SymbolicLinks.requireSupport(home);
        long start = cpuNanos();
        for (int i = 0; i < TREES; i++) {
            assertLoadIsExact(tree(i));
        }
        assertWithinCpuBudget(start, "load checks over " + TREES + " trees");
    }

    /** This thread's CPU time, or -1 when the JVM cannot measure it. */
    private static long cpuNanos() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        return threads.isCurrentThreadCpuTimeSupported() ? threads.getCurrentThreadCpuTime() : -1;
    }

    private static void assertWithinCpuBudget(long startNanos, String what) {
        if (startNanos >= 0) {
            assertThat(cpuNanos() - startNanos).as("CPU time of the %s", what).isLessThan(CPU_BUDGET_NANOS);
        }
    }

    /** The control for the mutants: the production fingerprint passes the whole oracle on the exhaustive tree. */
    @Test
    @Timeout(HANG_CAP_SECONDS)
    void theProductionFingerprintPassesTheWholeOracleOnTheExhaustiveTree() throws IOException {
        SymbolicLinks.requireSupport(home);
        assertExact(tree(0), PackFileSetExactnessTest::fingerprintOf);
    }

    // ---- the planted omission ------------------------------------------------------------------------------------
    //
    // These run the production seam, FileSystemPackSources.digest(locations, filesOf), with a file list that leaves
    // something out, and require the oracle to notice and to name a planted file. They may use PackFileSet because they
    // build the mutant; no expected set is computed from it.

    @ParameterizedTest
    @MethodSource("boundKinds")
    @Timeout(HANG_CAP_SECONDS)
    void aDigestThatOmitsOneLoadedKindFailsTheOracleNamingAPlantedFileOfThatKind(ArtifactKind omitted)
            throws IOException {
        SymbolicLinks.requireSupport(home);
        GeneratedPackTree tree = tree(0);
        Set<ArtifactKind> kept = EnumSet.complementOf(EnumSet.of(omitted));
        Function<List<PackLocation>, String> mutant =
                locations -> FileSystemPackSources.digest(locations, dir -> PackFileSet.digested(dir, kept));

        Kind kind = Kind.valueOf(omitted.name());
        assertOracleFailsNaming(tree, mutant, plantedPaths(tree, p -> p.kind() == kind));
    }

    static List<ArtifactKind> boundKinds() {
        return KindBindings.kinds();
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void aDigestThatDropsTheGraphInputsFailsTheOracleNamingAGraphInput() throws IOException {
        GeneratedPackTree tree = tree(0);
        Function<List<PackLocation>, String> mutant = locations -> FileSystemPackSources.digest(
                locations,
                dir -> PackFileSet.digested(dir).stream()
                        .filter(file -> !isGraphInput(file))
                        .toList());

        assertOracleFailsNaming(tree, mutant, plantedPaths(tree, p -> p.verdict() == Verdict.GRAPH_INPUT));
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void aDigestThatDropsTheManifestFailsTheOracleNamingIt() throws IOException {
        GeneratedPackTree tree = tree(0);
        Function<List<PackLocation>, String> mutant = locations -> FileSystemPackSources.digest(
                locations,
                dir -> PackFileSet.digested(dir).stream()
                        .filter(file -> !file.equals(PackFileSet.MANIFEST))
                        .toList());

        assertOracleFailsNaming(tree, mutant, plantedPaths(tree, p -> p.verdict() == Verdict.MANIFEST));
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void aDigestThatDropsThePackDocumentFailsTheOracleNamingIt() throws IOException {
        GeneratedPackTree tree = tree(0);
        Function<List<PackLocation>, String> mutant = locations -> FileSystemPackSources.digest(
                locations,
                dir -> PackFileSet.digested(dir).stream()
                        .filter(file -> !file.equals(PackDocumentMarkdown.FILE))
                        .toList());

        assertOracleFailsNaming(tree, mutant, plantedPaths(tree, p -> p.path().equals(PackDocumentMarkdown.FILE)));
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void aDigestThatDropsThePayloadsFailsTheOracleNamingAPayload() throws IOException {
        GeneratedPackTree tree = tree(0);
        Function<List<PackLocation>, String> mutant = locations -> FileSystemPackSources.digest(locations, dir -> {
            List<String> files = new ArrayList<>(PackFileSet.digested(dir));
            files.removeAll(PackFileSet.assetFiles(dir));
            files.removeAll(PackFileSet.templates(dir));
            return files;
        });

        assertOracleFailsNaming(tree, mutant, plantedPaths(tree, p -> p.verdict() == Verdict.PAYLOAD));
    }

    @Test
    @Timeout(HANG_CAP_SECONDS)
    void aDigestThatAddsANestedShardFailsTheOracleNamingIt() throws IOException {
        GeneratedPackTree tree = tree(0);
        Function<List<PackLocation>, String> mutant = locations -> FileSystemPackSources.digest(locations, dir -> {
            List<String> files = new ArrayList<>(PackFileSet.digested(dir));
            if (Files.isRegularFile(dir.resolve("sub/y.graph.yaml"))) {
                files.add("sub/y.graph.yaml");
            }
            return files;
        });

        assertOracleFailsNaming(tree, mutant, Set.of("sub/y.graph.yaml"));
    }

    private static void assertOracleFailsNaming(
            GeneratedPackTree tree, Function<List<PackLocation>, String> mutant, Set<String> plantedFiles) {
        assertThat(plantedFiles).as("the tree plants such a file").isNotEmpty();
        assertThatThrownBy(() -> assertExact(tree, mutant))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .as("the failure names a planted file")
                        .matches(message ->
                                plantedFiles.stream().anyMatch(path -> message.contains("file=" + path + " "))));
    }

    private static Set<String> plantedPaths(GeneratedPackTree tree, java.util.function.Predicate<Planted> which) {
        Set<String> out = new LinkedHashSet<>();
        tree.packs()
                .forEach(pack -> pack.planted().values().stream()
                        .filter(which)
                        .map(Planted::path)
                        .forEach(out::add));
        return out;
    }

    private static boolean isGraphInput(String file) {
        return "drg/fragment.yaml".equals(file) || (!file.contains("/") && file.endsWith(".graph.yaml"));
    }

    /**
     * Tree <em>i</em> of a run; the first is exhaustive: every row of the table is planted in every pack. A tree is
     * written once for the whole class: a check puts every file back as it found it.
     */
    private static GeneratedPackTree tree(int i) throws IOException {
        GeneratedPackTree tree = TREES_BY_INDEX.get(i);
        if (tree == null) {
            tree = GeneratedPackTree.generate(BASE_SEED + i, i, home.resolve("tree" + i), i == 0);
            TREES_BY_INDEX.put(i, tree);
        }
        return tree;
    }

    /** The production fingerprint over the given packs. */
    static String fingerprintOf(List<PackLocation> locations) {
        return new FileSystemPackSources(() -> locations).fingerprint();
    }

    // ---- the oracle ----------------------------------------------------------------------------------------------

    /**
     * The oracle: the store loads exactly the planted loaded files, and {@code fingerprintOf} changes if and only if a
     * file is {@code pack.yaml}, loaded or a graph input.
     *
     * @param tree the generated packs, already written
     * @param fingerprintOf the digest under test
     * @throws IOException when a planted file cannot be changed
     */
    static void assertExact(GeneratedPackTree tree, Function<List<PackLocation>, String> fingerprintOf)
            throws IOException {
        assertLoadIsExact(tree);
        assertDigestIsExact(tree, fingerprintOf);
    }

    private static void assertLoadIsExact(GeneratedPackTree tree) {
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        FilesystemPackStore store = new FilesystemPackStore(tree.home(), strategy);
        DomainRepositoryService service = new DomainRepositoryService(tree::locations, strategy);
        for (Pack pack : tree.packs()) {
            String where = where(tree, pack);
            FilesystemPackStore.PackRead read = store.read(new PackIdentifier(pack.directoryName()));

            Map<Kind, Set<String>> typed = new LinkedHashMap<>();
            typed.put(Kind.TACTIC, ids(read.dsos(TacticBinding.INSTANCE), TacticDso::id));
            typed.put(Kind.DIRECTIVE, ids(read.dsos(DirectiveBinding.INSTANCE), DirectiveDso::id));
            typed.put(Kind.STYLEGUIDE, ids(read.dsos(StyleguideBinding.INSTANCE), StyleguideDso::id));
            typed.put(Kind.TOOLGUIDE, ids(read.dsos(ToolguideBinding.INSTANCE), ToolguideDso::id));
            typed.put(Kind.PARADIGM, ids(read.dsos(ParadigmBinding.INSTANCE), ParadigmDso::id));
            typed.put(Kind.AGENT_PROFILE, ids(read.dsos(AgentProfileBinding.INSTANCE), AgentProfileDso::profileId));
            typed.put(
                    Kind.MISSION_STEP_CONTRACT,
                    ids(read.dsos(MissionStepContractBinding.INSTANCE), MissionStepContractDso::id));
            typed.put(Kind.GLOSSARY_PACK, ids(read.dsos(GlossaryPackBinding.INSTANCE), GlossaryPackDso::id));
            typed.put(Kind.SKILL, ids(read.dsos(SkillBinding.INSTANCE), SkillDso::id));
            typed.put(Kind.PROCEDURE, ids(read.dsos(ProcedureBinding.INSTANCE), ProcedureDso::id));
            typed.put(Kind.MISSION_TYPE, ids(read.dsos(MissionTypeBinding.INSTANCE), MissionTypeDso::id));
            typed.put(Kind.ASSET, ids(read.dsos(AssetBinding.INSTANCE), AssetDso::id));
            Set<String> observed = new LinkedHashSet<>();
            observed.add(PACK_YAML);
            for (Kind kind : Kind.values()) {
                Set<String> validIds = validIds(pack, kind);
                assertThat(typed.get(kind))
                        .as("%s the valid %s files the store read, by planted id", where, kind.token())
                        .isNotEmpty()
                        .isEqualTo(validIds);
                pack.planted().values().stream()
                        .filter(p ->
                                p.kind() == kind && p.valid() && typed.get(kind).contains(p.id()))
                        .forEach(p -> observed.add(p.path()));
            }
            // #195: a preset is no kind; the store reads the valid ones into their own slot.
            Set<String> presets = read.presets().stream()
                    .map(FilesystemPackStore.Stored::file)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            assertThat(presets).as("%s the valid presets the store read", where).isEqualTo(validPresets(pack));
            observed.addAll(presets);
            // #260: the pack document is no kind either; the store reads a valid one into its own slot.
            read.document().map(FilesystemPackStore.Stored::file).ifPresent(observed::add);
            Planted document = pack.planted().get(PackDocumentMarkdown.FILE);
            assertThat(read.document().isPresent())
                    .as("%s the pack document is held exactly when a valid one is planted", where)
                    .isEqualTo(document != null && document.valid());
            read.violations().stream().map(PackElementViolation::file).forEach(observed::add);
            Set<String> payloads = new LinkedHashSet<>();
            for (AssetFile file : read.assetFiles()) {
                payloads.add(PackFileSet.ASSETS + file.path());
                assertSameBytes(pack, PackFileSet.ASSETS + file.path(), file.payload(), where);
            }
            for (FilesystemPackStore.TemplateFile file : read.templates()) {
                payloads.add(file.place());
                assertSameBytes(pack, file.place(), file.content(), where);
                assertThat(PackFileSet.templateId(file.place()))
                        .as("%s the id of %s", where, file.place())
                        .isEqualTo(file.path());
            }
            assertThat(payloads)
                    .as("%s the asset files and templates the store read", where)
                    .isEqualTo(pack.paths(Verdict.PAYLOAD));

            Set<String> expected = new LinkedHashSet<>();
            expected.add(PACK_YAML);
            expected.addAll(pack.paths(Verdict.LOADED));
            assertThat(observed).as("%s the files the store read", where).isEqualTo(expected);

            assertThat(read.violations().stream()
                            .map(PackElementViolation::file)
                            .collect(Collectors.toSet()))
                    .as("%s the files the store reported as unreadable", where)
                    .isEqualTo(malformed(pack));
            assertThat(service.tacticsOf(new PackIdentifier(pack.name())).stream()
                            .map(Tactic::id)
                            .map(id -> id.value())
                            .collect(Collectors.toSet()))
                    .as("%s the tactics the service answers", where)
                    .isEqualTo(validIds(pack, Kind.TACTIC));
            ai.speckitty.charter.packs.domain.Pack loaded = service.load(new PackIdentifier(pack.name()));
            Set<String> held = new LinkedHashSet<>();
            loaded.assetFiles().forEach(file -> held.add(PackFileSet.ASSETS + file.path()));
            // A template is held by its id (a mission type's is <type>/<path>, #194), so its planted path is mapped to
            // it.
            loaded.templates()
                    .forEach(template -> held.add("template:" + template.id().value()));
            assertThat(held)
                    .as("%s the asset files and templates the loaded pack holds", where)
                    .isEqualTo(pack.paths(Verdict.PAYLOAD).stream()
                            .map(path -> path.startsWith(PackFileSet.ASSETS)
                                    ? path
                                    : "template:" + PackFileSet.templateId(path))
                            .collect(Collectors.toSet()));
            assertThat(loaded.assets().stream().map(asset -> asset.id().value()).collect(Collectors.toSet()))
                    .as("%s the assets the loaded pack holds", where)
                    .isEqualTo(validIds(pack, Kind.ASSET));
        }
    }

    private static void assertSameBytes(Pack pack, String path, Payload payload, String where) {
        try {
            assertThat(payload)
                    .as("%s the bytes of %s", where, path)
                    .isEqualTo(Payload.of(Files.readAllBytes(pack.directory().resolve(path))));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void assertDigestIsExact(GeneratedPackTree tree, Function<List<PackLocation>, String> fingerprintOf)
            throws IOException {
        String baseline = fingerprintOf.apply(tree.locations());
        assertThat(fingerprintOf.apply(tree.locations()))
                .as("seed=%d tree=%d the fingerprint is stable", tree.seed(), tree.index())
                .isEqualTo(baseline);
        for (Pack pack : tree.packs()) {
            List<PackLocation> only = List.of(pack.location());
            String packBaseline = fingerprintOf.apply(only);
            for (Target target : pack.targets()) {
                assertThat(changesWhenAppended(target.file(), only, fingerprintOf, packBaseline))
                        .as(
                                "seed=%d tree=%d pack=%s file=%s %s change the fingerprint",
                                tree.seed(),
                                tree.index(),
                                pack.name(),
                                target.label(),
                                target.digested() ? "must" : "must not")
                        .isEqualTo(target.digested());
            }
            assertNewFile(tree, pack, fingerprintOf, packBaseline);
        }
        assertThat(fingerprintOf.apply(tree.locations()))
                .as("seed=%d tree=%d the baseline returns", tree.seed(), tree.index())
                .isEqualTo(baseline);
    }

    private static void assertNewFile(
            GeneratedPackTree tree, Pack pack, Function<List<PackLocation>, String> fingerprintOf, String baseline)
            throws IOException {
        Planted added = tree.addFile(pack);
        String after;
        try {
            after = fingerprintOf.apply(List.of(pack.location()));
        } finally {
            Files.delete(added.file());
            pack.planted().remove(added.path());
        }
        assertThat(!after.equals(baseline))
                .as(
                        "seed=%d tree=%d pack=%s a new file=%s %s change the fingerprint",
                        tree.seed(),
                        tree.index(),
                        pack.name(),
                        added.path(),
                        added.verdict() != Verdict.DECOY ? "must" : "must not")
                .isEqualTo(added.verdict() != Verdict.DECOY);
    }

    private static boolean changesWhenAppended(
            Path file,
            List<PackLocation> locations,
            Function<List<PackLocation>, String> fingerprintOf,
            String baseline)
            throws IOException {
        byte[] original = Files.readAllBytes(file);
        byte[] changed = Arrays.copyOf(original, original.length + MARK.length);
        System.arraycopy(MARK, 0, changed, original.length, MARK.length);
        Files.write(file, changed);
        try {
            return !fingerprintOf.apply(locations).equals(baseline);
        } finally {
            Files.write(file, original);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static String where(GeneratedPackTree tree, Pack pack) {
        return "seed=%d tree=%d pack=%s root=%s".formatted(tree.seed(), tree.index(), pack.name(), pack.rootForm());
    }

    private static <D> Set<String> ids(List<D> dsos, Function<D, String> id) {
        return dsos.stream().map(id).collect(Collectors.toSet());
    }

    private static Set<String> validIds(Pack pack, Kind kind) {
        return pack.planted().values().stream()
                .filter(p -> p.kind() == kind && p.valid())
                .map(Planted::id)
                .collect(Collectors.toSet());
    }

    /** The planted presets the store must read: a loaded file of no kind but the pack document is a preset (#195). */
    private static Set<String> validPresets(Pack pack) {
        return pack.planted().values().stream()
                .filter(p -> p.verdict() == Verdict.LOADED && p.kind() == null && p.valid())
                .filter(p -> !p.path().equals(PackDocumentMarkdown.FILE))
                .map(Planted::path)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> malformed(Pack pack) {
        return pack.planted().values().stream()
                .filter(p -> p.verdict() == Verdict.LOADED && !p.valid())
                .map(Planted::path)
                .collect(Collectors.toSet());
    }
}
