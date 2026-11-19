package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.PackRegistrationService.Mode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The removal of a pack whole (ADR-0081 D5 to D9): what removes it, what refuses it, and what a dry run answers. */
class PackUninstallServiceTest {

    private static final String QA_ID = "01M4P01D5TWB5FKZGNNQG3943R";
    private static final String OTHER_ID = "01M4P01D5TWB5FKZGNNQG3943S";

    private static Packs qa() {
        return Packs.of(QA_ID, "qa", "1.0.0", "org").directive("D1", "D1");
    }

    private static Packs dependingOnQa(String extra) {
        return Packs.of(OTHER_ID, "other", "1.0.0", "org", extra).directive("R", "R");
    }

    private static PackUninstallService uninstalls(Rig rig) {
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        return new PackUninstallService(
                rig.writeStore,
                rig.commits,
                new WholePackStore(strategy),
                new StagedPackViewFactory(rig.locations, strategy),
                new ClosurePackDependents(
                        new InboundReferences(rig.repository, rig.repository, rig.repository), rig.activationPort),
                rig.impact);
    }

    private static PackUninstallService.Outcome uninstall(Rig rig, String pack, String ifMatch, Mode mode) {
        return uninstalls(rig).uninstall(new PackIdentifier(pack), ifMatch, mode, RevisionTrail.NONE);
    }

    private static CharterRefusal refusalOf(Runnable action) {
        return catchThrowableOfType(CharterRefusal.class, action::run);
    }

    @Test
    void aPackNothingDependsOnIsRemovedWholeAndItsHistoryIsKept(@TempDir Path host) {
        Rig rig = new Rig(host);
        PackRevision revision =
                rig.register("qa", rig.stage(qa()), Preconditions.none()).revision();

        PackUninstallService.Outcome outcome = uninstall(rig, "qa", revision.token(), Mode.COMMIT);

        assertThat(outcome.dryRun()).isFalse();
        assertThat(outcome.refused()).isEmpty();
        assertThat(outcome.version()).isEqualTo("1.0.0");
        assertThat(rig.packs.resolve("qa")).doesNotExist();
        assertThat(rig.asidesBeside(rig.packs.resolve("qa")))
                .as("no aside copy is left that a listing could restore")
                .isEmpty();
        assertThat(rig.commits.versionHistory("qa"))
                .extracting(PackVersionHistory.Entry::packVersion)
                .containsExactly("1.0.0");
        assertThat(refusalOf(() -> uninstall(rig, "qa", "*", Mode.COMMIT)).code())
                .as("a repeated removal finds nothing")
                .isEqualTo(RefusalCode.PACK_NOT_FOUND);
    }

    @Test
    void aDryRunJudgesTheRemovalAndRemovesNothing(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("qa"));

        PackUninstallService.Outcome outcome = uninstall(rig, "qa", "*", Mode.DRY_RUN);

        assertThat(outcome.dryRun()).isTrue();
        assertThat(outcome.refused()).isEmpty();
        assertThat(rig.filesOf(rig.packs.resolve("qa"))).as("nothing changed").isEqualTo(before);
    }

    @Test
    void aRemovalNeedsTheRevisionAndAStaleOneRefusesIt(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());

        PreconditionException missing =
                catchThrowableOfType(PreconditionException.class, () -> uninstall(rig, "qa", null, Mode.COMMIT));
        PreconditionException stale = catchThrowableOfType(
                PreconditionException.class,
                () -> uninstall(
                        rig,
                        "qa",
                        "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\"",
                        Mode.DRY_RUN));

        assertThat(missing.kind()).isEqualTo(PreconditionException.Kind.MISSING);
        assertThat(stale.kind()).as("a dry run judges the revision too").isEqualTo(PreconditionException.Kind.STALE);
        assertThat(rig.packs.resolve("qa")).isDirectory();
    }

    @Test
    void everyDependentIsNamedInOrderAndNothingIsRemoved(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        rig.install(
                "other",
                dependingOnQa(
                        "depends_on:\n  - pack_id: " + QA_ID + "\n    range: '>=1.0'\nparent_pack: " + QA_ID + "\n"));
        rig.activations.add(Optional.of(List.of("qa")));
        rig.activations.add(Optional.empty());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("qa"));

        CharterRefusal refused = refusalOf(() -> uninstall(rig, "qa", "*", Mode.COMMIT));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_HAS_DEPENDENTS);
        assertThat(refused.violations())
                .extracting(RefusalViolation::code)
                .as("the packs, then the activation that names qa; the one that lists no packs is no dependent")
                .containsExactly("depends_on", "parent_pack", "activation");
        assertThat(refused.violations().get(2).detail()).contains("activation-0");
        assertThat(refused.packs()).extracting(pack -> pack.packId()).containsExactly("other");
        assertThat(rig.filesOf(rig.packs.resolve("qa"))).as("nothing changed").isEqualTo(before);

        PackUninstallService.Outcome dryRun = uninstall(rig, "qa", "*", Mode.DRY_RUN);
        assertThat(dryRun.refused())
                .as("the dry run answers the same refusal as a body")
                .hasValueSatisfying(same -> {
                    assertThat(same.code()).isEqualTo(RefusalCode.PACK_HAS_DEPENDENTS);
                    assertThat(same.violations()).isEqualTo(refused.violations());
                });
    }

    @Test
    void aConfiguredPackThatCannotBeReadFailsTheRemovalClosed(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        Files.createDirectories(rig.packs.resolve("broken"));
        Files.writeString(rig.packs.resolve("broken").resolve("pack.yaml"), "pack_id: [\n");

        CharterRefusal refused = refusalOf(() -> uninstall(rig, "qa", "*", Mode.DRY_RUN));

        assertThat(refused.code())
                .as("a pack that cannot be read might depend on qa; even a dry run keeps the status")
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(refused.getMessage()).contains("broken");
        assertThat(rig.packs.resolve("qa")).isDirectory();
    }

    @Test
    void aLinkedPackIsNotRemoved(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        Path target = host.resolve("elsewhere").resolve("qa");
        qa().files().forEach((file, text) -> {
            try {
                Files.createDirectories(target.resolve(file).getParent());
                Files.writeString(target.resolve(file), text);
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });
        Files.createSymbolicLink(rig.packs.resolve("qa"), target);

        CharterRefusal refused = refusalOf(() -> uninstall(rig, "qa", "*", Mode.COMMIT));

        assertThat(refused.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT);
        assertThat(target.resolve("pack.yaml"))
                .as("nothing outside the writable root is deleted")
                .exists();
    }

    @Test
    void aStaleAsideCopyIsNotRestoredOverARemovedPack(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        Path stale = rig.packs.resolve("qa.pack-old-crashed");
        Files.createDirectories(stale);
        Files.writeString(stale.resolve("pack.yaml"), Packs.descriptorText(QA_ID, "qa", "0.9.0", "org", ""));

        uninstall(rig, "qa", "*", Mode.COMMIT);

        assertThat(rig.repository.configured())
                .as("a listing after the removal finds no qa to restore from the stale copy (#281)")
                .noneMatch(pack -> pack.id().value().equals("qa"));
        assertThat(rig.packs.resolve("qa")).doesNotExist();
        assertThat(stale).as("the stale copy was hidden before the removal").doesNotExist();
    }
}
