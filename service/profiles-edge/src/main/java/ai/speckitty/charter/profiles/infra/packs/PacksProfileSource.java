package ai.speckitty.charter.profiles.infra.packs;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.application.GovernanceWalk;
import ai.speckitty.charter.packs.application.ProfileGovernance;
import ai.speckitty.charter.profiles.application.EdgeWords;
import ai.speckitty.charter.profiles.application.GovernanceEntry;
import ai.speckitty.charter.profiles.application.ProfileRead;
import ai.speckitty.charter.profiles.application.ProfileSource;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link ProfileSource} over the packs slice: the one class of the profiles edge that asks it, and only its
 * application ring ({@link ForeignArtifactRead}). The packs slice reads the profile, its pack's revision and its
 * pack-scoped governance in one verified read; this adapter narrows the entity to the slice's own
 * {@link AgentProfile} and maps the governance to the slice's own {@link GovernanceEntry}, so nothing above it names a
 * packs type.
 */
@Component
public final class PacksProfileSource implements ProfileSource {

    private final ForeignArtifactRead packs;

    /**
     * A source over the packs slice's read of an artifact another slice renders.
     *
     * @param packs the one verified read of an artifact, its revision and its governance
     */
    @Autowired
    public PacksProfileSource(ForeignArtifactRead packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    @Override
    public ProfileRead read(PackIdentifier pack, String profileId, int depth) {
        ConsistentRead.Verified<ForeignArtifactRead.Foreign> verified =
                packs.read(pack, ArtifactKind.AGENT_PROFILE, profileId, depth);
        ForeignArtifactRead.Foreign foreign = verified.value();
        // The read of an agent profile answers the governance of a profile and an entity of the kind it was asked for.
        ProfileGovernance.Governance governance = foreign.governance().orElseThrow();
        return new ProfileRead(
                AgentProfile.class.cast(foreign.artifact()),
                foreign.revision().token(),
                verified.fingerprint(),
                governance.entries().stream().map(PacksProfileSource::entryOf).toList(),
                foreign.servedKinds());
    }

    private static GovernanceEntry entryOf(ProfileGovernance.Entry entry) {
        GovernanceEntry.Step step = stepOf(entry.step());
        return new GovernanceEntry(
                entry.urn(),
                step,
                entry.via(),
                entry.hops(),
                new EdgeWords(entry.when(), entry.reason()),
                entry.holder().map(holder -> holder.packId()),
                entry.held());
    }

    private static GovernanceEntry.Step stepOf(GovernanceWalk.Step step) {
        return switch (step) {
            case LINEAGE -> GovernanceEntry.Step.LINEAGE;
            case REQUIRES -> GovernanceEntry.Step.REQUIRES;
            case SUGGESTS -> GovernanceEntry.Step.SUGGESTS;
            case REQUIRES_OF_SUGGESTED -> GovernanceEntry.Step.REQUIRES_OF_SUGGESTED;
            case VOCABULARY -> GovernanceEntry.Step.VOCABULARY;
        };
    }
}
