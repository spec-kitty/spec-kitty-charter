package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
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
        List<DirectiveDso.Reference> references = new ArrayList<>();
        for (Object entry : list(root, "references")) {
            if (entry instanceof Map<?, ?> r) {
                references.add(new DirectiveDso.Reference(
                        required(r, "type"), required(r, "id"), optional(r, "when"), optional(r, "reason")));
            }
        }
        return new DirectiveDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "title"),
                required(root, "intent"),
                required(root, "enforcement"),
                optional(root, "scope"),
                strings(root, "procedures"),
                strings(root, "integrity_rules"),
                strings(root, "validation_criteria"),
                strings(root, "explicit_allowances"),
                references);
    }

    @Override
    public String write(DirectiveDso directive) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (DirectiveDso.Reference reference : directive.references()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("type", reference.type());
            out.put("id", reference.id());
            YamlDumpEmitter.scalarInto(out, "when", reference.when());
            YamlDumpEmitter.scalarInto(out, "reason", reference.reason());
            references.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("id", directive.id())
                .scalar("schema_version", directive.schemaVersion())
                .scalar("title", directive.title())
                .scalar("intent", directive.intent())
                .scalar("enforcement", directive.enforcement())
                .scalar("scope", directive.scope())
                .strings("procedures", directive.procedures())
                .strings("integrity_rules", directive.integrityRules())
                .strings("validation_criteria", directive.validationCriteria())
                .strings("explicit_allowances", directive.explicitAllowances())
                .mappings("references", references)
                .dump();
    }
}
