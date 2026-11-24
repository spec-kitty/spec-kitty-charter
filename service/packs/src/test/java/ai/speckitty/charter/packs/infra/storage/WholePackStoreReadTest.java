package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.WholePackStoreTest.FALLBACK_REASON;
import static ai.speckitty.charter.packs.infra.storage.WholePackStoreTest.exclusionReason;
import static ai.speckitty.charter.packs.infra.storage.WholePackStoreTest.filesUnder;
import static ai.speckitty.charter.packs.infra.storage.WholePackStoreTest.richPack;
import static ai.speckitty.charter.packs.infra.storage.WholePackStoreTest.write;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the store still does for a registration once the registration has judged the one read (#554, WP12, T049):
 * {@code loadStaged} assembles the aggregate, its facts and its digests from the readout alone, reading and judging
 * nothing, and {@code removeNotHeld} strips the files the readout lists as not held from the staged tree. The
 * registration's refusals are the registration's ({@code PackRegistrationServiceTest}, {@code OneReadRegistrationTest})
 * and the rules' ({@code StorageRulesetsTest}).
 */
@DisplayName("WholePackStore over the one read a registration judged (#554, WP12)")
class WholePackStoreReadTest {

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    private PackReadout read(Path pack) {
        return store.reader().read(new PackRoot(String.valueOf(pack.getFileName()), Optional.empty(), pack.toString()));
    }

    @Test
    @DisplayName("loadStaged assembles what the read holds, with no read of its own: a file written after the read is"
            + " not seen")
    void loadStagedAssemblesTheReadAlone(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        PackReadout read = read(pack);
        long walks = PackFileSet.walks();
        long runs = StoreRead.runs();
        write(pack, "tactics/late.tactic.yaml", "id: late\nschema_version: '1.0'\nname: Late\nsteps:\n- title: s\n");

        WholePackWriteStore.Contents contents = store.loadStaged(read);

        assertThat(PackFileSet.walks()).as("no walk").isEqualTo(walks);
        assertThat(StoreRead.runs()).as("no read").isEqualTo(runs);
        assertThat(contents.facts().held()).doesNotContain("tactic:late");
    }

    @Test
    @DisplayName("a read whose store half refused the pack holds no assembly input: loadStaged is asked only after"
            + " the registration judged it")
    void aRefusedReadIsNeverAssembled(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        Files.delete(pack.resolve("pack.yaml"));
        PackReadout read = read(pack);

        assertThat(read.contents().assembly()).isInstanceOf(PackContents.Assembly.Refused.class);
        assertThatThrownBy(() -> store.loadStaged(read))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("judge it first");
    }

    @Test
    @DisplayName(
            "removeNotHeld leaves alone a listed path that leaves the staged tree, and one that is no regular file")
    void removeNotHeldTouchesTheStagedTreeOnly(@TempDir Path host) throws IOException {
        Path pack = richPack(host.resolve("pack"));
        Path outside = host.resolve("outside.txt");
        Files.writeString(outside, "keep\n", UTF_8);
        Files.createDirectories(pack.resolve("a-directory"));
        List<String> before = filesUnder(pack);

        store.removeNotHeld(
                new StagedPack(pack),
                List.of(
                        NotHeldFile.unmodelled("../outside.txt"),
                        NotHeldFile.unmodelled("a-directory"),
                        NotHeldFile.unmodelled("absent.txt")));

        assertThat(outside).exists();
        assertThat(pack.resolve("a-directory")).isDirectory();
        assertThat(filesUnder(pack)).isEqualTo(before);
    }

    @Test
    @DisplayName(
            "a registration refuses neither an excluded file nor a file nothing models: after the read's own warnings"
                    + " it reports each as a file_not_held warning, in one path-sorted list, with its reason (Q1)")
    void loadStagedReportsTheFilesNotHeldInOnePathSortedList(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        write(pack, "docs/notes.md", "# Notes\n");
        write(pack, "missions/x/templates/README.md", "# Templates\n");
        write(pack, "data.json", "{}\n");
        write(pack, "notes.txt", "stray");

        PackRegistrationService.ReadUpload read = StagedReads.read(store, pack);

        assertThat(read.diagnostics())
                .extracting(Diagnostic::code, Diagnostic::detail)
                .containsExactly(
                        tuple(
                                Diagnostic.Code.FILE_NOT_HELD,
                                "data.json: not held (no pack file kind models this file)"),
                        tuple(Diagnostic.Code.FILE_NOT_HELD, "docs/notes.md: not held (" + FALLBACK_REASON + ")"),
                        tuple(
                                Diagnostic.Code.FILE_NOT_HELD,
                                "missions/x/templates/README.md: not held ("
                                        + exclusionReason("*/missions/*/templates/**/README.md") + ")"),
                        tuple(
                                Diagnostic.Code.FILE_NOT_HELD,
                                "notes.txt: not held (no pack file kind models this file)"));
        assertThat(read.read().notHeld())
                .as("the strip's list is the one the warnings are derived from")
                .extracting(NotHeldFile::path)
                .containsExactly("data.json", "docs/notes.md", "missions/x/templates/README.md", "notes.txt");
    }

    @Test
    @DisplayName("the staged tree stripped of a file nothing models is the tree of the pack without it (Q1)")
    void theStripOfAFileNothingModelsLeavesThePackWithoutIt(@TempDir Path host) throws IOException {
        Path plain = richPack(host.resolve("plain"));
        Path noted = richPack(host.resolve("noted"));
        write(noted, "sub/notes.txt", "stray");
        StagedPack staged = new StagedPack(noted);

        store.removeNotHeld(staged, StagedReads.read(store, noted).read().notHeld());

        assertThat(filesUnder(noted)).isEqualTo(filesUnder(plain));
        assertThat(noted.resolve("sub")).as("the directory the file left empty").doesNotExist();
    }

    @Test
    @DisplayName("removeNotHeld deletes the reported files and the directories they leave empty from the staged tree,"
            + " and nothing else")
    void removeNotHeldStripsOnlyTheReportedFilesFromTheStagedTree(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        write(pack, "docs/notes.md", "# Notes\n");
        write(pack, "missions/x/README.md", "# x\n");
        write(pack, "missions/x/templates/spec.md", "# Spec\n");
        StagedPack staged = new StagedPack(pack);
        List<String> before = filesUnder(pack);
        store.removeNotHeld(staged, StagedReads.read(store, pack).read().notHeld());

        List<String> expected = new java.util.ArrayList<>(before);
        expected.removeAll(List.of("docs/notes.md", "missions/x/README.md"));
        assertThat(filesUnder(pack)).isEqualTo(expected);
        assertThat(pack.resolve("docs")).as("the directory the file left empty").doesNotExist();
        assertThat(pack.resolve("missions/x"))
                .as("a directory that still holds a file")
                .isDirectory();
    }

    @Test
    @DisplayName(
            "loadStaged digests each artifact as it would be written, so a changed artifact is told from a kept one")
    void loadStagedDigestsEachArtifactAsItWouldBeWritten(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        Urn alpha = new Urn(NodeKind.TACTIC, "alpha-tactic");
        Urn gamma = new Urn(NodeKind.TACTIC, "gamma-tactic");
        WholePackWriteStore.Contents before = StagedReads.contents(store, pack);
        write(
                pack,
                PackFixtures.ALPHA_FILE,
                Files.readString(pack.resolve(PackFixtures.ALPHA_FILE), UTF_8)
                        .replace("Alpha tactic", "Alpha, changed"));

        WholePackWriteStore.Contents after = StagedReads.contents(store, pack);

        assertThat(before.digests()).containsKeys(alpha, gamma);
        assertThat(after.digests().get(alpha)).isNotEqualTo(before.digests().get(alpha));
        assertThat(after.digests().get(gamma)).isEqualTo(before.digests().get(gamma));
    }

    @Test
    @DisplayName("loadStaged hands the compose what the pack holds and the typed edges it writes")
    void loadStagedHandsOverTheFactsOfThePack(@TempDir Path host) throws IOException {
        Path pack = richPack(host);

        WholePackWriteStore.Contents contents = StagedReads.contents(store, pack);

        assertThat(contents.facts().held())
                .contains("tactic:alpha-tactic", "tactic:gamma-tactic", "directive:DIRECTIVE_043");
        assertThat(contents.pack().descriptor().packId()).isEqualTo("01M5DEH8R0VNDTR1PF1XTVRE0A");
    }
}
