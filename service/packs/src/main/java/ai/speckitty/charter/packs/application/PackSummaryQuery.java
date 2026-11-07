package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
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
     */
    public record Summary(PackIdentifier pack, PackDescriptor descriptor, PackLevel level, PackRevision revision) {

        /**
         * Checks the fields.
         *
         * @param pack the configured identifier
         * @param descriptor the descriptor
         * @param level the level
         * @param revision the revision
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

    /**
     * A query over the stored packs.
     *
     * @param read the verifier of the freshness rule
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param revisions their revisions
     */
    @Autowired
    public PackSummaryQuery(
            ConsistentRead read,
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            PackRevisions revisions) {
        this.read = Objects.requireNonNull(read, "read");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
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
        ConfiguredPack configured = configuredAs(identifier);
        if (configured.level() == PackLevel.BUILT_IN) {
            throw new CharterRefusal(
                    RefusalCode.PACK_NOT_FOUND,
                    "Pack " + identifier + " is a built-in pack: it is read-only and has no registration summary.");
        }
        PackDescriptor descriptor = descriptors.get(configured.id());
        return new Summary(configured.id(), descriptor, configured.level(), revisions.currentRevision(configured.id()));
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
