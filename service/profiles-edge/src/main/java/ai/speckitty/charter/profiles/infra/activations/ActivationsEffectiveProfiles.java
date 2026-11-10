package ai.speckitty.charter.profiles.infra.activations;

import ai.speckitty.charter.activations.application.ActivationService.Read;
import ai.speckitty.charter.activations.application.EffectiveArtifactAnswer;
import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.GovernanceFacts;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.profiles.application.EffectiveProfileRead;
import ai.speckitty.charter.profiles.application.EffectiveProfiles;
import ai.speckitty.charter.profiles.application.GovernanceEntry;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link EffectiveProfiles} over the activations slice: the one class of the profiles edge that asks it, and only its
 * application ring ({@link EffectiveReadService}) and its domain values. The activations slice reads the winner of the
 * profile, its state and its governance in one session, once, and this adapter narrows the entity to the slice's own
 * {@link AgentProfile} and maps the facts and the governance to the slice's own {@link EffectiveProfileRead}, so nothing
 * above it names an activations type. It decides nothing: the refusals, the {@code 304} and the order of the judgements
 * are the service's, unchanged.
 *
 * <p>The activations slice carries the artifacts in force that the walk lists apart from the artifacts out of force that
 * a listed artifact suggests. The wire lists them together, so they are merged here in URN order; a suggestion is a
 * {@code suggests} entry of its suggesting artifact.
 */
@Component
public final class ActivationsEffectiveProfiles implements EffectiveProfiles {

    private final EffectiveReadService effective;

    /**
     * An adapter over the activations slice's effective reads.
     *
     * @param effective the read of one artifact under an activation
     */
    @Autowired
    public ActivationsEffectiveProfiles(EffectiveReadService effective) {
        this.effective = Objects.requireNonNull(effective, "effective");
    }

    @Override
    public Outcome read(String activationId, String profileId, int depth, @Nullable String ifNoneMatch) {
        Read<EffectiveArtifactAnswer> read = effective.artifact(
                new ActivationId(activationId), ArtifactKind.AGENT_PROFILE, profileId, depth, ifNoneMatch);
        return switch (read) {
            case Read.NotModified<EffectiveArtifactAnswer> notModified -> new Outcome.NotModified(notModified.etag());
            case Read.Found<EffectiveArtifactAnswer> found -> new Outcome.Found(read(found.value()), found.etag());
        };
    }

    private EffectiveProfileRead read(EffectiveArtifactAnswer answer) {
        // The read of an agent profile answers the governance of a profile and an entity of the kind it was asked for.
        EffectiveArtifactAnswer.Governance governance = answer.governance().orElseThrow();
        return new EffectiveProfileRead(
                AgentProfile.class.cast(answer.artifact()),
                answer.stored().id().value(),
                answer.stored().revision().token(),
                answer.governanceFingerprint(),
                answer.sourceFingerprint(),
                facts(answer.winner()),
                entries(governance),
                effective.listedKinds());
    }

    private static List<EffectiveProfileRead.Entry> entries(EffectiveArtifactAnswer.Governance governance) {
        List<EffectiveProfileRead.Entry> entries = new ArrayList<>();
        for (EffectiveArtifactAnswer.Entry entry : governance.entries()) {
            entries.add(new EffectiveProfileRead.Entry(
                    facts(entry.artifact()), step(entry.step()), urn(entry.via()), entry.hops()));
        }
        for (EffectiveArtifactAnswer.Suggested suggested : governance.suggested()) {
            entries.add(new EffectiveProfileRead.Entry(
                    facts(suggested.artifact()),
                    GovernanceEntry.Step.SUGGESTS,
                    urn(suggested.via()),
                    suggested.hops()));
        }
        entries.sort(Comparator.comparing(entry -> entry.facts().urn()));
        return entries;
    }

    private static EffectiveProfileRead.Facts facts(EffectiveArtifact artifact) {
        return new EffectiveProfileRead.Facts(
                urn(artifact.ref()),
                new PackIdentifier(artifact.packId()),
                artifact.level(),
                artifact.state().token(),
                artifact.required(),
                artifact.policy().token(),
                artifact.reason().token(),
                artifact.satisfiedBy().map(ActivationsEffectiveProfiles::urn),
                artifact.suggestion()
                        .map(suggestion -> new GovernanceEntry.Suggestion(suggestion.when(), suggestion.reason())));
    }

    private static Urn urn(ArtifactRef ref) {
        return new Urn(NodeKind.of(ref.kind()), ref.id());
    }

    private static GovernanceEntry.Step step(GovernanceFacts.Step step) {
        return switch (step) {
            case LINEAGE -> GovernanceEntry.Step.LINEAGE;
            case REQUIRES -> GovernanceEntry.Step.REQUIRES;
            case SUGGESTS -> GovernanceEntry.Step.SUGGESTS;
            case REQUIRES_OF_SUGGESTED -> GovernanceEntry.Step.REQUIRES_OF_SUGGESTED;
            case VOCABULARY -> GovernanceEntry.Step.VOCABULARY;
        };
    }
}
