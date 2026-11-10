package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * IC-07: the composition facts carry each held artifact's {@code applies_to_languages}, read in the same assembly as
 * {@code defined} and {@code edges}, so the effective reads filter by language from the verified read and never read a
 * pack again (NFR-007).
 */
class CompositionFactsLanguagesTest {

    private static final String PACK = "pack-descriptor-only";

    private static PackCompositionFacts factsOf(Path host) throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        Files.writeString(pack.resolve("jvm.tactic.yaml"), """
                id: jvm
                schema_version: '1.0'
                name: JVM
                steps:
                - title: Build
                applies_to_languages:
                - java
                - kotlin
                """);
        Files.writeString(pack.resolve("anywhere.tactic.yaml"), """
                id: anywhere
                schema_version: '1.0'
                name: Anywhere
                steps:
                - title: Think
                """);
        Files.writeString(pack.resolve("land-a-change.procedure.yaml"), """
                schema_version: "1.0"
                id: land-a-change
                title: Land a change
                description: Lands a reviewed change.
                entry_condition: A change is ready to land.
                exit_condition: The change has landed.
                steps:
                - title: Ask for review
                applies_to_languages:
                - python
                """);
        Files.writeString(pack.resolve("implementer.agent.yaml"), """
                profile-id: implementer
                name: Implementer
                role: implementer
                purpose: Implements.
                specialization:
                  primary-focus: code
                applies_to_languages:
                - rust
                """);
        PackLocation location = new PackLocation(PACK, PackLevel.ORG, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy())
                .compositionFacts(new PackIdentifier(PACK));
    }

    @Test
    void eachHeldArtifactThatDeclaresLanguagesIsKeyedByItsBareUrn(@TempDir Path host) throws IOException {
        Map<String, List<String>> languages = factsOf(host).languages();

        assertThat(languages)
                .containsEntry("tactic:jvm", List.of("java", "kotlin"))
                .containsEntry("procedure:land-a-change", List.of("python"))
                .containsEntry("agent_profile:implementer", List.of("rust"));
    }

    @Test
    void anArtifactThatDeclaresNoLanguageIsAbsent(@TempDir Path host) throws IOException {
        assertThat(factsOf(host).languages()).doesNotContainKey("tactic:anywhere");
    }
}
