package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Map;

/** The YAML form of the toolguide. */
enum ToolguideYaml implements YamlFormat<ToolguideDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.TOOLGUIDE;
    }

    @Override
    public Class<ToolguideDso> dsoType() {
        return ToolguideDso.class;
    }

    @Override
    public ToolguideDso read(Map<?, ?> root) {
        return new ToolguideDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "tool"),
                required(root, "title"),
                required(root, "guide_path"),
                required(root, "summary"),
                strings(root, "commands"),
                strings(root, "applies_to_languages"),
                optional(root, "last_updated"),
                strings(root, "references"));
    }

    @Override
    public String write(ToolguideDso toolguide) {
        return new YamlDumpEmitter()
                .scalar("id", toolguide.id())
                .scalar("schema_version", toolguide.schemaVersion())
                .scalar("tool", toolguide.tool())
                .scalar("title", toolguide.title())
                .scalar("guide_path", toolguide.guidePath())
                .scalar("summary", toolguide.summary())
                .strings("commands", toolguide.commands())
                .strings("applies_to_languages", toolguide.appliesToLanguages())
                .scalar("last_updated", toolguide.lastUpdated())
                .strings("references", toolguide.references())
                .dump();
    }
}
