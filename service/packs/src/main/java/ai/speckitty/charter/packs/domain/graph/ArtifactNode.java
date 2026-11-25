package ai.speckitty.charter.packs.domain.graph;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.packs.domain.PackOrigins;
import java.util.Objects;

/**
 * An artifact a pack declares as a graph node or holds as a file.
 *
 * @param pack the declaring pack, by configured identifier
 * @param urn the artifact
 * @param held whether the pack holds it as an artifact file, not only as a graph node
 */
public record ArtifactNode(PackIdentifier pack, Urn urn, boolean held) implements PackGraphNode {

    /**
     * Checks the fields.
     *
     * @param pack the declaring pack
     * @param urn the artifact
     * @param held whether the pack holds a file for it
     */
    public ArtifactNode {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(urn, "urn");
    }

    @Override
    public Origin origin() {
        return PackOrigins.of(pack, urn);
    }
}
