package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;

/**
 * The port through which what a pack holds and what its graphs say is read, whatever the storage. One pack changes an
 * artifact when one of its DRG documents writes an {@code enhances} or {@code overrides} relation on it; whether two
 * packs that change or define one artifact are siblings of one compose is the resolver's business, not this port's.
 */
public interface PackArtifactRepository {

    /**
     * What the compose needs to know about one pack, from one assembly of it:
     *
     * <ul>
     *   <li>the artifacts its graph documents say it changes: the distinct targets of its {@code enhances} and
     *       {@code overrides} relations, in first-seen order;
     *   <li>the artifacts it holds, as bare URNs ({@code <kind>:<id>}), of every kind a reference can name. It is the
     *       one answer to whether a pack holds an artifact: two packs of one level that hold the same artifact contest
     *       it, and a reference is held by the pack whose list names it (see
     *       {@link ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution#holds}). It names the
     *       files the pack holds and the nodes its graph documents declare for a kind that is never a file (an
     *       {@code anti_pattern});
     *   <li>the references it writes: both endpoints of every edge of its graph documents, and the targets of its
     *       artifacts' derived relations, as written, bare ({@code <urn>}) or pack-qualified
     *       ({@code <pack-name>::<urn>}), in first-seen order.
     * </ul>
     *
     * @param pack the pack's identity
     * @return the facts; {@link PackCompositionFacts#NONE} for a pack that holds and writes nothing
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when a graph document or a
     *     file of the pack cannot be read: a partial answer is never judged, so the caller fails closed
     */
    PackCompositionFacts compositionFacts(PackIdentifier pack);
}
