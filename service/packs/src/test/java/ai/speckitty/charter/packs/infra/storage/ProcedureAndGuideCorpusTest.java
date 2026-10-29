package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every procedure, every toolguide and every toolguide guide of the pinned open-packs checkout round-trips (#194): the
 * built-in, internal and qa packs are each read through the gate's own round trip ({@link DomainRoundTrip}) and every
 * procedure and toolguide file, and every guide modelled with its toolguide, is compared with its source by the gate's
 * own comparison (YAML by meaning, ADR-0036; a guide by its Markdown model). The #187 gate covers only the built-in
 * pack; this covers the two org packs that hold procedures and toolguides too, and pins how many there are. The qa
 * pack has no {@code pack.yaml} at the pin, so it is read from a copy with a minimal descriptor added.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("Every pinned procedure, toolguide and guide round-trips (#194)")
class ProcedureAndGuideCorpusTest {

    private static final String PROCEDURE = "procedure";
    private static final String TOOLGUIDE = "toolguide";
    private static final String GUIDE_SUFFIX = ".guide.md";

    /** The throwaway migration harness of #194 (WP09) lived in this package; WP10 deleted it. */
    private static final String MIGRATION_PACKAGE = "ai/speckitty/charter/packs/migration";

    /** The descriptor added to a copy of a pack that has none, so the round trip can assemble it. */
    private static final String ASSEMBLY_PACK_YAML = """
            pack_id: 01M60H9B0000000000000000QA
            pack_version: 1.0.0
            name: qa
            pack_level: org
            """;

    @TempDir
    Path host;

    @ParameterizedTest(name = "{0}: {1} procedures, {2} toolguides, {2} guides")
    @CsvSource({"built-in, 22, 18", "internal, 12, 3", "qa, 5, 3"})
    @DisplayName("each pack's procedures, toolguides and guides round-trip, and their counts are pinned")
    void everyProcedureToolguideAndGuideRoundTrips(String pack, int procedures, int toolguides) {
        OpenPacksCorpus.assertPinned();
        Map<String, Optional<String>> observed = observe(pack);

        List<String> procedureFiles = filesOf(observed, path -> path.endsWith(".procedure.yaml"));
        List<String> toolguideFiles = filesOf(observed, path -> path.endsWith(".toolguide.yaml"));
        List<String> guideFiles = filesOf(observed, path -> path.endsWith(GUIDE_SUFFIX));
        List<String> red = observed.entrySet().stream()
                .filter(entry -> entry.getValue().isPresent())
                .map(entry -> entry.getKey() + ": " + entry.getValue().orElseThrow())
                .toList();

        assertThat(red)
                .as("%s: procedures, toolguides and guides that do not round-trip", pack)
                .isEmpty();
        assertThat(procedureFiles).as("%s procedures", pack).hasSize(procedures);
        assertThat(toolguideFiles).as("%s toolguides", pack).hasSize(toolguides);
        assertThat(guideFiles)
                .as("%s: one guide per toolguide, each <toolguide id>.guide.md", pack)
                .hasSize(toolguides)
                .allSatisfy(guide -> assertThat(guide).startsWith("toolguides/"));
        assertThat(procedureFiles.size() + toolguideFiles.size() + guideFiles.size())
                .as("%s: nothing else of the two kinds", pack)
                .isEqualTo(observed.size());
    }

    @Test
    @DisplayName("the corpus holds 39 procedures, 24 toolguides and 24 guides")
    void theCorpusCountsArePinned() {
        int procedures = 0;
        int toolguides = 0;
        int guides = 0;
        for (String pack : List.of("built-in", "internal", "qa")) {
            Map<String, Optional<String>> observed = observe(pack);
            procedures +=
                    filesOf(observed, path -> path.endsWith(".procedure.yaml")).size();
            toolguides +=
                    filesOf(observed, path -> path.endsWith(".toolguide.yaml")).size();
            guides += filesOf(observed, path -> path.endsWith(GUIDE_SUFFIX)).size();
        }

        assertThat(List.of(procedures, toolguides, guides)).containsExactly(39, 24, 24);
    }

    @Test
    @DisplayName("the throwaway migration harness is gone from the test classpath")
    void theMigrationHarnessIsGone() throws IOException {
        List<URL> found = Collections.list(getClass().getClassLoader().getResources(MIGRATION_PACKAGE));

        assertThat(found).as("package %s on the classpath", MIGRATION_PACKAGE).isEmpty();
    }

    /** The gate's observation of one pack, narrowed to its procedure and toolguide files (a guide is a toolguide's). */
    private Map<String, Optional<String>> observe(String pack) {
        Path source = assemblable(OpenPacksCorpus.packsDirectory().resolve(pack));
        SortedMap<String, String> census = WholePackCensus.of(source);
        Map<String, Optional<String>> observed = WholePackRoundTripTest.observe(
                new DomainRoundTrip(new YamlSerializationStrategy()),
                source,
                host.resolve(pack + "-" + System.nanoTime()).resolve(pack),
                census,
                new TreeMap<>(),
                new TreeSet<>(),
                WholePackCensus.formsOf(source));
        Map<String, Optional<String>> narrowed = new TreeMap<>();
        observed.forEach((path, red) -> {
            String kind = census.get(path);
            if (PROCEDURE.equals(kind) || TOOLGUIDE.equals(kind)) {
                narrowed.put(path, red);
            }
        });
        return narrowed;
    }

    /**
     * The pack itself when it has a {@code pack.yaml}; else a copy with a minimal one added (a test fixture only, never
     * the checkout), so the round trip can assemble it. {@code qa} has none at the pin.
     */
    private Path assemblable(Path pack) {
        if (Files.isRegularFile(pack.resolve(PackFileSet.PACK_YAML))) {
            return pack;
        }
        Path copy = host.resolve("assembled-" + System.nanoTime()).resolve(String.valueOf(pack.getFileName()));
        try (Stream<Path> walk = Files.walk(pack)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                Path to = copy.resolve(pack.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(path, to);
                }
            }
            Files.writeString(copy.resolve(PackFileSet.PACK_YAML), ASSEMBLY_PACK_YAML, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return copy;
    }

    private static List<String> filesOf(Map<String, Optional<String>> observed, Predicate<String> matches) {
        return observed.keySet().stream().filter(matches).sorted().toList();
    }
}
