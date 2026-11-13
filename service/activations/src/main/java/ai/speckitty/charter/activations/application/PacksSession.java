package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Optional;

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

    /**
     * The effective graph under a document's request (FR-018, rule E15): the nodes in force, the merged edges among them
     * and the layer overrides of those nodes. It answers from this session's read and its in-force resolution, so it
     * never reads the packs again nor resolves twice for one request (NFR-007).
     *
     * @param query what the document selects, denies and filters
     * @return the graph; empty for a session that holds no closure
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #inForce} does
     */
    GraphSnapshot graph(InForceQuery query);

    /**
     * What ADR-0018's walk reaches from one action's step over the artifacts in force under a document's request
     * (FR-019, rule E16). It answers from this session's read and its in-force resolution.
     *
     * @param missionType the mission type of the action
     * @param action the action
     * @param depth the number of {@code suggests} hops to follow, at least 0
     * @param query what the document selects, denies and filters
     * @return what the walk reached; nothing, and {@code missionTypeInForce} false, for a mission type not in force
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code mission_type_not_found} or
     *     {@code action_not_found} when the merged closure holds no such mission type or action, whether or not the
     *     mission type is in force, or as {@link #inForce} does
     */
    ActionContext actionContext(String missionType, String action, int depth, InForceQuery query);

    /**
     * The winning copy of one artifact under a document's request (FR-007): the entity of the version the layer merge
     * keeps, read from the pack that holds it, with that pack and its level and, for an agent profile, the governance
     * its walk lists over the artifacts in force. It answers from this session's read and its in-force resolution; the
     * entity is read lazily and against the digest the session was verified at, so a change since is a refusal and never
     * a mix of two states.
     *
     * @param kind the artifact's kind
     * @param id the artifact's id, exactly as declared
     * @param depth the number of {@code suggests} hops of a profile's governance, at least 0; it changes nothing for
     *     any other kind
     * @param query what the document selects, denies and filters
     * @return the winner; empty when no pack of the closure holds the artifact, and for a session that holds no closure
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #inForce} does, with {@code pack_unreadable}
     *     when the winner's pack holds the artifact and cannot read it, and with {@code stale_input} when the packs
     *     changed since the session was verified
     */
    Optional<EffectiveArtifactRead> effectiveArtifact(ArtifactKind kind, String id, int depth, InForceQuery query);

    /**
     * The winners of one kind under the layer merge, whatever the kind (research R2): the version of each artifact of
     * the kind the merge keeps, with the pack and level that hold it and its policy, and no state. It is how an
     * activation lists a kind the effective set does not resolve (an asset), which {@link #merged} leaves out, and the
     * winners of a kind it lists. It answers from this session's read, with no in-force resolution.
     *
     * @param kind the artifacts' kind
     * @return the winners, by id in code-point order; empty for a session that holds no closure
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #merged} does
     */
    List<EffectiveHeldWinner> heldWinners(ArtifactKind kind);

    /**
     * The winner of one artifact under the layer merge, whatever its kind, as {@link #heldWinners} lists it.
     *
     * @param kind the artifact's kind
     * @param id the artifact's id, exactly as declared
     * @return the winner; empty when no pack of the closure holds the artifact, and for a session that holds no closure
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #merged} does
     */
    Optional<EffectiveHeldWinner> heldWinner(ArtifactKind kind, String id);

    /**
     * Judges the names of an action context against the merged closure, and nothing else: no in-force resolution and no
     * walk. A read that answers {@code 304} calls it first, so an unknown name is a {@code 404} whatever
     * {@code If-None-Match} says.
     *
     * @param missionType the mission type of the action
     * @param action the action
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code mission_type_not_found} or
     *     {@code action_not_found} when the merged closure holds no such mission type or action, or as {@link #merged}
     *     does
     */
    void requireAction(String missionType, String action);
}
