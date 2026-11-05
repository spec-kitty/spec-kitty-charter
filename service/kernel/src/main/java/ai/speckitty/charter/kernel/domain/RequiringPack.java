package ai.speckitty.charter.kernel.domain;

import java.util.List;
import java.util.Objects;

/**
 * A pack that requires or locks an artifact, and the extends chain it does so through (ADR-0020 D10, ADR-0012 D7), as
 * the {@code requiredBy} of a {@code 409 artifact_required} or {@code artifact_locked} names it.
 *
 * @param packId the id of the pack that requires or locks the artifact
 * @param via the extends chain the requirement or lock is inherited through, from the pack the activation names to
 *     {@code packId}; empty when that pack declares it itself
 */
public record RequiringPack(String packId, List<String> via) {

    /**
     * Checks the fields and copies the chain.
     *
     * @param packId the requiring pack
     * @param via the extends chain
     */
    public RequiringPack {
        Objects.requireNonNull(packId, "packId");
        via = List.copyOf(Objects.requireNonNull(via, "via"));
    }
}
