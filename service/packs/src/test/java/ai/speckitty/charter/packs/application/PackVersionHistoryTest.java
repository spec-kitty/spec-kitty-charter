package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusalOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.application.PackVersionHistory.Entry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The version history of IC-16 (FR-026, B1): a pack name's registered {@code (pack_version, revision)} pairs. The judge
 * is a pure function of the history; the service tests run over the real filesystem adapters.
 */
class PackVersionHistoryTest {

    private static final String PACK = PackRegistrationServiceTest.ORG_PACK;
    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");

    private static PackRevision revision(String content) {
        return PackRevision.compute(List.of(content));
    }

    private static Entry entry(String version, String content) {
        return new Entry(version, revision(content).token(), T0);
    }

    // ---- the judge -------------------------------------------------------------------------------------------------

    @Test
    void aVersionSeenWithAnotherRevisionIsRefusedAndTheSameRevisionIsNot() {
        List<Entry> history = List.of(entry("1.0.0", "a"), entry("2.0.0", "b"));

        assertThat(refusalOf(() -> PackVersionHistory.requireNotReused("pack-x", history, "1.0.0", revision("c"))))
                .as("1.0.0 was registered with the content a, and this is c")
                .isEqualTo(RefusalCode.PACK_VERSION_CONFLICT);
        assertThatCode(() -> PackVersionHistory.requireNotReused("pack-x", history, "1.0.0", revision("a")))
                .as("the control: the content that was registered then")
                .doesNotThrowAnyException();
        assertThatCode(() -> PackVersionHistory.requireNotReused("pack-x", history, "3.0.0", revision("c")))
                .as("the control: a version nobody registered")
                .doesNotThrowAnyException();
    }

    @Test
    void theRefusalNamesThePackAndTheVersion() {
        List<Entry> history = List.of(entry("1.0.0", "a"));

        assertThatThrownBy(() -> PackVersionHistory.requireNotReused("pack-x", history, "1.0.0", revision("c")))
                .hasMessageContaining("pack-x")
                .hasMessageContaining("1.0.0");
    }

    @Test
    void aVersionAndRevisionAlreadyRecordedAreNotRecordedAgain() {
        List<Entry> history = List.of(entry("1.0.0", "a"), entry("2.0.0", "b"));

        assertThat(PackVersionHistory.appended(
                        history, new Entry("1.0.0", revision("a").token(), T0.plusSeconds(60))))
                .as("a downgrade to the content registered before leaves the record as it was")
                .isEqualTo(history);
        assertThat(PackVersionHistory.appended(history, entry("3.0.0", "c")))
                .as("a new version goes last")
                .containsExactly(entry("1.0.0", "a"), entry("2.0.0", "b"), entry("3.0.0", "c"));
    }

    @Test
    void theHistoryIsNotChangedByTheCall() {
        List<Entry> history = List.of(entry("1.0.0", "a"));

        PackVersionHistory.appended(history, entry("2.0.0", "b"));

        assertThat(history).containsExactly(entry("1.0.0", "a"));
    }

    // ---- the service ----------------------------------------------------------------------------------------------

    private static Packs pack(String version, String title) {
        return Packs.of(PACK, "pack-x", version, "org").directive("DIRECTIVE_001", title);
    }

    private static Path historyFile(Rig rig) {
        return rig.packs.resolve(".history").resolve("pack-x.json");
    }

    @Test
    void aReusedVersionWithOtherContentIsRefusedAfterADowngradeEvenThoughItIsNotTheStoredOne(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(pack("1.0.0", "First")), Preconditions.none());
        rig.register("pack-x", rig.stage(pack("2.0.0", "Second")), Preconditions.none());
        java.util.Map<String, String> before = rig.filesOf(rig.packs.resolve("pack-x"));

        assertThat(refusalOf(() -> rig.register("pack-x", rig.stage(pack("1.0.0", "Other")), Preconditions.none())))
                .as("1.0.0 was registered with other content, and the stored version is 2.0.0")
                .isEqualTo(RefusalCode.PACK_VERSION_CONFLICT);
        assertThat(rig.filesOf(rig.packs.resolve("pack-x")))
                .as("nothing changed")
                .isEqualTo(before);

        assertThat(rig.register("pack-x", rig.stage(pack("1.0.0", "First")), Preconditions.none())
                        .outcome())
                .as("the control: the content 1.0.0 had is accepted, a downgrade")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void theHistoryFileIsWrittenWithThePackAndAppendedToByEachReplace(@TempDir Path host) {
        Rig rig = new Rig(host);
        PackRegistrationService.RegistrationOutcome first =
                rig.register("pack-x", rig.stage(pack("1.0.0", "First")), Preconditions.none());
        assertThat(rig.commits.versionHistory("pack-x"))
                .extracting(Entry::packVersion, Entry::revision)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "1.0.0", first.revision().token()));
        assertThat(historyFile(rig)).isRegularFile();

        PackRegistrationService.RegistrationOutcome second =
                rig.register("pack-x", rig.stage(pack("2.0.0", "Second")), Preconditions.none());

        assertThat(rig.commits.versionHistory("pack-x"))
                .extracting(Entry::packVersion, Entry::revision)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "1.0.0", first.revision().token()),
                        org.assertj.core.groups.Tuple.tuple(
                                "2.0.0", second.revision().token()));
        assertThat(rig.commits.versionHistory("pack-x").getLast().registeredAt())
                .isAfterOrEqualTo(Instant.now().minusSeconds(60))
                .isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void aResendOfTheSameContentAndARefusedRegistrationLeaveTheHistoryAsItWas(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(pack("1.0.0", "First")), Preconditions.none());
        assertThat(historyFile(rig)).isRegularFile();
        String written = Files.readString(historyFile(rig));

        rig.register("pack-x", rig.stage(pack("1.0.0", "First")), Preconditions.none());
        refusalOf(() -> rig.register("pack-x", rig.stage(pack("1.0.0", "Other")), Preconditions.none()));

        assertThat(Files.readString(historyFile(rig))).isEqualTo(written);
    }

    @Test
    void theHistoryIsKeptWhenThePackIsDeletedAndTheNameStaysTaken(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(pack("1.0.0", "First")), Preconditions.none());
        deleteTree(rig.packs.resolve("pack-x"));

        assertThat(historyFile(rig)).as("a delete never touches .history").isRegularFile();
        assertThat(rig.repository.configured())
                .as(".history is no pack")
                .noneMatch(configured -> configured.id().value().contains("history"));
        assertThat(refusalOf(() -> rig.register("pack-x", rig.stage(pack("1.0.0", "Other")), Preconditions.none())))
                .as("the deleted pack's version is still taken by its content")
                .isEqualTo(RefusalCode.PACK_VERSION_CONFLICT);

        assertThat(rig.register("pack-x", rig.stage(pack("1.0.0", "First")), Preconditions.none())
                        .outcome())
                .as("the control: the same content creates the pack again")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aHistoryThatCannotBeReadRefusesTheRegistrationAndChangesNothing(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(pack("1.0.0", "First")), Preconditions.none());
        assertThat(historyFile(rig)).isRegularFile();
        Files.writeString(historyFile(rig), "{ not json");
        java.util.Map<String, String> before = rig.filesOf(rig.packs.resolve("pack-x"));

        assertThat(refusalOf(() -> rig.register("pack-x", rig.stage(pack("2.0.0", "Second")), Preconditions.none())))
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(rig.filesOf(rig.packs.resolve("pack-x"))).isEqualTo(before);
    }

    private static void deleteTree(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
