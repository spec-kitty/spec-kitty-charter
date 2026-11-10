package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The pack-scoped read of one agent profile (FR-002, FR-003): the profile as its pack stores it, with its governance.
 * It owns the read's rules and nothing of where the data comes from ({@link ProfileSource}): the depth of the walk is 0
 * or 1, as the action context's is, and the answer is the profile that was asked for.
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

    /**
     * A service over the profile source.
     *
     * @param source where a pack's profile and governance are read from
     */
    @Autowired
    public ProfileReadService(ProfileSource source) {
        this.source = Objects.requireNonNull(source, "source");
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
}
