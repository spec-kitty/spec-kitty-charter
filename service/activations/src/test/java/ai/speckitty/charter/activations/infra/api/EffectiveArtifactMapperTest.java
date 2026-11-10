package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.application.EffectiveArtifactAnswer;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSet.Suggestion;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.api.model.EffectiveArtifactRead;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/** The wire form of the read of one artifact: the facts of the winner, and links that follow the effective reads' style. */
class EffectiveArtifactMapperTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private final EffectiveArtifactMapper mapper = new EffectiveArtifactMapper("/api/v1/");
    private final StoredActivation stored = new InMemoryActivationRepository()
            .create(ID, ActivationDocument.builder().build().document())
            .orElseThrow();

    private EffectiveArtifactAnswer answer(EffectiveArtifact winner) {
        CharterArtifact entity = new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return winner.kind();
            }

            @Override
            public PackIdentifier packId() {
                return new PackIdentifier(winner.packId());
            }

            @Override
            public String localId() {
                return winner.artifactId();
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return winner.artifactId();
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
        return new EffectiveArtifactAnswer(stored, "sha256:source", "sha256:closure", winner, entity, Optional.empty());
    }

    private static EffectiveArtifact winner(
            ArtifactKind kind, String id, String pack, Optional<ArtifactRef> satisfiedBy, Optional<Suggestion> hint) {
        return new EffectiveArtifact(
                new ArtifactRef(kind, id),
                pack,
                PackLevel.PROJECT,
                State.INACTIVE,
                true,
                satisfiedBy,
                Policy.LOCKED,
                Reason.OVERRIDDEN,
                hint);
    }

    @Test
    void everyFactOfTheWinnerIsCopiedAndTheFingerprintsAreTheAnswers() {
        EffectiveArtifactRead wire = mapper.toRead(
                answer(winner(ArtifactKind.TACTIC, "t", "proj", Optional.empty(), Optional.empty())),
                OptionalInt.empty());

        assertThat(wire.getActivationId()).isEqualTo("a-1");
        assertThat(wire.getRevision()).isEqualTo(stored.revision().token());
        assertThat(wire.getGovernanceFingerprint()).isEqualTo("sha256:closure");
        assertThat(wire.getKind()).isEqualTo(EffectiveArtifactRead.KindEnum.TACTIC);
        assertThat(wire.getArtifactId()).isEqualTo("t");
        assertThat(wire.getPackId()).isEqualTo("proj");
        assertThat(wire.getLevel()).isEqualTo(EffectiveArtifactRead.LevelEnum.PROJECT);
        assertThat(wire.getState()).isEqualTo(EffectiveArtifactRead.StateEnum.INACTIVE);
        assertThat(wire.getRequired()).isTrue();
        assertThat(wire.getPolicy()).isEqualTo(EffectiveArtifactRead.PolicyEnum.LOCKED);
        assertThat(wire.getReason()).isEqualTo(EffectiveArtifactRead.ReasonEnum.OVERRIDDEN);
        assertThat(wire.getSatisfiedBy()).isNull();
        assertThat(wire.getSuggestion()).isNull();
    }

    @Test
    void theSatisfierAndTheSuggestionAreCarriedWhenTheWinnerHasThem() {
        EffectiveArtifactRead wire = mapper.toRead(
                answer(winner(
                        ArtifactKind.DIRECTIVE,
                        "D",
                        "main",
                        Optional.of(new ArtifactRef(ArtifactKind.DIRECTIVE, "E")),
                        Optional.of(new Suggestion(Optional.of("when"), Optional.empty())))),
                OptionalInt.empty());

        assertThat(wire.getSatisfiedBy().getArtifactId()).isEqualTo("E");
        assertThat(wire.getSuggestion().getWhen()).isEqualTo("when");
        assertThat(wire.getSuggestion().getReason()).isNull();

        EffectiveArtifactRead both = mapper.toRead(
                answer(winner(
                        ArtifactKind.DIRECTIVE,
                        "D",
                        "main",
                        Optional.empty(),
                        Optional.of(new Suggestion(Optional.empty(), Optional.of("why"))))),
                OptionalInt.empty());
        assertThat(both.getSuggestion().getWhen()).isNull();
        assertThat(both.getSuggestion().getReason()).isEqualTo("why");
    }

    @Test
    void theLinksAreTheRequestThePackScopedReadOfTheWinnersPackAndTheActivation() {
        EffectiveArtifactRead wire = mapper.toRead(
                answer(winner(ArtifactKind.AGENT_PROFILE, "java jenny", "my pack", Optional.empty(), Optional.empty())),
                OptionalInt.empty());

        assertThat(wire.getLinks().keySet()).containsExactly("self", "artifact", "up");
        assertThat(wire.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/agent_profile/java%20jenny");
        assertThat(wire.getLinks().get("artifact").getHref())
                .as("the winner's pack, not the first pack of the activation")
                .isEqualTo("/api/v1/packs/my%20pack/artifacts/agent_profile/java%20jenny");
        assertThat(wire.getLinks().get("up").getHref()).isEqualTo("/api/v1/activations/a-1");
    }

    @Test
    void theSelfLinkRepeatsTheDepthTheRequestSent() {
        EffectiveArtifact winner = winner(ArtifactKind.DIRECTIVE, "D", "main", Optional.empty(), Optional.empty());

        assertThat(mapper.toRead(answer(winner), OptionalInt.of(1))
                        .getLinks()
                        .get("self")
                        .getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/D?depth=1");
        assertThat(mapper.toRead(answer(winner), OptionalInt.of(0))
                        .getLinks()
                        .get("self")
                        .getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/D?depth=0");
        assertThat(mapper.toRead(answer(winner), OptionalInt.empty())
                        .getLinks()
                        .get("self")
                        .getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/D");
    }

    @Test
    void theNativeExecutableCanWriteTheAnswer() {
        RuntimeHints hints = new RuntimeHints();

        new ActivationRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(EffectiveArtifactRead.class))
                .accepts(hints);
    }
}
