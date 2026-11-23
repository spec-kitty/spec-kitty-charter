package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ORG_PACK;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.testfixtures.Fifos;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Operator ruling R-ad: a staged tree that holds a special file (here a FIFO, made at test time) is refused
 * {@code validation_failed}, with one {@code unreadable} violation at the file's path, and nothing is stored, as a link
 * is refused ({@link PackRegistrationLinkTest}). The test skips where the operating system cannot make a FIFO. Before
 * R-ad the walk passed over such a file, and the registration stored the pack.
 */
@Timeout(120)
@DisplayName("a staged pack holding a special file is refused (R-ad)")
class PackRegistrationSpecialFileTest {

    @ParameterizedTest(name = "a FIFO at {0}")
    @ValueSource(strings = {"NOTES.fifo", "directives/B.directive.yaml"})
    @DisplayName("the registration answers validation_failed with unreadable at the special file, and stores nothing")
    void aSpecialFileIsRefused(String at, @TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        StagedPack upload =
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("A", "A"));
        assumeTrue(Fifos.make(upload.directory().resolve(at)), "the operating system cannot make a FIFO here");

        CharterRefusal refused = refusal(() -> rig.register("pack-x", upload, Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refused.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.code()).isEqualTo("unreadable");
            assertThat(violation.pointer()).isEqualTo(at);
            assertThat(violation.detail())
                    .isEqualTo(at + ": is not a regular file or a directory; a pack holds only regular files and"
                            + " directories");
        });
        assertThat(rig.packs.resolve("pack-x")).as("nothing was stored").doesNotExist();
    }
}
