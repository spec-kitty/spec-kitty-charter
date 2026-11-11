package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The nightly open-packs drift watch (ADR-0070): every pack of a spec-kitty-open-packs checkout ({@code
 * charter.openPacksDir}, usually {@code main}) goes through the same whole-pack round trip as the gate
 * ({@link WholePackRoundTripTest#observe}): it loads through the production store, is written to a fresh directory, and
 * every modelled file is compared with its source by meaning (ADR-0036), with no written file left unpaired. It also
 * reports the files the store does not model ({@code unmodelled()}) and the load warnings ({@code warnings()}). It has
 * no expected-red baseline, so any of these is red. It is a signal, not a gate: no required check runs it.
 */
class OpenPacksDriftTest {

    private final YamlSerializationStrategy yaml = new YamlSerializationStrategy();
    private final WholePackStore store = new WholePackStore(yaml);

    @Test
    void everyOpenPacksPackRoundTripsByMeaning(@TempDir Path host) throws IOException {
        Path packs = Path.of(System.getProperty("charter.openPacksDir", "../spec-kitty-open-packs"))
                .resolve("packs");
        assertThat(packs).as("the open-packs checkout's packs directory").isDirectory();
        List<Path> dirs;
        try (Stream<Path> list = Files.list(packs)) {
            dirs = list.filter(dir -> Files.isRegularFile(dir.resolve(PackFileSet.PACK_YAML)))
                    .sorted()
                    .toList();
        }
        assertThat(dirs).as("packs with a pack.yaml under %s", packs).isNotEmpty();
        List<String> problems = new ArrayList<>();
        for (Path pack : dirs) {
            String name = String.valueOf(pack.getFileName());
            try {
                problems.addAll(drift(pack, host.resolve(name)));
            } catch (RuntimeException e) {
                problems.add(name + ": the round trip threw " + e);
            }
        }
        problems.forEach(System.out::println);
        assertThat(problems).as("open-packs drift").isEmpty();
    }

    private List<String> drift(Path pack, Path written) {
        String name = String.valueOf(pack.getFileName());
        List<String> problems = new ArrayList<>();
        WholePackStore.LoadedPack loaded = store.load(pack);
        loaded.pack().violations().forEach(v -> problems.add(name + ": violation " + v));
        loaded.unmodelled().forEach(file -> problems.add(name + ": unmodelled " + file));
        loaded.warnings().forEach(w -> problems.add(name + ": warning " + w));

        SortedMap<String, String> census = WholePackCensus.of(pack);
        Map<String, List<String>> information = new TreeMap<>();
        TreeSet<String> paired = new TreeSet<>();
        Map<String, Optional<String>> observed = WholePackRoundTripTest.observe(
                new DomainRoundTrip(yaml), pack, written, census, information, paired, WholePackCensus.formsOf(pack));
        observed.forEach((path, red) -> red.ifPresent(r -> problems.add(name + ": " + path + ": " + r)));
        WholePackRoundTripTest.unpaired(written, paired)
                .forEach(file -> problems.add(name + ": written but unpaired " + file));
        return problems;
    }
}
