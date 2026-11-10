package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The reads of one agent profile: the pack-scoped one (FR-002, FR-003), the profile as its pack stores it, with its
 * governance, and the one under an activation (FR-007), the winner of the layer merge with its state and the governance
 * with the state of each entry. It owns the reads' rules and nothing of where the data comes from ({@link ProfileSource},
 * {@link EffectiveProfiles}): the depth of the walk is 0 or 1, as the action context's is, and the answer is the profile
 * that was asked for.
 *
 * <p>The refusals are the source's, unchanged: an unknown pack is {@code pack_not_found}, a profile the pack does not
 * hold {@code artifact_not_found}, a closure that cannot compose or merge its composition code, and a change of the
 * inputs during the read {@code stale_input}.
 */
@Service
public final class ProfileReadService {

    /** The most {@code suggests} hops a governance walk follows, as the action context's walk follows at most. */
    public static final int MAX_DEPTH = 1;

    private final ProfileSource source;
    private final EffectiveProfiles activations;

    /**
     * A service over the profile source and the activations port.
     *
     * @param source where a pack's profile and governance are read from
     * @param activations where a profile under an activation, with its governance, is read from
     */
    @Autowired
    public ProfileReadService(ProfileSource source, EffectiveProfiles activations) {
        this.source = Objects.requireNonNull(source, "source");
        this.activations = Objects.requireNonNull(activations, "activations");
    }

    /**
     * One agent profile of a pack with its governance.
     *
     * @param pack the pack's identity, as the request names it
     * @param profileId the profile's id
     * @param depth the number of {@code suggests} hops of the governance walk, 0 or {@value #MAX_DEPTH}
     * @return the profile, its revision, the verified fingerprint and its governance
     * @throws IllegalArgumentException when {@code depth} is outside 0 to {@value #MAX_DEPTH}; nothing is read. The
     *     REST edge refuses such a request before it gets here (a 400 without a code), so this is a caller's fault
     * @throws CharterRefusal as the source refuses
     * @throws IllegalStateException when the source answers another profile than the one asked for
     */
    public ProfileRead read(PackIdentifier pack, String profileId, int depth) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(profileId, "profileId");
        if (depth < 0 || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("depth is 0 to " + MAX_DEPTH + ": " + depth);
        }
        ProfileRead read = source.read(pack, profileId, depth);
        if (!read.profile().localId().equals(profileId)) {
            throw new IllegalStateException("asked for the profile " + profileId + " and answered "
                    + read.profile().localId());
        }
        return read;
    }

    /**
     * One agent profile under an activation: the winner of the layer merge with the facts the activation gives it, and its
     * governance with the state of each entry.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param profileId the profile's id, exactly as declared
     * @param depth the number of {@code suggests} hops of the governance walk, 0 or {@value #MAX_DEPTH}
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the profile read, or that the client holds the current representation
     * @throws IllegalArgumentException when {@code depth} is outside 0 to {@value #MAX_DEPTH}; nothing is read. The
     *     REST edge refuses such a request before it gets here (a 400 without a code), so this is a caller's fault
     * @throws CharterRefusal as the port refuses: an unknown activation, a profile no pack of the closure holds, a pack
     *     that cannot be read, a closure that cannot compose or merge, and a change of the inputs during the read
     * @throws IllegalStateException when the port answers another profile or activation than the one asked for
     */
    public EffectiveProfiles.Outcome underActivation(
            String activationId, String profileId, int depth, @Nullable String ifNoneMatch) {
        Objects.requireNonNull(activationId, "activationId");
        Objects.requireNonNull(profileId, "profileId");
        if (depth < 0 || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("depth is 0 to " + MAX_DEPTH + ": " + depth);
        }
        EffectiveProfiles.Outcome outcome = activations.read(activationId, profileId, depth, ifNoneMatch);
        if (outcome instanceof EffectiveProfiles.Outcome.Found found) {
            if (!found.read().profile().localId().equals(profileId)) {
                throw new IllegalStateException("asked for the profile " + profileId + " and answered "
                        + found.read().profile().localId());
            }
            if (!found.read().activationId().equals(activationId)) {
                throw new IllegalStateException("asked for the activation " + activationId + " and answered "
                        + found.read().activationId());
            }
        }
        return outcome;
    }
}
