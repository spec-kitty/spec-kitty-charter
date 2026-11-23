package ai.speckitty.charter.packvalidation.antipattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
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
 * {@code pack-doctor validate} refuses an {@code anti_pattern} node the pack declares with no file as a whole-pack
 * registration does (ADR-0052 decision 14, ADR-0053 decision 31; ADR-0075 decision 9; #154): the same code and detail
 * as {@code registration/anti-pattern-nodes.feature}, at the node's place in its document, planted in a copy of the
 * project example pack. The copy holds no manifest, as the scenarios' archive does not, so membership is not judged and
 * each planted document is its one cause.
 */
class AntiPatternNodeCheckTest {

    private static final String SHARD = "styleguide.graph.yaml";

    private static final String FRAGMENT = "drg/fragment.yaml";

    private static final String FILE = "anti_patterns/x.anti-pattern.yaml";

    private static final String NODE_SHARD = """
            schema_version: '1.0'
            generated_at: STATIC
            nodes:
            - urn: anti_pattern:x
              kind: anti_pattern
              label: X
            edges: []
            """;

    private static final String NODE_FRAGMENT = """
            nodes:
            - {id: x, kind: anti_patterns, title: X}
            edges: []
            """;

    private static final String ANTI_PATTERN = """
            schema_version: '1.0'
            id: x
            title: X
            description: The anti-pattern the graph node declares.
            severity: smell
            bad_example: the bad example
            """;

    @TempDir
    Path dir;

    private static String detail(String document) {
        return document + ": anti_pattern:x is declared with no file; an anti-pattern is held by " + FILE
                + " in the same pack";
    }

    /** Validate mode's findings on a copy of the project example pack, without its manifest, holding the files. */
    private List<Reason> validateWith(String... pathsAndTexts) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.PROJECT, dir);
        Files.delete(pack.resolve("pack-manifest.yaml"));
        for (int i = 0; i < pathsAndTexts.length; i += 2) {
            Path at = pack.resolve(pathsAndTexts[i]);
            Files.createDirectories(at.getParent());
            Files.writeString(at, pathsAndTexts[i + 1], StandardCharsets.UTF_8);
        }
        return new PackCheck(new FileSystemPackInspector())
                .validate(new PackRoot(
                        ExamplePacks.PROJECT, Optional.of(ExamplePacks.level(ExamplePacks.PROJECT)), pack.toString()))
                .findings();
    }

    @Test
    void aShardNodeWithNoFileIsADrgSchemaErrorAtTheNode() throws IOException {
        assertThat(validateWith(SHARD, NODE_SHARD))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        finding -> finding.location().pointer(),
                        Reason::detail)
                .containsExactly(tuple("drg_schema_error", SHARD, "/nodes/0", detail(SHARD)));
    }

    @Test
    void aFragmentNodeWithNoFileIsADrgSchemaErrorAtTheNode() throws IOException {
        assertThat(validateWith(FRAGMENT, NODE_FRAGMENT))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        finding -> finding.location().pointer(),
                        Reason::detail)
                .containsExactly(tuple("drg_schema_error", FRAGMENT, "/nodes/0", detail(FRAGMENT)));
    }

    @Test
    void theNodeBesideItsFileWhoseIdIsNotItsStemGetsOnlyTheFilesFault() throws IOException {
        // The file is there, refused for its own fault: the node has its file, so the file's fault is the one cause.
        assertThat(validateWith(SHARD, NODE_SHARD, FILE, ANTI_PATTERN.replace("id: x", "id: other")))
                .filteredOn(finding -> finding.isFailing(Severity.VIOLATION))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        Reason::detail)
                .containsExactly(tuple(
                        "artifact_schema_error", FILE, FILE + ": anti_pattern id 'other' is not its file stem 'x'"));
    }

    @Test
    void theNodeBesideItsFileMissingSeverityGetsOnlyTheFilesFault() throws IOException {
        assertThat(validateWith(FRAGMENT, NODE_FRAGMENT, FILE, ANTI_PATTERN.replace("severity: smell\n", "")))
                .filteredOn(finding -> finding.isFailing(Severity.VIOLATION))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        Reason::detail)
                .containsExactly(tuple("artifact_schema_error", FILE, FILE + ": missing 'severity'"));
    }

    @Test
    void theNodeBesideItsFileGivesNoFindingNamingEither() throws IOException {
        List<Reason> findings = validateWith(SHARD, NODE_SHARD, FILE, ANTI_PATTERN);
        assertThat(findings)
                .as("the pack with the node beside its file is valid")
                .filteredOn(finding -> finding.isFailing(Severity.VIOLATION))
                .isEmpty();
        assertThat(findings)
                .noneMatch(finding -> finding.location().file().equals(SHARD)
                        || finding.location().file().equals(FILE)
                        || finding.detail().contains("anti_pattern:x"));
    }
}
