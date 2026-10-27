package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The YAML form of the pack skill. Its three booleans ({@code required}, {@code user_invocable},
 * {@code model_invocable}) are read and written as booleans and kept as written; no default is filled in and core's
 * forcing of {@code model_invocable} is not applied. The prompt body is not a YAML field: the store reads and writes
 * it beside the file (ADR-0039).
 */
enum SkillYaml implements YamlFormat<SkillDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.SKILL;
    }

    @Override
    public Class<SkillDso> dsoType() {
        return SkillDso.class;
    }

    @Override
    public SkillDso read(Map<?, ?> root) {
        Optional<SkillDso.Expansion> expandsTo = root.get("expands_to") instanceof Map<?, ?> e
                ? Optional.of(new SkillDso.Expansion(required(e, "target"), optional(e, "args")))
                : Optional.empty();
        List<SkillDso.Parameter> parameters = new ArrayList<>();
        for (Object entry : list(root, "parameters")) {
            if (entry instanceof Map<?, ?> p) {
                parameters.add(new SkillDso.Parameter(
                        required(p, "name"), bool(p, "required"), optional(p, "description"), optional(p, "default")));
            }
        }
        Optional<SkillDso.Invocation> invocation = root.get("invocation") instanceof Map<?, ?> i
                ? Optional.of(new SkillDso.Invocation(
                                bool(i, "user_invocable"), bool(i, "model_invocable"), strings(i, "side_effects")))
                        .filter(read -> !read.isEmpty())
                : Optional.empty();
        return new SkillDso(
                required(root, "schema_version"),
                required(root, "id"),
                required(root, "title"),
                required(root, "description"),
                strings(root, "triggers"),
                required(root, "form"),
                optional(root, "body_path"),
                expandsTo,
                parameters,
                invocation,
                strings(root, "tools"),
                optional(root, "version"),
                strings(root, "maintainers"),
                optional(root, "overrides"),
                optional(root, "enhances"),
                Optional.empty());
    }

    @Override
    public String write(SkillDso skill) {
        YamlDumpEmitter out = new YamlDumpEmitter()
                .scalar("schema_version", skill.schemaVersion())
                .scalar("id", skill.id())
                .scalar("title", skill.title())
                .scalar("description", skill.description())
                .strings("triggers", skill.triggers())
                .scalar("form", skill.form())
                .scalar("body_path", skill.bodyPath());
        skill.expandsTo().ifPresent(expansion -> {
            Map<String, Object> e = YamlDumpEmitter.mapping();
            e.put("target", expansion.target());
            YamlDumpEmitter.scalarInto(e, "args", expansion.args());
            out.present("expands_to", e);
        });
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (SkillDso.Parameter parameter : skill.parameters()) {
            Map<String, Object> p = YamlDumpEmitter.mapping();
            p.put("name", parameter.name());
            parameter.required().ifPresent(value -> p.put("required", value));
            YamlDumpEmitter.scalarInto(p, "description", parameter.description());
            YamlDumpEmitter.scalarInto(p, "default", parameter.defaultValue());
            parameters.add(p);
        }
        out.mappings("parameters", parameters);
        skill.invocation().filter(invocation -> !invocation.isEmpty()).ifPresent(invocation -> {
            Map<String, Object> i = YamlDumpEmitter.mapping();
            invocation.userInvocable().ifPresent(value -> i.put("user_invocable", value));
            invocation.modelInvocable().ifPresent(value -> i.put("model_invocable", value));
            YamlDumpEmitter.stringsInto(i, "side_effects", invocation.sideEffects());
            out.present("invocation", i);
        });
        return out.strings("tools", skill.tools())
                .scalar("version", skill.version())
                .strings("maintainers", skill.maintainers())
                .scalar("overrides", skill.overrides())
                .scalar("enhances", skill.enhances())
                .dump();
    }

    /** A boolean field as written; empty when absent or null. The schema has checked that a present value is one. */
    private static Optional<Boolean> bool(Map<?, ?> owner, String key) {
        return owner.get(key) instanceof Boolean value ? Optional.of(value) : Optional.empty();
    }
}
