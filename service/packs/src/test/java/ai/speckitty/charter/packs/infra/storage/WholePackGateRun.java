package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packvalidation.roundtrip.RoundTripObservation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * One gate run over a fixture pack, for the per-kind tests of {@code :packs}: the production flow
 * ({@link PackRoundTrip}) with an adapter of the test's choosing, observed by {@code pack-validation}'s
 * {@link RoundTripObservation}, the gate's own comparison (ADR-0075 decision 8, as amended on 2026-11-15).
 */
final class WholePackGateRun {

    private WholePackGateRun() {}

    /**
     * Runs the round trip, the pack configured under its directory name (each fixture pack's directory is its id), and
     * compares every answered file with its source by the content form {@code formOf} gives its path.
     *
     * @param roundTrip the adapter
     * @param pack the source pack
     * @param written the directory to write to
     * @param census every source file's gate kind
     * @param information filled with each file's extra lines (a graph shard's duplicate edges)
     * @param paired filled with the written files a source file paired with
     * @param formOf the content form of a source path
     * @return per answered or excluded file, empty when green, else the red detail
     */
    static Map<String, Optional<String>> observe(
            WholePackRoundTrip roundTrip,
            Path pack,
            Path written,
            SortedMap<String, String> census,
            Map<String, List<String>> information,
            Set<String> paired,
            Function<String, ContentForm> formOf) {
        PackRoundTripResult result = PackRoundTrip.run(
                roundTrip,
                new PackIdentifier(String.valueOf(pack.getFileName())),
                pack,
                written,
                census,
                notHeldOf(pack, census),
                formOf);
        RoundTripObservation observation = RoundTripObservation.of(result);
        information.putAll(observation.information());
        result.written().stream()
                .filter(file -> !observation.unpaired().contains(file))
                .forEach(paired::add);
        return observation.observed();
    }

    /** The files of {@code census} the pack's own census does not hold. */
    private static SortedMap<String, NotHeldFile> notHeldOf(Path pack, SortedMap<String, String> census) {
        SortedMap<String, NotHeldFile> notHeld = new TreeMap<>(WholePackCensus.notHeld(WholePackCensus.census(pack)));
        notHeld.keySet().retainAll(census.keySet());
        return notHeld;
    }

    /**
     * The written files no source file paired with.
     *
     * @param written the written pack
     * @param paired the written files a source file paired with
     * @return the others, sorted
     */
    static List<String> unpaired(Path written, Set<String> paired) {
        try (Stream<Path> files = Files.walk(written)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> written.relativize(file)
                            .toString()
                            .replace(file.getFileSystem().getSeparator(), "/"))
                    .filter(path -> !paired.contains(path))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
