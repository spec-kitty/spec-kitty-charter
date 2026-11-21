package ai.speckitty.charter.packvalidation.roundtrip;

import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.roundtrip.FileOutcome;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.domain.roundtrip.Reading;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What one round trip shows, file by file: every answered file compared with its source by meaning (ADR-0036), in its
 * content form, and every file the census does not hold checked to be unwritten (#451, #631). It reads no file; the
 * flow read both sides.
 *
 * @param observed for each answered file and each file the census does not hold, empty when it is green, else the red detail, which starts with
 *     where the file first differs followed by {@code ": "} ({@code steps[2].title: …}, {@code refused: …})
 * @param information extra lines per file, such as a graph shard's duplicate edges, which do not count as a difference
 * @param unpaired the written files no source file paired with (ADR-0036: an extra file is a difference)
 */
public record RoundTripObservation(
        SortedMap<String, Optional<String>> observed,
        SortedMap<String, List<String>> information,
        SortedSet<String> unpaired) {

    /**
     * Copies every collection, unmodifiable.
     *
     * @param observed the per-file details
     * @param information the per-file information
     * @param unpaired the unpaired written files
     */
    public RoundTripObservation {
        observed = Collections.unmodifiableSortedMap(new TreeMap<>(observed));
        information = Collections.unmodifiableSortedMap(new TreeMap<>(information));
        unpaired = Collections.unmodifiableSortedSet(new TreeSet<>(unpaired));
    }

    /**
     * Compares every answered file of a round trip with its source, and checks every file the census does not hold.
     *
     * @param result what the flow observed
     * @return the observation
     */
    public static RoundTripObservation of(PackRoundTripResult result) {
        MeaningComparator comparator = MeaningComparator.adr0036();
        SortedMap<String, Optional<String>> observed = new TreeMap<>();
        SortedMap<String, List<String>> information = new TreeMap<>();
        SortedSet<String> paired = new TreeSet<>();
        result.outcomes()
                .forEach((path, outcome) -> observed.put(
                        path,
                        switch (outcome) {
                            case FileOutcome.Refused refused -> Optional.of("refused: " + refused.reason());
                            case FileOutcome.Written written -> {
                                paired.add(written.writtenPath());
                                String kind = result.census().getOrDefault(path, "");
                                duplicates(comparator, kind, written.source())
                                        .ifPresent(lines -> information.put(path, lines));
                                yield red(comparator, kind, written);
                            }
                        }));
        // A file the census does not hold is not compared, it is checked: the store must not have written it (#451,
        // #631).
        for (NotHeldFile file : result.notHeld().values()) {
            String path = file.path();
            observed.put(
                    path,
                    result.written().contains(path)
                            ? Optional.of("the store wrote the not-held file " + path)
                            : Optional.empty());
        }
        SortedSet<String> unpaired = new TreeSet<>(result.written());
        unpaired.removeAll(paired);
        return new RoundTripObservation(observed, information, unpaired);
    }

    /** A graph shard's duplicate edges, as information. */
    private static Optional<List<String>> duplicates(MeaningComparator comparator, String kind, Reading source) {
        if (!(source instanceof Reading.Yaml(Object tree))) {
            return Optional.empty();
        }
        List<String> duplicates = comparator.duplicateEdges(kind, tree);
        return duplicates.isEmpty()
                ? Optional.empty()
                : Optional.of(duplicates.stream()
                        .map(edge -> "duplicate edge in the source: " + edge)
                        .toList());
    }

    /** The red detail of a written file, by the form both sides were read in. */
    private static Optional<String> red(MeaningComparator comparator, String kind, FileOutcome.Written written) {
        Reading source = written.source();
        Reading target = written.written();
        if (source instanceof Reading.Bytes s && target instanceof Reading.Bytes w) {
            return bytesRed(s.bytes(), w.bytes());
        }
        if (source instanceof Reading.Text(String s) && target instanceof Reading.Text(String w)) {
            return textRed(s, w);
        }
        if (source instanceof Reading.Yaml(Object s) && target instanceof Reading.Yaml(Object w)) {
            Optional<String> meaning = red(comparator.differences(kind, s, w));
            return withStale(meaning, written.writtenStale());
        }
        return red(comparator.compareMarkdown(source, target));
    }

    /**
     * The red detail of a written file: every difference path, joined by {@code " ; "}, then the first difference's
     * values. The baseline's {@code at:} pins that whole list, so a loss behind a baselined one cannot hide.
     */
    private static Optional<String> red(List<MeaningComparator.Difference> differences) {
        if (differences.isEmpty()) {
            return Optional.empty();
        }
        MeaningComparator.Difference first = differences.get(0);
        String at = differences.stream().map(MeaningComparator.Difference::path).collect(Collectors.joining(" ; "));
        return Optional.of(at + ": source " + first.source() + ", written " + first.written());
    }

    /**
     * #195: the written manifest's stale derived fields, which the comparison skips (ADR-0042), join the red detail as
     * {@code written stale: <fields>}. A source-stale field is not the round trip's: the storage edge states it as
     * {@code manifest_stale} (#630).
     */
    private static Optional<String> withStale(Optional<String> red, List<String> writtenStale) {
        List<String> parts = new ArrayList<>();
        red.ifPresent(parts::add);
        if (!writtenStale.isEmpty()) {
            parts.add("written stale: " + String.join(", ", writtenStale));
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(" ; ", parts));
    }

    /**
     * The red detail of a {@code bytes} file: where the bytes first differ, {@code bytes[<offset>]: source 0x..,
     * written 0x..}, or, when one is a prefix of the other, {@code length: source N, written M}.
     */
    private static Optional<String> bytesRed(byte[] source, byte[] written) {
        int offset = Arrays.mismatch(source, written);
        if (offset < 0) {
            return Optional.empty();
        }
        if (offset == source.length || offset == written.length) {
            return Optional.of("length: source " + source.length + ", written " + written.length);
        }
        return Optional.of("bytes[" + offset + "]: source 0x" + HexFormat.of().toHexDigits(source[offset])
                + ", written 0x" + HexFormat.of().toHexDigits(written[offset]));
    }

    /**
     * The red detail of a text companion: where the texts first differ, {@code text[<offset>]: source '..', written
     * '..'}, or, when one is a prefix of the other, {@code length: source N, written M}.
     */
    private static Optional<String> textRed(String source, String written) {
        int offset = Arrays.mismatch(source.toCharArray(), written.toCharArray());
        if (offset < 0) {
            return Optional.empty();
        }
        if (offset == source.length() || offset == written.length()) {
            return Optional.of("length: source " + source.length() + ", written " + written.length());
        }
        return Optional.of("text[" + offset + "]: source '" + source.charAt(offset) + "', written '"
                + written.charAt(offset) + "'");
    }
}
