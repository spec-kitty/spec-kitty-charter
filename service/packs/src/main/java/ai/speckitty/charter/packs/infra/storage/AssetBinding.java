package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetIdentifier;

/**
 * The {@link KindBinding} of the asset sidecar: {@link AssetDso} to {@link Asset} and back. A new sidecar is created as
 * {@code <id>.asset.yaml}. The payload is not mapped here: it is an asset file of the pack, and the store refuses a
 * sidecar whose payload it did not read.
 */
enum AssetBinding implements KindBinding<AssetDso, Asset> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.ASSET;
    }

    @Override
    public Class<Asset> entityType() {
        return Asset.class;
    }

    @Override
    public Class<AssetDso> dsoType() {
        return AssetDso.class;
    }

    @Override
    public String idOf(AssetDso dso) {
        return dso.id();
    }

    @Override
    public Asset toEntity(PackIdentifier pack, AssetDso dso) {
        return Asset.builder()
                .id(new AssetIdentifier(dso.id()))
                .packId(pack)
                .mime(dso.mime())
                .path(dso.path())
                .title(dso.title())
                .build();
    }

    @Override
    public AssetDso toDso(Asset asset) {
        return new AssetDso(
                asset.id().value(), asset.mime(), asset.path(), asset.title().orElse(null));
    }
}
