package ai.speckitty.charter.kernel.domain;

import java.util.Objects;

/**
 * A pack a refusal names: the pack that broke the rule, or a pack the rule relates it to. A refusal of the pack
 * composition carries these, so a client reads which pack to fix without parsing the sentence of the detail.
 *
 * @param packId the {@code pack_id} the pack's {@code pack.yaml} declares
 * @param name the {@code name} the pack's {@code pack.yaml} gives
 */
public record RefusalPack(String packId, String name) {

    /**
     * Checks the fields.
     *
     * @param packId the declared {@code pack_id}
     * @param name the declared {@code name}
     */
    public RefusalPack {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(name, "name");
    }
}
