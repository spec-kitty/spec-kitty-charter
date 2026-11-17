package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The one composition-facts builder (#554, T027, FR-015): the pack under resolution {@code CLOSURE} judges, read from a
 * pack directory without the service, is the one the composing read answers for the same pack, and it carries the
 * facts the example packs declare.
 */
@DisplayName("CompositionFactsBuilder builds a pack's composition facts without the service (#554, T027)")
class CompositionFactsBuilderTest {

    private static final YamlSerializationStrategy STRATEGY = new YamlSerializationStrategy();

    static List<String> examplePacks() {
        return ExamplePacks.names();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examplePacks")
    @DisplayName("each example pack: the facts read without the service are those the composing read answers")
    void theSpringFreeFactsAreTheComposingReadsFacts(String name, @TempDir Path host) {
        Path pack = ExamplePacks.copyOf(name, host);
        ConfiguredPack configured = new ConfiguredPack(new PackIdentifier(name), ExamplePacks.level(name));

        PackUnderResolution read = CompositionFactsBuilder.underResolution(configured, pack, STRATEGY);

        PackCompositionFacts served = service(name, pack).compositionFacts(configured.id());
        assertThat(read.configured()).isEqualTo(configured);
        assertThat(read.descriptor().name()).isEqualTo(name);
        assertThat(read.changedArtifacts()).isEqualTo(served.changed());
        assertThat(read.definedArtifacts()).isEqualTo(served.defined()).isNotEmpty();
        assertThat(read.heldTerms()).isEqualTo(Set.copyOf(served.terms()));
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

    private static DomainRepositoryService service(String name, Path pack) {
        return new DomainRepositoryService(
                () -> List.of(new PackLocation(name, ExamplePacks.level(name), pack)), STRATEGY);
    }
}
