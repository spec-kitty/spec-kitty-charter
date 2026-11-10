package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import java.util.List;
import java.util.Optional;

/** The profiles and entries the edge tests read, built the way a pack's storage builds them. */
public final class ProfileFixtures {

    /** A port for tests of the pack-scoped read: asked, it is a fault, because no such test reads under an activation. */
    public static final EffectiveProfiles NO_ACTIVATIONS = (activationId, profileId, depth, ifNoneMatch) -> {
        throw new AssertionError("the pack-scoped read does not ask the activations port");
    };

    private ProfileFixtures() {}

    /**
     * A profile with the least the domain accepts.
     *
     * @param pack the pack that holds it
     * @param id the profile's id
     * @return the profile
     */
    public static AgentProfile leanProfile(String pack, String id) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(new PackIdentifier(pack))
                .name("Name of " + id)
                .role("implementer")
                .purpose("Purpose of " + id)
                .specialization(new Specialization("Focus", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
    }

    /**
     * A node URN.
     *
     * @param text for example {@code directive:D}
     * @return the URN
     */
    public static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    /**
     * A held entry the profile's {@code requires} edge lists, one hop away.
     *
     * @param urn the listed artifact
     * @param pack the pack that holds it
     * @return the entry
     */
    public static GovernanceEntry requires(String urn, String pack) {
        return new GovernanceEntry(
                urn(urn),
                GovernanceEntry.Step.REQUIRES,
                urn("agent_profile:P"),
                1,
                EdgeWords.NONE,
                Optional.of(new PackIdentifier(pack)),
                true);
    }

    /**
     * A read of a lean profile with the given entries and the generic route's usual kinds.
     *
     * @param entries the governance
     * @return the read
     */
    public static ProfileRead read(GovernanceEntry... entries) {
        return new ProfileRead(
                leanProfile("built-in", "P"),
                "sha256:" + "a".repeat(64),
                "sha256:" + "b".repeat(64),
                List.of(entries),
                List.of(ArtifactKind.DIRECTIVE, ArtifactKind.STYLEGUIDE));
    }

    /**
     * The facts of an artifact in force, as the activations slice states them for a plain selection.
     *
     * @param urn the artifact, for example {@code directive:D}
     * @param pack the pack that holds the version in force
     * @return active, selected, open, not required, no suggestion
     */
    public static EffectiveProfileRead.Facts active(String urn, String pack) {
        return new EffectiveProfileRead.Facts(
                urn(urn),
                new PackIdentifier(pack),
                PackLevel.ORG,
                "active",
                false,
                "open",
                "selected",
                Optional.empty(),
                Optional.empty());
    }

    /**
     * An entry the profile's {@code requires} edge lists, one hop away, in force.
     *
     * @param urn the listed artifact
     * @param pack the pack that holds the version in force
     * @return the entry
     */
    public static EffectiveProfileRead.Entry effectiveRequires(String urn, String pack) {
        return new EffectiveProfileRead.Entry(
                active(urn, pack), GovernanceEntry.Step.REQUIRES, urn("agent_profile:P"), 1, EdgeWords.NONE);
    }

    /**
     * A read of the lean profile {@code P} under the activation {@code a-1}, the directive and styleguide kinds linkable.
     *
     * @param entries the governance
     * @return the read
     */
    public static EffectiveProfileRead effectiveRead(EffectiveProfileRead.Entry... entries) {
        return new EffectiveProfileRead(
                leanProfile("main", "P"),
                "a-1",
                "sha256:" + "c".repeat(64),
                "sha256:" + "d".repeat(64),
                "sha256:" + "b".repeat(64),
                active("agent_profile:P", "main"),
                List.of(entries),
                List.of(ArtifactKind.DIRECTIVE, ArtifactKind.STYLEGUIDE));
    }
}
