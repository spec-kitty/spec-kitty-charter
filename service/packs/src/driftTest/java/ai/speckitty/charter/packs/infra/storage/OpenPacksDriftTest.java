package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

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
 * charter.openPacksDir}, usually {@code main}) goes through the same whole-pack round trip as the gate
 * ({@link WholePackRoundTripTest#observe}): it loads through the production store, is written to a fresh directory, and
 * every modelled file is compared with its source by meaning (ADR-0036), with no written file left unpaired. Every
 * census file that is not excluded needs an outcome: one of a kind the round trip does not model is red, naming its kind
 * and path (#547). It also reports the files the store does not model ({@code unmodelled()}) and the load warnings
 * ({@code warnings()}). It has no expected-red baseline, so any of these is red ({@link WholePackDrift}). Charter's
 * nightly watch runs it as a signal; spec-kitty-open-packs runs it as a required gate from its pinned charter checkout
 * (ADR-0070 decision 4).
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
                problems.addAll(WholePackDrift.problems(store, new DomainRoundTrip(yaml), pack, host.resolve(name)));
            } catch (RuntimeException e) {
                problems.add(name + ": the round trip threw " + e);
            }
        }
        problems.forEach(System.out::println);
        assertThat(problems).as("open-packs drift").isEmpty();
    }
}
