package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackDocument;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.infra.storage.WholePackExpectation.Evaluation;
import ai.speckitty.charter.packs.infra.storage.WholePackExpectation.Status;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Refused;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Written;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole-pack round-trip gate (#187): every file of the pinned built-in pack is read into the domain, written to a
 * fresh directory through the storage service, read back, and compared with its source by meaning. The observed result
 * must equal the checked-in expectation exactly, both ways (see {@link WholePackExpectation}). The report lands in
 * {@code build/reports/whole-pack/report.md}.
 *
 * <p>Runs under {@code :packs:wholePackRoundTrip} (tag {@code whole-pack}), outside {@code check}: it is not a required
 * gate until it is green.
 */
@Tag("whole-pack")
class WholePackRoundTripTest {

    private static final String BUILT_IN = "built-in";

    /**
     * The gate kinds the round trip must model at least; an adapter that models fewer cannot pass by omission. The
     * adapter models every bound kind besides (checked against the registry below).
     */
    private static final Set<String> REQUIRED_KINDS = Set.of(
            "directive",
            "tactic",
            "styleguide",
            "toolguide",
            "paradigm",
            "pack-descriptor",
            "graph-shard",
            "agent-profile",
            "mission-step-contract",
            "asset",
            "asset-payload",
            "template", // #193
            "preset", // #195
            "pack-manifest", // #195
            "pack-document", // #260
            "mission-type"); // #194

    @Test
    void theBuiltInPackRoundTripsAsExpected(@TempDir Path temp) throws IOException {
        OpenPacksCorpus.assertPinned();
        Path pack = OpenPacksCorpus.packsDirectory().resolve(BUILT_IN);
        SortedMap<String, String> census = WholePackCensus.of(pack);
        WholePackRoundTrip roundTrip = new DomainRoundTrip(new YamlSerializationStrategy());
        Map<String, List<String>> information = new TreeMap<>();

        Set<String> paired = new TreeSet<>();
        Map<String, Optional<String>> observed =
                observe(roundTrip, pack, temp.resolve(BUILT_IN), census, information, paired);
        Evaluation evaluation =
                WholePackExpectation.fromClasspath().evaluate(census, roundTrip.modelledKinds(), observed);

        Path report = Path.of(System.getProperty("charter.wholePackReport", "build/reports/whole-pack/report.md"));
        Files.createDirectories(report.toAbsolutePath().getParent());
        Files.writeString(
                report,
                WholePackReport.render(
                        evaluation,
                        "spec-kitty-open-packs packs/built-in",
                        System.getProperty("charter.openPacksRef", "?"),
                        information),
                StandardCharsets.UTF_8);
        System.out.println(WholePackReport.kindTable(evaluation));

        OpenPacksCorpus.assertPinned(); // the run left the checkout untouched (NFR-004)
        assertThat(unpaired(temp.resolve(BUILT_IN), paired))
                .as("written files no source file pairs with (ADR-0036: an extra file is a difference)")
                .isEmpty();
        // The descriptor and the shards, #193's two payload kinds (bytes, no binding), #195's presets and manifest,
        // #260's pack document and every bound kind.
        Set<String> bound = new TreeSet<>(Set.of(
                DomainRoundTrip.DESCRIPTOR,
                DomainRoundTrip.GRAPH_SHARD,
                DomainRoundTrip.ASSET_PAYLOAD,
                DomainRoundTrip.TEMPLATE,
                DomainRoundTrip.PRESET,
                DomainRoundTrip.MANIFEST,
                DomainRoundTrip.PACK_DOCUMENT));
        KindBindings.all().forEach(binding -> bound.add(binding.schemaName()));
        assertThat(roundTrip.modelledKinds())
                .as("the kinds the round trip models")
                .containsAll(REQUIRED_KINDS)
                .isEqualTo(bound);
        assertThat(evaluation.problems())
                .as("whole-pack round trip against %s; report: %s", WholePackExpectation.EXPECTED_RED_RESOURCE, report)
                .isEmpty();
        assertAssetsAndTemplates(pack, census, evaluation, observed);
    }

    /**
     * #193, exact: every built-in sidecar is refused because its {@code path} names {@code assets/assets/…}, a known
     * failure until spec-kitty/spec-kitty-open-packs#75 lands, while every asset file and template round-trips byte for
     * byte. A no-op adapter cannot match: each refusal must name
     * the very path its sidecar declares.
     */
    private static void assertAssetsAndTemplates(
            Path pack,
            SortedMap<String, String> census,
            Evaluation evaluation,
            Map<String, Optional<String>> observed) {
        SortedMap<String, SortedMap<Status, Integer>> byKind = evaluation.byKind();
        assertThat(byKind.get("asset")).as("asset").isEqualTo(Map.of(Status.KNOWN_FAILURE, 6));
        assertThat(byKind.get("asset-payload")).as("asset-payload").isEqualTo(Map.of(Status.GREEN, 7));
        // Two top-level templates and 25 mission-type templates (#194).
        assertThat(byKind.get("template")).as("template").isEqualTo(Map.of(Status.GREEN, 27));
        assertThat(byKind.get("pack-document")).as("pack-document (#260)").isEqualTo(Map.of(Status.GREEN, 1));
        assertThat(observed.get(PackDocumentMarkdown.FILE)).as("pack.md").isEmpty();
        List<String> sidecars = census.entrySet().stream()
                .filter(entry -> entry.getValue().equals("asset"))
                .map(Map.Entry::getKey)
                .toList();
        assertThat(sidecars).hasSize(6).allSatisfy(sidecar -> {
            String named = String.valueOf(((Map<?, ?>) Yaml.load(read(pack.resolve(sidecar)))).get("path"));
            assertThat(named).as(sidecar).startsWith("assets/");
            assertThat(observed.get(sidecar))
                    .as(sidecar)
                    .hasValue("refused: payload assets/" + named + " does not exist");
        });
    }

    /**
     * The mission templates under {@code missions/<type>/templates/} census as {@code template} (#194, ADR-0044, which
     * ADR-0040 deferred them to), beside the pack's top-level {@code templates/}; their orientation READMEs are
     * excluded.
     */
    @Test
    void missionTemplatesAreTemplates() {
        OpenPacksCorpus.assertPinned();
        SortedMap<String, String> census =
                WholePackCensus.of(OpenPacksCorpus.packsDirectory().resolve(BUILT_IN));

        List<String> missionTemplates = census.keySet().stream()
                .filter(path -> path.matches("missions/[^/]+/templates/.+"))
                .toList();
        assertThat(missionTemplates)
                .filteredOn(path -> census.get(path).equals("template"))
                .as("every mission template but the orientation READMEs, which are excluded")
                .hasSize(25)
                .noneSatisfy(path -> assertThat(path).endsWith("README.md"));
        assertThat(missionTemplates)
                .filteredOn(path -> path.endsWith("README.md"))
                .isNotEmpty()
                .allSatisfy(path -> assertThat(census.get(path)).as(path).isNotEqualTo("template"));
        assertThat(census.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("templates/"))
                        .map(Map.Entry::getValue)
                        .toList())
                .containsExactly("template", "template");
    }

    /**
     * Positive control through the production write path (#260): the loaded pack's document has one character of its
     * preamble changed before the save, within the rendered prefix of a difference, and {@code pack.md}, green in the
     * faithful run, is red at {@code preamble} alone with both renderings; every other outcome is the faithful run's,
     * and neither run writes a file no source file pairs with.
     */
    @Test
    void aLossyBodyWriterTurnsPackMdRedBesideAGreenFaithfulRun(@TempDir Path temp) throws IOException {
        OpenPacksCorpus.assertPinned();
        Path pack = OpenPacksCorpus.packsDirectory().resolve(BUILT_IN);
        SortedMap<String, String> census = WholePackCensus.of(pack);
        SerializationStrategy yaml = new YamlSerializationStrategy();
        AtomicReference<PackDocument> loaded = new AtomicReference<>();
        Set<String> faithfulPaired = new TreeSet<>();
        Set<String> lossyPaired = new TreeSet<>();

        Map<String, Optional<String>> faithful = observe(
                new DomainRoundTrip(yaml), pack, temp.resolve("faithful"), census, new TreeMap<>(), faithfulPaired);
        Map<String, Optional<String>> lossy = observe(
                new DomainRoundTrip(yaml, read -> {
                    PackDocument document = read.document().orElseThrow();
                    loaded.set(document);
                    return read.toBuilder()
                            .document(withPreamble(document, lossyPreamble(document)))
                            .build();
                }),
                pack,
                temp.resolve("lossy"),
                census,
                new TreeMap<>(),
                lossyPaired);

        assertThat(census).containsEntry(PackDocumentMarkdown.FILE, DomainRoundTrip.PACK_DOCUMENT);
        assertThat(faithful.get(PackDocumentMarkdown.FILE))
                .as("faithful pack.md")
                .isEmpty();
        String source = loaded.get().preamble();
        assertThat(lossy.get(PackDocumentMarkdown.FILE))
                .as("lossy pack.md")
                .hasValue(
                        "preamble: source " + rendered(source) + ", written " + rendered(lossyPreamble(loaded.get())));
        Map<String, Optional<String>> others = new TreeMap<>(lossy);
        others.remove(PackDocumentMarkdown.FILE);
        Map<String, Optional<String>> faithfulOthers = new TreeMap<>(faithful);
        faithfulOthers.remove(PackDocumentMarkdown.FILE);
        assertThat(others).isEqualTo(faithfulOthers);
        assertThat(unpaired(temp.resolve("faithful"), faithfulPaired)).isEmpty();
        assertThat(unpaired(temp.resolve("lossy"), lossyPaired)).isEmpty();
    }

    /** The preamble with its first {@code built-in} changed to {@code built-iN}: one character, rendered. */
    private static String lossyPreamble(PackDocument document) {
        String preamble = document.preamble();
        int at = preamble.indexOf("built-in");
        assertThat(at).as("where the change goes").isBetween(0, 70);
        return preamble.substring(0, at) + "built-iN" + preamble.substring(at + "built-in".length());
    }

    private static PackDocument withPreamble(PackDocument document, String preamble) {
        return new PackDocument(document.frontmatter(), document.title(), preamble, document.sections());
    }

    /** A value as a gate difference renders it: its first 80 characters, then an ellipsis when longer. */
    private static String rendered(String value) {
        return value.length() <= 80 ? value : value.substring(0, 80) + "…";
    }

    /**
     * Positive control through the production write path (#193): the loaded pack has one byte flipped in every asset
     * file and template before the save, and every such file the faithful run turns green is red at the flipped
     * offset; dropping the last byte instead is red at {@code length}. The bytes-kind outcomes are exactly the loaded
     * pack's asset files and templates.
     */
    @Test
    void aLossyByteWriterTurnsEveryAssetFileAndTemplateRed(@TempDir Path temp) throws IOException {
        OpenPacksCorpus.assertPinned();
        Path pack = OpenPacksCorpus.packsDirectory().resolve(BUILT_IN);
        SortedMap<String, String> census = WholePackCensus.of(pack);
        SerializationStrategy yaml = new YamlSerializationStrategy();
        AtomicReference<Pack> loaded = new AtomicReference<>();

        Map<String, Optional<String>> faithful = observe(
                new DomainRoundTrip(yaml), pack, temp.resolve("faithful"), census, new TreeMap<>(), new TreeSet<>());
        Map<String, Optional<String>> flipped = observe(
                new DomainRoundTrip(yaml, read -> {
                    loaded.set(read);
                    return withPayloads(read, WholePackRoundTripTest::flipMiddleByte);
                }),
                pack,
                temp.resolve("flipped"),
                census,
                new TreeMap<>(),
                new TreeSet<>());
        Map<String, Optional<String>> truncated = observe(
                new DomainRoundTrip(yaml, read -> withPayloads(read, bytes -> Arrays.copyOf(bytes, bytes.length - 1))),
                pack,
                temp.resolve("truncated"),
                census,
                new TreeMap<>(),
                new TreeSet<>());

        List<String> bytesKind = faithful.keySet().stream()
                .filter(path -> WholePackCensus.contentFormOf(BUILT_IN, path) == ContentForm.BYTES)
                .toList();
        assertThat(bytesKind)
                .as("bytes-kind outcomes: the loaded pack's asset files plus templates")
                .hasSize(loaded.get().assetFiles().size()
                        + loaded.get().templates().size())
                .as("7 asset files, 2 top-level templates and 25 mission-type templates (#194)")
                .hasSize(7 + 2 + 25);
        assertThat(bytesKind)
                .allSatisfy(path -> assertThat(faithful.get(path)).as(path).isEmpty());
        // Nothing but the payloads moves: the pack document (#260) and every other outcome are the faithful run's.
        assertThat(flipped.get(PackDocumentMarkdown.FILE))
                .as("pack.md in the flipped run")
                .isEmpty();
        faithful.keySet().stream()
                .filter(path -> !bytesKind.contains(path))
                .forEach(path -> assertThat(flipped.get(path)).as(path).isEqualTo(faithful.get(path)));
        for (String path : bytesKind) {
            byte[] source = Files.readAllBytes(pack.resolve(path));
            int middle = source.length / 2;
            assertThat(flipped.get(path))
                    .as(path)
                    .hasValue("bytes[%d]: source 0x%s, written 0x%s"
                            .formatted(middle, hex(source[middle]), hex((byte) ~source[middle])));
            assertThat(truncated.get(path))
                    .as(path)
                    .hasValue("length: source %d, written %d".formatted(source.length, source.length - 1));
        }
    }

    /**
     * The bytes branch over a PNG/OTF-like fixture (NUL bytes, CR/LF pairs, bytes that are not UTF-8, no final
     * newline): equal bytes are green, one changed byte is red at its offset, a shorter file is red at its length, and
     * no file is ever read as text (that would throw on the bytes that are not UTF-8).
     */
    @Test
    void theBytesBranchComparesBytesAndNeverReadsText(@TempDir Path temp) throws IOException {
        byte[] png = {
            (byte) 0x89,
            'P',
            'N',
            'G',
            '\r',
            '\n',
            0x1A,
            '\n',
            0,
            0,
            0,
            0x0D,
            'I',
            'H',
            'D',
            'R',
            (byte) 0xFF,
            (byte) 0xFE
        };
        byte[] otf = {'O', 'T', 'T', 'O', 0, 0x0B, 0, (byte) 0x80, '\r', '\n', (byte) 0xC3, 0, '\r', '\n', 'x'};
        byte[] changedOtf = otf.clone();
        changedOtf[10] = (byte) 0xC4;
        Path source = temp.resolve("src").resolve(BUILT_IN);
        Path written = temp.resolve("out").resolve(BUILT_IN);
        writeBytes(source.resolve("assets/logo.png"), png);
        writeBytes(written.resolve("assets/logo.png"), png);
        writeBytes(source.resolve("assets/fonts/body.otf"), otf);
        writeBytes(written.resolve("assets/fonts/body.otf"), changedOtf);
        writeBytes(source.resolve("templates/cover.png"), png);
        writeBytes(written.resolve("templates/cover.png"), Arrays.copyOf(png, png.length - 1));
        SortedMap<String, String> census = new TreeMap<>(Map.of(
                "assets/logo.png", "asset-payload",
                "assets/fonts/body.otf", "asset-payload",
                "templates/cover.png", "template"));
        assertThat(census.keySet())
                .allSatisfy(path -> assertThat(WholePackCensus.contentFormOf(BUILT_IN, path))
                        .isEqualTo(ContentForm.BYTES));
        WholePackRoundTrip inPlace = new WholePackRoundTrip() {
            @Override
            public Set<String> modelledKinds() {
                return Set.of("asset-payload", "template");
            }

            @Override
            public Map<String, Outcome> roundTrip(Path from, Path to, Map<String, String> kindOf) {
                return kindOf.keySet().stream().collect(Collectors.toMap(path -> path, Written::new));
            }
        };
        Set<String> paired = new TreeSet<>();
        Map<String, List<String>> information = new TreeMap<>();

        Map<String, Optional<String>> observed = observe(inPlace, source, written, census, information, paired);

        assertThat(observed)
                .containsEntry("assets/logo.png", Optional.empty())
                .containsEntry("assets/fonts/body.otf", Optional.of("bytes[10]: source 0xc3, written 0xc4"))
                .containsEntry("templates/cover.png", Optional.of("length: source 18, written 17"));
        assertThat(paired).isEqualTo(census.keySet());
        assertThat(information).isEmpty();
    }

    /**
     * The Markdown branch (#194): a file whose form is {@code markdown} is compared by its model, so a blank-line change
     * is green, one changed character is red at its section, a written text the Markdown reader refuses is red naming
     * the refusal, and no text reaches the YAML reader (each source line {@code key: a: b} would throw there).
     */
    @Test
    void theMarkdownBranchComparesModelsAndNeverReadsYaml(@TempDir Path temp) throws IOException {
        String text = "---\nid: n\n---\n# Notes\n\n## Summary\n\nkey: a: b\n\n## Detail\n\nmore: c: d\n";
        Path source = temp.resolve("src").resolve(BUILT_IN);
        Path written = temp.resolve("out").resolve(BUILT_IN);
        for (String name : List.of("same.md", "spaced.md", "changed.md", "refused.md")) {
            writeBytes(source.resolve("notes/" + name), text.getBytes(StandardCharsets.UTF_8));
        }
        writeBytes(written.resolve("notes/same.md"), text.getBytes(StandardCharsets.UTF_8));
        writeBytes(
                written.resolve("notes/spaced.md"),
                text.replace("## Detail", "\n\n## Detail").getBytes(StandardCharsets.UTF_8));
        writeBytes(
                written.resolve("notes/changed.md"),
                text.replace("more: c: d", "more: c: e").getBytes(StandardCharsets.UTF_8));
        writeBytes(
                written.resolve("notes/refused.md"),
                text.replace("# Notes\n", "# Notes\r\n").getBytes(StandardCharsets.UTF_8));
        SortedMap<String, String> census = new TreeMap<>();
        List.of("same.md", "spaced.md", "changed.md", "refused.md")
                .forEach(name -> census.put("notes/" + name, "notes"));
        WholePackRoundTrip inPlace = new WholePackRoundTrip() {
            @Override
            public Set<String> modelledKinds() {
                return Set.of("notes");
            }

            @Override
            public Map<String, Outcome> roundTrip(Path from, Path to, Map<String, String> kindOf) {
                return kindOf.keySet().stream().collect(Collectors.toMap(path -> path, Written::new));
            }
        };
        Set<String> paired = new TreeSet<>();
        Map<String, List<String>> information = new TreeMap<>();

        Map<String, Optional<String>> observed =
                observe(inPlace, source, written, census, information, paired, path -> ContentForm.MARKDOWN);

        assertThat(observed)
                .containsEntry("notes/same.md", Optional.empty())
                .containsEntry("notes/spaced.md", Optional.empty())
                .containsEntry(
                        "notes/changed.md", Optional.of("sections[1].content: source more: c: d, written more: c: e"))
                .containsEntry(
                        "notes/refused.md",
                        Optional.of(
                                "document: source read, written refused: line 4: a carriage return is not admitted"));
        assertThat(paired).isEqualTo(census.keySet());
        assertThat(information).isEmpty();
    }

    /**
     * The loaded pack with every asset file's and template's bytes changed by {@code change}; nothing else moves, the
     * presets and the pack document included.
     */
    private static Pack withPayloads(Pack pack, UnaryOperator<byte[]> change) {
        Pack.Builder builder = Pack.builder().id(pack.id()).descriptor(pack.descriptor());
        for (CharterArtifact artifact : pack.artifacts()) {
            builder.artifact(
                    artifact instanceof Template template
                            ? Template.builder()
                                    .id(template.id())
                                    .packId(template.packId())
                                    .missionType(template.missionType().orElse(null))
                                    .content(Payload.of(
                                            change.apply(template.content().bytes())))
                                    .build()
                            : artifact);
        }
        pack.violations().forEach(builder::violation);
        pack.assetFiles()
                .forEach(file -> builder.assetFile(new AssetFile(
                        file.path(), Payload.of(change.apply(file.payload().bytes())))));
        pack.presets().forEach(builder::preset);
        pack.document().ifPresent(builder::document);
        return builder.build();
    }

    private static byte[] flipMiddleByte(byte[] bytes) {
        byte[] flipped = bytes.clone();
        flipped[bytes.length / 2] = (byte) ~bytes[bytes.length / 2];
        return flipped;
    }

    private static String hex(byte value) {
        return HexFormat.of().toHexDigits(value);
    }

    private static void writeBytes(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    /**
     * Positive control over the real corpus: a writer that drops every tactic's {@code purpose} must turn each tactic
     * that declares one, and round-trips with the faithful writer, red, with its first difference at {@code purpose}.
     * If this passes, the gate can see a lossy writer end to end.
     */
    @Test
    void aLossyWriterTurnsTheRealCorpusRed(@TempDir Path temp) throws IOException {
        OpenPacksCorpus.assertPinned();
        Path pack = OpenPacksCorpus.packsDirectory().resolve(BUILT_IN);
        SortedMap<String, String> census = WholePackCensus.of(pack);
        SerializationStrategy yaml = new YamlSerializationStrategy();
        SerializationStrategy lossy = (SerializationStrategy) Proxy.newProxyInstance(
                SerializationStrategy.class.getClassLoader(),
                new Class<?>[] {SerializationStrategy.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(yaml, args);
                    } catch (InvocationTargetException thrown) {
                        throw thrown.getCause();
                    }
                    boolean tacticWrite = method.getName().equals("write")
                            && args[0] instanceof KindBinding<?, ?> binding
                            && binding.kind() == ArtifactKind.TACTIC;
                    return tacticWrite ? withoutPurpose((String) result) : result;
                });

        Map<String, Optional<String>> faithful = observe(
                new DomainRoundTrip(yaml), pack, temp.resolve("faithful"), census, new TreeMap<>(), new TreeSet<>());
        Map<String, Optional<String>> observed = observe(
                new DomainRoundTrip(lossy), pack, temp.resolve("lossy"), census, new TreeMap<>(), new TreeSet<>());

        List<String> withPurpose = census.entrySet().stream()
                .filter(entry -> entry.getValue().equals("tactic"))
                .map(Map.Entry::getKey)
                .filter(path -> read(pack.resolve(path)).lines().anyMatch(line -> line.startsWith("purpose:")))
                .filter(path -> faithful.get(path).isEmpty())
                .toList();
        assertThat(withPurpose).as("green tactics that declare a purpose").hasSizeGreaterThan(100);
        assertThat(withPurpose)
                .allSatisfy(path -> assertThat(observed.get(path))
                        .as(path)
                        .hasValueSatisfying(red ->
                                assertThat(WholePackExpectation.atOf(red)).isEqualTo("purpose")));
    }

    /** A tactic's text without its top-level {@code purpose} key and the indented lines that continue it. */
    private static String withoutPurpose(String text) {
        StringBuilder out = new StringBuilder();
        boolean skipping = false;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("purpose:")) {
                skipping = true;
                continue;
            }
            if (skipping && line.startsWith(" ")) {
                continue;
            }
            skipping = false;
            out.append(line).append('\n');
        }
        return out.toString().stripTrailing() + "\n";
    }

    /**
     * #195: the gate driver verifies the manifest's derived fields on both sides, which the comparator skips. A writer
     * that keeps the source's hashes is red at {@code written stale}, an edited source at {@code source stale}; the
     * faithful run over the fresh fixture is green.
     */
    @Test
    void theManifestsDerivedFieldsAreVerifiedOnBothSides(@TempDir Path temp) throws IOException, URISyntaxException {
        Path fresh = PackManifestRoundTripTest.fixture(temp.resolve("fresh"));
        Path edited = PackManifestRoundTripTest.fixture(temp.resolve("edited"));
        Path tactic = edited.resolve(PackManifestRoundTripTest.TACTIC);
        Files.writeString(tactic, Files.readString(tactic) + "# edited\n");
        SerializationStrategy yaml = new YamlSerializationStrategy();

        Map<String, Optional<String>> clean = observe(
                new DomainRoundTrip(yaml),
                fresh,
                temp.resolve("out/clean"),
                WholePackCensus.of(fresh),
                new TreeMap<>(),
                new TreeSet<>());
        Map<String, Optional<String>> copied = observe(
                new DomainRoundTrip(new PackManifestRoundTripTest.StaleManifestWriter(yaml, fresh)),
                fresh,
                temp.resolve("out/copied"),
                WholePackCensus.of(fresh),
                new TreeMap<>(),
                new TreeSet<>());
        Map<String, Optional<String>> sourceStale = observe(
                new DomainRoundTrip(yaml),
                edited,
                temp.resolve("out/edited"),
                WholePackCensus.of(edited),
                new TreeMap<>(),
                new TreeSet<>());

        assertThat(clean).allSatisfy((path, red) -> assertThat(red).as(path).isEmpty());
        assertThat(copied.get(PackFileSet.MANIFEST))
                .contains(
                        "written stale: constituents{directives/018-doctrine-versioning.directive.yaml}.content_hash, "
                                + "constituents{" + PackManifestRoundTripTest.TACTIC + "}.content_hash, "
                                + "presets{presets/convenience.yaml}.content_hash, "
                                + "presets{presets/governing.yaml}.content_hash");
        assertThat(sourceStale.get(PackFileSet.MANIFEST))
                .contains("source stale: constituents{" + PackManifestRoundTripTest.TACTIC + "}.content_hash");
    }

    /** Runs the round trip and compares every modelled file with its source; empty is green, else the red detail. */
    private static Map<String, Optional<String>> observe(
            WholePackRoundTrip roundTrip,
            Path pack,
            Path written,
            SortedMap<String, String> census,
            Map<String, List<String>> information,
            Set<String> paired) {
        return observe(roundTrip, pack, written, census, information, paired, WholePackCensus.formsOf(pack));
    }

    /**
     * Runs the round trip and compares every modelled file with its source by the content form {@code formOf} gives
     * its path; the gate passes the bundled table's form with each companion's declared form
     * ({@link WholePackCensus#formsOf}), a test a fixture's.
     */
    static Map<String, Optional<String>> observe(
            WholePackRoundTrip roundTrip,
            Path pack,
            Path written,
            SortedMap<String, String> census,
            Map<String, List<String>> information,
            Set<String> paired,
            Function<String, ContentForm> formOf) {
        Map<String, Outcome> outcomes = roundTrip.roundTrip(pack, written, census);
        MeaningComparator comparator = MeaningComparator.adr0036();
        Map<String, Optional<String>> observed = new TreeMap<>();
        for (Map.Entry<String, Outcome> entry : outcomes.entrySet()) {
            String path = entry.getKey();
            String kind = census.get(path);
            Outcome outcome = entry.getValue();
            // The form is decided before any text is read; the dispatch is exhaustive, so a new form is decided here.
            Optional<String> red = switch (formOf.apply(path)) {
                // #193: a payload need not be text, and has no edges.
                case BYTES ->
                    switch (outcome) {
                        case Written w -> {
                            paired.add(w.writtenPath());
                            yield bytesRed(readBytes(pack.resolve(path)), readBytes(written.resolve(w.writtenPath())));
                        }
                        case Refused r -> Optional.of("refused: " + r.reason());
                    };
                // #194: compared by the Markdown model; the text never reaches the YAML reader, and has no edges.
                case MARKDOWN ->
                    switch (outcome) {
                        case Written w -> {
                            paired.add(w.writtenPath());
                            yield red(comparator.compareMarkdown(
                                    read(pack.resolve(path)), read(written.resolve(w.writtenPath()))));
                        }
                        case Refused r -> Optional.of("refused: " + r.reason());
                    };
                case YAML -> yamlRed(comparator, kind, pack, path, written, outcome, outcomes, information, paired);
                // ADR-0039: a text companion, compared exactly. No files rule is text (the table refuses the form).
                case TEXT ->
                    switch (outcome) {
                        case Written w -> {
                            paired.add(w.writtenPath());
                            yield textRed(read(pack.resolve(path)), read(written.resolve(w.writtenPath())));
                        }
                        case Refused r -> Optional.of("refused: " + r.reason());
                    };
            };
            observed.put(path, red);
        }
        return observed;
    }

    /**
     * A YAML file compared by meaning (ADR-0036), with a graph shard's duplicate edges noted as information and the
     * manifest's derived fields verified on both sides (#195).
     */
    private static Optional<String> yamlRed(
            MeaningComparator comparator,
            String kind,
            Path pack,
            String path,
            Path written,
            Outcome outcome,
            Map<String, Outcome> outcomes,
            Map<String, List<String>> information,
            Set<String> paired) {
        String source = read(pack.resolve(path));
        Optional<String> red = switch (outcome) {
            case Written w -> {
                paired.add(w.writtenPath());
                String writtenText = read(written.resolve(w.writtenPath()));
                Optional<String> meaning = red(comparator.differencesOfTexts(kind, source, writtenText));
                yield DomainRoundTrip.MANIFEST.equals(kind)
                        ? withStale(meaning, pack, written, outcomes, source, writtenText)
                        : meaning;
            }
            case Refused r -> Optional.of("refused: " + r.reason());
        };
        List<String> duplicates = comparator.duplicateEdges(kind, source);
        if (!duplicates.isEmpty()) {
            information.put(
                    path,
                    duplicates.stream()
                            .map(e -> "duplicate edge in the source: " + e)
                            .toList());
        }
        return red;
    }

    /**
     * #195: the manifest's derived fields, which the comparator skips (ADR-0042), are verified on both sides: the
     * source against the source bytes, the written manifest against the written file each listed path paired with. A
     * stale field joins the red detail as {@code source stale: <fields>} or {@code written stale: <fields>}.
     */
    private static Optional<String> withStale(
            Optional<String> red,
            Path pack,
            Path written,
            Map<String, Outcome> outcomes,
            String sourceText,
            String writtenText) {
        YamlSerializationStrategy yaml = new YamlSerializationStrategy();
        List<String> parts = new ArrayList<>();
        red.ifPresent(parts::add);
        if (yaml.readManifest(sourceText, PackFileSet.MANIFEST) instanceof FileRead.Read<PackManifestDso> source) {
            List<String> stale = PackManifests.stale(source.value(), path -> optionalBytes(pack.resolve(path)));
            if (!stale.isEmpty()) {
                parts.add("source stale: " + String.join(", ", stale));
            }
        }
        if (yaml.readManifest(writtenText, PackFileSet.MANIFEST) instanceof FileRead.Read<PackManifestDso> stamped) {
            List<String> stale = PackManifests.stale(
                    stamped.value(),
                    path -> outcomes.get(path) instanceof Written w
                            ? optionalBytes(written.resolve(w.writtenPath()))
                            : Optional.empty());
            if (!stale.isEmpty()) {
                parts.add("written stale: " + String.join(", ", stale));
            }
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(" ; ", parts));
    }

    private static Optional<byte[]> optionalBytes(Path file) {
        return Files.isRegularFile(file) ? FilesystemPackStore.readBytes(file) : Optional.empty();
    }

    /**
     * The red detail of a written file: every difference path, joined by {@code " ; "}, then the first difference's
     * values. The baseline's {@code at:} pins that whole list, so a loss behind a baselined one cannot hide.
     */
    private static Optional<String> red(List<MeaningComparator.Difference> differences) {
        if (differences.isEmpty()) {
            return Optional.empty();
        }
        MeaningComparator.Difference first = differences.get(0);
        String at = differences.stream().map(MeaningComparator.Difference::path).collect(Collectors.joining(" ; "));
        return Optional.of(at + ": source " + first.source() + ", written " + first.written());
    }

    /**
     * The red detail of a {@code bytes} file: where the bytes first differ, {@code bytes[<offset>]: source 0x..,
     * written 0x..}, or, when one is a prefix of the other, {@code length: source N, written M}.
     */
    private static Optional<String> bytesRed(byte[] source, byte[] written) {
        int offset = Arrays.mismatch(source, written);
        if (offset < 0) {
            return Optional.empty();
        }
        if (offset == source.length || offset == written.length) {
            return Optional.of("length: source " + source.length + ", written " + written.length);
        }
        return Optional.of(
                "bytes[" + offset + "]: source 0x" + hex(source[offset]) + ", written 0x" + hex(written[offset]));
    }

    /**
     * The red detail of a text companion: where the texts first differ, {@code text[<offset>]: source '..', written
     * '..'}, or, when one is a prefix of the other, {@code length: source N, written M}.
     */
    private static Optional<String> textRed(String source, String written) {
        int offset = Arrays.mismatch(source.toCharArray(), written.toCharArray());
        if (offset < 0) {
            return Optional.empty();
        }
        if (offset == source.length() || offset == written.length()) {
            return Optional.of("length: source " + source.length() + ", written " + written.length());
        }
        return Optional.of("text[" + offset + "]: source '" + source.charAt(offset) + "', written '"
                + written.charAt(offset) + "'");
    }

    /**
     * The written files no source file paired with. Pairing is by kind and id, not by path: the store writes a new
     * artefact at the pack root ({@code <id>.<kind>.yaml}) whatever its source path, a known divergence from ADR-0036's
     * "path for path" until the whole-pack seam (#189) writes artefacts in place.
     */
    static List<String> unpaired(Path written, Set<String> paired) {
        try (Stream<Path> files = Files.walk(written)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> written.relativize(file)
                            .toString()
                            .replace(file.getFileSystem().getSeparator(), "/"))
                    .filter(path -> !paired.contains(path))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
