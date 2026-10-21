package ai.speckitty.charter.packs.domain.badfixture;

import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.infra.storage.FileSystemCharterPackRepository;

/** A port-shaped interface whose signature names an adapter type: the known-bad of the port-signature rule. */
public interface DomainWithInfraImport {

    /**
     * Names the YAML adapter in a port signature.
     *
     * @param location where the pack lives
     * @return an adapter, which a port must never name
     */
    FileSystemCharterPackRepository open(PackIdentifier location);
}
