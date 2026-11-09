package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import org.jspecify.annotations.Nullable;

/**
 * The {@code If-Match} of a {@code DELETE} of an artifact or of a relationship: a sub-resource write of a pack, so it
 * requires the pack's revision, {@code 428} without it and {@code 412} with a revision that is not the stored one;
 * {@code *} passes and asks only that the pack exists. Judged under the registry lock and the pack's own, so the revision
 * it matched is the one the removal swaps.
 */
final class RemovalPrecondition {

    private RemovalPrecondition() {}

    /**
     * Checks the precondition against the stored revision.
     *
     * @param revisions the revisions of the stored packs
     * @param pack the pack's identity
     * @param ifMatch the {@code If-Match} field value, or null when the request carried none
     * @param trail told the stored revision as soon as it is read, before it is judged, so a refusal still enters it
     * @return the stored revision the precondition was judged against
     * @throws PreconditionException {@code MISSING} (428) when none was sent, {@code STALE} (412) when it is not the
     *     stored revision
     */
    static PackRevision require(
            PackRevisions revisions, PackIdentifier pack, @Nullable String ifMatch, RevisionTrail trail) {
        if (ifMatch == null) {
            throw new PreconditionException(
                    PreconditionException.Kind.MISSING,
                    "A removal requires an If-Match precondition on the pack revision.");
        }
        PackRevision stored = revisions.currentRevision(pack);
        trail.found(stored);
        if (!PackRevision.matches(ifMatch, stored)) {
            throw new PreconditionException(
                    PreconditionException.Kind.STALE, "The If-Match precondition is stale; read the pack again.");
        }
        return stored;
    }
}
