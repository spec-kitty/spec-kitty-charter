package ai.speckitty.charter.packvalidation.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.examples.GoldenSubjects.Subject;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packvalidation.PackCheck;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The golden snapshot of validate mode (#554, IC-01, NFR-002, SC-002): for each planted pack and each example pack,
 * what {@link PackCheck} answers equals {@code golden/validate.json} byte for byte, its order included. It is the
 * validate half of {@code :packs}' {@code golden/surfaces.json}, split out with its subject (Pr5); the registration and
 * the compose read stay there. A difference from the snapshot taken before #554 is allowed only when it cites an entry
 * of NFR-002's diff list.
 *
 * <p>Each finding is recorded as code, severity, file, place and detail: the code's token, its severity, the reason's
 * file, the segments of its pointer (RFC 6901, decoded) and its detail. A surface that throws is recorded as it threw.
 * The temporary directory the copies live in becomes {@value #SCRATCH} wherever a text names it.
 *
 * <p>The file is written only by {@code ./gradlew :pack-validation:test --tests '*ValidateGoldenTest'
 * -Dgolden.update=true}, and a reviewer reads its diff.
 */
@DisplayName("validate mode answers what golden/validate.json recorded (#554, NFR-002)")
class ValidateGoldenTest {

    static final String VALIDATE = "validate.json";

    private static final String SCRATCH = "<scratch>";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static List<Subject> subjects() {
        return GoldenSubjects.subjects();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("validate mode's answer equals the recorded one")
    void validateModeAnswersAsRecorded(Subject subject, @TempDir Path scratch) throws IOException {
        assumeFalse(updating(), "golden.update is true: the file is being rewritten, not compared");
        requireLinks(subject, scratch);
        JsonNode recorded = read().get(subject.key());
        assertThat(recorded)
                .as("%s has no entry in %s", subject.key(), VALIDATE)
                .isNotNull();

        assertThat(render(answer(subject, scratch)))
                .as("%s: what validate mode answers today differs from its entry in %s", subject.key(), VALIDATE)
                .isEqualTo(render(Objects.requireNonNull(recorded)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("the readout's files not held are the recorded file_not_held warnings (open cases 6 and 9)")
    void theFilesNotHeldAreTheRecordedWarnings(Subject subject, @TempDir Path scratch) throws IOException {
        assumeFalse(updating(), "golden.update is true: the file is being rewritten, not compared");
        requireLinks(subject, scratch);
        JsonNode recorded = Objects.requireNonNull(read().get(subject.key()), subject.key());
        Path pack = GoldenSubjects.copy(subject, scratch.resolve("validate"));
        Function<String, String> normaliser = normaliser(scratch);

        List<String> notHeld = new FileSystemPackInspector()
                .read(new PackRoot(subject.name(), subject.configuredLevel(), pack.toString())).notHeld().stream()
                        .map(file -> normaliser.apply(file.detail()))
                        .toList();
        List<String> warned = new ArrayList<>();
        recorded.path("findings").forEach(row -> {
            if ("file_not_held".equals(row.get("code").asString())) {
                warned.add(row.get("detail").asString());
            }
        });

        assertThat(notHeld).as(subject.key()).containsExactlyInAnyOrderElementsOf(warned);
    }

    @Test
    @DisplayName("the file holds one entry per subject and nothing else, written as the writer writes it")
    void theFileHoldsExactlyTheSubjects() {
        assumeFalse(updating(), "golden.update is true: the file is being rewritten, not compared");
        JsonNode recorded = read();

        assertThat(new ArrayList<>(recorded.propertyNames()))
                .containsExactlyElementsOf(
                        subjects().stream().map(Subject::key).sorted().toList());
        assertThat(text()).isEqualTo(render(recorded));
    }

    @Test
    @DisplayName("-Dgolden.update=true rewrites golden/validate.json from today's answers")
    void rewriteTheFile(@TempDir Path scratch) throws IOException {
        assumeTrue(updating(), "golden.update is not true");
        Map<String, Object> all = new TreeMap<>();
        for (Subject subject : subjects()) {
            Path own = Files.createDirectories(scratch.resolve(String.valueOf(all.size())));
            all.put(subject.key(), answer(subject, own));
        }
        String directory = System.getProperty("charter.goldenDir");
        assertThat(directory)
                .as("system property charter.goldenDir (set by service/pack-validation/build.gradle.kts)")
                .isNotNull();
        Files.writeString(
                Path.of(Objects.requireNonNull(directory)).resolve(VALIDATE), render(all), StandardCharsets.UTF_8);
    }

    /** What validate mode answers for the subject, judged on a fresh copy of it. */
    static Object answer(Subject subject, Path scratch) throws IOException {
        Path pack = GoldenSubjects.copy(subject, scratch.resolve("validate"));
        Function<String, String> normaliser = normaliser(scratch);
        try {
            List<Map<String, Object>> findings = new ArrayList<>();
            for (Reason finding : new PackCheck(new FileSystemPackInspector())
                    .validate(new PackRoot(subject.name(), subject.configuredLevel(), pack.toString()))
                    .findings()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", finding.code().value());
                row.put("severity", finding.severity().name().toLowerCase(Locale.ROOT));
                row.put("file", normaliser.apply(finding.location().file()));
                row.put("place", place(finding.location().pointer()));
                row.put("detail", normaliser.apply(finding.detail()));
                findings.add(row);
            }
            return Map.of("findings", findings);
        } catch (RuntimeException thrown) {
            Map<String, Object> threw = new LinkedHashMap<>();
            threw.put("type", thrown.getClass().getName());
            threw.put("message", normaliser.apply(String.valueOf(thrown.getMessage())));
            return Map.of("threw", threw);
        }
    }

    /** The segments of a JSON pointer (RFC 6901), decoded; none for the whole file. */
    static List<String> place(String pointer) {
        List<String> place = new ArrayList<>();
        if (!pointer.isEmpty()) {
            for (String segment : pointer.substring(1).split("/", -1)) {
                place.add(segment.replace("~1", "/").replace("~0", "~"));
            }
        }
        return place;
    }

    /** Replaces the scratch directory, as written and as its real path, with {@value #SCRATCH}. */
    private static Function<String, String> normaliser(Path scratch) {
        List<String> spellings = new ArrayList<>();
        try {
            spellings.add(scratch.toRealPath().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        spellings.add(scratch.toAbsolutePath().normalize().toString());
        return text -> {
            String normalised = text;
            for (String spelling : spellings) {
                normalised = normalised.replace(spelling, SCRATCH);
            }
            return normalised;
        };
    }

    private static boolean updating() {
        return Boolean.getBoolean("golden.update");
    }

    /** The recorded file, from the test classpath. */
    private static String text() {
        try (InputStream in = Objects.requireNonNull(
                ValidateGoldenTest.class.getResourceAsStream("/golden/" + VALIDATE), "/golden/" + VALIDATE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode read() {
        return JSON.readTree(text());
    }

    /** The one way a golden value is written: pretty, two-space indented, LF line ends, a final newline. */
    private static String render(Object value) {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value).replace("\r\n", "\n") + "\n";
    }

    /**
     * A plant that needs symbolic links is skipped where the file system cannot make one, or fails there when the test
     * JVM's {@code charter.requireSymlinks} is true, as CI sets it (#286).
     */
    private static void requireLinks(Subject subject, Path scratch) throws IOException {
        if (!subject.plantsLinks()) {
            return;
        }
        Path target = Files.createTempFile(scratch, "probe", ".tmp");
        Path link = scratch.resolve("probe.lnk");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | FileSystemException noLinks) {
            String why = "the file system cannot create symbolic links: " + noLinks;
            if (Boolean.getBoolean("charter.requireSymlinks")) {
                fail(why + " (charter.requireSymlinks is true)", noLinks);
            }
            abort(why);
        } finally {
            Files.deleteIfExists(link);
            Files.delete(target);
        }
    }
}
