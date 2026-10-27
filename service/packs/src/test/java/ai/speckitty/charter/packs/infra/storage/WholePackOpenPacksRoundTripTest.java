package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole-pack seam over the real packs (#189, SC-002; #193 FR-010, NFR-001): every open-packs pack and every pack of
 * this repository's {@code packs/} that has a {@code pack.yaml} loads with exactly the violations pinned for it in
 * {@code whole-pack/expected-red.yaml} (none for most packs), and every pack that loads clean writes to a fresh
 * directory and reloads with the same meaning: every bound kind, the asset files, the templates and every DRG document.
 * The files are compared too: asset sidecars by meaning (ADR-0036), asset files and templates by SHA-256.
 *
 * <p>The built-in pack's six sidecars anchor {@code path} at the pack root and are refused; a temporary copy with the
 * six paths corrected (the open-packs fix, applied here to a test fixture only) round-trips whole, so built-in stays
 * covered.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout.
 */
@Tag("open-packs")
class WholePackOpenPacksRoundTripTest {

    private static final String BUILT_IN = "built-in";
    private static final String SIDECAR_SUFFIX = ".asset.yaml";
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
     * pinned, and a pinned refusal that no longer occurs, are both problems.
     */
    private List<String> unpinned(Path pack, WholePackStore.LoadedPack loaded) {
        Set<String> pinned = expectation.refusals(String.valueOf(pack.getFileName())).stream()
                .map(refusal -> refusal.path() + ": " + refusal.refused())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> actual = loaded.pack().violations().stream()
                .map(violation -> violation.file() + ": " + violation.reason())
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> out = new ArrayList<>();
        actual.stream()
                .filter(v -> !pinned.contains(v))
                .forEach(v -> out.add(pack + " has an unpinned violation " + v));
        pinned.stream().filter(p -> !actual.contains(p)).forEach(p -> out.add(pack + " no longer refuses " + p));
        return out;
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
        return out;
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
     * Acceptance check 2: the built-in pack refuses exactly its six pinned sidecars; a copy with the six {@code path}
     * values corrected (the open-packs fix, applied to a test fixture only) loads clean and round-trips whole.
     */
    @Test
    void aCorrectedCopyOfBuiltInRoundTripsWholeWhileTheOriginalRefusesExactlyThePinnedSix(@TempDir Path host)
            throws IOException {
        Path builtIn = OpenPacksCorpus.packsDirectory().resolve(BUILT_IN);
        WholePackStore.LoadedPack original = store.load(builtIn);

        assertThat(expectation.refusals(BUILT_IN)).hasSize(6);
        assertThat(original.pack().violations())
                .hasSize(6)
                .allSatisfy(v -> assertThat(v.kind()).isEqualTo("asset"));
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
        WholePackStore.LoadedPack loaded = store.load(corrected);

        assertThat(fixed).isEqualTo(6);
        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(countsOf(loaded)).isEqualTo(new Counts(6, 7, 0, 2));
        assertThat(loaded.pack().templates())
                .extracting(template -> template.id().value())
                .containsExactly("writing/meeting-discussion-notes.md", "writing/meeting-minutes.md");
        assertThat(roundTrip(corrected, loaded, host.resolve("written").resolve(BUILT_IN)))
                .isEmpty();
        assertThat(payloadsLeftUnmodelled(corrected, loaded)).isEmpty();
        assertThat(original.pack().violations())
                .extracting(PackElementViolation::file)
                .allSatisfy(
                        file -> assertThat(file).startsWith(PackFileSet.ASSETS).endsWith(SIDECAR_SUFFIX));
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
