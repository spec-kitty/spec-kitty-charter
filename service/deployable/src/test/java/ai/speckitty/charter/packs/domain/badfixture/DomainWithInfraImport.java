package ai.speckitty.charter.packs.domain.badfixture;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;

/** A port-shaped interface whose signature names an adapter type: the known-bad of the port-signature rule. */
public interface DomainWithInfraImport {

    /**
     * Names the storage service adapter in a port signature.
     *
     * @param location where the pack lives
     * @return an adapter, which a port must never name
     */
    DomainRepositoryService open(PackIdentifier location);
}
