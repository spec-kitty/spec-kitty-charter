package ai.speckitty.charter.activations.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The activation documents' store, a port of the activations domain (FR-019). Each write is a compare-and-set on the
 * stored {@link Revision}, which backs {@code If-Match} and {@code If-None-Match: *} (FR-003).
 *
 * <p>The contract every adapter keeps, pinned by the shared contract test:
 *
 * <ul>
 *   <li><b>Atomic per id:</b> a write replaces the whole document or nothing; a failed write leaves the previous
 *       document as it was.
 *   <li><b>Linearisable per id:</b> the comparison and the write of one id happen as one step, so of concurrent writers
 *       holding the same expected revision exactly one succeeds and every other one is answered {@code Stale}.
 *   <li><b>No partial document is ever visible:</b> {@link #find} answers the previous document or the new one, never
 *       a mix, and never a half-written file.
 *   <li>Two ids are independent.
 * </ul>
 *
 * <p>A fault of the store itself (a document that cannot be read back, a write the platform refuses) is thrown as an
 * unchecked exception; it is the service's fault, not the client's, and the inbound adapter answers it as such.
 */
public interface ActivationRepository {

    /**
     * Every stored activation that can be read, in no particular order: the registration impact check asks which packs
     * the stored activations root at (IC-15). A document that cannot be read, or an entry of the store that is no
     * activation, is left out; the read of that activation answers its fault, which this listing does not repeat.
     *
     * @return the readable stored activations; empty when none is stored
     */
    List<StoredActivation> list();

    /**
     * The stored activation.
     *
     * @param id the activation's id
     * @return the activation; empty when none is stored under the id
     */
    Optional<StoredActivation> find(ActivationId id);

    /**
     * Stores a document only when the id is free ({@code If-None-Match: *}).
     *
     * @param id the activation's id
     * @param document the document
     * @return the stored activation; empty when the id already holds one, which is left unchanged
     */
    Optional<StoredActivation> create(ActivationId id, ActivationDocument document);

    /**
     * Replaces a document. With {@code expected} present the write happens only when the stored revision equals it;
     * with it empty the last writer wins, and a missing id is created.
     *
     * @param id the activation's id
     * @param expected the revision the caller read ({@code If-Match}), or empty for an unconditional write
     * @param document the new document
     * @return {@link CasResult.Written}, {@link CasResult.Stale} with the current revision, or {@link CasResult.Missing}
     *     when {@code expected} is present and nothing is stored
     */
    CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document);

    /**
     * Deletes a document. With {@code expected} present the delete happens only when the stored revision equals it.
     *
     * @param id the activation's id
     * @param expected the revision the caller read ({@code If-Match}), or empty for an unconditional delete
     * @return {@link DeleteResult.Deleted}, {@link DeleteResult.Stale} with the current revision, or
     *     {@link DeleteResult.Missing}
     */
    DeleteResult delete(ActivationId id, Optional<Revision> expected);

    /** The outcome of {@link #replace}. */
    sealed interface CasResult {

        /**
         * The document was written.
         *
         * @param stored the activation as now stored
         * @param created {@code true} when nothing was stored before (the service answers {@code 201})
         */
        record Written(StoredActivation stored, boolean created) implements CasResult {

            /**
             * Requires the activation.
             *
             * @param stored the activation as now stored
             * @param created whether it was created
             */
            public Written {
                Objects.requireNonNull(stored, "stored");
            }
        }

        /**
         * The expected revision is not the stored one; nothing was written.
         *
         * @param current the stored revision
         */
        record Stale(Revision current) implements CasResult {

            /**
             * Requires the revision.
             *
             * @param current the stored revision
             */
            public Stale {
                Objects.requireNonNull(current, "current");
            }
        }

        /** A revision was expected but nothing is stored under the id; nothing was written. */
        record Missing() implements CasResult {}
    }

    /** The outcome of {@link #delete}. */
    sealed interface DeleteResult {

        /** The document was deleted. */
        record Deleted() implements DeleteResult {}

        /**
         * The expected revision is not the stored one; nothing was deleted.
         *
         * @param current the stored revision
         */
        record Stale(Revision current) implements DeleteResult {

            /**
             * Requires the revision.
             *
             * @param current the stored revision
             */
            public Stale {
                Objects.requireNonNull(current, "current");
            }
        }

        /** Nothing is stored under the id. */
        record Missing() implements DeleteResult {}
    }
}
