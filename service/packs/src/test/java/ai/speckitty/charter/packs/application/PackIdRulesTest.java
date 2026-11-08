package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ORG_PACK;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.OTHER_PACK;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusalOf;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The name a pack is stored under (FR-003, NFR-005): {@code ^[a-z0-9][a-z0-9-]{0,62}$}, unique after case folding
 * against every stored pack, and the entry {@code <writable root>/<packId>} is where the pack really lives. Each hostile
 * id is refused before anything is written, and the same upload under a sound id is the positive control.
 */
class PackIdRulesTest {

    /** One hostile id, what it is, and how the registration refuses it. */
    record Hostile(String label, String id, RefusalCode code) {
        @Override
        public String toString() {
            return label;
        }
    }

    static Stream<Hostile> hostileIds() {
        return Stream.of(
                new Hostile("upper case", "Pack-X", RefusalCode.VALIDATION_FAILED),
                new Hostile("a leading hyphen", "-pack", RefusalCode.VALIDATION_FAILED),
                new Hostile("64 characters", "a".repeat(64), RefusalCode.VALIDATION_FAILED),
                new Hostile("a parent segment", "..", RefusalCode.VALIDATION_FAILED),
                new Hostile("a path separator", "a/b", RefusalCode.VALIDATION_FAILED),
                new Hostile("a NUL", "pack\u0000x", RefusalCode.VALIDATION_FAILED),
                new Hostile("a homoglyph (Cyrillic a)", "pаck", RefusalCode.VALIDATION_FAILED),
                new Hostile("the empty id", "", RefusalCode.VALIDATION_FAILED),
                new Hostile("a trailing newline", "pack\n", RefusalCode.VALIDATION_FAILED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hostileIds")
    void aHostilePackIdIsRefusedAndWritesNothing(Hostile hostile, @TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        StagedPack upload = rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org"));

        assertThat(refusalOf(() -> rig.register(hostile.id(), upload, Preconditions.none())))
                .isEqualTo(hostile.code());
        try (Stream<Path> entries = Files.list(rig.packs)) {
            assertThat(entries.toList()).as("the pack domain is untouched").isEmpty();
        }
    }

    @Test
    void theLongestSoundIdAndASoundIdWithDigitsAndHyphensRegister(@TempDir Path host) {
        Rig rig = new Rig(host);

        assertThat(rig.register(
                                "a".repeat(63),
                                rig.stage(Packs.of(ORG_PACK, "long", "1.0.0", "org")),
                                Preconditions.none())
                        .outcome())
                .as("63 characters is the longest id")
                .isEqualTo(Outcome.CREATED);
        assertThat(rig.register(
                                "0-pack-9",
                                rig.stage(Packs.of(OTHER_PACK, "digits", "1.0.0", "org")),
                                Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void anIdThatFoldsToAStoredIdIsAConflict(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("Legacy", Packs.of(ORG_PACK, "legacy", "1.0.0", "org"));
        StagedPack upload = rig.stage(Packs.of(OTHER_PACK, "legacy-two", "1.0.0", "org"));

        assertThat(refusalOf(() -> rig.register("legacy", upload, Preconditions.none())))
                .isEqualTo(RefusalCode.REGISTRATION_CONFLICT);
        assertThat(rig.packs.resolve("legacy")).doesNotExist();

        assertThat(rig.register("legacy-two", upload, Preconditions.none()).outcome())
                .as("the control: an id that folds to nothing stored")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aStoredPackThatIsALinkIsNotWrittenThrough(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        Path target = host.resolve("elsewhere").resolve("real");
        Files.createDirectories(target);
        Files.writeString(target.resolve("pack.yaml"), Packs.descriptorText(ORG_PACK, "linked", "1.0.0", "org", ""));
        Files.createSymbolicLink(rig.packs.resolve("linked"), target);
        StagedPack upload = rig.stage(Packs.of(ORG_PACK, "linked", "2.0.0", "org"));

        assertThat(refusalOf(() -> rig.register("linked", upload, Preconditions.none())))
                .isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(Files.readString(target.resolve("pack.yaml"))).contains("pack_version: 1.0.0");

        assertThat(rig.register("sound", rig.stage(Packs.of(OTHER_PACK, "sound", "1.0.0", "org")), Preconditions.none())
                        .outcome())
                .as("the control: a pack that is no link")
                .isEqualTo(Outcome.CREATED);
        assertThat(List.of(rig.packs.resolve("linked"))).allMatch(Files::isSymbolicLink);
    }
}
