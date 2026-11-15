package ai.speckitty.charter.profiles.infra.activations;

import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.application.ProfileCandidates;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.profiles.application.SelectionCandidates;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.selection.Candidates;
import ai.speckitty.charter.profiles.domain.selection.SelectionCandidate;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link SelectionCandidates} over the activations slice: it asks {@link EffectiveReadService#profileSelection}
 * for the candidates of a role, the pin and the fallback in force, and narrows each entity to the slice's own
 * {@link AgentProfile}. Each candidate keeps the pack the activation names as its holder, the winner's pack, never a
 * default. It decides nothing: the refusals, their order and the tag are the activations slice's, unchanged, and so is
 * the role a step names ({@link EffectiveReadService#stepProfileSelection}).
 */
@Component
public final class ActivationsSelectionCandidates implements SelectionCandidates {

    private final EffectiveReadService effective;

    /**
     * An adapter over the activations slice's effective reads.
     *
     * @param effective the reads under an activation
     */
    @Autowired
    public ActivationsSelectionCandidates(EffectiveReadService effective) {
        this.effective = Objects.requireNonNull(effective, "effective");
    }

    @Override
    public Read read(String activationId, String role, SelectionContext context) {
        EffectiveReadService.SelectionRead read = effective.profileSelection(
                new ActivationId(activationId),
                new EffectiveTag.SelectionQuery(role, context.language(), context.framework()));
        return readOf(read);
    }

    @Override
    public StepRead readForStep(String activationId, String missionType, String action, SelectionContext context) {
        EffectiveReadService.SelectionRead read = effective.stepProfileSelection(
                new ActivationId(activationId),
                new EffectiveTag.StepSelectionQuery(missionType, action, context.language(), context.framework()));
        return new StepRead(read.role(), readOf(read));
    }

    private static Read readOf(EffectiveReadService.SelectionRead read) {
        return new Read(
                read.stored().id().value(), candidates(read.candidates()), read.sourceFingerprint(), read.etag());
    }

    /** The domain's candidates for the activation's: the profiles, the pin with its specializations and the fallback. */
    static Candidates candidates(ProfileCandidates read) {
        Optional<String> pinned = switch (read.pin()) {
            case ProfileCandidates.Pin.Pinned pin -> Optional.of(pin.profileId());
            case ProfileCandidates.Pin.None _ -> Optional.empty();
            case ProfileCandidates.Pin.Conflict conflict ->
                throw new IllegalStateException("The activations slice answered a role pin conflict among the packs "
                        + conflict.packs() + " instead of refusing it.");
        };
        Optional<SelectionCandidate> fallback = switch (read.fallback()) {
            case ProfileCandidates.Fallback.Held held -> Optional.of(candidate(held.profile()));
            case ProfileCandidates.Fallback.None _ -> Optional.empty();
            case ProfileCandidates.Fallback.Conflict conflict ->
                throw new IllegalStateException("The activations slice answered a fallback conflict among the packs "
                        + conflict.packs() + " instead of refusing it.");
        };
        return new Candidates(
                read.candidates().stream()
                        .map(ActivationsSelectionCandidates::candidate)
                        .toList(),
                pinned,
                pinned.isPresent() ? read.pinnedSpecializations() : Set.of(),
                fallback);
    }

    private static SelectionCandidate candidate(ProfileCandidates.Candidate candidate) {
        return new SelectionCandidate(
                AgentProfile.class.cast(candidate.profile()),
                candidate.pack(),
                candidate.closurePosition(),
                candidate.depth());
    }
}
