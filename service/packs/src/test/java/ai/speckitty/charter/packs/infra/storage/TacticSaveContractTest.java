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

/**
 * The upsert contract of {@code save}, one proof per arm because the arms fail differently: the update arm must
 * replace the file that already holds the id (and nothing else), the create arm must add exactly one new file,
 * atomically and inside the pack. Each arm carries its non-vacuity control.
 */
class TacticSaveContractTest {

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

    private static List<String> fileNames(Path directory) throws IOException {
        try (Stream<Path> list = Files.list(directory)) {
            return list.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /** Update arm: a known id replaces its own file; the file set is unchanged and the mutation is visible on reload. */
    @Test
    void aKnownIdReplacesItsFileAndCreatesNothing(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        List<String> namesBefore = fileNames(pack);
        FilesystemPackStore store = new FilesystemPackStore(host, new YamlSerializationStrategy());

        store.write(PACK, TacticBinding.INSTANCE, tactic("beta-tactic", "Replaced beta"));

        assertThat(fileNames(pack)).isEqualTo(namesBefore);
        assertThat(store.read(PACK).dsos(TacticBinding.INSTANCE))
                .anyMatch(t -> t.id().equals("beta-tactic") && t.name().equals("Replaced beta"))
                .anyMatch(t -> t.id().equals("alpha-tactic") && t.name().equals("Alpha tactic"));
    }

    /**
     * Create arm: an unknown id adds exactly one {@code <id>.tactic.yaml} (visible on reload) through the temp then
     * rename path. Controls: a failing rename leaves no new file and no temp; a traversing id and a planted link at
     * the new file's name are refused and write nothing outside the pack.
     */
    @Test
    void anUnknownIdCreatesOneContainedFileAtomically(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        List<String> namesBefore = fileNames(pack);
        TacticDso fresh = tactic("brand-new-tactic", "Brand new");

        // Atomicity: the create goes through the rename step; when it fails, nothing is left behind.
        boolean[] tempSeen = {false};
        FilesystemPackStore failing = new FilesystemPackStore(host, new YamlSerializationStrategy(), (temp, to) -> {
            tempSeen[0] = Files.exists(temp);
            throw new IOException("injected failure");
        });
        assertThatThrownBy(() -> failing.write(PACK, TacticBinding.INSTANCE, fresh)).isInstanceOf(CharterRefusal.class);
        assertThat(tempSeen[0]).isTrue();
        assertThat(fileNames(pack)).isEqualTo(namesBefore);

        // Containment: a climbing id and a dangling link planted at the new file's name are refused.
        FilesystemPackStore store = new FilesystemPackStore(host, new YamlSerializationStrategy());
        assertThatThrownBy(() -> store.write(PACK, TacticBinding.INSTANCE, tactic("../escaped", "Escaped")))
                .isInstanceOf(CharterRefusal.class);
        Path outside = host.resolve("outside.yaml");
        Files.createSymbolicLink(pack.resolve("brand-new-tactic.tactic.yaml"), outside);
        assertThatThrownBy(() -> store.write(PACK, TacticBinding.INSTANCE, fresh)).isInstanceOf(CharterRefusal.class);
        assertThat(outside).doesNotExist();
        assertThat(host.resolve("escaped.tactic.yaml")).doesNotExist();
        Files.delete(pack.resolve("brand-new-tactic.tactic.yaml"));

        // The positive arm, same fixture: the unknown id is created, and only that file is added.
        store.write(PACK, TacticBinding.INSTANCE, fresh);

        assertThat(fileNames(pack))
                .containsExactlyInAnyOrder(
                        Stream.concat(namesBefore.stream(), Stream.of("brand-new-tactic.tactic.yaml"))
                                .toArray(String[]::new));
        assertThat(store.read(PACK).dsos(TacticBinding.INSTANCE))
                .anyMatch(t -> t.id().equals("brand-new-tactic") && t.name().equals("Brand new"));
        assertThat(Files.readString(pack.resolve(ALPHA_FILE))).contains("Alpha tactic");
    }
}
