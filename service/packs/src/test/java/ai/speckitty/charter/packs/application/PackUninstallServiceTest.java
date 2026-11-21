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
                removals(rig, strategy, rig.activationPort));
    }

    /** The impact check that judges a removal: its dependents over the inbound scan, then the closures left (#556). */
    private static ClosureImpactCheck removals(
            Rig rig, YamlSerializationStrategy strategy, ActivationImpact activations) {
        return new ClosureImpactCheck(
                new StagedPackViewFactory(rig.locations, strategy),
                activations,
                new OwnClosureWalk(),
                new InboundReferences(rig.repository, rig.repository, rig.repository));
    }

    private static PackUninstallService.Outcome uninstall(Rig rig, String pack, String ifMatch, Mode mode) {
        return uninstalls(rig).uninstall(new PackIdentifier(pack), ifMatch, mode, RevisionTrail.NONE);
    }

    /**
     * Removes a pack whole with {@code If-Match: *}, as a test that needs it gone does.
     *
     * @param rig the rig
     * @param pack the pack's configured name
     */
    static void uninstall(Rig rig, String pack) {
        uninstall(rig, pack, "*", Mode.COMMIT);
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
        assertThat(outcome.version()).contains("1.0.0");
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
    void aRemovalJudgesInOneSessionTheImpactCheckSharesNoSecond(@TempDir Path host) {
        // #556 (squad A3), #517 NFR-001: the uninstall and the impact check judge in the one session the uninstall
        // opened, so each stored pack's descriptor is read once per delete.
        Rig rig = new Rig(host);
        PackRevision revision =
                rig.register("qa", rig.stage(qa()), Preconditions.none()).revision();
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        java.util.concurrent.atomic.AtomicInteger sessions = new java.util.concurrent.atomic.AtomicInteger();
        StagedPackViewFactory real = new StagedPackViewFactory(rig.locations, strategy);
        StagedPackViews counted = () -> {
            sessions.incrementAndGet();
            return real.session();
        };
        PackUninstallService uninstalls = new PackUninstallService(
                rig.writeStore,
                rig.commits,
                new WholePackStore(strategy),
                counted,
                new ClosureImpactCheck(
                        counted,
                        rig.activationPort,
                        new OwnClosureWalk(),
                        new InboundReferences(rig.repository, rig.repository, rig.repository)));

        uninstalls.uninstall(new PackIdentifier("qa"), revision.token(), Mode.DRY_RUN, RevisionTrail.NONE);

        assertThat(sessions)
                .as("one session per delete, shared with the impact check")
                .hasValue(1);
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
        assertThat(refused.packs()).extracting(pack -> pack.packId()).containsExactly(new PackIdentifier("other"));
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
    void aRemovalNamesEveryFaultOfTheClosuresItLeavesAndRemovesNothing(@TempDir Path host) throws IOException {
        // #556, US3: with no dependent, only an activation that lists no packs remains, and every such activation
        // composes the same roots, so the removal leaves one closure (ADR-0081 D6); its every fault is named.
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        rig.install(
                "acme",
                Packs.of(PackRegistrationServiceTest.ulid('Q'), "acme", "1.0.0", "org")
                        .directive("A", "A")
                        .directive("B", "B")
                        .directive("C", "C")
                        .directive("D", "D")
                        .file(
                                "relations.graph.yaml",
                                ClosureImpactCheckTest.graph(
                                        ClosureImpactCheckTest.edge("A", "enhances", "B"),
                                        ClosureImpactCheckTest.edge("B", "enhances", "A"),
                                        ClosureImpactCheckTest.edge("C", "enhances", "D"),
                                        ClosureImpactCheckTest.edge("D", "enhances", "C"))));
        rig.activations.add(Optional.empty());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("qa"));

        CharterRefusal refused = refusalOf(() -> uninstall(rig, "qa", "*", Mode.COMMIT));

        assertThat(refused.code()).isEqualTo(RefusalCode.ENHANCES_CYCLE);
        assertThat(refused.violations())
                .extracting(RefusalViolation::code)
                .containsExactly("enhances_cycle", "enhances_cycle");
        assertThat(refused.getMessage()).endsWith("(2 fault(s); every one is in violations)");
        assertThat(rig.filesOf(rig.packs.resolve("qa"))).as("nothing changed").isEqualTo(before);

        PackUninstallService.Outcome dryRun = uninstall(rig, "qa", "*", Mode.DRY_RUN);
        assertThat(dryRun.refused())
                .as("the dry run answers the same refusal as a body")
                .hasValueSatisfying(same -> {
                    assertThat(same.code()).isEqualTo(RefusalCode.ENHANCES_CYCLE);
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
    void aPackWhoseOwnPackYamlCannotBeReadIsStillRemoved(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        Files.writeString(rig.packs.resolve("qa").resolve("pack.yaml"), "pack_id: [\n");
        Map<String, String> before = rig.filesOf(rig.packs.resolve("qa"));

        PackUninstallService.Outcome dryRun = uninstall(rig, "qa", "*", Mode.DRY_RUN);

        assertThat(dryRun.refused())
                .as("nothing depends on qa, judged by its configured name")
                .isEmpty();
        assertThat(dryRun.version())
                .as("an unreadable pack.yaml has no version")
                .isEmpty();
        assertThat(rig.filesOf(rig.packs.resolve("qa")))
                .as("a dry run changes nothing")
                .isEqualTo(before);

        PackUninstallService.Outcome removed = uninstall(rig, "qa", "*", Mode.COMMIT);

        assertThat(removed.refused()).isEmpty();
        assertThat(removed.version()).isEmpty();
        assertThat(rig.packs.resolve("qa")).doesNotExist();
    }

    @Test
    void anUnreadablePacksDependentsAreJudgedByItsConfiguredName(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        Files.writeString(rig.packs.resolve("qa").resolve("pack.yaml"), "pack_id: [\n");
        rig.activations.add(Optional.of(List.of("qa")));

        CharterRefusal refused = refusalOf(() -> uninstall(rig, "qa", "*", Mode.COMMIT));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_HAS_DEPENDENTS);
        assertThat(refused.violations())
                .extracting(RefusalViolation::code)
                .as("an activation names qa by its configured name")
                .containsExactly("activation");
        assertThat(rig.packs.resolve("qa")).isDirectory();
        assertThat(uninstall(rig, "qa", "*", Mode.DRY_RUN).refused())
                .as("the dry run answers the same refusal as a body")
                .hasValueSatisfying(same -> assertThat(same.violations()).isEqualTo(refused.violations()));
    }

    @Test
    void anUnreadablePackIsNotRemovedWhileAnotherPackCannotBeJudged(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        Files.writeString(rig.packs.resolve("qa").resolve("pack.yaml"), "pack_id: [\n");
        Files.createDirectories(rig.packs.resolve("broken"));
        Files.writeString(rig.packs.resolve("broken").resolve("pack.yaml"), "pack_id: [\n");

        CharterRefusal refused = refusalOf(() -> uninstall(rig, "qa", "*", Mode.DRY_RUN));

        assertThat(refused.code())
                .as("another pack the judgement needs fails it closed")
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(refused.getMessage()).contains("broken");
        assertThat(rig.packs.resolve("qa")).isDirectory();
    }

    @Test
    void aLinkedPackIsNotRemoved(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        // A link into a hidden directory of the pack domain is listed and served as the pack (LinkedPackCommitTest).
        Path target = rig.packs.resolve(".vendor").resolve("real-qa");
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

    @Test
    void anActivationThatCannotBeReadFailsTheRemovalClosed(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        ActivationImpact unreadable = new ActivationImpact() {
            @Override
            public List<ActivationRoots> activationRoots() {
                return List.of();
            }

            @Override
            public List<String> unreadableActivations() {
                return List.of("lost");
            }
        };
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        PackUninstallService service = new PackUninstallService(
                rig.writeStore,
                rig.commits,
                new WholePackStore(strategy),
                new StagedPackViewFactory(rig.locations, strategy),
                removals(rig, strategy, unreadable));

        IllegalStateException failed = catchThrowableOfType(
                IllegalStateException.class,
                () -> service.uninstall(new PackIdentifier("qa"), "*", Mode.DRY_RUN, RevisionTrail.NONE));

        assertThat(failed)
                .as("an activation that cannot be read might name qa (review R3-1)")
                .isNotNull();
        assertThat(failed.getMessage()).contains("lost");
        assertThat(rig.packs.resolve("qa")).isDirectory();
    }
}
