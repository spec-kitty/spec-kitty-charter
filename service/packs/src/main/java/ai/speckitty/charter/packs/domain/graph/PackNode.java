package ai.speckitty.charter.packs.domain.graph;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackOrigins;
import java.util.Objects;

/**
 * A configured pack and the descriptor it was read with.
 *
 * @param configured the configured pack
 * @param descriptor its descriptor
 */
public record PackNode(ConfiguredPack configured, PackDescriptor descriptor) implements PackGraphNode {

    /**
     * Checks the fields.
     *
     * @param configured the configured pack
     * @param descriptor its descriptor
     */
    public PackNode {
        Objects.requireNonNull(configured, "configured");
        Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public PackIdentifier pack() {
        return configured.id();
    }

    @Override
    public Origin origin() {
        return PackOrigins.of(configured.id());
    }
}
