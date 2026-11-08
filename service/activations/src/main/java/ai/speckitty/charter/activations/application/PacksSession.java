package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
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

    /**
     * The closure under the layer merge: the version of each artifact in force, its provenance and policy, the typed
     * edges among the winners and the layer overrides (FR-009 to FR-012). It answers from this session's read, so it
     * and {@link #inForce} never read the packs again (NFR-007); the merge is built on the first call.
     *
     * @return the merged closure, as values; empty for a session that holds no closure
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code pack_unreadable} when a layer override
     *     breaks the owner's level or an {@code overrides} edge resolves to its own source, or with
     *     {@code pack_sibling_conflict} when a sibling contest is not settled
     */
    MergedSnapshot merged();

    /**
     * What is in force under a document's request, over the merged closure (FR-013 to FR-016): one state and one reason
     * per winning artifact, and the rules that were not applied. The resolution is the packs slice's; this answers it
     * from the same read as {@link #merged}.
     *
     * @param query what the document selects, denies and filters
     * @return the states and the rules not applied; empty for a session that holds no closure
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #merged} does, or with
     *     {@code pack_unreadable} for an {@code overrides} cycle among the artifacts in force
     */
    InForceAnswer inForce(InForceQuery query);
}
