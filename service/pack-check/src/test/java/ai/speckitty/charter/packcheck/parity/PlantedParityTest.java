package ai.speckitty.charter.packcheck.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packcheck.PackCheckMain;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.examples.GoldenSubjects.Subject;
import ai.speckitty.charter.packvalidation.placement.PlacementCodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * NFR-003, parity with the registration (#478, T022, squad S11): for every planted golden subject, and every example
 * pack, the codes {@code pack-check} reports for the pack alone, run in process through {@link PackCheckMain#run}, equal
 * the codes the registration answered for it, as {@code :packs}' {@code golden/surfaces.json} records them, as a
 * multiset by file.
 *
 * <p>The expected side is read from the golden files, never from what {@code pack-check} renders. The one expected
 * difference is placement: the CLI tells validate mode each subject's configured level, so it adds the placement codes
 * ({@link PlacementCodes}) the registration never states. Those are asserted on their own, against what validate mode
 * answered for the subject ({@code :pack-validation}'s {@code golden/validate.json}): its placement violations, and,
 * for a pack with no level at all (none configured, and a {@code pack.yaml} that reads and declares no
 * {@code pack_level}), the {@code pack_level_missing} violation the pack-set check states at {@code pack.yaml}, since
 * {@code CLOSURE} cannot place such a pack (WP03 review M3). That one is read from the pack's own descriptor and from
 * surfaces.json, not from pack-check.
 *
 * <p>A planted subject is checked alone. An example pack is checked with the other example packs, as the registration
 * is answered beside the packs the store holds: the set holds the packs it depends on (ruling D5), so {@code CLOSURE}
 * runs and finds nothing, and only that pack's problems are compared.
 *
 * <p>The golden files are read from the sibling modules' test resources, under the module directory Gradle runs the
 * test in; neither is on this module's test classpath. A subject that plants links is skipped where the file system
 * cannot make one, and fails there when {@code charter.requireSymlinks} is true, as CI sets it (#286).
 */
@DisplayName("pack-check reports the registration's codes for every planted subject (#478, NFR-003)")
class PlantedParityTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** {@code :packs}' golden file of the registration and the compose read. */
    private static final Path SURFACES = Path.of("..", "packs", "src", "test", "resources", "golden", "surfaces.json");

    /** {@code :pack-validation}'s golden file of validate mode. */
    private static final Path VALIDATE =
            Path.of("..", "pack-validation", "src", "test", "resources", "golden", "validate.json");

    /** The codes of the descriptor's format prechecks: a pack.yaml that states one has no composition facts (#624). */
    private static final Set<String> DESCRIPTOR_FORMAT =
            Set.of("requires_core_invalid", "pack_version_invalid", "dependency_range_invalid");

    /** The tokens of the placement codes, which validate mode states and the registration does not. */
    private static final Set<String> PLACEMENT =
            PlacementCodes.ALL.stream().map(ReasonCode::value).collect(Collectors.toUnmodifiableSet());

    static List<Subject> subjects() {
        return GoldenSubjects.subjects();
    }

    private static final String EXAMPLE = "example-packs/";

    private static final String LEVEL_MISSING = PlacementCodes.PACK_LEVEL_MISSING.value();

    private static final String PACK_YAML = "pack.yaml";

    /** What one run answered. */
    private record Run(int exit, JsonNode report) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("the report's codes are the registration's, by file, and the placement codes are validate mode's")
    void theReportsCodesAreTheRegistrations(Subject subject, @TempDir Path scratch) throws IOException {
        Path pack = copy(subject, scratch);
        JsonNode registration = recorded(SURFACES, subject).get("registration");
        JsonNode findings = recorded(VALIDATE, subject).get("findings");
        List<String> args = new ArrayList<>(List.of("--pack", value(pack, subject.configuredLevel())));
        if (subject.key().startsWith(EXAMPLE)) {
            args.addAll(theOtherExamplePacks(subject, scratch.resolve("set")));
        }

        Run run = run(subject, args);

        String instance = pack.toUri().toString();
        Map<String, List<String>> reported =
                byFile(violationsOf(run.report(), instance), code -> !PLACEMENT.contains(code));
        Map<String, List<String>> placed = byFile(violationsOf(run.report(), instance), PLACEMENT::contains);
        assertThat(reported)
                .as("%s: the codes pack-check reports beside placement, by file, against surfaces.json", subject)
                .isEqualTo(byFile(registrationViolations(registration), code -> true));
        assertThat(placed)
                .as("%s: the placement codes pack-check reports, by file, against validate.json", subject)
                .isEqualTo(byFile(
                        placementIn(
                                findings,
                                subject.configuredLevel().isEmpty() && hasNoLevelAtAll(pack, registration, findings)),
                        PLACEMENT::contains));
        assertThat(otherProblems(run.report(), instance))
                .as("%s: the set has no problem but the pack's validation_failed", subject)
                .isEmpty();
        assertThat(run.exit())
                .as("%s: the exit code", subject)
                .isEqualTo(reported.isEmpty() && placed.isEmpty() ? 0 : 1);
    }

    @Test
    @DisplayName("every subject the golden files record is one this test runs, and the set is not empty")
    void theSubjectsAreTheGoldenFilesKeys() {
        List<String> keys = subjects().stream().map(Subject::key).sorted().toList();

        assertThat(keys).isNotEmpty().anyMatch(key -> key.startsWith("planted/"));
        assertThat(List.copyOf(read(SURFACES).propertyNames())).containsExactlyElementsOf(keys);
        assertThat(List.copyOf(read(VALIDATE).propertyNames())).containsExactlyElementsOf(keys);
    }

    /** A fresh copy of the subject, with its links; skipped, or failed, where the file system cannot make a link. */
    private static Path copy(Subject subject, Path scratch) throws IOException {
        try {
            return GoldenSubjects.copy(subject, scratch.resolve("host"));
        } catch (UnsupportedOperationException | FileSystemException noLinks) {
            if (!subject.plantsLinks()) {
                throw noLinks;
            }
            String why = "the file system cannot create symbolic links: " + noLinks;
            if (Boolean.getBoolean("charter.requireSymlinks")) {
                fail(why + " (charter.requireSymlinks is true)", noLinks);
            }
            return abort(why);
        }
    }

    /** A {@code --pack} value: the directory, at the level validate mode is told when there is one. */
    private static String value(Path pack, Optional<PackLevel> level) {
        return pack + level.map(known -> ":" + known.token()).orElse("");
    }

    /** {@code --pack} for each example pack but the subject, each copied under {@code host} at its own level. */
    private static List<String> theOtherExamplePacks(Subject subject, Path host) {
        List<String> args = new ArrayList<>();
        for (String name : ExamplePacks.names()) {
            if (!name.equals(subject.name())) {
                args.add("--pack");
                args.add(value(ExamplePacks.copyOf(name, host), Optional.of(ExamplePacks.level(name))));
            }
        }
        assertThat(args)
                .as("the other example packs")
                .hasSize(2 * (ExamplePacks.names().size() - 1));
        return args;
    }

    /** {@code pack-check} over {@code args}, in process. */
    private static Run run(Subject subject, List<String> args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = PackCheckMain.run(
                args.toArray(String[]::new),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertThat(exit)
                .as("%s: pack-check ran (stderr: %s)", subject, err.toString(StandardCharsets.UTF_8))
                .isIn(0, 1);
        return new Run(exit, JSON.readTree(out.toString(StandardCharsets.UTF_8)));
    }

    /** One violation: its file and its code. */
    private record Violation(String file, String code) {}

    /** Whether a problem is the {@code validation_failed} of the pack whose {@code instance} is given. */
    private static boolean isThePacks(JsonNode problem, String instance) {
        return "validation_failed".equals(problem.get("code").asString())
                && problem.has("instance")
                && instance.equals(problem.get("instance").asString());
    }

    /** The violations of the pack's {@code validation_failed} problems. */
    private static List<Violation> violationsOf(JsonNode report, String instance) {
        List<Violation> violations = new ArrayList<>();
        for (JsonNode problem : report.get("problems")) {
            if (isThePacks(problem, instance)) {
                problem.get("violations")
                        .forEach(violation -> violations.add(new Violation(
                                violation.get("pointer").asString(),
                                violation.get("code").asString())));
            }
        }
        return violations;
    }

    /** The report's problems that are not a {@code validation_failed} of the pack. */
    private static List<String> otherProblems(JsonNode report, String instance) {
        List<String> others = new ArrayList<>();
        for (JsonNode problem : report.get("problems")) {
            if (!isThePacks(problem, instance)) {
                others.add(problem.get("code").asString() + ": "
                        + problem.get("detail").asString());
            }
        }
        return others;
    }

    /** The registration's violations as surfaces.json records them; none for an accepted pack. */
    private static List<Violation> registrationViolations(JsonNode registration) {
        JsonNode refused = registration.get("refused");
        List<Violation> violations = new ArrayList<>();
        if (refused == null) {
            assertThat(registration.has("accepted"))
                    .as("a registration is refused or accepted")
                    .isTrue();
            return violations;
        }
        assertThat(refused.get("code").asString())
                .as("a refused registration of a pack's content")
                .isEqualTo("validation_failed");
        refused.get("violations")
                .forEach(violation -> violations.add(new Violation(
                        violation.get("pointer").asString(),
                        violation.get("code").asString())));
        return violations;
    }

    /**
     * The placement violations the CLI is expected to add: validate mode's, from validate.json, and the pack-set
     * check's {@code pack_level_missing} at {@code pack.yaml} when the pack has no level at all.
     */
    private static List<Violation> placementIn(JsonNode findings, boolean noLevelAtAll) {
        List<Violation> violations = new ArrayList<>();
        for (JsonNode finding : findings) {
            if ("violation".equals(finding.get("severity").asString())) {
                violations.add(new Violation(
                        finding.get("file").asString(), finding.get("code").asString()));
            }
        }
        if (noLevelAtAll) {
            violations.add(new Violation(PACK_YAML, LEVEL_MISSING));
        }
        return violations;
    }

    /**
     * Whether the pack, given no level, has none at all: its {@code pack.yaml} reads (the registration refuses no
     * {@code pack_unreadable} at it, as surfaces.json records), passes the format prechecks (validate mode states no
     * {@link #DESCRIPTOR_FORMAT} code at it, as validate.json records) and holds no top-level {@code pack_level} with a
     * value. A pack whose descriptor does not read, or is malformed, has no facts (#624), so {@code CLOSURE} is skipped
     * for it and no level is asked of it.
     */
    private static boolean hasNoLevelAtAll(Path pack, JsonNode registration, JsonNode findings) throws IOException {
        Path descriptor = pack.resolve(PACK_YAML);
        boolean unreadable = registrationViolations(registration).contains(new Violation(PACK_YAML, "pack_unreadable"));
        boolean malformed = false;
        for (JsonNode finding : findings) {
            malformed |= PACK_YAML.equals(finding.get("file").asString())
                    && DESCRIPTOR_FORMAT.contains(finding.get("code").asString());
        }
        if (unreadable || malformed || !Files.isRegularFile(descriptor)) {
            return false;
        }
        return Files.readAllLines(descriptor, StandardCharsets.UTF_8).stream()
                .noneMatch(line -> line.matches("pack_level:\\s*\\S.*"));
    }

    /** The codes of the violations {@code keep} accepts, per file, each file's codes sorted: a multiset by file. */
    private static Map<String, List<String>> byFile(List<Violation> violations, Predicate<String> keep) {
        Map<String, List<String>> byFile = new TreeMap<>();
        for (Violation violation : violations) {
            if (keep.test(violation.code())) {
                byFile.computeIfAbsent(violation.file(), file -> new ArrayList<>())
                        .add(violation.code());
            }
        }
        byFile.values().forEach(codes -> codes.sort(null));
        return byFile;
    }

    private static JsonNode recorded(Path golden, Subject subject) {
        JsonNode entry = read(golden).get(subject.key());
        assertThat(entry).as("%s has no entry in %s", subject.key(), golden).isNotNull();
        return Objects.requireNonNull(entry);
    }

    private static JsonNode read(Path golden) {
        assertThat(golden)
                .as("the golden file, read from the module directory the test runs in")
                .isRegularFile();
        return JSON.readTree(golden.toFile());
    }
}
