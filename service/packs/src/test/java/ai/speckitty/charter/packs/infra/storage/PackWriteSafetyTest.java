package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.PackFixtures.ALPHA_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** NFR-003: a write is atomic and stays inside the pack. (Create-or-replace by id: {@link TacticSaveContractTest}.) */
class PackWriteSafetyTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private static TacticDso tactic(String id, String name) {
        return new TacticDso(
                id,
                "1.0",
                name,
                List.of(new TacticDso.Step("Only step", Optional.empty(), List.of(), List.of())),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of());
    }

    private static FilesystemPackStore store(Path root) {
        return new FilesystemPackStore(root, new YamlSerializationStrategy());
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (Stream<Path> list = Files.list(directory)) {
            return list.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /** Positive control for the failure test: with the real rename, the same write succeeds and updates the file. */
    @Test
    void aWriteWithTheRealRenameUpdatesTheTargetAndLeavesNoTemp(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);

        store(host).write(PACK, TacticBinding.INSTANCE, tactic("alpha-tactic", "Written for real"));

        assertThat(Files.readString(pack.resolve(ALPHA_FILE))).contains("Written for real");
        assertThat(fileNames(pack)).noneMatch(name -> name.endsWith(".tmp"));
    }

    /** A real injected failure between the temp write and the rename: target untouched, no stray temp. */
    @Test
    void aFailureBetweenTheTempWriteAndTheRenameLeavesTheTargetUnchangedAndNoTemp(@TempDir Path host)
            throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        Path target = pack.resolve(ALPHA_FILE);
        byte[] before = Files.readAllBytes(target);
        List<String> namesBefore = fileNames(pack);
        boolean[] tempSeen = {false};
        FilesystemPackStore failing = new FilesystemPackStore(host, new YamlSerializationStrategy(), (temp, to) -> {
            tempSeen[0] = Files.exists(temp);
            throw new IOException("injected failure");
        });

        assertThatThrownBy(() -> failing.write(PACK, TacticBinding.INSTANCE, tactic("alpha-tactic", "Never written")))
                .isInstanceOf(CharterRefusal.class);

        assertThat(tempSeen[0])
                .as("the temp file existed when the rename was attempted")
                .isTrue();
        assertThat(Files.readAllBytes(target)).isEqualTo(before);
        assertThat(fileNames(pack)).isEqualTo(namesBefore);
    }

    /** A symlink that resolves outside the pack is refused before any write; the in-root control succeeds. */
    @Test
    void aTargetThatEscapesThePackIsRefusedWhileAnInRootTargetIsWritten(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        Path outside = Files.writeString(host.resolve("outside.tactic.yaml"), "id: escape-tactic\n");
        Files.createSymbolicLink(pack.resolve("escape.tactic.yaml"), outside);
        List<String> namesBefore = fileNames(pack);
        FilesystemPackStore store = store(host);

        assertThatThrownBy(() -> store.write(PACK, TacticBinding.INSTANCE, tactic("escape-tactic", "Escape")))
                .isInstanceOf(CharterRefusal.class);
        assertThat(Files.readString(outside)).isEqualTo("id: escape-tactic\n");
        assertThat(fileNames(pack)).isEqualTo(namesBefore);

        // Positive control, same fixture and store: an in-root target is accepted.
        store.write(PACK, TacticBinding.INSTANCE, tactic("alpha-tactic", "Written in root"));
        assertThat(Files.readString(pack.resolve(ALPHA_FILE))).contains("Written in root");
    }

    /** An identifier that climbs out of the storage root is refused; a plain one resolves (the control). */
    @Test
    void anIdentifierOutsideTheStorageRootIsRefusedWhileAPlainOneResolves(@TempDir Path host) throws IOException {
        PackFixtures.copyOf("pack-a", host);
        FilesystemPackStore store = store(host);

        assertThatThrownBy(() -> store.directoryOf(new PackIdentifier("../elsewhere")))
                .isInstanceOf(CharterRefusal.class);
        assertThatThrownBy(() -> store.directoryOf(new PackIdentifier("a/b"))).isInstanceOf(CharterRefusal.class);
        assertThat(store.directoryOf(PACK))
                .isEqualTo(host.toAbsolutePath().normalize().resolve("pack-a"));
    }
}
