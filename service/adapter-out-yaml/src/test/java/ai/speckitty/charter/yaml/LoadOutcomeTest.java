package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.DrgEdge;
import ai.speckitty.charter.domain.PackLevel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What loading a pack from files answers, against an oracle written by hand (research R10).
 *
 * <p>Each directory of {@code load-fixtures/} is one pack: its files under {@code files/} and the answer in
 * {@code expected.json}, either the refusal (code and detail) or the diagnostics, the node and edge counts and
 * optionally the edges. The file was written from the spec and the pinned texts of
 * {@code contracts/pack-finding-codes.md}, never from a run of the code, so a change to any text, code or order fails
 * here and has to be made on purpose. This is not the load-versus-judge parity check: that would compare the code
 * with itself on one scope. The scope parity is WP08's.
 */
class LoadOutcomeTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Path fixtures() {
        try {
            return Path.of(LoadOutcomeTest.class.getResource("/load-fixtures").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<String> cases() throws IOException {
        try (Stream<Path> dirs = Files.list(fixtures())) {
            return dirs.filter(Files::isDirectory)
                    .map(d -> d.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    @Test
    void thereIsOneCaseForEveryScenarioFixtureAndForTheTwoFaultAndMultiDefectPacks() throws IOException {
        assertThat(cases())
                .hasSizeGreaterThanOrEqualTo(55)
                .contains(
                        "scenario-listing-built-in",
                        "scenario-listing-org-fragment-only",
                        "scenario-descriptor-without-level",
                        "scenario-level-mismatch",
                        "scenario-bare-endpoint",
                        "scenario-shard-unknown-key",
                        "scenario-shard-not-yaml",
                        "scenario-descriptor-unknown-key",
                        "scenario-requires-core-banana",
                        "scenario-org-without-fragment",
                        "two-fault-level-mismatch-and-bad-shard",
                        "multi-defect-shard-reports-the-root-key-first");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void loadingAnswersAsTheOracleSays(String name) throws IOException {
        Path dir = fixtures().resolve(name);
        JsonNode expected = JSON.readTree(Files.readString(dir.resolve("expected.json"), StandardCharsets.UTF_8));
        String packName = expected.get("pack").get("name").asString();
        PackLevel level = PackLevel.fromToken(expected.get("pack").get("level").asString())
                .orElseThrow();
        FileSystemCharterPackRepository repository = new FileSystemCharterPackRepository(
                () -> List.of(new PackLocation(packName, level, dir.resolve("files"))));

        if (expected.has("refusal")) {
            JsonNode refusal = expected.get("refusal");
            assertThatThrownBy(repository::load).isInstanceOfSatisfying(CharterRefusal.class, r -> {
                assertThat(r.code().token()).isEqualTo(refusal.get("code").asString());
                assertThat(r.getMessage()).isEqualTo(refusal.get("detail").asString());
            });
            return;
        }
        List<CharterPack> packs = repository.load().packs();
        assertThat(packs).hasSize(1);
        CharterPack pack = packs.get(0);
        List<String> diagnostics = new ArrayList<>();
        pack.diagnostics().forEach(d -> diagnostics.add(d.code().token() + ": " + d.detail()));
        List<String> expectedDiagnostics = new ArrayList<>();
        expected.get("diagnostics")
                .forEach(d -> expectedDiagnostics.add(
                        d.get("code").asString() + ": " + d.get("detail").asString()));
        assertThat(diagnostics).containsExactlyElementsOf(expectedDiagnostics);
        assertThat(pack.graph().nodes()).hasSize(expected.get("nodeCount").asInt());
        assertThat(pack.graph().edges()).hasSize(expected.get("edgeCount").asInt());
        if (expected.has("edges")) {
            List<String> expectedEdges = new ArrayList<>();
            expected.get("edges").forEach(e -> expectedEdges.add(e.asString()));
            assertThat(pack.graph().edges().stream()
                            .map(LoadOutcomeTest::describe)
                            .toList())
                    .containsExactlyElementsOf(expectedEdges);
        }
    }

    private static String describe(DrgEdge edge) {
        return edge.source() + " " + edge.relation().token() + " " + edge.target()
                + edge.impacts().map(v -> " " + v).orElse("");
    }

    @Test
    void everyCaseHasExactlyOneKindOfAnswer() throws IOException {
        for (String name : cases()) {
            try {
                JsonNode expected = JSON.readTree(
                        Files.readString(fixtures().resolve(name).resolve("expected.json"), StandardCharsets.UTF_8));
                assertThat(expected.has("refusal"))
                        .as("%s: a refusal or the diagnostics, not both", name)
                        .isNotEqualTo(expected.has("diagnostics"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
