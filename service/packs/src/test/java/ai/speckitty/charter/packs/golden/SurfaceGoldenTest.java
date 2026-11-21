package ai.speckitty.charter.packs.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.examples.GoldenSubjects.Subject;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * The golden snapshot of the registration and the compose read (#554, IC-01, NFR-002, SC-002): for each planted pack
 * and each example pack, what the registration's staged load and the compose read answer, as {@link SurfaceAnswers}
 * records it, equals {@code golden/surfaces.json} byte for byte. Validate mode's half is {@code :pack-validation}'s
 * {@code golden/validate.json} (WP09). It was taken before any behaviour of
 * #554 changed; a later difference is allowed only when it cites Q1 or a row of the R2 code table
 * ({@code docs/development/notes/pack-code-unification.md}).
 *
 * <p>The file is written only by {@code ./gradlew :packs:test --tests '*SurfaceGoldenTest' -Dgolden.update=true}, and
 * a reviewer reads its diff. The contract scenarios build their archives inside Cucumber steps, so they stay their own
 * wire oracle, through {@code :deployable:test}.
 */
@DisplayName("the registration and the compose read answer what golden/surfaces.json recorded (#554, NFR-002)")
class SurfaceGoldenTest {

    static List<Subject> subjects() {
        return GoldenSubjects.subjects();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("each surface's answer equals the recorded one")
    void eachSurfaceAnswersAsRecorded(Subject subject, @TempDir Path scratch) throws IOException {
        assumeFalse(Golden.updating(), "golden.update is true: the file is being rewritten, not compared");
        requireLinks(subject, scratch);
        JsonNode recorded = Golden.read(Golden.SURFACES).get(subject.key());
        assertThat(recorded)
                .as("%s has no entry in %s", subject.key(), Golden.SURFACES)
                .isNotNull();

        String answered = Golden.render(SurfaceAnswers.of(subject, scratch));

        assertThat(answered)
                .as("%s: what the surfaces answer today differs from its entry in %s", subject.key(), Golden.SURFACES)
                .isEqualTo(Golden.render(Objects.requireNonNull(recorded)));
    }

    @Test
    @DisplayName(
            "a compose read refused for an unreadable graph document lists the violations a registration lists at its graph"
                    + " documents, file, pointer and reason (#617)")
    void anUnreadableGraphIsRefusedWithRegistrationsViolations() {
        assumeFalse(Golden.updating(), "golden.update is true: the file is being rewritten, not compared");
        JsonNode recorded = Golden.read(Golden.SURFACES);
        List<String> graphRefusals = new ArrayList<>();
        for (String key : recorded.propertyNames()) {
            JsonNode refused = recorded.get(key).path("composeRead").path("refused");
            if (!refused.path("detail").asString("").startsWith("a graph document of ")) {
                continue;
            }
            graphRefusals.add(key);
            List<JsonNode> atGraphs = new ArrayList<>();
            recorded.get(key)
                    .path("registration")
                    .path("refused")
                    .path("violations")
                    .forEach(violation -> {
                        if (isGraphDocument(violation.path("pointer").asString(""))) {
                            atGraphs.add(violation);
                        }
                    });
            List<JsonNode> composed = new ArrayList<>();
            refused.path("violations").forEach(composed::add);

            assertThat(composed)
                    .as("%s: the compose read's violations are registration's at its graph documents", key)
                    .isNotEmpty()
                    .containsExactlyElementsOf(atGraphs);
        }
        assertThat(graphRefusals)
                .as("the planted graph faults the compose read refuses")
                .hasSizeGreaterThan(16);
    }

    /** A root graph shard or the fragment: the documents a pack's graph is read from. */
    private static boolean isGraphDocument(String pointer) {
        return pointer.equals("drg/fragment.yaml") || (pointer.endsWith(".graph.yaml") && !pointer.contains("/"));
    }

    @Test
    @DisplayName("the file holds one entry per subject and nothing else, written as the writer writes it")
    void theFileHoldsExactlyTheSubjects() {
        assumeFalse(Golden.updating(), "golden.update is true: the file is being rewritten, not compared");
        JsonNode recorded = Golden.read(Golden.SURFACES);
        List<String> keys = new ArrayList<>(recorded.propertyNames());

        assertThat(keys)
                .containsExactlyElementsOf(
                        subjects().stream().map(Subject::key).sorted().toList());
        assertThat(Golden.text(Golden.SURFACES)).isEqualTo(Golden.render(recorded));
    }

    @Test
    @DisplayName("-Dgolden.update=true rewrites golden/surfaces.json from today's answers")
    void rewriteTheFile(@TempDir Path scratch) throws IOException {
        assumeTrue(Golden.updating(), "golden.update is not true");
        Map<String, Object> all = new TreeMap<>();
        for (Subject subject : subjects()) {
            Path own = Files.createDirectories(scratch.resolve(String.valueOf(all.size())));
            all.put(subject.key(), SurfaceAnswers.of(subject, own));
        }
        Golden.write(Golden.SURFACES, all);
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
