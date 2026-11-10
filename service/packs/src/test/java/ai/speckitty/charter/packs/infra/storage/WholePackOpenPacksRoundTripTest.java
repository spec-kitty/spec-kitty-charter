package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.Toolguide;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole-pack seam over the real packs (#189, SC-002; #193 FR-010, NFR-001): every open-packs pack that has a
 * {@code pack.yaml} loads with exactly the violations pinned for it as
 * known failures in {@code whole-pack/known-failures.yaml} (none at this pin), and every pack
 * that loads clean writes to a fresh directory and reloads with the same meaning: every bound kind, the asset files,
 * the templates, the presets, the pack document and every DRG document. The files are compared too: asset sidecars and
 * presets by meaning (ADR-0036), the pack document by its Markdown model (#260), asset files and templates by SHA-256.
 *
 * <p>The built-in pack's six sidecars anchor {@code path} at {@code assets/} since open-packs#75, so nothing is pinned
 * against it and it round-trips as it stands. The manifest's derived fields are verified on both sides
 * ({@link PackManifests#stale}) and the rest of it compared by meaning. A pinned refusal is one per file: the file's
 * reasons in the store's order, joined by {@code "; "}, as the gate pins them.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout.
 */
@Tag("open-packs")
class WholePackOpenPacksRoundTripTest {

    /** Fail with the pin's own message rather than as a count or diff mismatch when the checkout is at another commit. */
    @BeforeAll
    static void theCheckoutIsAtThePin() {
        OpenPacksCorpus.assertPinned();
    }

    private static final String BUILT_IN = "built-in";
    private static final String SIDECAR_SUFFIX = ".asset.yaml";
    private static final String REFUSED = "refused: ";
    private static final String README = "README.md";

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());
    private final WholePackExpectation expectation = WholePackExpectation.fromClasspath();

    /** What a pack holds of the asset and template kinds, counted from the aggregate. */
    record Counts(int sidecars, int assetFiles, int binary, int templates) {}

    private static List<Path> packDirectories() throws IOException {
        List<Path> packs = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(OpenPacksCorpus.packsDirectory())) {
            dirs.filter(dir -> Files.isRegularFile(dir.resolve(PackFileSet.PACK_YAML)))
                    .sorted()
                    .forEach(packs::add);
        }
        return packs;
    }

    /**
     * The meaning the gate compares. Edges are compared only through {@code graphs}, the pack's graph documents: no
     * artifact DSO carries a relationship field (ADR-0064), so no edge is compared twice or from a second source.
     */
    private static Map<String, Object> meaning(WholePackStore.LoadedPack loaded) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("descriptor", loaded.pack().descriptor());
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            List<Object> dsos = new ArrayList<>();
            for (CharterArtifact artifact : loaded.pack().artifacts(kind.kind())) {
                dsos.add(kind.dsoOf(artifact));
            }
            out.put(kind.kind().token(), dsos);
        }
        out.put("assetFiles", loaded.pack().assetFiles());
        out.put(
                "templates",
                loaded.pack().templates().stream()
                        .map(template ->
                                template.id().value() + "=" + template.content().sha256())
                        .toList());
        out.put("presets", loaded.pack().presets());
        out.put("document", loaded.pack().document());
        out.put("graphs", loaded.graphs());
        out.put("layout", loaded.layout().paths());
        out.put("fragments", PackFixtures.fragments(loaded));
        return out;
    }

    // ---- every real pack -----------------------------------------------------------------------------------------

    @Test
    void everyRealPackLoadsWithItsPinnedRefusalsAndEveryCleanPackRoundTripsWhole(@TempDir Path host)
            throws IOException {
        List<Path> packs = packDirectories();
        List<String> problems = new ArrayList<>();
        int artifacts = 0;
        int payloads = 0;
        for (Path pack : packs) {
            WholePackStore.LoadedPack loaded = store.load(pack);
            problems.addAll(unpinned(pack, loaded));
            problems.addAll(payloadsLeftUnmodelled(pack, loaded));
            if (!loaded.pack().violations().isEmpty()) {
                continue;
            }
            artifacts += loaded.pack().artifacts().size();
            payloads += loaded.pack().assetFiles().size()
                    + loaded.pack().templates().size();
            Path target = host.resolve(String.valueOf(packs.indexOf(pack))).resolve(String.valueOf(pack.getFileName()));
            problems.addAll(roundTrip(pack, loaded, target));
        }
        assertThat(problems).isEmpty();
        assertThat(packs).isNotEmpty();
        assertThat(artifacts).isPositive();
        assertThat(payloads).isPositive();
    }

    /**
     * The violations of a pack against its pinned refusals, matched by file and reason (R10): a violation nobody
     * pinned, and a pinned refusal that no longer occurs, are both problems. The refusals are the gate's own known
     * failures ({@code known-failures.yaml}), which can only pin the open-packs built-in pack, and hold none at this pin.
     */
    private List<String> unpinned(Path pack, WholePackStore.LoadedPack loaded) {
        Set<String> pinned =
                pack.equals(OpenPacksCorpus.packsDirectory().resolve(BUILT_IN)) ? pinnedRefusals() : Set.of();
        Set<String> actual = refusalsByFile(loaded);
        List<String> out = new ArrayList<>();
        actual.stream()
                .filter(v -> !pinned.contains(v))
                .forEach(v -> out.add(pack + " has an unpinned violation " + v));
        pinned.stream().filter(p -> !actual.contains(p)).forEach(p -> out.add(pack + " no longer refuses " + p));
        return out;
    }

    /** A pack's violations, one per file: {@code <file>: refused: <reasons>}, the reasons joined by {@code "; "}. */
    private static Set<String> refusalsByFile(WholePackStore.LoadedPack loaded) {
        Map<String, List<String>> byFile = new LinkedHashMap<>();
        for (PackElementViolation violation : loaded.pack().violations()) {
            byFile.computeIfAbsent(violation.file(), file -> new ArrayList<>()).add(violation.reason());
        }
        Set<String> out = new TreeSet<>();
        byFile.forEach((file, reasons) -> out.add(file + ": " + REFUSED + String.join("; ", reasons)));
        return out;
    }

    /**
     * The built-in pack's pinned load refusals, each as {@code <file>: refused: <reasons>}. A {@code round_trip_only}
     * entry is not one: the load accepts that file, so it has no place in this ratchet — the gate still pins it.
     */
    private Set<String> pinnedRefusals() {
        return expectation.knownFailures().stream()
                .filter(failure -> !failure.roundTripOnly())
                .filter(failure -> failure.at().startsWith(REFUSED))
                .map(failure -> failure.path() + ": " + failure.at())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * No file under {@code assets/} or {@code templates/} is left unmodelled, other than the excluded READMEs and the
     * files the load refused (a refused sidecar is a violation, checked against the pinned list).
     */
    private static List<String> payloadsLeftUnmodelled(Path pack, WholePackStore.LoadedPack loaded) {
        Set<String> refused = loaded.pack().violations().stream()
                .map(PackElementViolation::file)
                .collect(Collectors.toSet());
        return loaded.unmodelled().stream()
                .filter(file -> file.startsWith(PackFileSet.ASSETS) || file.startsWith(PackFileSet.TEMPLATES))
                .filter(file -> !file.endsWith("/" + README) && !refused.contains(file))
                .map(file -> pack + " leaves " + file + " unmodelled")
                .toList();
    }

    /**
     * Writes a clean pack, reloads it and compares the aggregates, then the files: each sidecar with the ADR-0036
     * comparator, each asset file and template by SHA-256.
     */
    private List<String> roundTrip(Path pack, WholePackStore.LoadedPack loaded, Path target) throws IOException {
        List<String> out = new ArrayList<>();
        store.write(loaded, target);
        WholePackStore.LoadedPack reloaded = store.load(target);
        if (!meaning(reloaded).equals(meaning(loaded))) {
            out.add(pack + " reloads with a different meaning");
        }
        for (Asset asset : loaded.pack().assets()) {
            String file = loaded.layout().pathOf(asset);
            MeaningComparator.adr0036()
                    .compareTexts("asset", Files.readString(pack.resolve(file)), Files.readString(target.resolve(file)))
                    .ifPresent(difference -> out.add(pack + "/" + file + " differs: " + difference));
        }
        for (AssetFile file : loaded.pack().assetFiles()) {
            out.addAll(bytesDiffer(pack, target, PackFileSet.ASSETS + file.path()));
        }
        for (Template template : loaded.pack().templates()) {
            out.addAll(bytesDiffer(pack, target, loaded.layout().pathOf(template)));
        }
        if (loaded.manifest().isPresent()) {
            out.addAll(manifestProblems(pack, target, loaded, reloaded));
        }
        for (Preset preset : loaded.pack().presets()) {
            String file = PresetFiles.pathOf(preset.id());
            MeaningComparator.adr0036()
                    .compareTexts(
                            PresetFiles.KIND,
                            Files.readString(pack.resolve(file)),
                            Files.readString(target.resolve(file)))
                    .ifPresent(difference -> out.add(pack + "/" + file + " differs: " + difference));
        }
        if (Files.isRegularFile(pack.resolve(PackDocumentMarkdown.FILE))
                != loaded.pack().document().isPresent()) {
            out.add(pack + "/" + PackDocumentMarkdown.FILE + ": the file and the loaded document disagree");
        }
        if (loaded.pack().document().isPresent()) {
            String file = PackDocumentMarkdown.FILE;
            MeaningComparator.adr0036()
                    .compareMarkdown(Files.readString(pack.resolve(file)), Files.readString(target.resolve(file)))
                    .forEach(difference -> out.add(pack + "/" + file + " differs: " + difference));
        }
        return out;
    }

    /** The manifest of a clean round trip: fresh on both sides, and the same by meaning. */
    private static List<String> manifestProblems(
            Path pack, Path target, WholePackStore.LoadedPack loaded, WholePackStore.LoadedPack reloaded)
            throws IOException {
        List<String> out = new ArrayList<>();
        PackManifests.stale(loaded.manifest().orElseThrow(), path -> bytesOf(pack.resolve(path)))
                .forEach(field -> out.add(pack + "/" + PackFileSet.MANIFEST + " is stale at " + field));
        if (reloaded.manifest().isEmpty()) {
            out.add(target + " reloads without its manifest");
            return out;
        }
        PackManifests.stale(reloaded.manifest().get(), path -> bytesOf(target.resolve(path)))
                .forEach(field -> out.add(target + "/" + PackFileSet.MANIFEST + " is written stale at " + field));
        MeaningComparator.adr0036()
                .compareTexts(
                        MeaningComparator.MANIFEST,
                        Files.readString(pack.resolve(PackFileSet.MANIFEST)),
                        Files.readString(target.resolve(PackFileSet.MANIFEST)))
                .ifPresent(difference -> out.add(pack + "/" + PackFileSet.MANIFEST + " differs: " + difference));
        return out;
    }

    private static Optional<byte[]> bytesOf(Path file) {
        return Files.isRegularFile(file) ? FilesystemPackStore.readBytes(file) : Optional.empty();
    }

    private static List<String> bytesDiffer(Path pack, Path target, String file) throws IOException {
        String source = sha256(pack.resolve(file));
        String written = sha256(target.resolve(file));
        return source.equals(written)
                ? List.of()
                : List.of(pack + "/" + file + " is written with sha256 " + written + ", not " + source);
    }

    // ---- counts held by the files
    // -------------------------------------------------------------------------------------------

    /**
     * Acceptance check 1: what the open packs hold of our kinds, read from the files themselves. A pack with a {@code pack.yaml} is included
     * from its whole-pack load; the
     * {@code qa} and {@code event-driven} packs have none, so no store loads them, and they are counted from the
     * {@link PackFileSet} listing any load reads. The internal pack's whole-pack load time is logged (NFR-004), not
     * asserted.
     */
    @Test
    void theAssetAndTemplateCountsOfEveryPackMatchTheFilesOnDisk() throws IOException {
        Path openPacks = OpenPacksCorpus.packsDirectory();
        long start = System.nanoTime();
        WholePackStore.LoadedPack internal = store.load(openPacks.resolve("internal"));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("NFR-004 whole-pack load of open-packs internal: " + elapsedMillis + " ms ("
                + internal.pack().assetFiles().stream()
                        .mapToInt(file -> file.payload().size())
                        .sum()
                + " payload bytes)");

        // Non-vacuity: internal holds assets, binary ones among them (fonts, an image).
        assertThat(countsOf(internal).assetFiles()).as("internal asset files").isPositive();
        assertThat(countsOf(internal).binary())
                .as("internal binary asset files")
                .isPositive();
        assertThat(binaryExtensions(internal)).hasSize(countsOf(internal).binary());
        assertThat(listed(openPacks.resolve("internal"))).isEqualTo(countsOf(internal));

        // The counts read from the files, without the store, match what the store and the listing report.
        Counts internalOnDisk = countedOnDisk(openPacks.resolve("internal"));
        assertThat(internalOnDisk.sidecars()).as("internal holds sidecars").isPositive();
        assertThat(countsOf(internal)).as("internal").isEqualTo(internalOnDisk);
        Counts qaOnDisk = countedOnDisk(openPacks.resolve("qa"));
        assertThat(qaOnDisk.sidecars()).as("qa holds sidecars").isPositive();
        assertThat(listed(openPacks.resolve("qa"))).as("qa").isEqualTo(qaOnDisk);
        // qa's mission type holds templates (#194: a mission type's templates are templates).
        assertThat(qaOnDisk.templates()).as("qa holds templates").isPositive();
        assertThat(listed(openPacks.resolve("event-driven")))
                .as("event-driven")
                .isEqualTo(countedOnDisk(openPacks.resolve("event-driven")));
    }

    /**
     * #194: what built-in and internal hold of procedures and toolguides is what their files hold, each toolguide with
     * its guide. {@code qa} is counted file by file by {@code ProcedureAndGuideCorpusTest}.
     */
    @Test
    void theProcedureAndToolguideCountsOfEveryWholePackMatchTheFilesOnDisk() throws IOException {
        Path openPacks = OpenPacksCorpus.packsDirectory();
        Map<String, Path> packs = new LinkedHashMap<>();
        packs.put(BUILT_IN, openPacks.resolve(BUILT_IN));
        packs.put("internal", openPacks.resolve("internal"));
        for (Map.Entry<String, Path> entry : packs.entrySet()) {
            String name = entry.getKey();
            Path pack = entry.getValue();
            WholePackStore.LoadedPack loaded = store.load(pack);
            List<Toolguide> toolguides = loaded.pack().artifacts(ArtifactKind.TOOLGUIDE).stream()
                    .map(Toolguide.class::cast)
                    .toList();
            assertThat(toolguides)
                    .as("%s: every toolguide carries its guide", name)
                    .allSatisfy(toolguide -> assertThat(toolguide.guide()).isNotNull());
            // Counted from the files, without the store: one procedure per *.procedure.yaml, one toolguide per
            // *.toolguide.yaml, and its guide beside it.
            long procedureFiles = filesNamed(pack.resolve("procedures"), ".procedure.yaml");
            long toolguideFiles = filesNamed(pack.resolve("toolguides"), ".toolguide.yaml");
            assertThat(procedureFiles).as("%s: procedure files", name).isPositive();
            assertThat(toolguideFiles).as("%s: toolguide files", name).isPositive();
            assertThat(loaded.pack().artifacts(ArtifactKind.PROCEDURE))
                    .as("%s: procedures held", name)
                    .hasSize((int) procedureFiles);
            assertThat(toolguides).as("%s: toolguides held", name).hasSize((int) toolguideFiles);
            assertThat(filesNamed(pack.resolve("toolguides"), ".guide.md"))
                    .as("%s: guide files", name)
                    .isEqualTo(toolguideFiles);
        }
    }

    private static long filesNamed(Path directory, String suffix) throws IOException {
        return regularFilesUnder(directory).stream()
                .filter(file -> file.getFileName().toString().endsWith(suffix))
                .count();
    }

    /** The ids of the top-level templates on disk: every file under {@code templates/}, relative, in '/' form. */
    private static List<String> topLevelTemplatesOnDisk(Path pack) throws IOException {
        Path templates = pack.resolve("templates");
        return regularFilesUnder(templates).stream()
                .map(file -> templates.relativize(file).toString().replace('\\', '/'))
                .toList();
    }

    /** The counts as {@link PackFileSet} lists the files, without the store. */
    private static Counts listed(Path pack) throws IOException {
        List<String> files = PackFileSet.assetFiles(pack);
        int binary = 0;
        for (String file : files) {
            binary += isUtf8(Payload.of(Files.readAllBytes(pack.resolve(file)))) ? 0 : 1;
        }
        return new Counts(
                PackFileSet.files(pack, AssetBinding.INSTANCE.kind()).size(),
                files.size(),
                binary,
                PackFileSet.templates(pack).size());
    }

    private static Counts countsOf(WholePackStore.LoadedPack loaded) {
        List<AssetFile> files = loaded.pack().assetFiles();
        return new Counts(
                loaded.pack().assets().size(),
                files.size(),
                (int) files.stream().filter(file -> !isUtf8(file.payload())).count(),
                loaded.pack().templates().size());
    }

    private static List<String> binaryExtensions(WholePackStore.LoadedPack loaded) {
        return loaded.pack().assetFiles().stream()
                .filter(file -> !isUtf8(file.payload()))
                .map(file -> file.path().substring(file.path().lastIndexOf('.')))
                .sorted()
                .toList();
    }

    /** The counts read from the files, without the store: every sidecar, every other file under assets/ but READMEs. */
    private static Counts countedOnDisk(Path pack) throws IOException {
        List<Path> underAssets = regularFilesUnder(pack.resolve("assets"));
        List<Path> payloads = underAssets.stream()
                .filter(file -> !file.getFileName().toString().endsWith(SIDECAR_SUFFIX))
                .filter(file -> !file.getFileName().toString().equals(README))
                .toList();
        int binary = 0;
        for (Path file : payloads) {
            binary += isUtf8(Payload.of(Files.readAllBytes(file))) ? 0 : 1;
        }
        int sidecars = (int) underAssets.stream()
                .filter(file -> file.getFileName().toString().endsWith(SIDECAR_SUFFIX))
                .count();
        return new Counts(
                sidecars,
                payloads.size(),
                binary,
                regularFilesUnder(pack.resolve("templates")).size() + missionTemplatesOnDisk(pack));
    }

    /**
     * The mission types' templates on disk (#194): every file under {@code missions/<type>/templates/} but the
     * orientation READMEs, hidden files and the excluded {@code plan-field-declaration.yaml}.
     */
    private static int missionTemplatesOnDisk(Path pack) throws IOException {
        Path missions = pack.resolve("missions");
        if (!Files.isDirectory(missions)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> types = Files.list(missions)) {
            for (Path type : types.filter(Files::isDirectory).toList()) {
                count += (int) regularFilesUnder(type.resolve("templates")).stream()
                        .map(file -> file.getFileName().toString())
                        .filter(name -> !name.equals(README)
                                && !name.startsWith(".")
                                && !name.equals("plan-field-declaration.yaml"))
                        .count();
            }
        }
        return count;
    }

    private static List<Path> regularFilesUnder(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    // ---- built-in, as pinned ------------------------------------------------------------------------------------

    /**
     * Acceptance checks 2 and 3, now on the real pack: built-in loads clean with every kind it holds, nothing is pinned
     * against it, and it round-trips whole with its manifest fresh on both sides. A copy with one sidecar put back to the
     * old root anchor is refused for exactly that file, so a load that stopped judging payloads cannot pass here.
     */
    @Test
    void theBuiltInPackRoundTripsWholeWhileASidecarPutBackToTheRootAnchorIsRefused(@TempDir Path host)
            throws IOException {
        Path builtIn = OpenPacksCorpus.packsDirectory().resolve(BUILT_IN);
        long start = System.nanoTime();
        WholePackStore.LoadedPack loaded = store.load(builtIn);
        System.out.println("NFR-004 whole-pack load of the built-in pack, manifest included: "
                + (System.nanoTime() - start) / 1_000_000 + " ms");

        assertThat(pinnedRefusals()).isEmpty();
        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.pack().presets()).extracting(Preset::id).containsExactly("default", "minimal");
        assertThat(loaded.unmodelled())
                .doesNotContain("presets/default.yaml", "presets/minimal.yaml", PackFileSet.MANIFEST);
        assertThat(loaded.manifest()).isPresent();
        assertThat(loaded.manifest().orElseThrow().constituents())
                .as("every constituent: #194 binds procedures, so every listed kind is modelled")
                .isNotEmpty();
        // The manifest's presets are the presets the pack holds, and the built-in preset ids are among them.
        assertThat(loaded.pack().presets())
                .extracting(Preset::id)
                .containsExactlyInAnyOrderElementsOf(loaded.manifest().orElseThrow().presets().stream()
                        .map(entry -> entry.id())
                        .toList());
        // The assets, asset files and templates held are the ones on disk (top-level and mission-type templates, #194).
        assertThat(countsOf(loaded)).isEqualTo(countedOnDisk(builtIn));
        assertThat(countsOf(loaded).templates()).as("built-in templates").isPositive();
        assertThat(loaded.pack().templates())
                .filteredOn(template -> template.missionType().isEmpty())
                .extracting(template -> template.id().value())
                .containsExactlyInAnyOrderElementsOf(topLevelTemplatesOnDisk(builtIn));
        assertThat(roundTrip(builtIn, loaded, host.resolve("written").resolve(BUILT_IN)))
                .isEmpty();
        assertThat(payloadsLeftUnmodelled(builtIn, loaded)).isEmpty();

        Path copy = copyTree(builtIn, host.resolve("root-anchored").resolve(BUILT_IN));
        String file = "assets/audiences/line_manager.md" + SIDECAR_SUFFIX;
        String sidecar = Files.readString(copy.resolve(file));
        assertThat(sidecar).contains("\npath: audiences/line_manager.md");
        Files.writeString(copy.resolve(file), sidecar.replace("\npath: ", "\npath: assets/"));

        assertThat(refusalsByFile(store.load(copy)))
                .containsExactly(
                        file + ": " + REFUSED + "payload assets/assets/audiences/line_manager.md does not exist");
    }

    private static Path copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                Path to = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(path, to);
                }
            }
        }
        return target;
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static String sha256(Path file) throws IOException {
        return Payload.of(Files.readAllBytes(file)).sha256();
    }

    private static boolean isUtf8(Payload payload) {
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload.bytes()));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /**
     * #190: every agent-profile file of every real pack is in the aggregate, so a walk that missed them cannot pass.
     * Every pack has a {@code pack.yaml} at this pin, so {@code qa}'s profiles are counted here too.
     */
    @Test
    void everyAgentProfileFileOfARealPackLoadsAsAProfile() throws IOException {
        Map<String, Integer> onDisk = new LinkedHashMap<>();
        Map<String, Integer> loaded = new LinkedHashMap<>();
        for (Path pack : packDirectories()) {
            try (Stream<Path> files = Files.walk(pack)) {
                onDisk.put(pack.toString(), (int) files.filter(Files::isRegularFile)
                        .filter(file -> file.getFileName().toString().endsWith(".agent.yaml"))
                        .count());
            }
            loaded.put(
                    pack.toString(),
                    store.load(pack)
                            .pack()
                            .artifacts(ArtifactKind.AGENT_PROFILE)
                            .size());
        }

        assertThat(loaded).isEqualTo(onDisk);
        assertThat(loaded.values().stream().mapToInt(Integer::intValue).sum())
                .as("the 26 built-in profiles and qa's 5")
                .isEqualTo(31);
    }
}
