package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Objects;

/**
 * What a scope holds of one mission type, with the pack that holds it: under an activation, the pack that holds the
 * winner of the layer merge, which the answer names as its {@code packId} and links as its {@code artifact}.
 *
 * @param holder the configured identifier of the pack that holds the type
 * @param holding what that pack, and the scope's step contracts, hold of the type
 */
public record EffectiveMissionTypeHolding(PackIdentifier holder, MissionTypeHolding holding) {

    /**
     * Checks the fields.
     *
     * @param holder the holding pack
     * @param holding the holding
     */
    public EffectiveMissionTypeHolding {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(holding, "holding");
    }
}
