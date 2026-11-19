package ai.speckitty.charter.kernel.domain;

import java.util.Objects;

/**
 * A pack a refusal names: the pack that broke the rule, or a pack the rule relates it to. A refusal of the pack
 * composition carries these, so a client reads which pack to fix without parsing the sentence of the detail.
 *
 * @param descriptorPackId the {@code pack_id} the pack's {@code pack.yaml} declares, the identity a dependency edge names
 * @param packId the id the pack is registered under, the one every pack-scoped path takes
 * @param name the {@code name} the pack's {@code pack.yaml} gives, the handle a pack-qualified reference uses
 */
public record RefusalPack(PackUlid descriptorPackId, PackIdentifier packId, String name) {

    /**
     * Checks the fields.
     *
     * @param descriptorPackId the declared {@code pack_id}
     * @param packId the registered id
     * @param name the declared {@code name}
     */
    public RefusalPack {
        Objects.requireNonNull(descriptorPackId, "descriptorPackId");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(name, "name");
    }
}
