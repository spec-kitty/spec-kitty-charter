package ai.speckitty.charter.packs.infra.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The drift judgement of one pack (ADR-0070), which {@code OpenPacksDriftTest} runs over every pack of a
 * spec-kitty-open-packs checkout: the gate's round trip ({@link WholePackRoundTripTest#observe}) judged with no
 * expectation at all ({@link WholePackExpectation#none}), so every red is a problem, beside the load's own violations,
 * unmodelled files and warnings and the written files no source file pairs with.
 *
 * <p>Judging through {@link WholePackExpectation#evaluate} (#547) means every census file that is not excluded needs an
 * outcome: a file of a kind the round trip does not model fails as {@code no adapter models kind <kind>}, and a file of
 * a modelled kind the round trip skipped fails as {@code the round trip gave no outcome for this file}, each naming its
 * path and kind. Before, the drift test read only the outcomes the round trip gave, so such a file passed in silence.
 */
final class WholePackDrift {

    private WholePackDrift() {}

    /**
     * Every drift problem of one pack, each prefixed with the pack's name.
     *
     * @param store the store the pack loads through
     * @param roundTrip the round trip under test
     * @param pack the pack directory
     * @param written a fresh directory to write the pack to
     * @return the problems; empty when the pack round-trips whole
     */
    static List<String> problems(WholePackStore store, WholePackRoundTrip roundTrip, Path pack, Path written) {
        String name = String.valueOf(pack.getFileName());
        List<String> problems = new ArrayList<>();
        WholePackStore.LoadedPack loaded = store.load(pack);
        loaded.pack().violations().forEach(v -> problems.add(name + ": violation " + v));
        loaded.unmodelled().forEach(file -> problems.add(name + ": unmodelled " + file));
        loaded.warnings().forEach(w -> problems.add(name + ": warning " + w));

        SortedMap<String, String> census = WholePackCensus.of(pack);
        TreeSet<String> paired = new TreeSet<>();
        Map<String, Optional<String>> observed = WholePackRoundTripTest.observe(
                roundTrip, pack, written, census, new TreeMap<>(), paired, WholePackCensus.formsOf(pack));
        WholePackExpectation.none()
                .evaluate(census, roundTrip.modelledKinds(), observed)
                .problems()
                .forEach(problem -> problems.add(name + ": " + problem));
        WholePackRoundTripTest.unpaired(written, paired)
                .forEach(file -> problems.add(name + ": written but unpaired " + file));
        return problems;
    }
}
