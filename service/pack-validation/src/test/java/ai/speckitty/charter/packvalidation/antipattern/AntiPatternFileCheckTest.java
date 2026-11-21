package ai.speckitty.charter.packvalidation.antipattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packvalidation.PackCheck;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code pack-doctor validate} holds and judges an anti-pattern file as a whole-pack registration does (ADR-0052
 * decision 8, as amended by ADR-0053 decision 30; ADR-0075 decision 9; #154): the same codes, pointers and details as
 * {@code registration/anti-pattern-files.feature}, planted in a copy of the project example pack. The copy holds no
 * manifest, as the scenarios' archive does not, so membership is not judged and each planted file is its one cause.
 */
class AntiPatternFileCheckTest {

    private static final String FILE = "anti_patterns/clever-over-direct.anti-pattern.yaml";

    private static final String VALID = """
            schema_version: '1.0'
            id: clever-over-direct
            title: Clever over direct
            description: Using a clever language feature when a direct, familiar idiom would be easier to review.
            severity: smell
            bad_example: result = next((v for v in xs if pred(v)), None)
            good_example: write the loop that finds the first match
            """;

    @TempDir
    Path dir;

    /** Validate mode's findings on a copy of the project example pack, without its manifest, holding one file. */
    private List<Reason> validateWith(String file, String text) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.PROJECT, dir);
        Files.delete(pack.resolve("pack-manifest.yaml"));
        Path at = pack.resolve(file);
        Files.createDirectories(at.getParent());
        Files.writeString(at, text, StandardCharsets.UTF_8);
        return new PackCheck(new FileSystemPackInspector())
                .validate(new PackRoot(
                        ExamplePacks.PROJECT, Optional.of(ExamplePacks.level(ExamplePacks.PROJECT)), pack.toString()))
                .findings();
    }

    @Test
    void aValidAntiPatternFileIsHeldWithNoFindingNamingIt() throws IOException {
        assertThat(validateWith(FILE, VALID))
                .as("neither a violation nor a file_not_held diagnostic names the file")
                .noneMatch(finding -> finding.location().file().equals(FILE)
                        || finding.detail().contains(FILE));
    }

    @Test
    void aFileWithoutSeverityIsAnArtifactSchemaErrorAtTheFile() throws IOException {
        assertThat(validateWith(FILE, VALID.replace("severity: smell\n", "")))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        finding -> finding.location().pointer(),
                        Reason::detail)
                .containsExactly(tuple("artifact_schema_error", FILE, "", FILE + ": missing 'severity'"));
    }

    @Test
    void aFileWithoutBadExampleIsAnArtifactSchemaErrorAtTheFile() throws IOException {
        assertThat(validateWith(
                        FILE, VALID.replace("bad_example: result = next((v for v in xs if pred(v)), None)\n", "")))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        finding -> finding.location().pointer(),
                        Reason::detail)
                .containsExactly(tuple("artifact_schema_error", FILE, "", FILE + ": missing 'bad_example'"));
    }

    @Test
    void anUnknownSeverityIsAnArtifactSchemaErrorAtTheFile() throws IOException {
        assertThat(validateWith(FILE, VALID.replace("severity: smell", "severity: minor")))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        finding -> finding.location().pointer(),
                        Reason::detail)
                .containsExactly(tuple(
                        "artifact_schema_error",
                        FILE,
                        "/severity",
                        FILE + ": 'severity' has an unsupported value 'minor'"));
    }

    @Test
    void aFileOutsideAntiPatternsIsRefusedByPlacement() throws IOException {
        String misplaced = "clever-over-direct.anti-pattern.yaml";

        assertThat(validateWith(misplaced, VALID))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        Reason::detail)
                .containsExactly(tuple(
                        "artifact_schema_error",
                        misplaced,
                        misplaced + ": anti_pattern 'clever-over-direct' is not at its place '" + FILE + "'"));
    }

    @Test
    void aFileWhoseIdIsNotItsStemIsRefusedByTheStemRule() throws IOException {
        assertThat(validateWith(FILE, VALID.replace("id: clever-over-direct", "id: other")))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        Reason::detail)
                .containsExactly(tuple(
                        "artifact_schema_error",
                        FILE,
                        FILE + ": anti_pattern id 'other' is not its file stem 'clever-over-direct'"));
    }
}
