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
 * {@code pack-doctor validate} refuses an inline {@code anti_patterns} block on a styleguide or a procedure as a
 * whole-pack registration does (ADR-0052 decision 16; ruling R-r; ADR-0075 decision 9; #154): the closed schema answers
 * {@code artifact_schema_error} at the file, the unknown key named, as {@code registration/inline-anti-patterns.feature}
 * does. Each file is planted in a copy of the project example pack without its manifest, so membership is not judged,
 * and each refusal is paired with the same file without the block, which is held.
 */
class InlineAntiPatternCheckTest {

    private static final String STYLEGUIDE = "styleguides/reasons-canvas-writing.styleguide.yaml";

    private static final String STYLEGUIDE_TEXT = """
            schema_version: '1.0'
            id: reasons-canvas-writing
            title: REASONS Canvas Writing (project override)
            scope: docs
            principles:
            - name: Lead with the summary
              description: Every canvas section opens with one sentence a reader can act on.
            """;

    private static final String STYLEGUIDE_BLOCK = """
            anti_patterns:
            - name: Burying the lede
              description: The section's point comes last.
              bad_example: Three paragraphs of context, then the ask.
              good_example: The ask, then the context.
            """;

    private static final String PROCEDURE = "procedures/land-a-change.procedure.yaml";

    private static final String PROCEDURE_TEXT = """
            schema_version: '1.0'
            id: land-a-change
            title: Land a change
            description: Land a reviewed change.
            entry_condition: A change is ready to land.
            exit_condition: The change has landed.
            steps:
            - title: Land the change
            """;

    private static final String PROCEDURE_BLOCK = """
            anti_patterns:
            - title: Landing unreviewed work
              content: Work lands only after review.
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

    private static List<Reason> naming(List<Reason> findings, String file) {
        return findings.stream()
                .filter(finding -> finding.location().file().equals(file)
                        || finding.detail().contains(file))
                .toList();
    }

    @Test
    void aStyleguideWithoutTheBlockIsHeldWithNoFindingNamingIt() throws IOException {
        assertThat(naming(validateWith(STYLEGUIDE, STYLEGUIDE_TEXT), STYLEGUIDE))
                .isEmpty();
    }

    @Test
    void aStyleguideWithAnInlineAntiPatternsBlockIsAnArtifactSchemaErrorAtTheFile() throws IOException {
        assertThat(validateWith(STYLEGUIDE, STYLEGUIDE_TEXT + STYLEGUIDE_BLOCK))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        finding -> finding.location().pointer(),
                        Reason::detail)
                .containsExactly(
                        tuple("artifact_schema_error", STYLEGUIDE, "", STYLEGUIDE + ": unknown key 'anti_patterns'"));
    }

    @Test
    void aProcedureWithoutTheBlockIsHeldWithNoFindingNamingIt() throws IOException {
        assertThat(naming(validateWith(PROCEDURE, PROCEDURE_TEXT), PROCEDURE)).isEmpty();
    }

    @Test
    void aProcedureWithAnInlineAntiPatternsBlockIsAnArtifactSchemaErrorAtTheFile() throws IOException {
        assertThat(validateWith(PROCEDURE, PROCEDURE_TEXT + PROCEDURE_BLOCK))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        finding -> finding.location().pointer(),
                        Reason::detail)
                .containsExactly(
                        tuple("artifact_schema_error", PROCEDURE, "", PROCEDURE + ": unknown key 'anti_patterns'"));
    }
}
