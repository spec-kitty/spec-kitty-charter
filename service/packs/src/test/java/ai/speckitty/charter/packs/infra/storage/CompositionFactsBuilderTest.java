package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one composition-facts builder (#554, T027, FR-015): the pack under resolution {@code CLOSURE} judges, read from a
 * pack directory without the service through the one reader's {@code StoreRead} half, carries the facts recorded for
 * each example pack in {@code facts/example-packs.json}: the facts the golden composition answers were built from.
 */
@DisplayName("CompositionFactsBuilder builds a pack's composition facts without the service (#554, T027)")
class CompositionFactsBuilderTest {

    private static final YamlSerializationStrategy STRATEGY = new YamlSerializationStrategy();

    static List<String> examplePacks() {
        return ExamplePacks.names();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examplePacks")
    @DisplayName("each example pack: the facts are those recorded for it in facts/example-packs.json")
    void theFactsAreTheRecordedFacts(String name, @TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(name, host);
        ConfiguredPack configured = new ConfiguredPack(new PackIdentifier(name), ExamplePacks.level(name));

        PackUnderResolution read = CompositionFactsBuilder.underResolution(configured, pack, STRATEGY);

        JsonNode recorded = recorded().required(name);
        assertThat(read.configured()).isEqualTo(configured);
        assertThat(read.descriptor().name()).isEqualTo(name);
        assertThat(read.descriptor().packId())
                .isEqualTo(recorded.required("packId").asString());
        assertThat(read.changedArtifacts()).isEqualTo(strings(recorded.required("changed")));
        assertThat(read.definedArtifacts()).isEqualTo(strings(recorded.required("defined")));
        assertThat(read.heldTerms()).isEqualTo(Set.copyOf(strings(recorded.required("heldTerms"))));
    }

    private static JsonNode recorded() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                CompositionFactsBuilderTest.class.getResourceAsStream("/facts/example-packs.json"),
                "/facts/example-packs.json")) {
            return JsonMapper.builder().build().readTree(in);
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }

    @Test
    @DisplayName("the example packs' own facts: what each holds, and the terms org-base's glossary holds")
    void theExamplePacksDeclareTheirFacts(@TempDir Path host) {
        PackUnderResolution builtIn = underResolution(ExamplePacks.BUILT_IN, host);
        PackUnderResolution org = underResolution(ExamplePacks.ORG, host);

        assertThat(builtIn.definedArtifacts()).contains("mission_type:software-dev");
        assertThat(builtIn.descriptor().packId()).isEqualTo("01ARWG13C000000000000000FG");
        assertThat(org.descriptor().packId()).isEqualTo("01M6Y9ACNAAD96XPNQS0XWP2FF");
        assertThat(org.definedArtifacts()).isNotEmpty().doesNotContainAnyElementsOf(builtIn.definedArtifacts());
    }

    @Test
    @DisplayName(
            "a pack whose graph document cannot be read is refused pack_unreadable, as the composing read refuses it")
    void anUnreadableGraphIsRefused(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        Files.writeString(pack.resolve("broken.graph.yaml"), "nodes: [valid\n", StandardCharsets.UTF_8);
        ConfiguredPack configured = new ConfiguredPack(new PackIdentifier("built-in"), PackLevel.BUILT_IN);

        assertThatThrownBy(() -> CompositionFactsBuilder.underResolution(configured, pack, STRATEGY))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage("a graph document of built-in cannot be read");
    }

    private static PackUnderResolution underResolution(String name, Path host) {
        Path pack = ExamplePacks.copyOf(name, host.resolve(name));
        return CompositionFactsBuilder.underResolution(
                new ConfiguredPack(new PackIdentifier(name), ExamplePacks.level(name)), pack, STRATEGY);
    }

    @Test
    @DisplayName(
            "a pack with a file the load refuses is refused pack_unreadable, naming the file, as the composing read"
                    + " refuses it")
    void aRefusedFileIsRefused(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        Files.writeString(pack.resolve("tactics/broken.tactic.yaml"), "id: [\n", StandardCharsets.UTF_8);
        ConfiguredPack configured = new ConfiguredPack(new PackIdentifier("built-in"), PackLevel.BUILT_IN);

        assertThatThrownBy(() -> CompositionFactsBuilder.underResolution(configured, pack, STRATEGY))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessageStartingWith("the file tactics/broken.tactic.yaml of built-in cannot be read");
    }
}
