package ai.speckitty.charter.artifacts.infra.activations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.application.EffectiveHeldWinner;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.ContextsInForce;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The adapter asks the activations slice for the contexts in force under the tag of the vocabulary read it serves, and
 * maps the answer to the edge's own values unchanged; a {@code 304} passes through.
 */
class ActivationsArtifactsVocabularyTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final StoredActivation STORED = new StoredActivation(
            ID,
            ActivationDocument.builder().build().document(),
            ActivationDocument.builder().build().document().revision());

    private final EffectiveReadService effective = mock(EffectiveReadService.class);

    @ParameterizedTest
    @CsvSource({"TERMS, VOCABULARY_TERMS", "CONTEXTIVE, VOCABULARY_CONTEXTIVE", "TABOOS, VOCABULARY_TABOOS"})
    void eachVocabularyReadIsAskedUnderItsOwnTagAndTheAnswerIsMappedUnchanged(
            ContextsInForce.Vocabulary vocabulary, EffectiveTag.Read read) {
        EffectiveHeldWinner winner = new EffectiveHeldWinner(
                new ArtifactRef(ArtifactKind.GLOSSARY, "spec-kitty-core"), "proj", PackLevel.PROJECT, Policy.LOCKED);
        when(effective.contextsInForce(ID, read, "\"x\""))
                .thenReturn(new Read.Found<>(
                        new EffectiveReadService.HeldWinners(
                                STORED, "sha256:source", "sha256:closure", List.of(winner)),
                        "sha256:tag"));

        Outcome<ContextsInForce> outcome =
                new ActivationsArtifacts(effective).contextsInForce("a-1", vocabulary, "\"x\"");

        assertThat(outcome)
                .isEqualTo(new Outcome.Found<>(
                        new ContextsInForce(
                                new Scope("a-1", STORED.revision().token(), "sha256:closure", "sha256:source"),
                                List.of(new Held(
                                        "spec-kitty-core", new PackIdentifier("proj"), PackLevel.PROJECT, "locked"))),
                        "sha256:tag"));
    }

    @Test
    void aNotModifiedPassesThrough() {
        when(effective.contextsInForce(ID, EffectiveTag.Read.VOCABULARY_TABOOS, "*"))
                .thenReturn(new Read.NotModified<>("sha256:tag"));

        assertThat(new ActivationsArtifacts(effective).contextsInForce("a-1", ContextsInForce.Vocabulary.TABOOS, "*"))
                .isEqualTo(new Outcome.NotModified<ContextsInForce>("sha256:tag"));
    }
}
