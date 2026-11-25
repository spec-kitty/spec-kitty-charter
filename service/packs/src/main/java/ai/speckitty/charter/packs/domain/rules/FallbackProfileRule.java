package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.packs.domain.ClosureFacts;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackOrigins;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The fallback-profile rule of ADR-0068, judged over the declaring pack's own closure: the profile a pack names in
 * {@code fallback_profile} is held by a pack of that closure (decision 2), is {@code locked} along it (decision 3), and
 * declares the reserved role {@value RoleBearer#GENERIC} and no alt-roles (decision 4), and is agent-performed (ruling
 * D4: a fallback serves every role nothing else fits, so a human never stands in as one). The rule is data-blind
 * beyond these facts: the service never holds fallback content. {@link PackRulesets#ownClosure} hands it out, and the
 * {@link ai.speckitty.charter.packs.domain.ValidationWalker} runs it (#556, #638).
 */
final class FallbackProfileRule {

    private FallbackProfileRule() {}

    /**
     * Judges one pack's declaration, every breach (#556, FR-005).
     *
     * @param pack the declaring pack's configured identifier: the detail names it, and each breach is about it and the
     *     profile it names
     * @param profileId the profile-id the pack names
     * @param closure the declaring pack's own closure
     * @param profiles where the winning profile's entity is read
     * @return {@code fallback_profile_unknown} alone when no pack of the closure holds the profile, since nothing more
     *     can be judged of it; otherwise every one of not locked, not role-neutral and not agent-performed that holds,
     * in     that order; empty when the declaration holds
     */
    static List<ClosureFault> check(
            PackIdentifier pack, String profileId, ClosureFacts closure, ProfileReader profiles) {
        String named = "the fallback_profile " + profileId + " of pack " + pack.value();
        Origin origin = profileOrigin(pack, profileId);
        Optional<RoleBearer> held = heldProfile(profileId, closure, profiles);
        if (held.isEmpty()) {
            return List.of(ClosureFault.of(
                    origin,
                    RefusalCode.FALLBACK_PROFILE_UNKNOWN,
                    named + " names no agent profile the pack or a pack it depends on holds",
                    List.of()));
        }
        RoleBearer profile = held.get();
        List<ClosureFault> breaches = new ArrayList<>();
        if (!closure.isLocked(new Urn(NodeKind.of(ArtifactKind.AGENT_PROFILE), profileId))) {
            breaches.add(ClosureFault.of(
                    origin,
                    RefusalCode.FALLBACK_PROFILE_NOT_LOCKED,
                    named + " is not locked by any pack of its closure; a fallback profile is locked",
                    List.of()));
        }
        if (!RoleBearer.GENERIC.equals(profile.role()) || !profile.altRoles().isEmpty()) {
            breaches.add(ClosureFault.of(
                    origin,
                    RefusalCode.FALLBACK_PROFILE_NOT_ROLE_NEUTRAL,
                    named + " declares role " + profile.role()
                            + (profile.altRoles().isEmpty() ? "" : " and alt-roles " + profile.altRoles())
                            + "; a fallback profile declares role " + RoleBearer.GENERIC
                            + " and no alt-roles",
                    List.of()));
        }
        if (profile.humanPerformed()) {
            breaches.add(ClosureFault.of(
                    origin,
                    RefusalCode.FALLBACK_PROFILE_NOT_AGENT,
                    named + " is human-performed; a fallback profile is agent-performed",
                    List.of()));
        }
        return List.copyOf(breaches);
    }

    /**
     * What a breach of a profile a pack names is about (R-o): the pack and the profile's URN, or the pack alone when
     * the id it names is no URN id.
     *
     * @param pack the pack that names the profile
     * @param profileId the profile's id, as the pack names it
     * @return the origin
     */
    static Origin profileOrigin(PackIdentifier pack, String profileId) {
        return profileId.isEmpty() || profileId.contains("::")
                ? PackOrigins.of(pack)
                : PackOrigins.of(pack, new Urn(NodeKind.of(ArtifactKind.AGENT_PROFILE), profileId));
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
    static Optional<RoleBearer> heldProfile(String profileId, ClosureFacts closure, ProfileReader profiles) {
        Optional<CharterArtifact> held = closure.holds(ArtifactKind.AGENT_PROFILE, profileId)
                ? closure.winnerPackOf(ArtifactKind.AGENT_PROFILE, profileId)
                        .flatMap(holder -> profiles.read(holder, profileId))
                : Optional.empty();
        return held.filter(RoleBearer.class::isInstance).map(RoleBearer.class::cast);
    }
}
