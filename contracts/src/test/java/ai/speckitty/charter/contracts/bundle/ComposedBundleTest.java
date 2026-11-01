package ai.speckitty.charter.contracts.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** What the normalization of vacuum's composed bundle keeps, moves, merges and refuses (#294). */
class ComposedBundleTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);

    @Test
    void pathItemsMoveBackUnderPathsAndTheirSectionIsDropped() {
        ComposedBundle.Normalized normalized = ComposedBundle.normalize(yaml("""
                openapi: 3.1.0
                paths:
                  /probe:
                    $ref: '#/components/pathItems/probe'
                components:
                  pathItems:
                    probe:
                      get:
                        responses:
                          '200':
                            $ref: '#/components/responses/Probe'
                  responses:
                    Probe:
                      description: The probe.
                """));

        assertThat(normalized.problems()).isEmpty();
        assertThat(normalized.document()).isEqualTo(yaml("""
                        openapi: 3.1.0
                        paths:
                          /probe:
                            get:
                              responses:
                                '200':
                                  $ref: '#/components/responses/Probe'
                        components:
                          responses:
                            Probe:
                              description: The probe.
                        """));
    }

    @Test
    void aRenamedClashThatHoldsTheSameContentIsMergedIntoItsName() {
        // vacuum names one file twice when two files reach it by different relative paths (Problem, Problem__schemas).
        ComposedBundle.Normalized normalized = ComposedBundle.normalize(yaml("""
                paths: {}
                components:
                  schemas:
                    Problem:
                      type: object
                    Refusal:
                      allOf:
                        - $ref: '#/components/schemas/Problem__schemas'
                    Problem__schemas:
                      type: object
                """));

        assertThat(normalized.problems()).isEmpty();
        assertThat(normalized.document()).isEqualTo(yaml("""
                        paths: {}
                        components:
                          schemas:
                            Problem:
                              type: object
                            Refusal:
                              allOf:
                                - $ref: '#/components/schemas/Problem'
                        """));
    }

    @Test
    void renamedClashesThatDifferOnlyByOtherRenamedClashesAreMergedTogether() {
        ComposedBundle.Normalized normalized = ComposedBundle.normalize(yaml("""
                paths: {}
                components:
                  schemas:
                    Outer:
                      $ref: '#/components/schemas/Inner'
                    Outer__schemas:
                      $ref: '#/components/schemas/Inner__schemas'
                    Inner:
                      type: string
                    Inner__schemas:
                      type: string
                """));

        assertThat(normalized.problems()).isEmpty();
        assertThat(normalized.document()).isEqualTo(yaml("""
                        paths: {}
                        components:
                          schemas:
                            Outer:
                              $ref: '#/components/schemas/Inner'
                            Inner:
                              type: string
                        """));
    }

    @Test
    void aRenamedClashWhoseContentDiffersIsRefused() {
        ComposedBundle.Normalized normalized = ComposedBundle.normalize(yaml("""
                paths: {}
                components:
                  schemas:
                    Problem:
                      type: object
                    Problem__schemas:
                      type: string
                """));

        assertThat(normalized.problems())
                .containsExactly("/components/schemas/Problem__schemas: two source files share the name Problem;"
                        + " give one of them another file name");
    }

    @Test
    void aNameHoldingTheClashSeparatorWithNoNamedComponentIsRefusedAsSuch() {
        ComposedBundle.Normalized normalized = ComposedBundle.normalize(yaml("""
                paths: {}
                components:
                  schemas:
                    Pack__Level:
                      type: string
                """));

        assertThat(normalized.problems())
                .containsExactly("/components/schemas/Pack__Level: the name holds __, which vacuum puts in the name"
                        + " of a clash; give the source file a name without it");
    }

    @Test
    void aReferenceThatIsNotToAComponentIsRefused() {
        ComposedBundle.Normalized normalized = ComposedBundle.normalize(yaml("""
                paths:
                  /probe:
                    get:
                      responses:
                        '200':
                          $ref: ../responses/Probe.yaml
                """));

        assertThat(normalized.problems())
                .containsExactly("/paths/~1probe/get/responses/200/$ref: ../responses/Probe.yaml is not a reference"
                        + " to a component of the bundle; the bundle must be self-contained");
    }

    @Test
    void aReferenceToAMissingComponentIsRefused() {
        ComposedBundle.Normalized normalized = ComposedBundle.normalize(yaml("""
                paths:
                  /probe:
                    get:
                      responses:
                        '200':
                          $ref: '#/components/responses/Missing'
                components:
                  responses: {}
                """));

        assertThat(normalized.problems())
                .containsExactly("/paths/~1probe/get/responses/200/$ref: #/components/responses/Missing names no"
                        + " component of the bundle");
    }

    @Test
    void aReferenceWithSiblingKeysIsRefused() {
        ComposedBundle.Normalized normalized = ComposedBundle.normalize(yaml("""
                paths: {}
                components:
                  schemas:
                    Probe:
                      type: string
                    Holder:
                      properties:
                        probe:
                          $ref: '#/components/schemas/Probe'
                          description: A sibling of the reference.
                """));

        assertThat(normalized.problems())
                .containsExactly("/components/schemas/Holder/properties/probe/$ref: a reference with sibling keys"
                        + " (description); the checks read a reference as the component it names, nothing more");
    }

    @Test
    void theWrittenBundleRepeatsASharedNodeInsteadOfAnchoringIt() {
        Map<String, Object> shared = Map.of("$ref", "#/components/headers/H");
        String written = ComposedBundle.dump(Map.of(
                "paths",
                Map.of("/a", Map.of("headers", Map.of("H", shared)), "/b", Map.of("headers", Map.of("H", shared)))));

        assertThat(written).doesNotContain("&").doesNotContain("*").contains("$ref: '#/components/headers/H'");
    }

    @Test
    void theCommandLineWritesTheNormalizedBundle(@TempDir Path dir) throws IOException {
        Path composed = Files.writeString(dir.resolve("composed.yaml"), """
                openapi: 3.1.0
                paths:
                  /probe:
                    $ref: '#/components/pathItems/probe'
                components:
                  pathItems:
                    probe:
                      get:
                        responses:
                          '200':
                            description: The probe.
                """);
        Path bundle = dir.resolve("bundle.yaml");

        assertThat(ComposedBundle.run(List.of(composed.toString(), bundle.toString()), out))
                .isZero();
        assertThat(yaml(Files.readString(bundle))).isEqualTo(yaml("""
                        openapi: 3.1.0
                        paths:
                          /probe:
                            get:
                              responses:
                                '200':
                                  description: The probe.
                        """));
    }

    @Test
    void theCommandLineRefusesABundleWithProblemsAndWritesNothing(@TempDir Path dir) throws IOException {
        Path composed = Files.writeString(dir.resolve("composed.yaml"), """
                paths:
                  /probe:
                    $ref: ../paths/probe.yaml
                """);
        Path bundle = dir.resolve("bundle.yaml");

        assertThat(ComposedBundle.run(List.of(composed.toString(), bundle.toString()), out))
                .isEqualTo(1);
        assertThat(printed())
                .isEqualTo("1 problem with the composed bundle:\n/paths/~1probe/$ref: ../paths/probe.yaml is not a"
                        + " reference to a component of the bundle; the bundle must be self-contained\n");
        assertThat(bundle).doesNotExist();
    }

    @Test
    void theCommandLineNeedsTwoArgumentsAndAReadableFile(@TempDir Path dir) {
        assertThat(ComposedBundle.run(List.of(), out)).isEqualTo(2);
        assertThat(printed()).startsWith("usage:");
        output.reset();
        Path missing = dir.resolve("missing.yaml");
        assertThat(ComposedBundle.run(
                        List.of(missing.toString(), dir.resolve("out.yaml").toString()), out))
                .isEqualTo(2);
        assertThat(printed()).startsWith("Cannot read " + missing + ": ");
    }

    private static Object yaml(String text) {
        return new Load(LoadSettings.builder().build()).loadFromString(text);
    }

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
