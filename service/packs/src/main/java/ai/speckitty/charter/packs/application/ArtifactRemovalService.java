package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** The {@code DELETE} of one artifact of a pack (IC-06, FR-005, FR-036). */
@Service
public final class ArtifactRemovalService {

    private final PackRemovalStore store;

    /**
     * A service over its ports.
     *
     * @param packs loads the pack
     * @param store locks and swaps
     * @param gate the mutability gate
     * @param inbound the inbound scan
     * @param views the stored packs, read through one session per removal
     * @param impact the impact check
     */
    @Autowired
    public ArtifactRemovalService(
            PackRepository packs,
            PackRemovalStore store,
            WritePolicyGate gate,
            InboundReferences inbound,
            StagedPackViews views,
            RegistrationImpact impact) {
        Objects.requireNonNull(packs, "packs");
        this.store = Objects.requireNonNull(store, "store");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(inbound, "inbound");
        Objects.requireNonNull(views, "views");
        Objects.requireNonNull(impact, "impact");
    }

    /**
     * Removes one artifact.
     *
     * @param pack the pack's identity
     * @param kindToken the kind's token as the request names it
     * @param artifactId the artifact's id
     * @param ifMatch the {@code If-Match} precondition, required
     * @return the revision after the removal
     */
    public PackRevision remove(PackIdentifier pack, String kindToken, String artifactId, @Nullable String ifMatch) {
        return store.currentRevision(pack); // removes nothing yet
    }
}
