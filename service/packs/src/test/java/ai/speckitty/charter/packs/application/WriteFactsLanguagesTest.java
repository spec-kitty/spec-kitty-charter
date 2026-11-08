package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The facts a write is judged on keep what they say of each artifact's languages, as they keep everything else. */
class WriteFactsLanguagesTest {

    private static final Urn TACTIC = Urn.parse("tactic:jvm").orElseThrow();

    private static PackCompositionFacts facts() {
        return new PackCompositionFacts(
                List.of(),
                List.of("tactic:jvm", "tactic:other"),
                List.of(),
                MutabilityPolicySource.empty(),
                List.of(),
                List.of(),
                Map.of("tactic:jvm", List.of("java"), "tactic:other", List.of("go")));
    }

    @Test
    void anArtifactAddedLeavesTheLanguagesOfTheOthers() {
        PackCompositionFacts after =
                WriteFacts.withArtifact(facts(), Urn.parse("tactic:new").orElseThrow());

        assertThat(after.languages()).containsEntry("tactic:jvm", List.of("java"));
    }

    @Test
    void registeredEdgesReplacedLeaveTheLanguages() {
        PackCompositionFacts after = WriteFacts.withRegisteredEdges(facts(), List.of(), List.of());

        assertThat(after.languages()).containsKeys("tactic:jvm", "tactic:other");
    }

    @Test
    void anArtifactRemovedTakesItsLanguagesWithIt() {
        PackCompositionFacts after = WriteFacts.withoutArtifact(facts(), TACTIC, List.of());

        assertThat(after.languages()).doesNotContainKey("tactic:jvm").containsKey("tactic:other");
    }
}
