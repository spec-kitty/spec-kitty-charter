package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Map;
import java.util.Optional;

/** The YAML form of the asset sidecar. An absent title is not written; {@code title: null} reads as absent. */
enum AssetYaml implements YamlFormat<AssetDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.ASSET;
    }

    @Override
    public Class<AssetDso> dsoType() {
        return AssetDso.class;
    }

    @Override
    public AssetDso read(Map<?, ?> root) {
        return new AssetDso(
                required(root, "id"),
                required(root, "mime"),
                required(root, "path"),
                optional(root, "title").orElse(null));
    }

    @Override
    public String write(AssetDso asset) {
        return new YamlDumpEmitter()
                .scalar("id", asset.id())
                .scalar("mime", asset.mime())
                .scalar("path", asset.path())
                .scalar("title", Optional.ofNullable(asset.title()))
                .dump();
    }
}
