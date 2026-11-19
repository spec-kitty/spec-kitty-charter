package ai.speckitty.charter.packvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PackCheck#check}: one read gives the readout and validate mode's verdict, so the pack-set check takes a pack's
 * composition facts from the read validate mode made (#478, T010, S1).
 */
@DisplayName("PackCheck.check: the readout beside the verdict, from one read (#478, T010)")
class PackCheckReadoutTest {

    /** The real reader, counting the roots it is asked for. */
    private static final class CountingReading implements PackReading {
        private final FileSystemPackInspector inspector = new FileSystemPackInspector();
        private final List<PackRoot> roots = new ArrayList<>();

        @Override
        public PackReadout read(PackRoot root) {
            roots.add(root);
            return inspector.read(root);
        }
    }

    private static PackRoot example(String name) {
        return new PackRoot(
                name,
                Optional.of(ExamplePacks.level(name)),
                ExamplePacks.pack(name).toString());
    }

    @Test
    @DisplayName("a check reads the root once, and its readout carries the facts of that read")
    void aCheckReadsOnce() {
        CountingReading reading = new CountingReading();
        PackRoot root = example(ExamplePacks.ORG);

        PackCheck.Checked checked = new PackCheck(reading).check(root);

        assertThat(reading.roots).containsExactly(root);
        assertThat(checked.readout().facts())
                .isInstanceOfSatisfying(
                        PackReadout.Built.class,
                        built -> assertThat(built.descriptor().name()).isEqualTo(ExamplePacks.ORG));
        assertThat(checked.verdict().files()).isEqualTo(checked.readout().files());
    }

    @Test
    @DisplayName("a validation reads the root once too")
    void aValidationReadsOnce() {
        CountingReading reading = new CountingReading();
        PackRoot root = example(ExamplePacks.ORG);

        new PackCheck(reading).validate(root);

        assertThat(reading.roots).containsExactly(root);
    }

    @Test
    @DisplayName("a valid example pack: check's verdict is validate's")
    void aValidPacksVerdictIsValidates() {
        PackCheck check = new PackCheck(new FileSystemPackInspector());
        PackRoot root = example(ExamplePacks.PROJECT);

        assertThat(check.check(root).verdict()).isEqualTo(check.validate(root));
    }

    @Test
    @DisplayName("a planted subject: check's verdict is validate's, findings and all")
    void aPlantedSubjectsVerdictIsValidates(@TempDir Path dir) {
        Path pack = PlantedPacks.copyOf("duplicate-artifact-id", dir);
        PackCheck check = new PackCheck(new FileSystemPackInspector());
        PackRoot root = new PackRoot("qa", Optional.of(PackLevel.ORG), pack.toString());

        PackCheck.Checked checked = check.check(root);

        assertThat(checked.verdict().findings())
                .extracting(finding -> finding.code().value())
                .contains("duplicate_artifact_id");
        assertThat(checked.verdict()).isEqualTo(check.validate(root));
    }
}
