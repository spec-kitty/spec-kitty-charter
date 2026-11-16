package ai.speckitty.charter.packvalidation.roundtrip;

import ai.speckitty.charter.packs.application.WholePackWriteStore.ExcludedFile;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The drift judgement of one pack (ADR-0070), which {@code OpenPacksDriftTest} runs over every pack of a
 * spec-kitty-open-packs checkout: the round trip judged by {@link WholePackRoundTripRule} with no expectation at all
 * ({@link WholePackExpectation#none}), so every red is a problem, beside the load's own violations, its warnings and
 * the files nothing models. Those are not held (operator ruling Q1, #554): the round trip never sees them, so the drift
 * names each one, as the registration warns about it. A file an exclusion names is not drift.
 *
 * <p>Judging through {@link WholePackExpectation#evaluate} (#547) means every census file that is not excluded needs an
 * outcome: a file of a kind the round trip does not model fails as {@code no adapter models kind <kind>}, and a file of
 * a modelled kind the round trip skipped fails as {@code the round trip gave no outcome for this file}, each naming its
 * path and kind.
 */
final class WholePackDrift {

    private WholePackDrift() {}

    /**
     * Every drift problem of one pack, each prefixed with the pack's name.
     *
     * @param store the store the pack loads through
     * @param pack the pack directory
     * @param result the pack's round trip
     * @return the problems; empty when the pack round-trips whole
     */
    static List<String> problems(WholePackStore store, Path pack, PackRoundTripResult result) {
        String name = String.valueOf(pack.getFileName());
        List<String> problems = new ArrayList<>();
        WholePackStore.LoadedPack loaded = store.load(pack);
        loaded.pack().violations().forEach(v -> problems.add(name + ": violation " + v));
        loaded.notHeld().stream()
                .filter(ExcludedFile::isUnmodelled)
                .forEach(file -> problems.add(name + ": " + file.notHeld().detail()));
        loaded.warnings().forEach(w -> problems.add(name + ": warning " + w));
        new WholePackRoundTripRule(WholePackExpectation.none())
                .apply(result)
                .forEach(reason -> problems.add(name + ": " + reason.detail()));
        return problems;
    }
}
