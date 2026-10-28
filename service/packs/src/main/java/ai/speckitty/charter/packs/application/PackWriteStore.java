package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.Pack;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
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
 *
 * <p>Revalidation is whole-aggregate, but persistence is a <em>delta</em>: a commit carries the one artefact the
 * registration added or replaced (a {@link Change}), and the store writes only that artefact and, when it changed, the
 * relationship shard. The descriptor and every sibling artefact stay byte-identical, so authored comments, quoting,
 * and key order survive a registration.
 */
public interface PackWriteStore extends PackRevisions {

    /**
     * What one registration changes: the revalidated aggregate (whose invariants gated the write), the one artefact
     * it added or replaced, if any, and the complete registered-relationship set to persist, if the registration
     * changes relationships at all.
     *
     * @param revalidated the revalidated aggregate, after the mutation
     * @param artifact the artefact the registration added or replaced; empty when only relationships change
     * @param relationships the complete registered-relationship set to persist (replaces the shard wholesale); empty
     *     when the registration leaves the relationship shard untouched
     */
    record Change(Pack revalidated, Optional<CharterArtifact> artifact, Optional<List<Relationship>> relationships) {
        /**
         * Checks the parts and copies the list.
         *
         * @param revalidated the revalidated aggregate
         * @param artifact the changed artefact, if any
         * @param relationships the relationship set to persist, if any
         */
        public Change {
            Objects.requireNonNull(revalidated, "revalidated");
            Objects.requireNonNull(artifact, "artifact");
            relationships = relationships.map(List::copyOf);
        }

        /**
         * A change that adds or replaces {@code artifact} in {@code revalidated} and leaves the relationships alone.
         *
         * @param revalidated the revalidated aggregate holding {@code artifact}
         * @param artifact the added or replaced artefact
         * @return the change
         */
        public static Change artifact(Pack revalidated, CharterArtifact artifact) {
            return new Change(revalidated, Optional.of(artifact), Optional.empty());
        }

        /**
         * A change that adds or replaces {@code artifact} in {@code revalidated} and persists {@code relationships}.
         *
         * @param revalidated the revalidated aggregate holding {@code artifact}
         * @param artifact the added or replaced artefact
         * @param relationships the relationship set to persist
         * @return the change
         */
        public static Change artifact(Pack revalidated, CharterArtifact artifact, List<Relationship> relationships) {
            return new Change(revalidated, Optional.of(artifact), Optional.of(relationships));
        }

        /**
         * A change that touches no artefact, only the registered relationships.
         *
         * @param current the unchanged aggregate
         * @param relationships the relationship set to persist
         * @return the change
         */
        public static Change relationships(Pack current, List<Relationship> relationships) {
            return new Change(current, Optional.empty(), Optional.of(relationships));
        }
    }

    /**
     * The revision of the pack's committed state as it stands now, for the {@code ETag} a read answers with and the
     * {@code If-Match} a write is matched against.
     *
     * @param pack the pack's identity
     * @return the current revision
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_NOT_FOUND} when no configured pack has
     *     this identifier, or with {@code PACK_UNREADABLE} when the pack cannot be read
     */
    @Override
    PackRevision currentRevision(PackIdentifier pack);

    /**
     * The standalone registered-relationship edges currently persisted in the pack's shard, in registration order.
     *
     * @param pack the pack's identity
     * @return the registered relationships; empty when the pack has none
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_NOT_FOUND} when no configured pack has
     *     this identifier, or with {@code PACK_UNREADABLE} when the pack cannot be read, or when its shard holds
     *     anything that does not bind (a malformed document or an invalid edge): a write built on the bound subset
     *     would silently drop the rest
     */
    List<Relationship> registeredRelationships(PackIdentifier pack);

    /**
     * Runs one registration's whole read-check-commit sequence while holding the pack's write lock, so its
     * precondition, the state it builds on and its commit cannot be interleaved with another write to the same pack.
     * The lock is reentrant; {@link #commit} takes it too.
     *
     * @param pack the pack's identity
     * @param work the load &rarr; precondition &rarr; mutate &rarr; commit sequence
     * @param <T> the outcome type
     * @return what {@code work} returns
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_NOT_FOUND} when the identifier names no
     *     configured pack (neither by configured name nor by declared {@code pack_id}); a configured pack that cannot be
     *     read is not "not found", and its {@code PACK_UNREADABLE} surfaces from {@code work}
     */
    <T> T exclusively(PackIdentifier pack, Supplier<T> work);

    /**
     * Atomically commits one registration's {@link Change}, writing only its artefact and (when present) its
     * relationship set, and returns the new revision. The {@code If-Match} is compared with the live revision under
     * the pack's write lock, so a precondition checked earlier cannot go stale before the swap.
     *
     * @param pack the pack's identity
     * @param ifMatch the {@code If-Match} the registration presented, or null when it presented none
     * @param change what the registration changes (its aggregate's invariants are already guarded by its builder)
     * @return the revision of the committed state
     * @throws PreconditionException {@link PreconditionException.Kind#STALE} when {@code ifMatch} no longer matches
     *     the live revision; nothing is written
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when the commit fails;
     *     in every failure case the live pack directory is unchanged (NFR-002)
     */
    PackRevision commit(PackIdentifier pack, @Nullable String ifMatch, Change change);
}
