package ai.speckitty.charter.packvalidation.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackDocument;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.roundtrip.FileOutcome;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.domain.roundtrip.Reading;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
import ai.speckitty.charter.packs.infra.storage.RoundTripControls;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import ai.speckitty.charter.packvalidation.roundtrip.WholePackExpectation.Evaluation;
import ai.speckitty.charter.packvalidation.roundtrip.WholePackExpectation.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The whole-pack round-trip gate (#187): every file of every charter example pack (ADR-0070: {@code built-in},
 * {@code org-base}, {@code project}) goes through the production flow ({@link PackRoundTrip}, in {@code packs} main): it
 * is read into the domain, written to a fresh directory through the storage service, read back, and judged by
 * {@link WholePackRoundTripRule} against the checked-in expectation scoped to its pack, exactly, both ways (see
 * {@link WholePackExpectation}). The reports land in {@code build/reports/whole-pack/<pack>.md}.
 *
 * <p>The gate cannot pass over a file kind it does not model (#547): every census file needs an outcome or an
 * expectation, every {@code file-kinds.yaml} schema is modelled or listed with a reason, and every modelled kind has a
 * file in some example pack.
 *
 * <p>Runs under {@code :pack-validation:wholePackRoundTrip} (tag {@code whole-pack}), outside {@code check}. CI requires
 * it: the {@code CI gate / gate} check calls {@code whole-pack.yml} for the {@code wholepack} group (epic #186).
 */
@Tag("whole-pack")
class WholePackRoundTripTest {

    private static final String BUILT_IN = ExamplePacks.BUILT_IN;
    private static final PackIdentifier BUILT_IN_ID = new PackIdentifier(BUILT_IN);
    private static final String ORG_FRAGMENT = "org-fragment";
    private static final String FRAGMENT = "drg/fragment.yaml";
    private static final String README = "README.md";
    private static final String POLICY = "policy.yaml";

    /** Every charter example pack, one gate run each. */
    static List<String> examplePacks() {
        return ExamplePacks.names();
    }

    /**
     * One example pack round-trips as its scoped expectation says: every file is green, excluded, or red where an entry
     * pins it, and no written file is left unpaired but the written copy of a file the expectation lists as refused.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("examplePacks")
    void everyExamplePackRoundTripsAsExpected(String name, @TempDir Path temp) throws IOException {
        PackRoundTripResult result =
                PackRoundTrip.run(new PackIdentifier(name), ExamplePacks.pack(name), temp.resolve(name));
        WholePackRoundTripRule.Verdict verdict =
                new WholePackRoundTripRule(WholePackExpectation.fromClasspath().forPack(name)).judge(result);

        Path report = Path.of(System.getProperty("charter.wholePackReportDir", "build/reports/whole-pack"))
                .resolve(name + ".md");
        Files.createDirectories(report.toAbsolutePath().getParent());
        Files.writeString(
                report,
                WholePackReport.render(
                        verdict.evaluation(),
                        "charter example pack " + name,
                        "service/packs/src/testFixtures/resources/example-packs/" + name,
                        verdict.observation().information()),
                StandardCharsets.UTF_8);
        System.out.println(name + "\n" + WholePackReport.kindTable(verdict.evaluation()));

        assertThat(verdict.evaluation().problems())
                .as("%s against %s; report: %s", name, WholePackExpectation.EXPECTED_RED_RESOURCE, report)
                .isEmpty();
        assertThat(verdict.unpaired())
                .as("%s: written files no source file pairs with (ADR-0036: an extra file is a difference)", name)
                .isEmpty();
        assertThat(new WholePackRoundTripRule(
                                WholePackExpectation.fromClasspath().forPack(name))
                        .apply(result))
                .as("the rule's reasons are the verdict's")
                .isEqualTo(verdict.reasons())
                .isEmpty();
    }

    /**
     * The gate's kinds are tied to the file-kind table, not to the registry (#547): every {@code file-kinds.yaml}
     * schema is modelled by the round trip or listed in the expectation's {@code kinds} with a reason, and every listed
     * kind is a schema the round trip does not model.
     */
    @Test
    void everyFileKindsSchemaIsModelledOrListedWithAReason() {
        Set<String> schemas = PackRoundTrip.fileKindsSchemas();

        assertThat(schemas).as("the file-kinds.yaml schemas").contains(ORG_FRAGMENT);
        assertThat(WholePackExpectation.fromClasspath().schemaProblems(schemas, PackRoundTrip.modelledKinds()))
                .isEmpty();
    }

    /**
     * What only the example packs together prove: every modelled kind has a file in one of them, and every file entry,
     * known failure and exclusion names one of them, so no pack's run leaves it unjudged.
     */
    @Test
    void theExamplePacksTogetherHoldEveryModelledKindAndEveryEntry() throws IOException {
        Map<String, SortedMap<String, String>> censusByPack = new TreeMap<>();
        examplePacks().forEach(name -> censusByPack.put(name, PackRoundTrip.census(ExamplePacks.pack(name))));
        WholePackExpectation expectation = WholePackExpectation.fromClasspath();

        assertThat(censusByPack.keySet())
                .containsExactly(
                        BUILT_IN,
                        ExamplePacks.ORG_ALT,
                        ExamplePacks.ORG,
                        ExamplePacks.ORG_MANDATE,
                        ExamplePacks.PROJECT,
                        ExamplePacks.TENSIONS);
        try (Stream<Path> dirs = Files.list(ExamplePacks.packsDirectory())) {
            assertThat(dirs.filter(Files::isDirectory).map(dir -> String.valueOf(dir.getFileName())))
                    .as("every directory under the example packs is a gated example pack")
                    .containsExactlyInAnyOrderElementsOf(censusByPack.keySet());
        }
        assertThat(WholePackExpectation.emptyKindProblems(censusByPack, PackRoundTrip.modelledKinds()))
                .isEmpty();
        assertThat(expectation.unscopedEntries(censusByPack.keySet())).isEmpty();
    }

    /**
     * The planted break of #547: a round trip that no longer models {@code org-fragment} gives the {@code org-base}
     * fragment no outcome. The drift judgement fails naming the kind and the path, and the schema check fails naming
     * the schema; the faithful round trip fails neither way.
     */
    @Test
    void anUnmodelledOrgFragmentFailsTheDriftAndTheSchemaChecks(@TempDir Path temp) {
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());
        Path org = ExamplePacks.pack(ExamplePacks.ORG);
        PackRoundTripResult faithful =
                PackRoundTrip.run(new PackIdentifier(ExamplePacks.ORG), org, temp.resolve("faithful/org-base"));
        PackRoundTripResult planted = withoutKind(faithful, ORG_FRAGMENT);
        WholePackExpectation expectation = WholePackExpectation.fromClasspath();

        assertThat(WholePackDrift.problems(store, org, faithful))
                .noneSatisfy(problem -> assertThat(problem).contains("no adapter models kind"));
        assertThat(WholePackDrift.problems(store, org, planted))
                .contains("org-base: UNEXPECTED_RED " + FRAGMENT
                        + " (org-fragment): no adapter models kind org-fragment");
        assertThat(expectation.schemaProblems(PackRoundTrip.fileKindsSchemas(), faithful.modelledKinds()))
                .isEmpty();
        assertThat(expectation.schemaProblems(PackRoundTrip.fileKindsSchemas(), planted.modelledKinds()))
                .containsExactly("UNMODELLED_SCHEMA org-fragment: the round trip does not model this file-kinds.yaml"
                        + " schema, and " + WholePackExpectation.EXPECTED_RED_RESOURCE
                        + " does not list it in kinds with a reason");
        assertThat(new WholePackRoundTripRule(WholePackExpectation.none()).apply(planted))
                .as("the rule fails on it too, with the gate's code")
                .extracting(Reason::code, Reason::detail)
                .contains(tuple(
                        WholePackRoundTripRule.RED,
                        "UNEXPECTED_RED " + FRAGMENT + " (org-fragment): no adapter models kind org-fragment"));
    }

    /**
     * #106, #547: the built-in glossary context is a modelled file kind that round-trips green, not a file the gate
     * skips. A round trip that no longer models {@code glossary} gives the file no outcome: the drift judgement fails
     * naming the kind and the path, and the schema check fails naming the schema; the faithful round trip fails neither
     * way.
     */
    @Test
    void theBuiltInGlossaryIsModelledAndRoundTripsGreen(@TempDir Path temp) {
        String glossary = ArtifactKind.GLOSSARY.token();
        String file = "glossaries/spec-kitty-core.glossary.yaml";
        Path pack = ExamplePacks.pack(BUILT_IN);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());
        PackRoundTripResult faithful = PackRoundTrip.run(BUILT_IN_ID, pack, temp.resolve("faithful/" + BUILT_IN));
        PackRoundTripResult planted = withoutKind(faithful, glossary);

        assertThat(faithful.census())
                .as("the census classes the file by its kind")
                .containsEntry(file, glossary);
        assertThat(faithful.modelledKinds()).contains(glossary);
        RoundTripObservation observed = RoundTripObservation.of(faithful);
        assertThat(observed.observed())
                .as("the file has an outcome, and it is equal meaning")
                .containsEntry(file, Optional.empty());
        assertThat(WholePackExpectation.fromClasspath()
                        .forPack(BUILT_IN)
                        .evaluate(faithful.census(), faithful.notHeld(), faithful.modelledKinds(), observed.observed())
                        .byKind()
                        .get(glossary))
                .isEqualTo(Map.of(Status.GREEN, 1));
        assertThat(WholePackDrift.problems(store, pack, faithful))
                .noneSatisfy(problem -> assertThat(problem).contains(file));
        assertThat(WholePackDrift.problems(store, pack, planted))
                .contains(BUILT_IN + ": UNEXPECTED_RED " + file + " (" + glossary + "): no adapter models kind "
                        + glossary);
        assertThat(WholePackExpectation.fromClasspath()
                        .schemaProblems(PackRoundTrip.fileKindsSchemas(), planted.modelledKinds()))
                .containsExactly(
                        "UNMODELLED_SCHEMA " + glossary + ": the round trip does not model this file-kinds.yaml"
                                + " schema, and " + WholePackExpectation.EXPECTED_RED_RESOURCE
                                + " does not list it in kinds with a reason");
    }

    /**
     * {@code result} as if the round trip did not model {@code kind}: the kind leaves its modelled kinds and its files
     * get no outcome, as {@code org-fragment}'s had none before #509.
     */
    private static PackRoundTripResult withoutKind(PackRoundTripResult result, String kind) {
        Set<String> kinds = new TreeSet<>(result.modelledKinds());
        kinds.remove(kind);
        SortedMap<String, FileOutcome> outcomes = new TreeMap<>(result.outcomes());
        outcomes.keySet().removeIf(path -> kind.equals(result.census().get(path)));
        SortedMap<String, String> census = result.census();
        Set<String> written = new TreeSet<>(result.written());
        result.outcomes().forEach((path, outcome) -> {
            if (kind.equals(census.get(path)) && outcome instanceof FileOutcome.Written w) {
                written.remove(w.writtenPath());
            }
        });
        return new PackRoundTripResult(census, result.notHeld(), kinds, outcomes, new TreeSet<>(written));
    }

    /**
     * #193, #260, exact: every built-in sidecar, every asset file and every template round-trips, each sidecar anchoring
     * its {@code path} at {@code assets/}, and the pack document round-trips. A no-op adapter cannot match: each sidecar
     * must name a payload that is in the census as one.
     */
    @Test
    void theBuiltInPacksAssetsTemplatesAndDocumentRoundTrip(@TempDir Path temp) {
        PackRoundTripResult result =
                PackRoundTrip.run(BUILT_IN_ID, ExamplePacks.pack(BUILT_IN), temp.resolve(BUILT_IN));
        SortedMap<String, String> census = result.census();
        Map<String, Optional<String>> observed = RoundTripObservation.of(result).observed();
        Evaluation evaluation = WholePackExpectation.fromClasspath()
                .forPack(BUILT_IN)
                .evaluate(census, result.notHeld(), result.modelledKinds(), observed);
        SortedMap<String, SortedMap<Status, Integer>> byKind = evaluation.byKind();

        // Every file the census classes as a kind is green, and the kind is not empty (top-level templates and
        // mission-type templates, #194, are both templates).
        for (String kind : List.of("asset", "asset-payload", "template")) {
            long classed = census.values().stream().filter(kind::equals).count();
            assertThat(classed).as("%s files in the census", kind).isPositive();
            assertThat(byKind.get(kind)).as(kind).isEqualTo(Map.of(Status.GREEN, (int) classed));
        }
        assertThat(byKind.get("pack-document")).as("pack-document (#260)").isEqualTo(Map.of(Status.GREEN, 1));
        assertThat(observed.get(README)).as(README).isEmpty();
        List<String> sidecars = census.entrySet().stream()
                .filter(entry -> entry.getValue().equals("asset"))
                .map(Map.Entry::getKey)
                .toList();
        assertThat(sidecars).isNotEmpty().allSatisfy(sidecar -> {
            FileOutcome.Written written =
                    (FileOutcome.Written) result.outcomes().get(sidecar);
            Object named = ((Map<?, ?>) ((Reading.Yaml) written.source()).tree()).get("path");
            assertThat(census).as(sidecar).containsEntry("assets/" + named, "asset-payload");
            assertThat(observed.get(sidecar)).as(sidecar).isEmpty();
        });
    }

    /**
     * Positive control through the production write path (#260): the loaded pack's document has one character of its
     * preamble changed before the save, within the rendered prefix of a difference, and {@code README.md}, green in the
     * faithful run, is red at {@code preamble} alone with both renderings; every other outcome is the faithful run's,
     * and neither run writes a file no source file pairs with.
     */
    @Test
    void aLossyBodyWriterTurnsPackMdRedBesideAGreenFaithfulRun(@TempDir Path temp) {
        Path pack = ExamplePacks.pack(BUILT_IN);
        AtomicReference<PackDocument> loaded = new AtomicReference<>();

        RoundTripObservation faithful =
                RoundTripObservation.of(PackRoundTrip.run(BUILT_IN_ID, pack, temp.resolve("faithful")));
        RoundTripObservation lossy =
                RoundTripObservation.of(RoundTripControls.beforeSave(BUILT_IN_ID, pack, temp.resolve("lossy"), read -> {
                    PackDocument document = read.document().orElseThrow();
                    loaded.set(document);
                    return read.toBuilder()
                            .document(withPreamble(document, lossyPreamble(document)))
                            .build();
                }));

        assertThat(faithful.observed().get(README)).as("faithful README.md").isEmpty();
        String source = loaded.get().preamble();
        assertThat(lossy.observed().get(README))
                .as("lossy README.md")
                .hasValue(
                        "preamble: source " + rendered(source) + ", written " + rendered(lossyPreamble(loaded.get())));
        Map<String, Optional<String>> others = new TreeMap<>(lossy.observed());
        others.remove(README);
        Map<String, Optional<String>> faithfulOthers = new TreeMap<>(faithful.observed());
        faithfulOthers.remove(README);
        assertThat(others).isEqualTo(faithfulOthers);
        assertThat(faithful.unpaired()).isEmpty();
        assertThat(lossy.unpaired()).isEmpty();
    }

    /**
     * Positive control through the gate's policy seam (#476): the source's policy loses its lock on the fallback
     * profile, and, in a second run, its {@code template} kind default drops from {@code extendable} to {@code open};
     * {@code policy.yaml}, green in the faithful run, is red naming the lost entry each time; every other outcome is the
     * faithful run's.
     */
    @Test
    void aLossyPolicyTurnsPolicyYamlRedBesideAGreenFaithfulRun(@TempDir Path temp) {
        Path pack = ExamplePacks.pack(BUILT_IN);
        Urn locked = new Urn(NodeKind.AGENT_PROFILE, "generic-agent");

        PackRoundTripResult faithfulRun = PackRoundTrip.run(BUILT_IN_ID, pack, temp.resolve("faithful"));
        Map<String, Optional<String>> faithful =
                RoundTripObservation.of(faithfulRun).observed();
        assertThat(faithfulRun.census()).containsEntry(POLICY, "policy");
        assertThat(faithful.get(POLICY)).as("faithful policy.yaml").isEmpty();
        Map<String, Optional<String>> faithfulOthers = new TreeMap<>(faithful);
        faithfulOthers.remove(POLICY);

        Map<String, UnaryOperator<MutabilityPolicySource>> losses = new TreeMap<>();
        losses.put(locked.toString(), policy -> without(policy, locked));
        losses.put("template", policy -> withKindDefault(policy, NodeKind.TEMPLATE, MutabilityLevel.OPEN));
        losses.forEach((lost, loss) -> {
            Map<String, Optional<String>> lossy = RoundTripObservation.of(RoundTripControls.beforePolicySave(
                            BUILT_IN_ID, pack, temp.resolve("lossy-" + lost.replace(':', '-')), loss))
                    .observed();

            assertThat(lossy.get(POLICY))
                    .as("lossy policy.yaml, losing " + lost)
                    .hasValueSatisfying(red -> assertThat(red).contains(lost));
            Map<String, Optional<String>> others = new TreeMap<>(lossy);
            others.remove(POLICY);
            assertThat(others).isEqualTo(faithfulOthers);
        });
    }

    /** The policy without its entry for {@code target}. */
    private static MutabilityPolicySource without(MutabilityPolicySource policy, Urn target) {
        MutabilityPolicySource.Builder builder = MutabilityPolicySource.builder();
        policy.kindDefaults().forEach(builder::kindDefault);
        policy.urnEntries().forEach((urn, level) -> {
            if (!urn.equals(target)) {
                builder.entry(urn, level);
            }
        });
        MutabilityPolicySource changed = builder.build();
        assertThat(changed).as("the control changes the policy").isNotEqualTo(policy);
        return changed;
    }

    /** The policy with the default of {@code kind} set to {@code level}. */
    private static MutabilityPolicySource withKindDefault(
            MutabilityPolicySource policy, NodeKind kind, MutabilityLevel level) {
        MutabilityPolicySource.Builder builder = MutabilityPolicySource.builder();
        policy.kindDefaults().forEach(builder::kindDefault);
        builder.kindDefault(kind, level);
        policy.urnEntries().forEach(builder::entry);
        MutabilityPolicySource changed = builder.build();
        assertThat(changed).as("the control changes the policy").isNotEqualTo(policy);
        return changed;
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
     * offset; dropping the last byte instead is red at {@code length}. The bytes-form outcomes are exactly the loaded
     * pack's asset files and templates.
     */
    @Test
    void aLossyByteWriterTurnsEveryAssetFileAndTemplateRed(@TempDir Path temp) throws IOException {
        Path pack = ExamplePacks.pack(BUILT_IN);
        AtomicReference<Pack> loaded = new AtomicReference<>();

        PackRoundTripResult faithfulRun = PackRoundTrip.run(BUILT_IN_ID, pack, temp.resolve("faithful"));
        Map<String, Optional<String>> faithful =
                RoundTripObservation.of(faithfulRun).observed();
        Map<String, Optional<String>> flipped = RoundTripObservation.of(
                        RoundTripControls.beforeSave(BUILT_IN_ID, pack, temp.resolve("flipped"), read -> {
                            loaded.set(read);
                            return withPayloads(read, WholePackRoundTripTest::flipMiddleByte);
                        }))
                .observed();
        Map<String, Optional<String>> truncated = RoundTripObservation.of(RoundTripControls.beforeSave(
                        BUILT_IN_ID,
                        pack,
                        temp.resolve("truncated"),
                        read -> withPayloads(read, bytes -> Arrays.copyOf(bytes, bytes.length - 1))))
                .observed();

        List<String> bytesForm = faithfulRun.outcomes().entrySet().stream()
                .filter(entry ->
                        entry.getValue() instanceof FileOutcome.Written w && w.source() instanceof Reading.Bytes)
                .map(Map.Entry::getKey)
                .toList();
        assertThat(bytesForm)
                .as("bytes-form outcomes: the loaded pack's asset files plus templates")
                .hasSize(loaded.get().assetFiles().size()
                        + loaded.get().templates().size())
                .as("the pack holds asset files and templates (#194)")
                .isNotEmpty();
        assertThat(bytesForm)
                .allSatisfy(path -> assertThat(faithful.get(path)).as(path).isEmpty());
        // Nothing but the payloads moves: the pack document (#260) and every other outcome are the faithful run's.
        assertThat(flipped.get(README)).as("README.md in the flipped run").isEmpty();
        faithful.keySet().stream()
                .filter(path -> !bytesForm.contains(path))
                .forEach(path -> assertThat(flipped.get(path)).as(path).isEqualTo(faithful.get(path)));
        for (String path : bytesForm) {
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

    /** A PNG-like fixture: NUL bytes, CR/LF pairs, bytes that are not UTF-8, no final newline. */
    private static byte[] pngLikeFixture() {
        return new byte[] {
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
    }

    /**
     * The bytes branch over a PNG/OTF-like fixture (NUL bytes, CR/LF pairs, bytes that are not UTF-8, no final
     * newline): equal bytes are green, one changed byte is red at its offset, a shorter file is red at its length, and
     * no file is ever read as text (that would throw on the bytes that are not UTF-8).
     */
    @Test
    void theBytesBranchComparesBytesAndNeverReadsText(@TempDir Path temp) throws IOException {
        byte[] png = pngLikeFixture();
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

        PackRoundTripResult result = RoundTripControls.inPlace(BUILT_IN_ID, source, written, census, false);
        RoundTripObservation observation = RoundTripObservation.of(result);

        assertThat(result.outcomes().values())
                .as("the table reads every one of them as bytes")
                .allSatisfy(outcome ->
                        assertThat(((FileOutcome.Written) outcome).source()).isInstanceOf(Reading.Bytes.class));
        assertThat(observation.observed())
                .containsEntry("assets/logo.png", Optional.empty())
                .containsEntry("assets/fonts/body.otf", Optional.of("bytes[10]: source 0xc3, written 0xc4"))
                .containsEntry("templates/cover.png", Optional.of("length: source 18, written 17"));
        assertThat(observation.unpaired()).isEmpty();
        assertThat(observation.information()).isEmpty();
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

        RoundTripObservation observation =
                RoundTripObservation.of(RoundTripControls.inPlace(BUILT_IN_ID, source, written, census, true));

        assertThat(observation.observed())
                .containsEntry("notes/same.md", Optional.empty())
                .containsEntry("notes/spaced.md", Optional.empty())
                .containsEntry(
                        "notes/changed.md", Optional.of("sections[1].content: source more: c: d, written more: c: e"))
                .containsEntry(
                        "notes/refused.md",
                        Optional.of(
                                "document: source read, written refused: line 4: a carriage return is not admitted"));
        assertThat(observation.unpaired()).isEmpty();
        assertThat(observation.information()).isEmpty();
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
     * Positive control over the built-in example pack: a writer that drops every tactic's {@code purpose} must turn each
     * tactic that declares one, and round-trips with the faithful writer, red, with its first difference at
     * {@code purpose}. If this passes, the gate can see a lossy writer end to end.
     */
    @Test
    void aLossyWriterTurnsTheRealCorpusRed(@TempDir Path temp) throws IOException {
        Path pack = ExamplePacks.pack(BUILT_IN);

        PackRoundTripResult faithfulRun = PackRoundTrip.run(BUILT_IN_ID, pack, temp.resolve("faithful"));
        Map<String, Optional<String>> faithful =
                RoundTripObservation.of(faithfulRun).observed();
        Map<String, Optional<String>> observed = RoundTripObservation.of(RoundTripControls.rewritingWrites(
                        BUILT_IN_ID,
                        pack,
                        temp.resolve("lossy"),
                        ArtifactKind.TACTIC,
                        WholePackRoundTripTest::withoutPurpose))
                .observed();

        List<String> declaringPurpose = faithfulRun.census().entrySet().stream()
                .filter(entry -> entry.getValue().equals("tactic"))
                .map(Map.Entry::getKey)
                .filter(path -> read(pack.resolve(path)).lines().anyMatch(line -> line.startsWith("purpose:")))
                .toList();
        List<String> withPurpose = declaringPurpose.stream()
                .filter(path -> faithful.get(path).isEmpty())
                .toList();
        assertThat(declaringPurpose)
                .as("tactics of the example pack that declare a purpose")
                .isNotEmpty();
        assertThat(withPurpose)
                .as("every one of them is green in the faithful run")
                .isEqualTo(declaringPurpose);
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

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
