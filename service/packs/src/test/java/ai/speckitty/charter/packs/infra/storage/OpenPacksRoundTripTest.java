package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.packs.domain.CharterPack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * Run by {@code ./gradlew :packs:openPacksRoundTrip} (part of {@code check}).
 */
@Tag("open-packs")
class OpenPacksRoundTripTest {

    @TestFactory
    Stream<DynamicTest> everyYamlFileRoundTrips() throws IOException {
        OpenPacksCorpus.assertPinned();
        Path root = OpenPacksCorpus.root();
        List<Path> files = OpenPacksCorpus.yamlFiles();
        assertThat(files).as("YAML files under packs/").hasSize(OpenPacksCorpus.YAML_FILE_COUNT);
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
        OpenPacksCorpus.assertPinned();
        List<PackLocation> packs = OpenPacksCorpus.packs();
        assertThat(packs)
                .extracting(PackLocation::name)
                .as("packs of the corpus")
                .containsExactlyInAnyOrderElementsOf(OpenPacksCorpus.DIAGNOSTICS.keySet());
        return packs.stream()
                .map(location -> dynamicTest(location.name(), () -> {
                    CharterPack pack = FileSystemCharterPackRepository.loadPack(location);
                    assertThat(pack.diagnostics())
                            .as("diagnostics of " + location.name())
                            .containsExactlyElementsOf(OpenPacksCorpus.DIAGNOSTICS.get(location.name()));
                    DoctrineGraph graph = pack.graph();
                    assertThat(graph.nodes()).as("nodes of " + location.name()).isNotEmpty();
                    FileRead<RawGraph> written = GraphDocumentReader.read(
                            GraphShardWriter.write(graph), GraphDocumentReader.Shape.SHARD, "written.graph.yaml");
                    assertThat(written).as("the written shard reads back").isInstanceOf(FileRead.Read.class);
                    DoctrineGraph reread = PackPolicy.graph(
                                    location.name(), ((FileRead.Read<RawGraph>) written).value())
                            .graph();
                    assertThat(reread).isEqualTo(graph);
                }));
    }
}
