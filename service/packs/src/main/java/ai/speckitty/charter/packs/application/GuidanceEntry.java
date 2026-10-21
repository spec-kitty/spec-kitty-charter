package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Relation;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Optional;

/**
 * One artifact of an action-scoped context and the walk step that listed it.
 *
 * @param urn the artifact's URN
 * @param kind the artifact's node kind
 * @param label the artifact's label; empty when its node has none or is not declared in the merged graph
 * @param relation the relation of the step that listed the artifact: {@code scope}, {@code requires},
 *     {@code suggests} or {@code vocabulary}
 * @param impacts the edge's strength when its relation is {@code impacts}; empty for every relation this read walks
 * @param via the artifact's parent in that step: the action node for a scope entry, otherwise another entry of the
 *     same answer, which may itself be listed under a stronger relation
 * @param hops how many edges separate the artifact from the action node along {@code via}: 1 for a scope entry,
 *     otherwise the parent's {@code hops} plus 1; at least 1
 */
public record GuidanceEntry(
        Urn urn,
        NodeKind kind,
        Optional<String> label,
        Relation relation,
        Optional<Double> impacts,
        Urn via,
        int hops) {}
