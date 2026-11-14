package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;
import java.util.Objects;

/**
 * The pack-scoped glossary reads (#106, ADR-0074 decisions 3 and 9 to 14): a pack's glossary contexts, one context with
 * every term, and one term of a context. It is the port of the edge (ADR-0037 as amended 2026-11-09): the adapter is
 * {@code artifacts.infra.packs}, the one place of the edge that asks the packs slice, and each read is one verified
 * read there, so the answer, its revision and its fingerprint name the same inputs.
 *
 * <p>The segments are judged in path order (research R3): an unknown pack is {@code pack_not_found}; a context the pack
 * does not hold is {@code artifact_not_found} with the detail {@code glossary context `<id>` is not held}; a term the
 * context does not hold is {@code artifact_not_found} with the detail {@link #termNotHeld}. A pack that cannot be read
 * is {@code pack_unreadable}, and a change of the inputs during the read {@code stale_input}.
 */
public interface GlossaryReads {

    /**
     * The detail of the refusal of a term its context does not hold (ruling {@code DM-01M7B1Q4BPQB95EVQBRWN4Q4F8}).
     *
     * @param contextId the context's id, as the request names it
     * @param termId the term's id, as the request names it
     * @return {@code term `<termId>` is not held in glossary context `<contextId>`}
     */
    static String termNotHeld(String contextId, String termId) {
        return "term `" + termId + "` is not held in glossary context `" + contextId + "`";
    }

    /**
     * The glossary contexts a pack holds, in id order.
     *
     * @param pack the pack's identity, as the request names it
     * @return the contexts, the pack revision and the verified fingerprint
     * @throws CharterRefusal as the packs slice refuses
     */
    Listed list(PackIdentifier pack);

    /**
     * One glossary context, with every term.
     *
     * @param pack the pack's identity, as the request names it
     * @param contextId the context's id
     * @return the context, the pack revision and the verified fingerprint
     * @throws CharterRefusal as the packs slice refuses, {@code artifact_not_found} for a context the pack does not hold
     */
    Read context(PackIdentifier pack, String contextId);

    /**
     * One term of a glossary context, judged after the pack and the context.
     *
     * @param pack the pack's identity, as the request names it
     * @param contextId the context's id
     * @param termId the term's id
     * @return the term, the context that holds it, the pack revision and the verified fingerprint
     * @throws CharterRefusal as {@link #context} refuses, then {@code artifact_not_found} for a term the context does
     *     not hold
     */
    TermRead term(PackIdentifier pack, String contextId, String termId);

    /**
     * What the list read answers.
     *
     * @param contexts the pack's contexts, in id order
     * @param revision the pack revision they were read at: the {@code ETag} token, without quotes
     * @param sourceFingerprint the whole-source digest the verified read agreed on
     */
    record Listed(List<GlossaryView> contexts, String revision, String sourceFingerprint) {

        /**
         * Checks and copies the fields.
         *
         * @param contexts the contexts
         * @param revision the revision
         * @param sourceFingerprint the fingerprint
         */
        public Listed {
            contexts = List.copyOf(Objects.requireNonNull(contexts, "contexts"));
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }

    /**
     * What the context read answers.
     *
     * @param context the context
     * @param revision the pack revision it was read at: the {@code ETag} token, without quotes
     * @param sourceFingerprint the whole-source digest the verified read agreed on
     */
    record Read(GlossaryView context, String revision, String sourceFingerprint) {

        /**
         * Checks the fields.
         *
         * @param context the context
         * @param revision the revision
         * @param sourceFingerprint the fingerprint
         */
        public Read {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }

    /**
     * What the term read answers.
     *
     * @param term the term
     * @param contextId the id of the context that holds it
     * @param packId the pack that holds the context, by the identifier the request named it with
     * @param revision the pack revision it was read at: the {@code ETag} token, without quotes
     * @param sourceFingerprint the whole-source digest the verified read agreed on
     */
    record TermRead(
            GlossaryView.Term term,
            String contextId,
            PackIdentifier packId,
            String revision,
            String sourceFingerprint) {

        /**
         * Checks the fields.
         *
         * @param term the term
         * @param contextId the context's id
         * @param packId the pack
         * @param revision the revision
         * @param sourceFingerprint the fingerprint
         */
        public TermRead {
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(contextId, "contextId");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }
}
