package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every procedure, every toolguide and every toolguide guide of the example packs round-trips (#194): the
 * built-in, org and project example packs are each read through the gate's own round trip ({@link DomainRoundTrip}) and every
 * procedure and toolguide file, and every guide modelled with its toolguide, is compared with its source by the gate's
 * own comparison (YAML by meaning, ADR-0036; a guide by its Markdown model). The #187 gate covers only the built-in
 * pack; this reads every example pack and holds the gate to every such file on disk.
 *
 * <p>Reads the charter-owned example packs ({@code ExamplePacks}, ADR-0070) and runs under {@code :packs:test}.
 */
@DisplayName("Every example procedure, toolguide and guide round-trips (#194)")
class ProcedureAndGuideCorpusTest {

    private static final String PROCEDURE = "procedure";
    private static final String TOOLGUIDE = "toolguide";
    private static final String GUIDE_SUFFIX = ".guide.md";

    /** The throwaway migration harness of #194 (WP09) lived in this package; WP10 deleted it. */
    private static final String MIGRATION_PACKAGE = "ai/speckitty/charter/packs/migration";

    @TempDir
    Path host;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"built-in", "org-base", "project"})
    @DisplayName("each pack's procedures, toolguides and guides round-trip, and the gate observes every one on disk")
    void everyProcedureToolguideAndGuideRoundTrips(String pack) throws IOException {
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
        Path packDirectory = ExamplePacks.pack(pack);
        assertThat(procedureFiles)
                .as("%s: the procedures the gate observes are the procedure files on disk", pack)
                .containsExactlyElementsOf(PackFiles.filesEndingWith(packDirectory, ".procedure.yaml"));
        assertThat(toolguideFiles)
                .as("%s: the toolguides the gate observes are the toolguide files on disk", pack)
                .containsExactlyElementsOf(PackFiles.filesEndingWith(packDirectory, ".toolguide.yaml"));
        assertThat(guideFiles)
                .as("%s: one guide per toolguide, each <toolguide id>.guide.md beside it", pack)
                .hasSameSizeAs(toolguideFiles)
                .containsExactlyInAnyOrderElementsOf(toolguideFiles.stream()
                        .map(toolguide -> guideOf(packDirectory, toolguide))
                        .toList());
        assertThat(procedureFiles.size() + toolguideFiles.size() + guideFiles.size())
                .as("%s: nothing else of the two kinds", pack)
                .isEqualTo(observed.size());
    }

    @Test
    @DisplayName("the example packs hold procedures, toolguides and guides, so the round trip is not vacuous")
    void theCorpusIsNotVacuous() {
        int procedures = 0;
        int toolguides = 0;
        int guides = 0;
        for (String pack : ExamplePacks.names()) {
            Map<String, Optional<String>> observed = observe(pack);
            procedures +=
                    filesOf(observed, path -> path.endsWith(".procedure.yaml")).size();
            toolguides +=
                    filesOf(observed, path -> path.endsWith(".toolguide.yaml")).size();
            guides += filesOf(observed, path -> path.endsWith(GUIDE_SUFFIX)).size();
        }

        assertThat(procedures).as("procedures").isPositive();
        assertThat(toolguides).as("toolguides").isPositive();
        assertThat(guides).as("guides").isPositive();
    }

    @Test
    @DisplayName("the throwaway migration harness is gone from the test classpath")
    void theMigrationHarnessIsGone() throws IOException {
        List<URL> found = Collections.list(getClass().getClassLoader().getResources(MIGRATION_PACKAGE));

        assertThat(found).as("package %s on the classpath", MIGRATION_PACKAGE).isEmpty();
    }

    /** The guide a toolguide file names: {@code <its id>.guide.md} in the toolguide's directory. */
    private static String guideOf(Path pack, String toolguide) {
        String text;
        try {
            text = Files.readString(pack.resolve(toolguide), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!(ParsedYaml.parse(text, toolguide).document() instanceof Map<?, ?> declared)) {
            throw new AssertionError(toolguide + " is not a mapping");
        }
        return toolguide.substring(0, toolguide.lastIndexOf('/') + 1) + declared.get("id") + GUIDE_SUFFIX;
    }

    /** The gate's observation of one pack, narrowed to its procedure and toolguide files (a guide is a toolguide's). */
    private Map<String, Optional<String>> observe(String pack) {
        Path source = ExamplePacks.pack(pack);
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

    private static List<String> filesOf(Map<String, Optional<String>> observed, Predicate<String> matches) {
        return observed.keySet().stream().filter(matches).sorted().toList();
    }
}
