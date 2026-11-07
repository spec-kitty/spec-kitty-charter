package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Map;

/** The YAML form of the directive. */
enum DirectiveYaml implements YamlFormat<DirectiveDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.DIRECTIVE;
    }

    @Override
    public Class<DirectiveDso> dsoType() {
        return DirectiveDso.class;
    }

    @Override
    public DirectiveDso read(Map<?, ?> root) {
        return new DirectiveDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "title"),
                required(root, "intent"),
                required(root, "enforcement"),
                optional(root, "scope"),
                strings(root, "integrity_rules"),
                strings(root, "validation_criteria"),
                strings(root, "explicit_allowances"));
    }

    @Override
    public String write(DirectiveDso directive) {
        return new YamlDumpEmitter()
                .scalar("id", directive.id())
                .scalar("schema_version", directive.schemaVersion())
                .scalar("title", directive.title())
                .scalar("intent", directive.intent())
                .scalar("enforcement", directive.enforcement())
                .scalar("scope", directive.scope())
                .strings("integrity_rules", directive.integrityRules())
                .strings("validation_criteria", directive.validationCriteria())
                .strings("explicit_allowances", directive.explicitAllowances())
                .dump();
    }
}
