package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.packs.domain.PackRepository;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** The {@code DELETE} of one registered relationship of a pack (IC-06, FR-006). */
@Service
public final class RelationshipRemovalService {

    private final PackRemovalStore store;

    /**
     * A service over its ports.
     *
     * @param packs loads the pack
     * @param store locks and swaps
     * @param impact the impact check
     */
    @Autowired
    public RelationshipRemovalService(PackRepository packs, PackRemovalStore store, RegistrationImpact impact) {
        Objects.requireNonNull(packs, "packs");
        this.store = Objects.requireNonNull(store, "store");
        Objects.requireNonNull(impact, "impact");
    }

    /**
     * Removes the registered edge of an unordered pair.
     *
     * @param pack the pack's identity
     * @param from one end of the pair
     * @param to the other end
     * @param ifMatch the {@code If-Match} precondition, required
     * @return the revision after the removal
     */
    public PackRevision remove(
            PackIdentifier pack, PackQualifiedReference from, PackQualifiedReference to, @Nullable String ifMatch) {
        return store.currentRevision(pack); // removes nothing yet
    }
}
