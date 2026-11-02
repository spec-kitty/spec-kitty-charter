package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.ConfigValue;
import java.util.Map;
import java.util.Optional;

/**
 * The YAML form of the guard (ADR-0054 decision 1), in schema key order. The schema has already refused unknown keys
 * and wrong types before {@link #read(Map)} runs. {@code config_schema} is an open tree (ADR-0042 S4 as amended), held
 * as parsed and written back as held.
 */
enum GuardYaml implements YamlFormat<GuardDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.GUARD;
    }

    @Override
    public Class<GuardDso> dsoType() {
        return GuardDso.class;
    }

    @Override
    public GuardDso read(Map<?, ?> root) {
        Optional<ConfigValue> configSchema = root.get("config_schema") instanceof Map<?, ?> schema
                ? Optional.of(ConfigValue.of(schema))
                : Optional.empty();
        Optional<GuardDso.Limits> limits = root.get("limits") instanceof Map<?, ?> l
                ? Optional.of(new GuardDso.Limits(
                        l.get("timeout_seconds") instanceof Number n ? Optional.of(n.intValue()) : Optional.empty(),
                        l.get("network") instanceof Boolean b ? Optional.of(b) : Optional.empty()))
                : Optional.empty();
        return new GuardDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "title"),
                optional(root, "description"),
                required(root, "interface"),
                required(root, "implemented_by"),
                configSchema,
                limits,
                optional(root, "on_indeterminate"));
    }

    @Override
    public String write(GuardDso guard) {
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("schema_version", guard.schemaVersion())
                .scalar("id", guard.id())
                .scalar("title", guard.title())
                .scalar("description", guard.description())
                .scalar("interface", guard.interfaceName())
                .scalar("implemented_by", guard.implementedBy());
        guard.configSchema().ifPresent(schema -> emitter.present("config_schema", schema.toParsed()));
        guard.limits().ifPresent(limits -> {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            limits.timeoutSeconds().ifPresent(t -> out.put("timeout_seconds", t));
            limits.network().ifPresent(n -> out.put("network", n));
            emitter.present("limits", out);
        });
        return emitter.scalar("on_indeterminate", guard.onIndeterminate()).dump();
    }
}
