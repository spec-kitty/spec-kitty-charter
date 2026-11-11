package ai.speckitty.charter.packs.drift;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The nightly open-packs drift watch (ADR-0070): every pack of a spec-kitty-open-packs checkout ({@code
 * charter.openPacksDir}, usually {@code main}) loads through the production store with no violation, writes to a fresh
 * directory and reloads with no violation. It is a signal, not a gate: no required check runs it.
 */
class OpenPacksDriftTest {

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    @Test
    void everyOpenPacksPackLoadsAndRoundTrips(@TempDir Path host) throws IOException {
        Path packs = Path.of(System.getProperty("charter.openPacksDir", "../spec-kitty-open-packs")).resolve("packs");
        assertThat(packs).as("the open-packs checkout's packs directory").isDirectory();
        List<Path> dirs;
        try (Stream<Path> list = Files.list(packs)) {
            dirs = list.filter(dir -> Files.isRegularFile(dir.resolve("pack.yaml"))).sorted().toList();
        }
        assertThat(dirs).as("packs with a pack.yaml under %s", packs).isNotEmpty();
        List<String> problems = new ArrayList<>();
        for (Path pack : dirs) {
            WholePackStore.LoadedPack loaded = store.load(pack);
            loaded.pack().violations().forEach(v -> problems.add(pack.getFileName() + ": " + v));
            if (!loaded.pack().violations().isEmpty()) {
                continue;
            }
            Path target = host.resolve(String.valueOf(pack.getFileName()));
            store.write(loaded, target);
            store.load(target).pack().violations()
                    .forEach(v -> problems.add(pack.getFileName() + " (after write): " + v));
        }
        assertThat(problems).as("open-packs drift").isEmpty();
    }
}
