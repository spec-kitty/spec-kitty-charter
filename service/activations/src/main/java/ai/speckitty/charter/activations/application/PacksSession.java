package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import java.util.Map;
import java.util.Set;

/**
 * One verified read of an activation's closure, kept for the whole request (NFR-007): the snapshot the document rules
 * read and the walks the cascade rules plan all answer from the same view, so a write never mixes two states of the
 * packs. {@link AvailablePacks#session} opens it with one consistent read; nothing here reads the packs again.
 *
 * <p>A session whose snapshot is not {@linkplain ClosureSnapshot#resolved() resolved} holds no closure: its walks reach
 * nothing, and a write refuses it before it walks.
 */
public interface PacksSession {

    /**
     * What the closure holds, as the document rules read it.
     *
     * @return the snapshot of this session's read
     */
    ClosureSnapshot snapshot();

    /**
     * Walks the closure's edges as a plan asks.
     *
     * @param plan the walk the cascade rules planned
     * @return what the walk reached, stopped at, left behind a stop and passed through
     */
    Reach reach(ReachPlan plan);

    /**
     * What the {@code enhances} pull reaches from some artifacts: both directions (an artifact's enhancers and an
     * enhancer's targets), transitively over {@code enhances} only (ADR-0059 D2.2).
     *
     * @param starts the artifacts the pull starts from
     * @return the artifacts pulled, the starts left out
     */
    Set<ArtifactRef> enhancesPull(Set<ArtifactRef> starts);

    /**
     * The enhancers of each target, one hop, in reverse over {@code enhances}.
     *
     * @param targets the targets
     * @return each target that has an enhancer, with its enhancers
     */
    Map<ArtifactRef, Set<ArtifactRef>> enhancersOf(Set<ArtifactRef> targets);
}
