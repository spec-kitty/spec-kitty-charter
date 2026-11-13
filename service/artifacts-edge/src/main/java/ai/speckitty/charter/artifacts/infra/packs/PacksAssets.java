package ai.speckitty.charter.artifacts.infra.packs;

import ai.speckitty.charter.artifacts.application.AssetReads;
import ai.speckitty.charter.artifacts.application.AssetView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.Executable;
import ai.speckitty.charter.packs.domain.MediaTypes;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link AssetReads} over the packs slice: the one class of the artifacts edge that asks it for assets, and only its
 * application ring ({@link ForeignArtifactRead}) and domain. Each read is one verified read of the packs slice; this
 * adapter narrows the entities to the packs domain's {@link Asset} and maps them to the edge's own {@link AssetView},
 * judging the mime by the packs domain's one media-type rule ({@link MediaTypes#wellFormed}), so nothing above it names
 * a packs type.
 */
@Component
public final class PacksAssets implements AssetReads {

    private final ForeignArtifactRead packs;

    /**
     * Reads over the packs slice's read of the artifacts another slice renders.
     *
     * @param packs the verified reads of an artifact, a kind's artifacts and an asset's payload
     */
    @Autowired
    public PacksAssets(ForeignArtifactRead packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    @Override
    public Listed list(PackIdentifier pack) {
        ConsistentRead.Verified<ForeignArtifactRead.Held> verified = packs.held(pack, ArtifactKind.ASSET);
        return new Listed(
                verified.value().artifacts().stream()
                        .map(Asset.class::cast)
                        .map(PacksAssets::viewOf)
                        .sorted(Comparator.comparing(AssetView::id))
                        .toList(),
                verified.value().revision().token(),
                verified.fingerprint());
    }

    @Override
    public Read asset(PackIdentifier pack, String assetId) {
        ConsistentRead.Verified<ForeignArtifactRead.Foreign> verified =
                packs.read(pack, ArtifactKind.ASSET, assetId, 0);
        return new Read(
                viewOf(Asset.class.cast(verified.value().artifact())),
                verified.value().revision().token(),
                verified.fingerprint());
    }

    @Override
    public PayloadRead payload(PackIdentifier pack, String assetId) {
        ConsistentRead.Verified<ForeignArtifactRead.AssetPayload> verified = packs.payload(pack, assetId);
        ForeignArtifactRead.AssetPayload read = verified.value();
        return new PayloadRead(
                viewOf(read.asset()), read.payload().bytes(), read.revision().token(), verified.fingerprint());
    }

    @Override
    public Listed list(PackIdentifier pack, String expectedFingerprint) {
        Listed listed = list(pack);
        Fingerprints.require(listed.sourceFingerprint(), expectedFingerprint);
        return listed;
    }

    @Override
    public Read asset(PackIdentifier pack, String assetId, String expectedFingerprint) {
        Read read = asset(pack, assetId);
        Fingerprints.require(read.sourceFingerprint(), expectedFingerprint);
        return read;
    }

    @Override
    public PayloadRead payload(PackIdentifier pack, String assetId, String expectedFingerprint) {
        // The packs slice's own check: the bytes are of the inputs the caller judged, or stale_input.
        ConsistentRead.Verified<ForeignArtifactRead.AssetPayload> verified =
                packs.payload(pack, assetId, expectedFingerprint);
        ForeignArtifactRead.AssetPayload read = verified.value();
        return new PayloadRead(
                viewOf(read.asset()), read.payload().bytes(), read.revision().token(), verified.fingerprint());
    }

    /**
     * The edge's view of a packs asset; the mime is judged by the packs domain's rule, which validate applies too.
     *
     * @param asset the packs slice's asset
     * @return the view
     */
    static AssetView viewOf(Asset asset) {
        return new AssetView(
                asset.localId(),
                asset.packId(),
                asset.mime(),
                MediaTypes.wellFormed(asset.mime()),
                asset.path(),
                asset.title(),
                asset.executable().map(PacksAssets::executableOf));
    }

    private static AssetView.Executable executableOf(Executable executable) {
        return new AssetView.Executable(
                executable.runtime().token(), executable.minVersion(), executable.entry(), executable.args());
    }
}
