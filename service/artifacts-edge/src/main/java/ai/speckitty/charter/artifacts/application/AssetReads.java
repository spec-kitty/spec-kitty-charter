package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The pack-scoped asset reads (#105, ADR-0074 decisions 2, 10, 11 and 13): a pack's assets, one asset's descriptor, and
 * one asset's payload with its bytes. It is the port of the edge (ADR-0037 as amended 2026-11-09): the adapter is
 * {@code artifacts.infra.packs}, the one place of the edge that asks the packs slice, and each read is one verified
 * read there, so the answer, its revision and its fingerprint name the same inputs.
 *
 * <p>The refusals are the packs slice's, unchanged: an unknown pack is {@code pack_not_found}, an asset the pack does
 * not hold {@code artifact_not_found}, a pack that cannot be read {@code pack_unreadable}, and a change of the inputs
 * during the read {@code stale_input}.
 */
public interface AssetReads {

    /**
     * The assets a pack holds, in id order.
     *
     * @param pack the pack's identity, as the request names it
     * @return the assets, the pack revision and the verified fingerprint
     * @throws CharterRefusal as the packs slice refuses
     */
    Listed list(PackIdentifier pack);

    /**
     * One asset's descriptor.
     *
     * @param pack the pack's identity, as the request names it
     * @param assetId the asset's id
     * @return the asset, the pack revision and the verified fingerprint
     * @throws CharterRefusal as the packs slice refuses
     */
    Read asset(PackIdentifier pack, String assetId);

    /**
     * One asset with the bytes of its payload.
     *
     * @param pack the pack's identity, as the request names it
     * @param assetId the asset's id
     * @return the asset, its bytes, the pack revision and the verified fingerprint
     * @throws CharterRefusal as the packs slice refuses
     */
    PayloadRead payload(PackIdentifier pack, String assetId);

    /**
     * What the list read answers.
     *
     * @param assets the pack's assets, in id order
     * @param revision the pack revision they were read at: the {@code ETag} token, without quotes
     * @param sourceFingerprint the whole-source digest the verified read agreed on
     */
    record Listed(List<AssetView> assets, String revision, String sourceFingerprint) {

        /**
         * Checks and copies the fields.
         *
         * @param assets the assets
         * @param revision the revision
         * @param sourceFingerprint the fingerprint
         */
        public Listed {
            assets = List.copyOf(Objects.requireNonNull(assets, "assets"));
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }

    /**
     * What the item read answers.
     *
     * @param asset the asset
     * @param revision the pack revision it was read at: the {@code ETag} token, without quotes
     * @param sourceFingerprint the whole-source digest the verified read agreed on
     */
    record Read(AssetView asset, String revision, String sourceFingerprint) {

        /**
         * Checks the fields.
         *
         * @param asset the asset
         * @param revision the revision
         * @param sourceFingerprint the fingerprint
         */
        public Read {
            Objects.requireNonNull(asset, "asset");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }
    }

    /**
     * What the payload read answers: the asset, the exact bytes of the payload file it names, the pack revision and
     * the fingerprint, all of one verified read. The bytes never leave it except as a copy.
     */
    final class PayloadRead {

        private final AssetView asset;
        private final byte[] bytes;
        private final String revision;
        private final String sourceFingerprint;

        /**
         * Checks the fields and copies the bytes.
         *
         * @param asset the asset
         * @param bytes the payload's bytes
         * @param revision the pack revision they were read at: the {@code ETag} token, without quotes
         * @param sourceFingerprint the whole-source digest the verified read agreed on
         */
        public PayloadRead(AssetView asset, byte[] bytes, String revision, String sourceFingerprint) {
            this.asset = Objects.requireNonNull(asset, "asset");
            this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
            this.revision = Objects.requireNonNull(revision, "revision");
            this.sourceFingerprint = Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        }

        /**
         * The asset.
         *
         * @return the asset whose payload this is
         */
        public AssetView asset() {
            return asset;
        }

        /**
         * The payload's bytes.
         *
         * @return a fresh copy of the bytes
         */
        public byte[] bytes() {
            return bytes.clone();
        }

        /**
         * The pack revision.
         *
         * @return the {@code ETag} token, without quotes
         */
        public String revision() {
            return revision;
        }

        /**
         * The verified fingerprint.
         *
         * @return the whole-source digest the read agreed on
         */
        public String sourceFingerprint() {
            return sourceFingerprint;
        }

        @Override
        public String toString() {
            return "PayloadRead[" + asset.id() + ", " + bytes.length + " bytes, revision " + revision + "]";
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return this == other
                    || (other instanceof PayloadRead that
                            && asset.equals(that.asset)
                            && Arrays.equals(bytes, that.bytes)
                            && revision.equals(that.revision)
                            && sourceFingerprint.equals(that.sourceFingerprint));
        }

        @Override
        public int hashCode() {
            return Objects.hash(asset, Arrays.hashCode(bytes), revision, sourceFingerprint);
        }
    }
}
