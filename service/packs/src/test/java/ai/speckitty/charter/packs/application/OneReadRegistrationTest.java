package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ORG_PACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.infra.storage.ReadCounters;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One read of the staged tree per registration (#554, S1, S6, FR-006, FR-016): the registration reads the tree once
 * through the domain port, under the name it is registered as and with no configured level, and judges that read; the
 * aggregate, the strip and the stamp work from it. The storage half of the count (one walk and one run of the store's
 * read, and none for the strip and the stamp) is {@code OneReadTest}'s.
 */
@DisplayName("a registration reads the staged tree once, through PackReading (#554, FR-016)")
class OneReadRegistrationTest {

    @Test
    @DisplayName("an accepted registration reads the staged tree once, under the pack id it is registered as")
    void anAcceptedRegistrationReadsOnce(@TempDir Path host) {
        Rig rig = new Rig(host);
        StagedPack upload =
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("DIRECTIVE_001", "One"));
        ReadCounters before = ReadCounters.now();

        assertThat(rig.register("pack-x", upload, Preconditions.none()).outcome())
                .isEqualTo(Outcome.CREATED);

        // The registry holds no pack before this one. One walk, one run of the store half and one whole read are the
        // read the registration judges; the two other walks are listings to digest bytes, not reads: the revision of
        // the staged tree after the strip and the stamp (accepted, WP12 review), and the commit's revision of the
        // pack it stored (PackCommitStore).
        assertThat(ReadCounters.now().since(before)).isEqualTo(new ReadCounters(3, 1, 1));

        assertThat(rig.reading.roots)
                .containsExactly(new PackRoot(
                        "pack-x", Optional.empty(), upload.directory().toString()));
    }

    @Test
    @DisplayName("a refused registration reads the staged tree once too, and refuses on that read's rationale")
    void aRefusedRegistrationReadsOnce(@TempDir Path host) {
        Rig rig = new Rig(host);
        StagedPack upload = rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")
                .directive("DIRECTIVE_001", "One")
                .file("PROVENANCE.md", "# Provenance\n"));

        assertThatThrownBy(() -> rig.register("pack-x", upload, Preconditions.none()))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations())
                            .extracting(violation -> violation.pointer() + " " + violation.code())
                            .containsExactly("PROVENANCE.md retired");
                });
        assertThat(rig.reading.roots).hasSize(1);
    }
}
