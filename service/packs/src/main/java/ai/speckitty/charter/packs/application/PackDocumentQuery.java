package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackDocumentRepository;
import ai.speckitty.charter.packs.domain.PackDocumentText;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The read of one stored pack's document (#451): its {@code README.md}, with the pack revision it was read at, verified
 * against the source fingerprint as every read is. The pack is resolved as {@link PackSummaryQuery} resolves it (a
 * configured name or the declared {@code pack_id}; a built-in pack is {@code pack_not_found}; a pack whose
 * {@code pack.yaml} cannot be read is {@code pack_unreadable}, as {@code getPack} answers it), and the document and
 * the revision are taken inside the one verified read, as {@link ProcedureReadService} takes an item and its revision,
 * so the {@code ETag} names the state the body came from.
 */
@Service
public final class PackDocumentQuery {

    /**
     * A pack's document with the pack it was read from and the revision it was read at.
     *
     * @param pack the identifier the pack is configured under, which its links name
     * @param document the document
     * @param revision the pack's revision at the time of the read, the {@code ETag}
     */
    public record Read(PackIdentifier pack, PackDocumentText document, PackRevision revision) {

        /**
         * Checks the fields.
         *
         * @param pack the configured identifier
         * @param document the document
         * @param revision the revision
         */
        public Read {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(revision, "revision");
        }
    }

    private static final String STALE = "The packs changed while the pack document was read; read it again.";

    private final ConsistentRead read;
    private final PackSummaryQuery summaries;
    private final PackDocumentRepository documents;
    private final PackRevisions revisions;

    /**
     * A query over the stored packs' documents.
     *
     * @param read the verifier of the freshness rule
     * @param summaries the resolution of a stored pack
     * @param documents the port that reads a pack's document
     * @param revisions the pack revision, read inside the verified read for the {@code ETag}
     */
    @Autowired
    public PackDocumentQuery(
            ConsistentRead read,
            PackSummaryQuery summaries,
            PackDocumentRepository documents,
            PackRevisions revisions) {
        this.read = Objects.requireNonNull(read, "read");
        this.summaries = Objects.requireNonNull(summaries, "summaries");
        this.documents = Objects.requireNonNull(documents, "documents");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
    }

    /**
     * The document of a stored pack and the revision it was read at.
     *
     * @param identifier the configured name, or the declared {@code pack_id}
     * @return the document, the pack it belongs to and the pack revision, and the fingerprint the read verified
     * @throws CharterRefusal {@code PACK_NOT_FOUND} when no configured pack has the identifier, the pack is a built-in
     *     one, or the pack cannot be resolved; {@code PACK_UNREADABLE} when the pack's {@code pack.yaml} cannot be read,
     *     as the summary refuses it; {@code PACK_DOCUMENT_NOT_FOUND} when the pack holds no document;
     *     {@code PACK_UNREADABLE} when the document cannot be read as the store reads it; {@code STALE_INPUT} when the
     *     packs changed during the read
     */
    public ConsistentRead.Verified<Read> document(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return read.read(STALE, () -> documentOf(identifier));
    }

    private Read documentOf(String identifier) {
        ConfiguredPack pack =
                summaries.described(identifier).pack(); // judged as getPack judges it, descriptor included
        PackRevision revision = revisionOf(identifier, pack);
        PackDocumentText text = documents
                .document(pack.id())
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PACK_DOCUMENT_NOT_FOUND,
                        "Pack " + pack.id().value() + " holds no document (README.md)."));
        return new Read(pack.id(), text, revision);
    }

    /**
     * A pack the storage cannot resolve is, to the caller, a pack that is not there (as {@link ProcedureReadService}
     * answers it); the original {@code PACK_UNREADABLE} refusal is kept as the cause. A document that cannot be read is
     * not this: it stays {@code PACK_UNREADABLE}.
     */
    private PackRevision revisionOf(String identifier, ConfiguredPack pack) {
        try {
            return revisions.currentRevision(pack.id());
        } catch (CharterRefusal refused) {
            if (refused.code() != RefusalCode.PACK_UNREADABLE) {
                throw refused;
            }
            throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + identifier + " was not found.", refused);
        }
    }
}
