package ai.speckitty.charter.packs.domain.graph;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.Origin;

/**
 * One element of the pack graph a walk visits: a pack, an artifact a pack declares, or an edge a pack writes. Rules use
 * a pattern switch over it; there is no double dispatch.
 */
public sealed interface PackGraphNode permits PackNode, ArtifactNode, EdgeNode {

    /**
     * The pack the element belongs to: the pack itself, the pack that declares the artifact, or the pack that writes
     * the edge.
     *
     * @return the pack, by its configured identifier
     */
    PackIdentifier pack();

    /**
     * What a finding about the element is about (ADR-0082 decision 4).
     *
     * @return the element's origin
     */
    Origin origin();
}
