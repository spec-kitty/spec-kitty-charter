package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;

/** The port through which a pack's descriptor is fetched and replaced, whatever the storage. */
public interface PackDescriptorRepository {

    /**
     * Fetches the pack's descriptor.
     *
     * @param pack the pack's identity
     * @return the descriptor
     */
    PackDescriptor get(PackIdentifier pack);

    /**
     * Replaces the pack's descriptor.
     *
     * @param pack the pack's identity
     * @param descriptor the descriptor to store
     */
    void save(PackIdentifier pack, PackDescriptor descriptor);
}
