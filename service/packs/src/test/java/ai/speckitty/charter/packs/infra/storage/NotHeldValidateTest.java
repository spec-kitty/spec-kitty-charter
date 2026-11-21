package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import ai.speckitty.charter.packvalidation.PackCheck;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Validate mode judges nothing in a file the store does not hold (#554, open case 6; spec US3 scenarios 2 and 4). Each
 * of the golden subjects of open case 6 plants a {@code charter.yaml} or a shard below the pack root, some of them
 * invalid. Of the twelve, the C4 and C16 plants moved their shard to the pack root in WP11's review, so that those
 * rules keep a plant, and are judged there. Validate mode answers one {@code file_not_held} warning at that file, in
 * the registration's words, and no finding of its contents: on the bare plant that is the whole verdict beside the
 * refusal of its missing descriptor (R2 row 24, WP09), and with a descriptor beside it the warning stands beside the
 * descriptor's own findings.
 */
@DisplayName("validate mode warns file_not_held for a charter.yaml or a nested shard and judges nothing in it (#554)")
class NotHeldValidateTest {

    private static final String DESCRIPTOR =
            "pack_id: 01ARWG13C000000000000000FG\npack_version: 1.0.0\nname: qa\npack_level: org\n";

    private static final String PACK_YAML = "pack.yaml";

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "c2-charter-nested-unknown-key, charter.yaml",
        "c3-shard-edge-bare-endpoint, drg/tactic.graph.yaml",
        "c3-shard-urn-malformed, drg/tactic.graph.yaml",
        "c7-shard-relation-extends, drg/tactic.graph.yaml",
        "c8-shard-without-generated-by-is-accepted, drg/tactic.graph.yaml",
        "charter-cli-timeout-not-a-number, charter.yaml",
        "charter-invalid, charter.yaml",
        "charter-template-set, charter.yaml",
        "charter-valid, charter.yaml",
        "r15-glossary-urn-in-a-shard, drg/glossary.graph.yaml"
    })
    @DisplayName("each plant: valid with one file_not_held warning, and beside a descriptor only its findings added")
    void aFileTheStoreDoesNotHoldIsOneWarningAndNothingElse(String plant, String file, @TempDir Path host)
            throws IOException {
        Path bare = PlantedPacks.copyOf(plant, host.resolve("bare"));
        assertThat(bare.resolve(PACK_YAML)).as("the plant holds no descriptor").doesNotExist();
        Reason warning = new Reason(
                StorageCodes.FILE_NOT_HELD.value(),
                new Origin(StorageCodes.FILE_NOT_HELD.value()),
                Evaluation.FAIL,
                StorageCodes.FILE_NOT_HELD,
                Location.in(file),
                file + ": not held (no pack file kind models this file)");

        PackVerdict alone = validate(bare, Optional.empty());

        assertThat(alone.findings())
                .as("the bare plant: the one warning, beside the missing descriptor's refusal (R2 row 24)")
                .filteredOn(finding -> !finding.location().file().equals(PACK_YAML))
                .containsExactly(warning);
        assertThat(alone.findings())
                .filteredOn(finding -> finding.location().file().equals(PACK_YAML))
                .extracting(Reason::code, Reason::detail)
                .containsExactly(
                        tuple(StorageCodes.PACK_UNREADABLE, "pack " + bare.getFileName() + " has no pack.yaml"));

        Path described = PlantedPacks.copyOf(plant, host.resolve("described"));
        Files.writeString(described.resolve(PACK_YAML), DESCRIPTOR, StandardCharsets.UTF_8);
        Path control = PlantedPacks.copyOf(plant, host.resolve("control"));
        Files.writeString(control.resolve(PACK_YAML), DESCRIPTOR, StandardCharsets.UTF_8);
        Files.delete(control.resolve(file));

        List<Reason> withDescriptor =
                validate(described, Optional.of(PackLevel.ORG)).findings();

        assertThat(withDescriptor)
                .as("the descriptor's findings, and the warning")
                .filteredOn(finding -> !finding.location().file().equals(PACK_YAML))
                .containsExactly(warning);
        assertThat(withDescriptor)
                .as("nothing at the file but the warning, and the rest as for the pack without the file")
                .filteredOn(finding -> finding.location().file().equals(PACK_YAML))
                .containsExactlyElementsOf(
                        validate(control, Optional.of(PackLevel.ORG)).findings());
        assertThat(withDescriptor)
                .as("valid: a file the store does not hold refuses nothing")
                .noneMatch(finding -> finding.severity() == Severity.VIOLATION
                        && !finding.location().file().equals(PACK_YAML));
    }

    private static PackVerdict validate(Path pack, Optional<PackLevel> level) {
        return new PackCheck(new FileSystemPackInspector())
                .validate(new PackRoot(String.valueOf(pack.getFileName()), level, pack.toString()));
    }
}
