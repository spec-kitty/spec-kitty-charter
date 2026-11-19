package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ORG_PACK;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * #597's acceptance (D1): a staged tree that holds a symbolic link is refused {@code validation_failed}, with one
 * {@code pack_entry_linked} violation at the link's path, and nothing is stored. #597's probe registered each of these
 * {@code CREATED} with no diagnostic.
 */
@DisplayName("a staged pack holding a link is refused, whatever the link leads to (#597, D1)")
class PackRegistrationLinkTest {

    /** Where the link stands in the staged pack, and what it leads to, outside the pack. */
    static Stream<Arguments> links() {
        return Stream.of(
                Arguments.of("a valid directive", "directives/B.directive.yaml", "B.directive.yaml"),
                Arguments.of("a directory", "directives/more", "more"),
                Arguments.of("an unmodelled text file", "NOTES.txt", "NOTES.txt"));
    }

    @ParameterizedTest(name = "a link to {0}")
    @MethodSource("links")
    @DisplayName("the registration answers validation_failed with pack_entry_linked at the link, and stores nothing")
    void aLinkIsRefused(String what, String at, String target, @TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        Path outside = Files.createDirectories(host.resolve("outside"));
        Files.writeString(
                outside.resolve("B.directive.yaml"),
                Packs.directiveText("B", "Valid and outside"),
                StandardCharsets.UTF_8);
        Files.createDirectories(outside.resolve("more"));
        Files.writeString(
                outside.resolve("more/C.directive.yaml"),
                Packs.directiveText("C", "In a directory"),
                StandardCharsets.UTF_8);
        Files.writeString(outside.resolve("NOTES.txt"), "notes outside the pack\n", StandardCharsets.UTF_8);
        StagedPack upload =
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("A", "A"));
        link(upload.directory().resolve(at), outside.resolve(target));

        CharterRefusal refused = refusal(() -> rig.register("pack-x", upload, Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refused.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.code()).isEqualTo("pack_entry_linked");
            assertThat(violation.pointer()).isEqualTo(at);
            assertThat(violation.detail()).isEqualTo(at + ": is a symbolic link; a pack holds no links");
        });
        assertThat(rig.packs.resolve("pack-x")).as("nothing was stored").doesNotExist();
    }

    @Test
    @DisplayName("the control: the same staged pack without the link registers")
    void theSamePackWithoutTheLinkRegisters(@TempDir Path host) {
        Rig rig = new Rig(host);
        StagedPack upload =
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("A", "A"));

        assertThat(rig.register("pack-x", upload, Preconditions.none()).outcome())
                .isEqualTo(Outcome.CREATED);
    }

    /** Makes a link, or skips the test where the file system cannot; fails it instead when CI requires links. */
    private static void link(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | FileSystemException noLinks) {
            String why = "the file system cannot create symbolic links: " + noLinks;
            if (Boolean.getBoolean("charter.requireSymlinks")) {
                fail(why + " (charter.requireSymlinks is true, so link support is mandatory)", noLinks);
            }
            abort(why);
        }
    }
}
