package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.infra.storage.WholePackExpectation.Evaluation;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Refused;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Written;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole-pack round-trip gate (#187): every file of the pinned built-in pack is read into the domain, written to a
 * fresh directory through the storage service, read back, and compared with its source by meaning. The observed result
 * must equal the checked-in expectation exactly, both ways (see {@link WholePackExpectation}). The report lands in
 * {@code build/reports/whole-pack/report.md}.
 *
 * <p>Runs under {@code :packs:wholePackRoundTrip} (tag {@code whole-pack}), outside {@code check}: it is not a required
 * gate until it is green.
 */
@Tag("whole-pack")
class WholePackRoundTripTest {

    private static final String BUILT_IN = "built-in";

    /** The gate kinds the round trip must model today; an adapter that models fewer cannot pass by omission. */
    private static final Set<String> REQUIRED_KINDS =
            Set.of("directive", "tactic", "styleguide", "toolguide", "paradigm", "pack-descriptor", "graph-shard");

    @Test
    void theBuiltInPackRoundTripsAsExpected(@TempDir Path temp) throws IOException {
        OpenPacksCorpus.assertPinned();
        Path pack = OpenPacksCorpus.packsDirectory().resolve(BUILT_IN);
        SortedMap<String, String> census = WholePackCensus.of(pack);
        WholePackRoundTrip roundTrip = new DomainRoundTrip(new YamlSerializationStrategy());
        Map<String, List<String>> information = new TreeMap<>();

        Map<String, Optional<String>> observed = observe(roundTrip, pack, temp.resolve(BUILT_IN), census, information);
        Evaluation evaluation =
                WholePackExpectation.fromClasspath().evaluate(census, roundTrip.modelledKinds(), observed);

        Path report = Path.of(System.getProperty("charter.wholePackReport", "build/reports/whole-pack/report.md"));
        Files.createDirectories(report.toAbsolutePath().getParent());
        Files.writeString(
                report,
                WholePackReport.render(
                        evaluation,
                        "spec-kitty-open-packs packs/built-in",
                        System.getProperty("charter.openPacksRef", "?"),
                        information),
                StandardCharsets.UTF_8);
        System.out.println(WholePackReport.kindTable(evaluation));

        OpenPacksCorpus.assertPinned(); // the run left the checkout untouched (NFR-004)
        assertThat(roundTrip.modelledKinds())
                .as("the kinds the round trip models")
                .isEqualTo(REQUIRED_KINDS);
        assertThat(evaluation.problems())
                .as("whole-pack round trip against %s; report: %s", WholePackExpectation.EXPECTED_RED_RESOURCE, report)
                .isEmpty();
    }

    /**
     * Positive control over the real corpus: a writer that drops every tactic's {@code purpose} must turn each tactic
     * that declares one, and round-trips with the faithful writer, red, with its first difference at {@code purpose}.
     * If this passes, the gate can see a lossy writer end to end.
     */
    @Test
    void aLossyWriterTurnsTheRealCorpusRed(@TempDir Path temp) throws IOException {
        OpenPacksCorpus.assertPinned();
        Path pack = OpenPacksCorpus.packsDirectory().resolve(BUILT_IN);
        SortedMap<String, String> census = WholePackCensus.of(pack);
        SerializationStrategy yaml = new YamlSerializationStrategy();
        SerializationStrategy lossy = (SerializationStrategy) Proxy.newProxyInstance(
                SerializationStrategy.class.getClassLoader(),
                new Class<?>[] {SerializationStrategy.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(yaml, args);
                    } catch (InvocationTargetException thrown) {
                        throw thrown.getCause();
                    }
                    return method.getName().equals("writeTactic") ? withoutPurpose((String) result) : result;
                });

        Map<String, Optional<String>> faithful =
                observe(new DomainRoundTrip(yaml), pack, temp.resolve("faithful"), census, new TreeMap<>());
        Map<String, Optional<String>> observed =
                observe(new DomainRoundTrip(lossy), pack, temp.resolve("lossy"), census, new TreeMap<>());

        List<String> withPurpose = census.entrySet().stream()
                .filter(entry -> entry.getValue().equals("tactic"))
                .map(Map.Entry::getKey)
                .filter(path -> read(pack.resolve(path)).lines().anyMatch(line -> line.startsWith("purpose:")))
                .filter(path -> faithful.get(path).isEmpty())
                .toList();
        assertThat(withPurpose).as("green tactics that declare a purpose").hasSizeGreaterThan(100);
        assertThat(withPurpose)
                .allSatisfy(path -> assertThat(observed.get(path))
                        .as(path)
                        .hasValueSatisfying(red ->
                                assertThat(WholePackExpectation.atOf(red)).isEqualTo("purpose")));
    }

    /** A tactic's text without its top-level {@code purpose} key and the indented lines that continue it. */
    private static String withoutPurpose(String text) {
        StringBuilder out = new StringBuilder();
        boolean skipping = false;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("purpose:")) {
                skipping = true;
                continue;
            }
            if (skipping && line.startsWith(" ")) {
                continue;
            }
            skipping = false;
            out.append(line).append('\n');
        }
        return out.toString().stripTrailing() + "\n";
    }

    /** Runs the round trip and compares every modelled file with its source; empty is green, else the red detail. */
    private static Map<String, Optional<String>> observe(
            WholePackRoundTrip roundTrip,
            Path pack,
            Path written,
            SortedMap<String, String> census,
            Map<String, List<String>> information) {
        Map<String, Outcome> outcomes = roundTrip.roundTrip(pack, written, census);
        MeaningComparator comparator = MeaningComparator.adr0036();
        Map<String, Optional<String>> observed = new TreeMap<>();
        for (Map.Entry<String, Outcome> entry : outcomes.entrySet()) {
            String path = entry.getKey();
            String kind = census.get(path);
            String source = read(pack.resolve(path));
            observed.put(
                    path,
                    switch (entry.getValue()) {
                        case Written w ->
                            red(comparator.differencesOfTexts(kind, source, read(written.resolve(w.writtenPath()))));
                        case Refused r -> Optional.of("refused: " + r.reason());
                    });
            List<String> duplicates = comparator.duplicateEdges(kind, source);
            if (!duplicates.isEmpty()) {
                information.put(
                        path,
                        duplicates.stream()
                                .map(e -> "duplicate edge in the source: " + e)
                                .toList());
            }
        }
        return observed;
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

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
