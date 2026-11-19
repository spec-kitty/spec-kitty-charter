package ai.speckitty.charter.packvalidation.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
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
 * The open-packs drift test (ADR-0070): every pack of a spec-kitty-open-packs checkout ({@code
 * charter.openPacksDir}, usually {@code main}) goes through the production round trip ({@link PackRoundTrip}, in
 * {@code packs} main), as the gate does: it loads through the production store, is written to a fresh directory, and
 * {@link WholePackRoundTripRule} judges it with no expectation, so every modelled file must mean the same as its source
 * (ADR-0036) and no written file may be left unpaired. Every census file that is not excluded needs an outcome: one of a
 * kind the round trip does not model is red, naming its kind and path (#547). It also reports the files nothing models,
 * which the store does not hold ({@code notHeld()}, operator ruling Q1), and the load warnings ({@code warnings()}). It
 * has no expected-red baseline, so any of
 * these is red ({@link WholePackDrift}). spec-kitty-open-packs runs it as a required gate from its pinned charter checkout
 * (ADR-0070 decision 4), and against charter releases and charter {@code main} from its charter track; no charter
 * workflow runs it (#604).
 */
class OpenPacksDriftTest {

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    @Test
    void everyOpenPacksPackRoundTripsByMeaning(@TempDir Path host) throws IOException {
        Path packs = Path.of(System.getProperty("charter.openPacksDir", "../spec-kitty-open-packs"))
                .resolve("packs");
        assertThat(packs).as("the open-packs checkout's packs directory").isDirectory();
        List<Path> dirs;
        try (Stream<Path> list = Files.list(packs)) {
            dirs = list.filter(dir -> Files.isRegularFile(dir.resolve("pack.yaml")))
                    .sorted()
                    .toList();
        }
        assertThat(dirs).as("packs with a pack.yaml under %s", packs).isNotEmpty();
        List<String> problems = new ArrayList<>();
        for (Path pack : dirs) {
            String name = String.valueOf(pack.getFileName());
            try {
                problems.addAll(WholePackDrift.problems(store, pack, PackRoundTrip.run(pack, host.resolve(name))));
            } catch (RuntimeException e) {
                problems.add(name + ": the round trip threw " + e);
            }
        }
        problems.forEach(System.out::println);
        assertThat(problems).as("open-packs drift").isEmpty();
    }
}
