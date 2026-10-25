package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The write-path seam a registration service drives (ADR-0026 Decision 3, NFR-002). It is an application-ring
 * <em>driven port</em>: the registration services depend only on this interface and the domain ports, so the onion
 * dependency rule (ADR-0006; application never reaches {@code infra}) holds, while the persistence edge binds it to
 * WP04's aggregate-commit store and WP02's registered-relationship shard.
 *
 * <p>It returns the application-level {@link PackRevision} (the {@code ETag}/{@code If-Match} concurrency token,
 * ADR-0026 Decision 5), which is deliberately distinct from the read-path {@link ConsistentRead} source fingerprint.
 *
 * <p>A commit is whole-aggregate atomic: a failure before the swap changes zero stored files (NFR-002). This store
 * never performs the per-child {@code save()} upsert of the repository ports; registration goes load &rarr; mutate
 * &rarr; revalidate the whole {@link Pack} &rarr; commit here.
 */
public interface PackWriteStore extends PackRevisions {

    /**
     * The revision of the pack's committed state as it stands now, for the {@code ETag} a read answers with and the
     * {@code If-Match} a write is matched against.
     *
     * @param pack the pack's identity
     * @return the current revision
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when the pack cannot be
     *     resolved or read
     */
    @Override
    PackRevision currentRevision(PackIdentifier pack);

    /**
     * The standalone registered-relationship edges currently persisted in the pack's shard, in registration order.
     *
     * @param pack the pack's identity
     * @return the registered relationships; empty when the pack has none
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when the pack cannot be
     *     resolved or read, or when its shard holds anything that does not bind (a malformed document or an invalid
     *     edge): a write built on the bound subset would silently drop the rest
     */
    List<Relationship> registeredRelationships(PackIdentifier pack);

    /**
     * Atomically commits a revalidated aggregate together with the full set of registered relationships, and returns
     * the new revision.
     *
     * @param pack the pack's identity
     * @param mutated the revalidated aggregate to persist (its invariants are already guarded by its builder)
     * @param relationships the complete registered-relationship set to persist (replaces the shard wholesale), or
     *     null when the registration does not change relationships, which leaves the shard untouched
     * @return the revision of the committed state
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when the commit fails;
     *     in every failure case the live pack directory is unchanged (NFR-002)
     */
    PackRevision commit(PackIdentifier pack, Pack mutated, @Nullable List<Relationship> relationships);
}
