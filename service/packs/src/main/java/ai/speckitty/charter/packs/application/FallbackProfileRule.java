package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Objects;
import java.util.Optional;

/**
 * The fallback-profile rule of ADR-0068, judged over the declaring pack's own closure: the profile a pack names in
 * {@code fallback_profile} is held by a pack of that closure (decision 2), is {@code locked} along it (decision 3), and
 * declares the reserved role {@value RoleBearer#GENERIC} and no alt-roles (decision 4), and is agent-performed (ruling
 * D4: a fallback serves every role nothing else fits, so a human never stands in as one). The rule is data-blind
 * beyond these facts: the service never holds fallback content.
 */
final class FallbackProfileRule {

    /**
     * A breach of the rule.
     *
     * @param code the refusal code
     * @param detail what is wrong, naming the pack and the profile
     */
    record Breach(RefusalCode code, String detail) {
        Breach {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** Reads the agent profile of an id from the pack that holds the version in force. */
    @FunctionalInterface
    interface Profiles {
        /**
         * The profile a pack holds.
         *
         * @param holder the pack that holds the winning version
         * @param id the profile's id
         * @return the entity, when the pack holds and can read it
         */
        Optional<CharterArtifact> read(PackIdentifier holder, String id);
    }

    private FallbackProfileRule() {}

    /**
     * Judges one pack's declaration.
     *
     * @param pack the declaring pack's name, for the detail
     * @param profileId the profile-id the pack names
     * @param closure the declaring pack's own closure
     * @param profiles where the winning profile's entity is read
     * @return the first breach, in the order unknown, not locked, not role-neutral, not agent-performed; empty when the
     *     declaration holds
     */
    static Optional<Breach> check(String pack, String profileId, ClosureView closure, Profiles profiles) {
        String named = "the fallback_profile " + profileId + " of pack " + pack;
        Optional<RoleBearer> held = heldProfile(profileId, closure, profiles);
        if (held.isEmpty()) {
            return Optional.of(new Breach(
                    RefusalCode.FALLBACK_PROFILE_UNKNOWN,
                    named + " names no agent profile the pack or a pack it depends on holds"));
        }
        RoleBearer profile = held.get();
        if (!closure.isLocked(new Urn(NodeKind.of(ArtifactKind.AGENT_PROFILE), profileId))) {
            return Optional.of(new Breach(
                    RefusalCode.FALLBACK_PROFILE_NOT_LOCKED,
                    named + " is not locked by any pack of its closure; a fallback profile is locked"));
        }
        if (!RoleBearer.GENERIC.equals(profile.role()) || !profile.altRoles().isEmpty()) {
            return Optional.of(new Breach(
                    RefusalCode.FALLBACK_PROFILE_NOT_ROLE_NEUTRAL,
                    named + " declares role " + profile.role()
                            + (profile.altRoles().isEmpty() ? "" : " and alt-roles " + profile.altRoles())
                            + "; a fallback profile declares role " + RoleBearer.GENERIC
                            + " and no alt-roles"));
        }
        if (profile.humanPerformed()) {
            return Optional.of(new Breach(
                    RefusalCode.FALLBACK_PROFILE_NOT_AGENT,
                    named + " is human-performed; a fallback profile is agent-performed"));
        }
        return Optional.empty();
    }

    /**
     * The agent profile of an id in force along a closure: held by a pack of the closure, read from the pack that holds
     * the winning version. Every rule that names a profile of the closure finds it here.
     *
     * @param profileId the profile's id
     * @param closure the closure
     * @param profiles where the winning profile's entity is read
     * @return the profile; empty when no pack of the closure holds a readable agent profile of that id
     */
    static Optional<RoleBearer> heldProfile(String profileId, ClosureView closure, Profiles profiles) {
        Optional<CharterArtifact> held = closure.holds(ArtifactKind.AGENT_PROFILE, profileId)
                ? closure.winnerPackOf(ArtifactKind.AGENT_PROFILE, profileId)
                        .flatMap(holder -> profiles.read(holder, profileId))
                : Optional.empty();
        return held.filter(RoleBearer.class::isInstance).map(RoleBearer.class::cast);
    }
}
