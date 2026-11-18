package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;
import java.util.Objects;

/**
 * The port of the pack-scoped routing catalog reads (ADR-0080 decision 6): what a pack holds of the kind, read and
 * verified against the source fingerprint by the packs slice, as {@link SkillReads} reads the skills.
 */
public interface RoutingCatalogReads {

    /**
     * The catalogs a pack holds, in id order.
     *
     * @param pack the pack, by configured name or declared {@code pack_id}
     * @return the catalogs with the pack revision and the source fingerprint of the read
     * @throws CharterRefusal with {@code PACK_NOT_FOUND}, {@code PACK_UNREADABLE} or {@code STALE_INPUT}
     */
    Listed list(PackIdentifier pack);

    /**
     * One catalog of a pack.
     *
     * @param pack the pack, by configured name or declared {@code pack_id}
     * @param catalogId the catalog's id
     * @return the catalog with the pack revision and the source fingerprint of the read
     * @throws CharterRefusal as {@link #list(PackIdentifier)} does, and with {@code ARTIFACT_NOT_FOUND} when the pack
     *     holds no such catalog
     */
    Read catalog(PackIdentifier pack, String catalogId);

    /**
     * The catalogs a pack holds, read against an expected source fingerprint: the read of the winners' packs under an
     * activation, which must agree with the fingerprint of the activation's read.
     *
     * @param pack the pack
     * @param expectedFingerprint the fingerprint the read must carry
     * @return the catalogs
     * @throws CharterRefusal with {@code STALE_INPUT} when the fingerprints differ, or as {@link #list(PackIdentifier)}
     */
    Listed list(PackIdentifier pack, String expectedFingerprint);

    /**
     * The view of a catalog the activations slice read as a kernel artifact.
     *
     * @param catalog the winning catalog
     * @param read what was being read, for the message of a defect
     * @return its view
     * @throws IllegalStateException when the artifact is no routing catalog
     */
    RoutingCatalogView view(CharterArtifact catalog, String read);

    /**
     * The catalogs of a pack.
     *
     * @param catalogs the catalogs, in id order
     * @param revision the pack revision
     * @param sourceFingerprint the source fingerprint of the read
     */
    record Listed(List<RoutingCatalogView> catalogs, String revision, String sourceFingerprint) {

        /**
         * Checks the fields and copies the list.
         *
         * @param catalogs the catalogs
         * @param revision the revision
         * @param sourceFingerprint the fingerprint
         */
        public Listed {
            catalogs = List.copyOf(Objects.requireNonNull(catalogs, "catalogs"));
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }

    /**
     * One catalog of a pack.
     *
     * @param catalog the catalog
     * @param revision the pack revision
     * @param sourceFingerprint the source fingerprint of the read
     */
    record Read(RoutingCatalogView catalog, String revision, String sourceFingerprint) {

        /**
         * Checks the fields.
         *
         * @param catalog the catalog
         * @param revision the revision
         * @param sourceFingerprint the fingerprint
         */
        public Read {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }
}
