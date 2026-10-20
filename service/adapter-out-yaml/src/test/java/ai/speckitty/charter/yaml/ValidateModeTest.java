package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackFinding;
import ai.speckitty.charter.domain.PackFindingCode;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.PackRoot;
import ai.speckitty.charter.domain.PackValidation;
import ai.speckitty.charter.domain.PackVerdict;
import ai.speckitty.charter.domain.RefusalCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validate mode through its production entry point, {@link PackValidation}, over the real inspector (FR-010, SC-005):
 * a pack with three independent defects yields three findings, and the load of the same pack yields only its
 * load-path one.
 */
class ValidateModeTest {

    private static final String DUPLICATE_STEPS = """
            id: dup
            schema_version: '1.0'
            action: plan
            mission: m
            steps:
            - id: first
              description: d
            - id: second
              description: d
            - id: first
              description: d
            """;

    private static void write(Path dir, String relative, String text) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** An org pack with an unknown key in pack.yaml, a duplicate step id and a bare endpoint in the fragment. */
    private static Path threeDefectPack(Path dir) throws IOException {
        write(dir, "pack.yaml", """
                pack_id: 01ABC
                pack_version: 1.0.0
                name: qa
                pack_level: org
                mystery: 1
                """);
        write(dir, "missions/dup.step-contract.yaml", DUPLICATE_STEPS);
        write(dir, "drg/fragment.yaml", """
                nodes: []
                edges:
                - source: QA_404
                  target: directive:DIRECTIVE_010
                  relation: suggests
                """);
        return dir;
    }

    private static PackRoot root(Path dir) {
        return new PackRoot("qa", Optional.of(PackLevel.ORG), dir.toString());
    }

    @Test
    void threeIndependentDefectsAreThreeFindingsEachWithItsOwnFileAndPlace(@TempDir Path dir) throws IOException {
        threeDefectPack(dir);

        PackVerdict verdict = new PackValidation(new FileSystemPackInspector()).validate(root(dir));

        assertThat(verdict.findings())
                .containsExactly(
                        new PackFinding(
                                PackFindingCode.PACK_UNKNOWN_KEY,
                                "pack.yaml",
                                List.of("mystery"),
                                "pack.yaml of qa declares unknown key 'mystery'; it is ignored"),
                        new PackFinding(
                                PackFindingCode.DRG_SCHEMA_ERROR,
                                "drg/fragment.yaml",
                                List.of("edges", "0", "source"),
                                "qa/drg/fragment.yaml: edge endpoint 'QA_404' is not a URN"),
                        new PackFinding(
                                PackFindingCode.DUPLICATE_STEP_ID,
                                "missions/dup.step-contract.yaml",
                                List.of("steps", "2", "id"),
                                "missions/dup.step-contract.yaml: step id 'first' is declared more than once"));
        assertThat(verdict.findings())
                .extracting(PackFinding::severity)
                .containsExactly(
                        PackFindingCode.Severity.WARNING,
                        PackFindingCode.Severity.BLOCKING,
                        PackFindingCode.Severity.BLOCKING);
    }

    @Test
    void theVerdictClassifiesEveryYamlFileOfThePack(@TempDir Path dir) throws IOException {
        threeDefectPack(dir);

        PackVerdict verdict = new PackValidation(new FileSystemPackInspector()).validate(root(dir));

        assertThat(verdict.files())
                .extracting(f -> f.file() + "=" + f.kind().orElse("?"))
                .containsExactly(
                        "drg/fragment.yaml=org-fragment",
                        "missions/dup.step-contract.yaml=mission-step-contract",
                        "pack.yaml=pack-descriptor");
    }

    @Test
    void loadingTheSamePackRefusesWithTheBareEndpointAndIgnoresTheOtherTwoDefects(@TempDir Path dir)
            throws IOException {
        threeDefectPack(dir);
        FileSystemCharterPackRepository repository =
                new FileSystemCharterPackRepository(() -> List.of(new PackLocation("qa", PackLevel.ORG, dir)));

        assertThatThrownBy(repository::load).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
            assertThat(refusal.getMessage()).isEqualTo("qa/drg/fragment.yaml: edge endpoint 'QA_404' is not a URN");
        });
    }

    @Test
    void withTheBareEndpointFixedTheLoadSucceedsAndOnlyValidationStillSeesTheDuplicateStep(@TempDir Path dir)
            throws IOException {
        threeDefectPack(dir);
        write(dir, "drg/fragment.yaml", "nodes: []\nedges: []\n");
        FileSystemCharterPackRepository repository =
                new FileSystemCharterPackRepository(() -> List.of(new PackLocation("qa", PackLevel.ORG, dir)));

        CharterPack loaded = repository.load().packs().get(0);
        PackVerdict verdict = new PackValidation(new FileSystemPackInspector()).validate(root(dir));

        assertThat(loaded.diagnostics()).extracting(d -> d.code().token()).containsExactly("pack_unknown_key");
        assertThat(verdict.findings())
                .extracting(PackFinding::code)
                .containsExactly(PackFindingCode.PACK_UNKNOWN_KEY, PackFindingCode.DUPLICATE_STEP_ID);
    }

    @Test
    void aPackThatIsOnlyAPathIsValidatedWithoutAConfiguredLevel(@TempDir Path dir) throws IOException {
        write(dir, "pack.yaml", "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\n");

        PackVerdict verdict = new PackValidation(new FileSystemPackInspector())
                .validate(new PackRoot("qa", Optional.empty(), dir.toString()));

        assertThat(verdict.findings()).isEmpty();
    }
}
