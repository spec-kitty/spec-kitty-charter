package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.NodeKind;
import ai.speckitty.charter.domain.Relation;
import ai.speckitty.charter.domain.Urn;
import java.util.Optional;

/**
 * One artifact of an action-scoped context and the edge that brought it in.
 *
 * @param urn the artifact's URN
 * @param kind the artifact's node kind
 * @param label the artifact's label; empty when its node has none or is not declared in the merged graph
 * @param relation the relation of the edge that brought the artifact in
 * @param impacts the edge's strength when its relation is {@code impacts}; empty for every other relation
 * @param via the node the edge starts from: the action or its mission type
 */
public record GuidanceEntry(
        Urn urn, NodeKind kind, Optional<String> label, Relation relation, Optional<Double> impacts, Urn via) {}
