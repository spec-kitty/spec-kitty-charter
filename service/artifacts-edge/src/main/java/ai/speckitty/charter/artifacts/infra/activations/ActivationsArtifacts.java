package ai.speckitty.charter.artifacts.infra.activations;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.application.EffectiveHeldWinner;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link ActivationArtifacts} over the activations slice: the one class of the artifacts edge that asks it, and only
 * its application ring ({@link EffectiveReadService}) and its domain values, as the missions edge asks it for a mission
 * type's winner. The activations slice reads the winner, its pack and level and the source fingerprint from one
 * session; this adapter maps them to the edge's own values, so nothing above it names an activations type. It decides
 * nothing: the refusals, the {@code 304} and the order of the judgements are the service's, unchanged.
 */
@Component
public final class ActivationsArtifacts implements ActivationArtifacts {

    private final EffectiveReadService effective;

    /**
     * An adapter over the activations slice's effective reads.
     *
     * @param effective the reads of what an activation puts in force
     */
    @Autowired
    public ActivationsArtifacts(EffectiveReadService effective) {
        this.effective = Objects.requireNonNull(effective, "effective");
    }

    @Override
    public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
        return outcome(
                effective.heldWinners(new ActivationId(activationId), kind, ifNoneMatch),
                held -> new Winners(
                        scope(held.stored(), held.governanceFingerprint(), held.sourceFingerprint()),
                        held.winners().stream().map(ActivationsArtifacts::held).toList()));
    }

    @Override
    public Outcome<Winner> asset(String activationId, String assetId, @Nullable String ifNoneMatch) {
        return outcome(
                effective.heldWinner(new ActivationId(activationId), ArtifactKind.ASSET, assetId, ifNoneMatch),
                ActivationsArtifacts::winner);
    }

    @Override
    public Outcome<Winner> assetPayload(String activationId, String assetId, @Nullable String ifNoneMatch) {
        return outcome(
                effective.heldPayload(new ActivationId(activationId), assetId, ifNoneMatch),
                ActivationsArtifacts::winner);
    }

    @Override
    public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
        return outcome(
                effective.artifact(new ActivationId(activationId), ArtifactKind.SKILL, skillId, 0, ifNoneMatch),
                answer -> new SkillWinner(
                        scope(answer.stored(), answer.governanceFingerprint(), answer.sourceFingerprint()),
                        facts(answer.winner()),
                        answer.artifact()));
    }

    @Override
    public Referrer referrer(String activationId, String kindToken, String artifactId) {
        EffectiveReadService.ListedWinner listed =
                effective.listedWinner(new ActivationId(activationId), kindToken, artifactId);
        EffectiveHeldWinner winner = listed.winner();
        return new Referrer(
                scope(listed.stored(), listed.governanceFingerprint(), listed.sourceFingerprint()),
                winner.ref().kind(),
                held(winner),
                listed.held().stream().map(ActivationsArtifacts::urn).collect(Collectors.toUnmodifiableSet()));
    }

    private static <T, R> Outcome<R> outcome(Read<T> read, Function<T, R> found) {
        return switch (read) {
            case Read.NotModified<T> notModified -> new Outcome.NotModified<>(notModified.etag());
            case Read.Found<T> answered -> new Outcome.Found<>(found.apply(answered.value()), answered.etag());
        };
    }

    private static Winner winner(EffectiveReadService.HeldAnswer answer) {
        return new Winner(
                scope(answer.stored(), answer.governanceFingerprint(), answer.sourceFingerprint()),
                held(answer.winner()));
    }

    private static Scope scope(StoredActivation stored, String governanceFingerprint, String sourceFingerprint) {
        return new Scope(stored.id().value(), stored.revision().token(), governanceFingerprint, sourceFingerprint);
    }

    private static Held held(EffectiveHeldWinner winner) {
        return new Held(
                winner.ref().id(),
                new PackIdentifier(winner.packId()),
                winner.level(),
                winner.policy().token());
    }

    private static Facts facts(EffectiveArtifact artifact) {
        return new Facts(
                new Held(
                        artifact.ref().id(),
                        new PackIdentifier(artifact.packId()),
                        artifact.level(),
                        artifact.policy().token()),
                artifact.state().token(),
                artifact.required(),
                artifact.reason().token(),
                artifact.satisfiedBy().map(ActivationsArtifacts::urn),
                artifact.suggestion().map(words -> new Words(words.when(), words.reason())));
    }

    private static Urn urn(ArtifactRef ref) {
        return new Urn(NodeKind.of(ref.kind()), ref.id());
    }
}
