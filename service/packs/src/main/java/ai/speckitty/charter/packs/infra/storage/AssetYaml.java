package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Map;
import java.util.Optional;

/**
 * The YAML form of the asset sidecar. An absent title is not written; {@code title: null} reads as absent. The
 * {@code executable} block (ADR-0054 decision 5) is written last, in schema key order.
 */
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
                optional(root, "title").orElse(null),
                root.get("executable") instanceof Map<?, ?> e
                        ? Optional.of(new AssetDso.Executable(
                                required(e, "runtime"),
                                optional(e, "min_version"),
                                required(e, "entry"),
                                strings(e, "args")))
                        : Optional.empty());
    }

    @Override
    public String write(AssetDso asset) {
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("id", asset.id())
                .scalar("mime", asset.mime())
                .scalar("path", asset.path())
                .scalar("title", Optional.ofNullable(asset.title()));
        asset.executable().ifPresent(executable -> {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("runtime", executable.runtime());
            YamlDumpEmitter.scalarInto(out, "min_version", executable.minVersion());
            out.put("entry", executable.entry());
            YamlDumpEmitter.stringsInto(out, "args", executable.args());
            emitter.present("executable", out);
        });
        return emitter.dump();
    }
}
