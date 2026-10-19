package ai.speckitty.charter.contracts.compat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckReleaseGateTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    @Test
    void aPendingReleaseIsComparedWithThePreviousTag() throws IOException {
        int code = run("1.1.0", "## 1.1.0\n\n## 1.0.0\n", "contract-charter-v1.0.0\n\n", "");
        assertThat(code).isZero();
        assertThat(printed())
                .isEqualTo("COMPARE contract-charter-v1.0.0\npending release 1.1.0: compared against 1.0.0\n");
    }

    @Test
    void theReleaseCommitIsComparedWithItselfAndALaterCommitIsRefused() throws IOException {
        String log = "## 1.0.0\n";
        assertThat(run("1.0.0", log, "contract-charter-v1.0.0\n", "contract-charter-v1.0.0\n"))
                .isZero();
        assertThat(printed()).startsWith("COMPARE contract-charter-v1.0.0\ncompared against 1.0.0\n");
        output.reset();
        assertThat(run("1.0.0", log, "contract-charter-v1.0.0\n", "")).isEqualTo(1);
        assertThat(printed()).contains("HEAD is not that release");
    }

    @Test
    void aBaselineContractMustBeTheVersionItsTagNames() throws IOException {
        Path archived = Files.writeString(dir.resolve("baseline.yaml"), "info:\n  version: 1.1.0-SNAPSHOT\n");
        assertThat(CheckReleaseGate.run(List.of("--baseline", archived.toString(), "contract-charter-v1.0.0"), out))
                .isEqualTo(1);
        assertThat(printed()).contains("contract-charter-v1.0.0 names 1.0.0");
        output.reset();
        Files.writeString(archived, "info:\n  version: 1.0.0\n");
        assertThat(CheckReleaseGate.run(List.of("--baseline", archived.toString(), "contract-charter-v1.0.0"), out))
                .isZero();
        assertThat(printed()).startsWith("BASELINE 1.0.0\n");
    }

    @Test
    void aMajorBumpNamesThePreviousMajorsRelease() throws IOException {
        Path openapi = Files.writeString(dir.resolve("openapi.yaml"), "info:\n  version: 2.0.0-SNAPSHOT\n");
        Path log = Files.writeString(
                dir.resolve("CHANGELOG.md"), "## 2.0.0-SNAPSHOT\n\n### Breaking\n\n- x\n\n## 1.0.0\n");
        Path merged = Files.writeString(dir.resolve("tags.txt"), "contract-charter-v1.0.0\n");
        assertThat(CheckReleaseGate.run(
                        List.of("--previous-major", openapi.toString(), log.toString(), merged.toString()), out))
                .isZero();
        assertThat(printed()).isEqualTo("PREVIOUS_MAJOR contract-charter-v1.0.0\n");
        output.reset();
        Files.writeString(merged, "");
        assertThat(CheckReleaseGate.run(
                        List.of("--previous-major", openapi.toString(), log.toString(), merged.toString()), out))
                .isZero();
        assertThat(printed()).isEqualTo("NONE\n");
    }

    @Test
    void noReleasedVersionIsNoBaseline() throws IOException {
        assertThat(run("1.0.0-SNAPSHOT", "## 1.0.0-SNAPSHOT\n", "", "")).isZero();
        assertThat(printed()).startsWith("NONE\nNo released baseline: ");
    }

    @Test
    void aMismatchExitsOne() throws IOException {
        assertThat(run("1.1.0-SNAPSHOT", "## 1.0.0-SNAPSHOT\n", "", "")).isEqualTo(1);
        assertThat(printed()).startsWith("info.version 1.1.0-SNAPSHOT");
    }

    @Test
    void withoutGitOnlyTheChangelogGatesRun() throws IOException {
        Path openapi = Files.writeString(dir.resolve("openapi.yaml"), "info:\n  version: 1.0.0-SNAPSHOT\n");
        Path changelog = Files.writeString(dir.resolve("CHANGELOG.md"), "## 1.0.0-SNAPSHOT\n");
        assertThat(CheckReleaseGate.run(List.of(openapi.toString(), changelog.toString(), "--no-git"), out))
                .isZero();
        assertThat(printed()).startsWith("SKIPPED\n");
    }

    @Test
    void aYamlNumberVersionCannotBeRead() throws IOException {
        Path openapi = Files.writeString(dir.resolve("openapi.yaml"), "info:\n  version: 1.0\n");
        Path changelog = Files.writeString(dir.resolve("CHANGELOG.md"), "## 1.0.0\n");
        assertThat(CheckReleaseGate.run(List.of(openapi.toString(), changelog.toString(), "--no-git"), out))
                .isEqualTo(2);
        assertThat(printed()).contains("has no string info.version (found the YAML value 1.0; quote it)");
    }

    @Test
    void aMissingFileCannotBeRead() {
        assertThat(CheckReleaseGate.run(List.of("missing.yaml", "missing.md", "--no-git"), out))
                .isEqualTo(2);
        assertThat(printed()).startsWith("Cannot read the release gate's inputs: ");
    }

    @Test
    void wrongArgumentsAreAUsageError() {
        assertThat(CheckReleaseGate.run(List.of("a", "b"), out)).isEqualTo(2);
        assertThat(printed()).startsWith("usage:");
    }

    private int run(String version, String changelog, String tags, String headTags) throws IOException {
        Path openapi =
                Files.writeString(dir.resolve("openapi.yaml"), "openapi: 3.1.0\ninfo:\n  version: " + version + "\n");
        Path log = Files.writeString(dir.resolve("CHANGELOG.md"), "# Changelog\n\n" + changelog);
        Path merged = Files.writeString(dir.resolve("tags.txt"), tags);
        Path atHead = Files.writeString(dir.resolve("head-tags.txt"), headTags);
        return CheckReleaseGate.run(
                List.of(openapi.toString(), log.toString(), "--git", merged.toString(), atHead.toString()), out);
    }

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
