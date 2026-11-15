package ai.speckitty.charter.packs.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The role pins one pack declares in its policy file (ADR-0030, ADR-0032 decision 4, ruling D3): each pinned role
 * with the profile-id it is fixed to, in authored order. Pins are tighten-only along a closure: two packs of one
 * closure that pin one role to different profiles conflict, whatever their derivation. Whether the pinned profile is
 * held and fits the role is judged over the declaring pack's closure, not here.
 *
 * @param byRole the profile-id of each pinned role, in authored order
 */
public record RolePins(Map<String, String> byRole) {

    /** No role pinned. */
    public static final RolePins NONE = new RolePins(Map.of());

    /** The role token grammar, as the agent profile's {@code role} declares it. */
    private static final Pattern ROLE = Pattern.compile("^[a-z][a-z0-9-]{0,63}$");

    /** The identifier grammar of a profile-id. */
    private static final Pattern PROFILE_ID = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

    /** The role reserved for a fallback profile, which fits no role and so is never pinned (ADR-0068). */
    private static final String GENERIC = "generic";

    /**
     * Checks every pin and keeps the authored order.
     *
     * @param byRole the profile-id of each pinned role
     * @throws IllegalArgumentException when a role is outside the role grammar or is {@code generic}, or a profile-id
     *     is outside the identifier grammar
     */
    public RolePins {
        Objects.requireNonNull(byRole, "byRole");
        Map<String, String> copy = new LinkedHashMap<>();
        byRole.forEach((role, profileId) -> {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(profileId, "profileId");
            if (!ROLE.matcher(role).matches() || GENERIC.equals(role)) {
                throw new IllegalArgumentException("'" + role + "' is no role a pin may name");
            }
            if (!PROFILE_ID.matcher(profileId).matches()) {
                throw new IllegalArgumentException("'" + profileId + "' is no profile-id");
            }
            copy.put(role, profileId);
        });
        byRole = Collections.unmodifiableMap(copy);
    }

    /**
     * The profile a role is pinned to.
     *
     * @param role the role token
     * @return the pinned profile-id; empty when the pack pins no profile for the role
     */
    public Optional<String> pinOf(String role) {
        return Optional.ofNullable(byRole.get(Objects.requireNonNull(role, "role")));
    }

    /**
     * Whether the pack pins no role.
     *
     * @return {@code true} when no role is pinned
     */
    public boolean isEmpty() {
        return byRole.isEmpty();
    }
}
