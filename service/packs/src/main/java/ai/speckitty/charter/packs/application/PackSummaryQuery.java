package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackDocumentRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The read of one stored pack as a summary (FR-039): its identity, version, level and revision, verified against the
 * source fingerprint as every read is. The identifier is judged as every pack path judges it: a configured name, or
 * the {@code pack_id} a pack declares.
 */
@Service
public final class PackSummaryQuery {

    /**
     * What a stored pack is.
     *
     * @param pack the identifier the pack is configured under
     * @param descriptor its descriptor
     * @param level the level it is stored at
     * @param revision the revision of its stored tree
     * @param hasDocument whether the pack holds a document ({@code README.md}), which the {@code doc} link is present for
     */
    public record Summary(
            PackIdentifier pack,
            PackDescriptor descriptor,
            PackLevel level,
            PackRevision revision,
            boolean hasDocument) {

        /**
         * Checks the fields.
         *
         * @param pack the configured identifier
         * @param descriptor the descriptor
         * @param level the level
         * @param revision the revision
         * @param hasDocument whether the pack holds a document
         */
        public Summary {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(revision, "revision");
        }
    }

    private final ConsistentRead read;
    private final PackCatalogRepository catalog;
    private final PackDescriptorRepository descriptors;
    private final PackRevisions revisions;
    private final PackDocumentRepository documents;

    /**
     * A query over the stored packs.
     *
     * @param read the verifier of the freshness rule
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param revisions their revisions
     * @param documents their documents, for whether a pack holds one
     */
    @Autowired
    public PackSummaryQuery(
            ConsistentRead read,
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            PackRevisions revisions,
            PackDocumentRepository documents) {
        this.read = Objects.requireNonNull(read, "read");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.documents = Objects.requireNonNull(documents, "documents");
    }

    /**
     * The summary of a stored pack.
     *
     * @param identifier the configured name, or the declared {@code pack_id}
     * @return the summary and the fingerprint the read verified
     * @throws CharterRefusal {@code PACK_NOT_FOUND} when no configured pack has the identifier, or the pack is a built-in
     *     one, which is read-only and has no registration summary; {@code PACK_UNREADABLE} when it cannot be read;
     *     {@code STALE_INPUT} when the packs changed during the read
     */
    public ConsistentRead.Verified<Summary> summary(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return read.read("The packs changed while the pack was read; read it again.", () -> summaryOf(identifier));
    }

    private Summary summaryOf(String identifier) {
        Described described = described(identifier);
        ConfiguredPack configured = described.pack();
        return new Summary(
                configured.id(),
                described.descriptor(),
                configured.level(),
                revisions.currentRevision(configured.id()),
                documents.holdsDocument(configured.id()));
    }

    /**
     * A stored pack and its descriptor, as read.
     *
     * @param pack the configured pack
     * @param descriptor its descriptor
     */
    record Described(ConfiguredPack pack, PackDescriptor descriptor) {}

    /**
     * The stored pack an identifier names, with its descriptor read: the one judgement of the pack that the summary and
     * the document read share (#451), so a pack whose {@code pack.yaml} cannot be read (a stored {@code active_charter},
     * for one) is refused by both alike. Call it inside a verified read.
     *
     * @param identifier the configured name, or the declared {@code pack_id}
     * @return the configured pack and its descriptor
     * @throws CharterRefusal {@code PACK_NOT_FOUND} as {@link #stored} refuses; {@code PACK_UNREADABLE} when the pack's
     *     {@code pack.yaml} cannot be read
     */
    Described described(String identifier) {
        ConfiguredPack configured = stored(identifier);
        return new Described(configured, descriptors.get(configured.id()));
    }

    /**
     * The stored pack an identifier names: judged as every pack path judges it, and never a built-in one, which is
     * read-only and has no registration summary and no document read. Call it inside a verified read.
     *
     * @param identifier the configured name, or the declared {@code pack_id}
     * @return the configured pack
     * @throws CharterRefusal {@code PACK_NOT_FOUND} when no configured pack has the identifier, or the pack is a built-in
     *     one
     */
    ConfiguredPack stored(String identifier) {
        ConfiguredPack configured = configuredAs(identifier);
        if (configured.level() == PackLevel.BUILT_IN) {
            throw new CharterRefusal(
                    RefusalCode.PACK_NOT_FOUND,
                    "Pack " + identifier + " is a built-in pack: it is read-only and has no registration summary.");
        }
        return configured;
    }

    private ConfiguredPack configuredAs(String identifier) {
        List<ConfiguredPack> configured = catalog.configured();
        Optional<ConfiguredPack> named = configured.stream()
                .filter(pack -> pack.id().value().equals(identifier))
                .findFirst();
        if (named.isPresent()) {
            return named.get();
        }
        for (ConfiguredPack pack : configured) {
            if (declaresId(pack, identifier)) {
                return pack;
            }
        }
        throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + identifier + " was not found.");
    }

    private boolean declaresId(ConfiguredPack pack, String identifier) {
        try {
            return descriptors
                    .find(pack.id())
                    .filter(d -> d.packId().equals(identifier))
                    .isPresent();
        } catch (CharterRefusal unreadable) {
            return false;
        }
    }
}
