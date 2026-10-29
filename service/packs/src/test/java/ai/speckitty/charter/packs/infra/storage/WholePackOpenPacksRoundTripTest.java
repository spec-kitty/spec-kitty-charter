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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole-pack seam over the real packs (#189, SC-002; #193 FR-010, NFR-001): every open-packs pack and every pack of
 * this repository's {@code packs/} that has a {@code pack.yaml} loads with exactly the violations pinned for it as
 * known failures in {@code whole-pack/known-failures.yaml} (built-in only; none for every other pack), and every pack
 * that loads clean writes to a fresh directory and reloads with the same meaning: every bound kind, the asset files,
 * the templates and every DRG document. The files are compared too: asset sidecars by meaning (ADR-0036), asset files
 * and templates by SHA-256.
 *
 * <p>The built-in pack's six sidecars anchor {@code path} at the pack root and are refused, and its two presets and its
 * manifest are not in the sanitized shapes and are refused too (#195, ADR-0042); a temporary copy with the six paths,
 * the two presets and the manifest corrected (the open-packs fixes, applied here to a test fixture only) round-trips
 * whole, so built-in stays covered. The manifest's derived fields are verified on both sides
 * ({@link PackManifests#stale}) and the rest of it compared by meaning. A pinned refusal is one per file: the file's
 * reasons in the store's order, joined by {@code "; "}, as the gate pins them.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout.
 */
@Tag("open-packs")
class WholePackOpenPacksRoundTripTest {

    private static final String BUILT_IN = "built-in";
    private static final String SIDECAR_SUFFIX = ".asset.yaml";
    private static final String REFUSED = "refused: ";
    private static final String README = "README.md";

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());
    private final WholePackExpectation expectation = WholePackExpectation.fromClasspath();

    /** What a pack holds of the asset and template kinds, counted from the aggregate. */
    record Counts(int sidecars, int assetFiles, int binary, int templates) {}

    private static Path repositoryPacks() {
        return Path.of(System.getProperty("charter.repositoryRoot")).resolve("packs");
    }

    private static List<Path> packDirectories() throws IOException {
        List<Path> roots = List.of(OpenPacksCorpus.packsDirectory(), repositoryPacks());
        List<Path> packs = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> dirs = Files.list(root)) {
                dirs.filter(dir -> Files.isRegularFile(dir.resolve(PackFileSet.PACK_YAML)))
                        .sorted()
                        .forEach(packs::add);
            }
        }
        return packs;
    }

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
        out.put("graphs", loaded.graphs());
        out.put("layout", loaded.layout().paths());
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
     * failures ({@code known-failures.yaml}), which pin the open-packs built-in pack; every other pack has none.
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

    /** The built-in pack's pinned refusals, each as {@code <file>: refused: <reasons>}. */
    private Set<String> pinnedRefusals() {
        return expectation.knownFailures().stream()
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

    // ---- pinned counts -------------------------------------------------------------------------------------------

    /**
     * Acceptance check 1: what the open packs hold of our kinds at the pin, and what this repository's packs hold
     * counted from their own files. A pack with a {@code pack.yaml} is counted from its whole-pack load; the
     * {@code qa} and {@code event-driven} packs have none, so no store loads them, and they are counted from the
     * {@link PackFileSet} listing any load reads. The internal pack's whole-pack load time is logged (NFR-004), not
     * asserted.
     */
    @Test
    void theAssetAndTemplateCountsOfEveryPackArePinned() throws IOException {
        Path openPacks = OpenPacksCorpus.packsDirectory();
        long start = System.nanoTime();
        WholePackStore.LoadedPack internal = store.load(openPacks.resolve("internal"));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("NFR-004 whole-pack load of open-packs internal: " + elapsedMillis + " ms ("
                + internal.pack().assetFiles().stream()
                        .mapToInt(file -> file.payload().size())
                        .sum()
                + " payload bytes)");

        assertThat(countsOf(internal)).isEqualTo(new Counts(10, 10, 4, 0));
        assertThat(binaryExtensions(internal)).containsExactly(".otf", ".otf", ".otf", ".png");
        assertThat(listed(openPacks.resolve("qa"))).isEqualTo(new Counts(3, 3, 0, 0));
        assertThat(listed(openPacks.resolve("event-driven"))).isEqualTo(new Counts(1, 1, 0, 0));
        assertThat(listed(openPacks.resolve("internal"))).isEqualTo(countsOf(internal));

        Path repositoryInternal = repositoryPacks().resolve("internal");
        Counts internalOnDisk = countedOnDisk(repositoryInternal);
        assertThat(internalOnDisk.sidecars())
                .as("packs/internal holds sidecars")
                .isPositive();
        assertThat(countsOf(store.load(repositoryInternal)))
                .as("packs/internal")
                .isEqualTo(internalOnDisk);
        Path repositoryQa = repositoryPacks().resolve("qa");
        Counts qaOnDisk = countedOnDisk(repositoryQa);
        assertThat(qaOnDisk.sidecars()).as("packs/qa holds sidecars").isPositive();
        assertThat(listed(repositoryQa)).as("packs/qa").isEqualTo(qaOnDisk);
    }

    /** The counts as {@link PackFileSet} lists the files, for a pack no store loads (it has no {@code pack.yaml}). */
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
                regularFilesUnder(pack.resolve("templates")).size());
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

    // ---- built-in, as pinned and as corrected --------------------------------------------------------------------

    /**
     * Acceptance checks 2 and 3: the built-in pack refuses exactly its six pinned sidecars, its two pinned presets and
     * its pinned manifest; a copy with the six {@code path} values corrected, the two presets rewritten in the
     * sanitized shape and the manifest rewritten in the sanitized shape (the open-packs fixes, applied to a test
     * fixture only) loads clean and round-trips whole, its manifest fresh on both sides.
     */
    @Test
    void aCorrectedCopyOfBuiltInRoundTripsWholeWhileTheOriginalRefusesExactlyThePinnedNine(@TempDir Path host)
            throws IOException {
        Path builtIn = OpenPacksCorpus.packsDirectory().resolve(BUILT_IN);
        WholePackStore.LoadedPack original = store.load(builtIn);

        assertThat(pinnedRefusals()).hasSize(9);
        assertThat(refusalsByFile(original)).hasSize(9);
        assertThat(original.pack().violations())
                .allSatisfy(v -> assertThat(v.kind()).isIn("asset", "schema"))
                .filteredOn(v -> v.kind().equals("schema"))
                .extracting(PackElementViolation::file)
                .containsOnly("presets/default.yaml", "presets/minimal.yaml", PackFileSet.MANIFEST);
        assertThat(unpinned(builtIn, original)).isEmpty();

        Path corrected = copyTree(builtIn, host.resolve("corrected").resolve(BUILT_IN));
        int fixed = 0;
        for (Path sidecar : regularFilesUnder(corrected.resolve("assets"))) {
            if (sidecar.getFileName().toString().endsWith(SIDECAR_SUFFIX)) {
                String text = Files.readString(sidecar);
                String fixedText = text.replace("\npath: assets/", "\npath: ");
                fixed += fixedText.equals(text) ? 0 : 1;
                Files.writeString(sidecar, fixedText);
            }
        }
        for (String preset : List.of("default", "minimal")) {
            Path file = corrected.resolve(PresetFiles.pathOf(preset));
            Files.writeString(file, sanitizedPreset(Files.readString(file)));
        }
        Path manifest = corrected.resolve(PackFileSet.MANIFEST);
        Files.writeString(manifest, sanitizedManifest(Files.readString(manifest), corrected));
        long start = System.nanoTime();
        WholePackStore.LoadedPack loaded = store.load(corrected);
        System.out.println("NFR-004 whole-pack load of the corrected built-in pack, presets and manifest included: "
                + (System.nanoTime() - start) / 1_000_000 + " ms");

        assertThat(fixed).isEqualTo(6);
        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.pack().presets()).extracting(Preset::id).containsExactly("default", "minimal");
        assertThat(loaded.unmodelled())
                .doesNotContain("presets/default.yaml", "presets/minimal.yaml", PackFileSet.MANIFEST);
        assertThat(loaded.manifest()).isPresent();
        assertThat(loaded.manifest().orElseThrow().constituents())
                .as("the modelled constituents only, until #194 binds procedures")
                .hasSize(272 - 23);
        assertThat(countsOf(loaded)).isEqualTo(new Counts(6, 7, 0, 2));
        assertThat(loaded.pack().templates())
                .extracting(template -> template.id().value())
                .containsExactly("writing/meeting-discussion-notes.md", "writing/meeting-minutes.md");
        assertThat(roundTrip(corrected, loaded, host.resolve("written").resolve(BUILT_IN)))
                .isEmpty();
        assertThat(payloadsLeftUnmodelled(corrected, loaded)).isEmpty();
        assertThat(original.pack().violations())
                .filteredOn(v -> v.kind().equals("asset"))
                .hasSize(6)
                .extracting(PackElementViolation::file)
                .allSatisfy(
                        file -> assertThat(file).startsWith(PackFileSet.ASSETS).endsWith(SIDECAR_SUFFIX));
    }

    /**
     * A built-in preset rewritten in the sanitized shape that spec-kitty/spec-kitty-open-packs#TBD-preset-shape asks
     * for (OP-1): {@code name} becomes {@code id}, {@code schema_version} is added, {@code mission_type_activations}
     * becomes {@code activated_mission_types}, every item becomes {@code {id}}, and a directive named by its file stem
     * ({@code 001-...}) is named by its id ({@code DIRECTIVE_001}). No item is marked must-have.
     */
    private static String sanitizedPreset(String text) {
        Map<?, ?> raw = (Map<?, ?>) Yaml.load(text);
        YamlDumpEmitter out = new YamlDumpEmitter()
                .scalar("schema_version", "1.0")
                .scalar("id", String.valueOf(raw.get("name")))
                .scalar("description", String.valueOf(raw.get("description")));
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.startsWith("activated_")) {
                out.mappings(key, items(entry.getValue(), "activated_directives".equals(key)));
            }
        }
        out.mappings("activated_mission_types", items(raw.get("mission_type_activations"), false));
        return out.dump();
    }

    /**
     * The built-in manifest rewritten in the sanitized shape that spec-kitty/spec-kitty-open-packs#TBD-manifest-shape
     * asks for (OP-2): only {@code schema_version}, {@code manifest_hash}, the constituents (each {@code kind},
     * {@code id}, {@code path}, {@code content_hash}) and the presets (keyed by {@code id}), every derived field
     * recomputed over {@code pack}. Until #194 binds procedures, a constituent of a kind no binding models is left
     * out, as the round trip cannot write it.
     */
    private static String sanitizedManifest(String text, Path pack) {
        Map<?, ?> raw = (Map<?, ?>) Yaml.load(text);
        List<PackManifestDso.Constituent> constituents = new ArrayList<>();
        for (Object item : (List<?>) raw.get("constituents")) {
            Map<?, ?> entry = (Map<?, ?>) item;
            String kind = String.valueOf(entry.get("kind"));
            if (ArtifactKind.fromToken(kind).flatMap(KindBindings::of).isPresent()) {
                constituents.add(new PackManifestDso.Constituent(
                        kind,
                        String.valueOf(entry.get("id")),
                        String.valueOf(entry.get("path")),
                        String.valueOf(entry.get("content_hash"))));
            }
        }
        List<PackManifestDso.PresetEntry> presets = new ArrayList<>();
        for (Object item : (List<?>) raw.get("presets")) {
            Map<?, ?> entry = (Map<?, ?>) item;
            presets.add(new PackManifestDso.PresetEntry(
                    String.valueOf(entry.get("name")),
                    String.valueOf(entry.get("path")),
                    String.valueOf(entry.get("content_hash"))));
        }
        PackManifestDso sanitized = new PackManifestDso("1.0", "0".repeat(64), constituents, presets);
        return PackManifestYaml.write(PackManifests.recompute(sanitized, path -> bytesOf(pack.resolve(path)))
                .stamped());
    }

    private static List<Map<String, Object>> items(Object list, boolean directives) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : (List<?>) list) {
            String id = String.valueOf(item);
            Map<String, Object> entry = YamlDumpEmitter.mapping();
            entry.put("id", directives && id.matches("\\d{3}-.*") ? "DIRECTIVE_" + id.substring(0, 3) : id);
            out.add(entry);
        }
        return out;
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
     * The {@code qa} packs have no {@code pack.yaml} and are no whole pack; their profiles are covered file by file by
     * {@code PackDoctrineCorpusRoundTripTest}.
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
                .as("the 26 built-in profiles")
                .isEqualTo(26);
    }
}
