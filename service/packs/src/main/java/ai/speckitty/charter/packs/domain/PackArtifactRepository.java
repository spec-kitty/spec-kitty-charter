package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;

/**
 * The port through which what a pack holds and what its graphs say is read, whatever the storage: the artifacts it
 * defines, the artifacts its graphs change and the references they write. One pack changes an artifact when one of its
 * DRG documents writes an {@code enhances} or {@code overrides} relation on it; whether two packs that change or define
 * one artifact are siblings of one compose is the resolver's business, not this port's.
 */
public interface PackArtifactRepository {

    /**
     * The artifacts the pack's graph documents say it changes: the distinct targets of its {@code enhances} and
     * {@code overrides} relations, in first-seen order.
     *
     * @param pack the pack's identity
     * @return the refined artifact references; empty when the pack's graphs name none
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when one of the pack's
     *     graph documents cannot be read: a partial graph is never judged, so the caller fails closed
     */
    List<String> changedArtifacts(PackIdentifier pack);

    /**
     * The artifacts the pack itself holds, as bare URNs ({@code <kind>:<id>}) of the kinds the service serves. Two packs
     * of one level that hold the same artifact contest it, and the resolver refuses them unless an {@code overrides}
     * edge settles it.
     *
     * @param pack the pack's identity
     * @return the held artifacts' URNs, in a stable order; empty when the pack holds none (the default, for a storage
     *     with no artifacts)
     */
    default List<String> definedArtifacts(PackIdentifier pack) {
        return List.of();
    }

    /**
     * The references the pack's graph documents write: the distinct edge targets of every relation, as written —
     * bare ({@code <urn>}) or pack-qualified ({@code <pack-name>::<urn>}) — in first-seen order.
     *
     * @param pack the pack's identity
     * @return the references; empty when the pack's graphs write none (the default, for a storage with no graphs)
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when one of the pack's
     *     graph documents cannot be read
     */
    default List<String> referencesOf(PackIdentifier pack) {
        return List.of();
    }

    /**
     * Whether the pack holds the artifact a bare URN names.
     *
     * @param pack the pack's identity
     * @param urn the URN ({@code <kind>:<id>}); a kind the service does not serve is held by no pack
     * @return {@code true} when the pack holds the artifact (the default is {@code false})
     */
    default boolean holds(PackIdentifier pack, String urn) {
        return false;
    }
}
