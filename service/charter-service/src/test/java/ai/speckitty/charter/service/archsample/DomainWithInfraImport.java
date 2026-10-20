package ai.speckitty.charter.service.archsample;

import ai.speckitty.charter.domain.PackLocator;
import ai.speckitty.charter.yaml.FilesystemPackElementRepository;

/**
 * The known-bad sample of the port-signature rule: a port-shaped interface whose signature names an infrastructure
 * (adapter) type. It must stay in this test-only package: production imports exclude tests, so the real rules never
 * see it, and the known-bad control scopes the shared production rule to it explicitly.
 */
public interface DomainWithInfraImport {

    /**
     * Names the YAML adapter in a port signature.
     *
     * @param location where the pack lives
     * @return an adapter, which a port must never name
     */
    FilesystemPackElementRepository open(PackLocator location);
}
