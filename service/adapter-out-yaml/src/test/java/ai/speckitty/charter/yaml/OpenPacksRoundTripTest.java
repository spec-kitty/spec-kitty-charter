package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.PackLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.snakeyaml.engine.v2.common.ScalarStyle;

/**
 * Every YAML file of the spec-kitty-open-packs packs must survive the file-based adapter (operator
 * decision, 2026-10-18). Four legs per file or pack:
 * <ol>
 *   <li>bytes: the lossless document model renders each file byte for byte;</li>
 *   <li>data: rewriting every scalar of the file with its own value through the model's writer loads to
 *       the same data;</li>
 *   <li>identity: in that rewrite, every scalar whose style carries its value unchanged (a single-line plain or
 *       single-quoted scalar) is written byte for byte as it was read;</li>
 *   <li>domain: every pack loads without a refusal with exactly the diagnostics listed below, and its graph
 *       written as a shard and read back is the same graph.</li>
 * </ol>
 * Run by {@code ./gradlew :adapter-out-yaml:openPacksRoundTrip} (part of {@code check}).
 */
@Tag("open-packs")
class OpenPacksRoundTripTest {

    /**
     * The load diagnostics of every pack of the pinned corpus (OPEN_PACKS_REF), under the ratified strict rule:
     * a pack without pack.yaml, or whose pack.yaml has no pack_level, is diagnosed at every level. Bringing
     * the packs up to the rule is spec-kitty-open-packs #47; a pack added to or fixed in the corpus changes this
     * table on purpose.
     */
    private static final Map<String, List<Diagnostic>> DIAGNOSTICS = Map.of(
            "built-in", List.of(),
            "cave-kitty", List.of(noPackYaml("cave-kitty")),
            "event-driven", List.of(noPackYaml("event-driven")),
            "internal",
                    List.of(new Diagnostic(
                            Diagnostic.Code.PACK_LEVEL_MISSING,
                            "pack.yaml of internal has no pack_level; the configured level org is used")),
            "qa", List.of(noPackYaml("qa")));

    private static Diagnostic noPackYaml(String orgPack) {
        return new Diagnostic(
                Diagnostic.Code.PACK_LEVEL_MISSING,
                "org pack " + orgPack + " has no pack.yaml; the configured level org is used");
    }

    private static Path openPacks() {
        String dir = System.getProperty("charter.openPacksDir");
        if (dir == null || !Files.isDirectory(Path.of(dir, "built-in"))) {
            throw new IllegalStateException("No spec-kitty-open-packs checkout at '" + dir
                    + "'; pass -PopenPacksDir=<checkout>. The round-trip never skips.");
        }
        return Path.of(dir);
    }

    private static List<Path> yamlFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String top : List.of("built-in", "packs")) {
            try (Stream<Path> walk = Files.walk(root.resolve(top))) {
                walk.filter(Files::isRegularFile)
                        .filter(p ->
                                p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                        .sorted()
                        .forEach(files::add);
            }
        }
        return files;
    }

    @TestFactory
    Stream<DynamicTest> everyYamlFileRoundTrips() throws IOException {
        Path root = openPacks();
        List<Path> files = yamlFiles(root);
        assertThat(files).as("YAML files under built-in/ and packs/").hasSizeGreaterThan(100);
        return files.stream()
                .map(file -> dynamicTest(root.relativize(file).toString(), () -> {
                    byte[] bytes = Files.readAllBytes(file);
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    YamlDocument document = YamlDocument.parse(text);
                    assertThat(document.render().getBytes(StandardCharsets.UTF_8))
                            .as("bytes")
                            .isEqualTo(bytes);
                    YamlDocument rewritten = document.mapScalars(v -> v);
                    assertThat(Yaml.loadAll(rewritten.render())).as("data").isEqualTo(Yaml.loadAll(text));
                    List<YamlDocument.Scalar> before = document.scalars();
                    List<YamlDocument.Scalar> after = rewritten.scalars();
                    for (int i = 0; i < before.size(); i++) {
                        if (carriesItsValue(before.get(i))) {
                            assertThat(after.get(i).text())
                                    .as("identity of scalar %d", i)
                                    .isEqualTo(before.get(i).text());
                        }
                    }
                }));
    }

    /** A single-line plain or single-quoted scalar: the writer gives its own value back as the same text. */
    private static boolean carriesItsValue(YamlDocument.Scalar scalar) {
        return scalar.text().indexOf('\n') < 0
                && (scalar.style() == ScalarStyle.PLAIN || scalar.style() == ScalarStyle.SINGLE_QUOTED);
    }

    @TestFactory
    Stream<DynamicTest> everyPackLoadsAndItsGraphRoundTrips() throws IOException {
        Path root = openPacks();
        List<PackLocation> packs = new ArrayList<>();
        packs.add(new PackLocation("built-in", PackLevel.BUILT_IN, root.resolve("built-in")));
        try (Stream<Path> dirs = Files.list(root.resolve("packs"))) {
            dirs.filter(Files::isDirectory)
                    .sorted()
                    .forEach(dir -> packs.add(new PackLocation(dir.getFileName().toString(), PackLevel.ORG, dir)));
        }
        assertThat(packs)
                .extracting(PackLocation::name)
                .as("packs of the corpus")
                .containsExactlyInAnyOrderElementsOf(DIAGNOSTICS.keySet());
        return packs.stream()
                .map(location -> dynamicTest(location.name(), () -> {
                    CharterPack pack = FileSystemCharterPackRepository.loadPack(location);
                    assertThat(pack.diagnostics())
                            .as("diagnostics of " + location.name())
                            .containsExactlyElementsOf(DIAGNOSTICS.get(location.name()));
                    DoctrineGraph graph = pack.graph();
                    assertThat(graph.nodes()).as("nodes of " + location.name()).isNotEmpty();
                    DoctrineGraph reread = GraphDocumentReader.read(
                            GraphShardWriter.write(graph),
                            GraphDocumentReader.Shape.SHARD,
                            location.name() + " (written)");
                    assertThat(reread).isEqualTo(graph);
                }));
    }
}
