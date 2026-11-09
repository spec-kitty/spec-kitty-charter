package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;

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
}
