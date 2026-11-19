package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.packcheck.PackCheckMain;
import ai.speckitty.charter.packcheck.PackCheckSeam;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRefusalMapping;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.rules.CompositionRules;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packvalidation.PackCheck;
import ai.speckitty.charter.web.RefusalHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The pack-check report is the contract's {@code PackCheckReport}, and its problems are the API's (ADR-0075 decision 9,
 * #478, FR-005, T021). pack-check runs in process ({@link PackCheckMain#run}) over real reads of the example packs and
 * the planted packs:
 *
 * <ul>
 *   <li>its standard output validates against {@code charter/schemas/PackCheckReport.yaml}, its problems against the
 *       shared {@code Problem}, and each problem's {@code instance} is the {@code file:} URI of a pack it checked;
 *   <li>for one fixture per code family, each problem but its {@code instance} equals what {@link RefusalHandler}
 *       renders for {@code getPackRegistration} from the same refusal, built from the registration's mapping
 *       ({@link PackRefusalMapping#of}) or from the composing read's resolver ({@link PackCompositionResolver}), never
 *       from pack-check's own rendering;
 *   <li>for every code pack-check can emit, its title and status are {@link RefusalHandler}'s.
 * </ul>
 */
@DisplayName("pack-check's report is the contract's PackCheckReport, its problems the API's (#478, FR-005)")
class PackCheckReportContractTest {

    private static final String SCHEMA = "charter/schemas/PackCheckReport.yaml";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final RefusalHandler HANDLER = new RefusalHandler();

    private static final String PLANT = "planted/duplicate-artifact-id" + GoldenSubjects.WITH_DESCRIPTOR;

    private static final String LINK_PLANT = "planted/pack-entry-linked-file";

    private static ContractConformance contract;

    @TempDir
    Path temp;

    @BeforeAll
    static void loadTheContract() {
        contract = new ContractConformance();
    }

    /** What one run answered. */
    private record Run(int exit, String out, String err) {
        JsonNode report() {
            return JSON.readTree(out);
        }

        List<JsonNode> problems() {
            List<JsonNode> problems = new ArrayList<>();
            report().get("problems").forEach(problems::add);
            return problems;
        }
    }

    private static Run run(String... args) {
        return run((out, err) -> PackCheckMain.run(args, out, err));
    }

    private interface Runner {
        int run(PrintStream out, PrintStream err);
    }

    private static Run run(Runner runner) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = runner.run(
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /** The report conforms to the schema, and every {@code instance} is the file URI of one of {@code packs}. */
    private static void assertConforms(Run run, Path... packs) {
        assertThat(run.out())
                .as("a report on standard output (stderr: %s)", run.err())
                .isNotEmpty();
        JsonNode report = run.report();
        assertThat(contract.schemaViolations(SCHEMA, report))
                .as("the report's violations of %s", SCHEMA)
                .isEmpty();
        List<Path> directories =
                Stream.of(packs).map(pack -> pack.toAbsolutePath().normalize()).toList();
        for (JsonNode problem : report.get("problems")) {
            if (problem.has("instance")) {
                URI instance = URI.create(problem.get("instance").asString());
                assertThat(instance.getScheme()).as("the instance %s", instance).isEqualTo("file");
                assertThat(instance.isAbsolute()).isTrue();
                assertThat(Path.of(instance)).as("the pack the instance names").isIn(directories);
            }
        }
    }

    /** What {@link RefusalHandler} renders for a refused registration, as JSON, its absent members left out. */
    private static ObjectNode registrationProblem(CharterRefusal refusal) {
        ObjectNode rendered = JSON.valueToTree(HANDLER.registrationProblem(refusal));
        List<String> absent = new ArrayList<>();
        rendered.properties().forEach(member -> {
            if (member.getValue().isNull()) {
                absent.add(member.getKey());
            }
        });
        rendered.remove(absent);
        return rendered;
    }

    /** A problem of the report without its {@code instance}, which only pack-check sets. */
    private static ObjectNode withoutInstance(JsonNode problem) {
        ObjectNode copy = (ObjectNode) problem.deepCopy();
        copy.remove("instance");
        return copy;
    }

    /** The five example packs under {@code root/packs}, a config root. */
    private Path configRoot() {
        Path root = temp.resolve("root");
        ExamplePacks.names().forEach(name -> ExamplePacks.copyOf(name, root.resolve("packs")));
        return root;
    }

    private Path plant(String key) throws IOException {
        GoldenSubjects.Subject subject = GoldenSubjects.subjects().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no golden subject " + key));
        try {
            return GoldenSubjects.copy(subject, temp.resolve("plant"));
        } catch (IOException | UnsupportedOperationException noLinks) {
            if (!subject.plantsLinks()) {
                throw noLinks;
            }
            assumeTrue(Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
            throw noLinks;
        }
    }

    /**
     * Error handling: a pack of 101 unreadable tactics, so its problem lists 100 violations and counts the rest. The
     * descriptor plant's copy of {@code artifact-not-yaml}, its one unreadable tactic copied 100 times more.
     */
    private Path aPackOfAHundredAndOneFaults() throws IOException {
        Path pack = plant("planted/artifact-not-yaml" + GoldenSubjects.WITH_DESCRIPTOR);
        Path tactic = pack.resolve("tactics/a.tactic.yaml");
        IntFunction<Path> copy = n -> pack.resolve("tactics/a" + n + ".tactic.yaml");
        for (int n = 1; n <= 100; n++) {
            Files.copy(tactic, copy.apply(n));
        }
        return pack;
    }

    /** The validate-mode verdict pack-check states for one pack, as the registration's mapping words it. */
    private static CharterRefusal registrationRefusalOf(Path pack, Optional<PackLevel> level) {
        String name = String.valueOf(pack.getFileName());
        PackRoot root = new PackRoot(name, level, pack.toString());
        return PackRefusalMapping.of(new Rationale(
                new PackCheck(new FileSystemPackInspector()).validate(root).findings()));
    }

    /**
     * The composing read's refusals of a set of packs, each read for real and configured under its directory's name at
     * its level: every fault the resolver collects, then its preset contests in merge order, as the offering judges
     * them in turn.
     */
    private static List<CharterRefusal> composingRefusalsOf(Map<Path, PackLevel> packs) {
        FileSystemPackInspector reader = new FileSystemPackInspector();
        List<PackUnderResolution> set = new ArrayList<>();
        Map<String, List<String>> presets = new HashMap<>();
        packs.forEach((pack, level) -> {
            String name = String.valueOf(pack.getFileName());
            PackReadout read = reader.read(new PackRoot(name, Optional.of(level), pack.toString()));
            PackReadout.Built built = (PackReadout.Built) read.facts();
            set.add(built.underResolution(new ConfiguredPack(new PackIdentifier(name), level)));
            presets.put(name, built.facts().presets().stream().map(Preset::id).toList());
        });
        List<CharterRefusal> refusals = new ArrayList<>();
        PackCompositionResolver.faults(set).forEach(fault -> refusals.add(fault.toCharterRefusal()));
        PackCompositionResolver.presetContests(
                        PackCompositionResolver.mergeOrder(set),
                        pack -> presets.getOrDefault(pack.configured().id().value(), List.of()))
                .forEach(fault -> refusals.add(fault.toCharterRefusal()));
        return refusals;
    }

    @Nested
    @DisplayName("the report validates against PackCheckReport")
    class Validates {

        @Test
        void theExamplePacksAsASet() {
            Path root = configRoot();

            Run run = run("--config-root", root.toString());

            assertThat(run.exit()).isZero();
            assertConforms(run);
            assertThat(run.problems()).isEmpty();
        }

        @Test
        void onePlantedPackFault() throws IOException {
            Path pack = plant(PLANT);

            Run run = run("--pack", pack.toString());

            assertThat(run.exit()).isEqualTo(1);
            assertConforms(run, pack);
            assertThat(run.problems())
                    .singleElement()
                    .satisfies(problem -> assertThat(problem.get("instance").asString())
                            .isEqualTo(pack.toUri().toString()));
        }

        @Test
        void theProjectPackAloneACompositionFault() {
            Path project = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp.resolve("packs"));

            Run run = run("--pack", project + ":project");

            assertThat(run.exit()).isEqualTo(1);
            assertConforms(run, project);
            assertThat(run.problems())
                    .isNotEmpty()
                    .allSatisfy(problem -> assertThat(problem.has("instance")).isFalse());
        }

        @Test
        void aPlantedLink() throws IOException {
            Path pack = plant(LINK_PLANT);

            Run run = run("--pack", pack + ":org");

            assertThat(run.exit()).isEqualTo(1);
            assertConforms(run, pack);
        }

        @Test
        void aPackOfAHundredAndOneViolations() throws IOException {
            Path pack = aPackOfAHundredAndOneFaults();

            Run run = run("--pack", pack.toString());

            assertThat(run.exit()).isEqualTo(1);
            assertConforms(run, pack);
            JsonNode problem = run.problems().getFirst();
            assertThat(problem.get("violations")).hasSize(100);
            assertThat(problem.get("detail").asString()).endsWith(" more violations are not listed.");
        }

        @Test
        void aPathWithAColonThatWouldReadAsASchemeIsAFileUri() throws IOException {
            Path pack = plant(PLANT);
            Path colon = Files.move(pack, pack.resolveSibling("qa:org"));

            Run run = run("--pack", colon.toString());

            assertThat(run.exit()).isEqualTo(1);
            assertConforms(run, colon);
            assertThat(run.problems().getFirst().get("instance").asString())
                    .startsWith("file:/")
                    .endsWith("/qa:org/");
        }

        @Test
        void theRoundTripOverTheExamplePacks() {
            Path root = configRoot();

            Run run = run("--config-root", root.toString(), "--round-trip");

            assertThat(run.exit()).as(run.out()).isZero();
            assertConforms(run);
            assertThat(run.report().get("checks").toString()).contains("ROUND_TRIP");
        }
    }

    @Nested
    @DisplayName("each problem but its instance is what RefusalHandler renders for getPackRegistration")
    class TheProblemIsTheApis {

        @Test
        @DisplayName("validation_failed: a planted pack fault")
        void validationFailed() throws IOException {
            Path pack = plant(PLANT);

            Run run = run("--pack", pack.toString());

            assertThat(run.problems()).hasSize(1);
            assertThat(withoutInstance(run.problems().getFirst()))
                    .isEqualTo(registrationProblem(registrationRefusalOf(pack, Optional.empty())));
        }

        @Test
        @DisplayName("validation_failed: more than 100 violations, the first 100 listed and the rest counted")
        void validationFailedPastTheCap() throws IOException {
            Path pack = aPackOfAHundredAndOneFaults();

            Run run = run("--pack", pack.toString());

            CharterRefusal refusal = registrationRefusalOf(pack, Optional.empty());
            assertThat(refusal.violations()).hasSizeGreaterThan(100);
            assertThat(run.problems()).hasSize(1);
            assertThat(withoutInstance(run.problems().getFirst())).isEqualTo(registrationProblem(refusal));
        }

        @Test
        @DisplayName("pack_unreadable: a pack whose composition facts cannot be read and that has no violation")
        void packUnreadable() throws IOException {
            Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
            Path temporary = Files.createDirectories(temp.resolve("tmp"));

            Run run = run((out, err) -> PackCheckSeam.runWithUnreadableFacts(
                    new String[] {"--pack", builtIn + ":built-in"}, out, err, temporary));

            assertThat(run.exit()).isEqualTo(1);
            assertConforms(run, builtIn);
            PackReadout.Unreadable unreadable = PackReadout.Unreadable.noAssembly(ExamplePacks.BUILT_IN);
            assertThat(run.problems())
                    .singleElement()
                    .satisfies(problem -> assertThat(withoutInstance(problem))
                            .isEqualTo(
                                    registrationProblem(new CharterRefusal(unreadable.code(), unreadable.detail()))));
        }

        @Test
        @DisplayName("pack_dependency_unknown: the project pack without the packs it depends on")
        void packDependencyUnknown() {
            Path project = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp.resolve("packs"));

            Run run = run("--pack", project + ":project");

            List<CharterRefusal> expected = composingRefusalsOf(Map.of(project, PackLevel.PROJECT));
            assertThat(expected)
                    .extracting(CharterRefusal::code)
                    .isNotEmpty()
                    .containsOnly(RefusalCode.PACK_DEPENDENCY_UNKNOWN);
            assertThat(run.problems())
                    .map(PackCheckReportContractTest::withoutInstance)
                    .containsExactlyElementsOf(expected.stream()
                            .map(PackCheckReportContractTest::registrationProblem)
                            .toList());
        }

        @Test
        @DisplayName("pack_duplicate_id and pack_name_conflict: the built-in pack twice, under two directory names")
        void packDuplicateId() throws IOException {
            Path first = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("one"));
            Path second = Files.move(
                    ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("two")),
                    temp.resolve("two").resolve("built-in-again"));

            Run run = run("--pack", first + ":built-in", "--pack", second + ":built-in");

            assertConforms(run, first, second);
            Map<Path, PackLevel> set = new java.util.LinkedHashMap<>();
            set.put(first, PackLevel.BUILT_IN);
            set.put(second, PackLevel.BUILT_IN);
            List<CharterRefusal> expected = composingRefusalsOf(set);
            assertThat(expected).extracting(CharterRefusal::code).contains(RefusalCode.PACK_DUPLICATE_ID);
            assertThat(run.problems())
                    .map(PackCheckReportContractTest::withoutInstance)
                    .containsExactlyElementsOf(expected.stream()
                            .map(PackCheckReportContractTest::registrationProblem)
                            .toList());
        }
    }

    @Nested
    @DisplayName("every code pack-check can emit has RefusalHandler's title and status for getPackRegistration")
    class EveryCode {

        /**
         * The codes pack-check can emit, enumerated from their sources: {@code validation_failed} (a pack's violations
         * and its round trip), the refusals the composition facts' builder raises, and every {@code CLOSURE} failure of
         * the resolver, the preset phase's sibling conflict among them. A new {@code CLOSURE} failure joins this set
         * by itself.
         */
        private static Set<RefusalCode> emitted() {
            Set<RefusalCode> codes = EnumSet.of(
                    RefusalCode.VALIDATION_FAILED, RefusalCode.PACK_UNREADABLE, RefusalCode.REGISTRATION_CONFLICT);
            for (Failure failure : Failure.values()) {
                if (isClosure(failure)) {
                    codes.add(failure.code());
                }
            }
            return codes;
        }

        private static boolean isClosure(Failure failure) {
            try {
                CompositionRules.codeOf(failure);
                return true;
            } catch (IllegalArgumentException referenceFailure) {
                return false;
            }
        }

        @Test
        void eachHasTheRegistrationsTitleAndStatus() {
            assertThat(emitted()).hasSizeGreaterThan(3);
            for (RefusalCode code : emitted()) {
                ObjectNode api = registrationProblem(new CharterRefusal(code, "detail"));
                assertThat(PackCheckSeam.title(code))
                        .as("%s's title", code)
                        .isEqualTo(api.get("title").asString());
                assertThat(PackCheckSeam.status(code))
                        .as("%s's status", code)
                        .isEqualTo(api.get("status").asInt());
            }
        }

        @Test
        void theStatusTableOfPackCheckHoldsExactlyTheseCodes() {
            assertThat(PackCheckSeam.statusTableCodes()).containsExactlyInAnyOrderElementsOf(emitted());
        }
    }
}
