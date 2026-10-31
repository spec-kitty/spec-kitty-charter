package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Optional;

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
     * Looks the pack's descriptor up, telling a pack without one from a pack whose descriptor cannot be read.
     *
     * @param pack the pack's identity
     * @return the descriptor; empty when the pack has no {@code pack.yaml}
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when the pack is not
     *     configured, its directory is missing, or its {@code pack.yaml} is there but cannot be read or is invalid
     */
    Optional<PackDescriptor> find(PackIdentifier pack);

    /**
     * Replaces the pack's descriptor.
     *
     * @param pack the pack's identity
     * @param descriptor the descriptor to store
     */
    void save(PackIdentifier pack, PackDescriptor descriptor);
}
