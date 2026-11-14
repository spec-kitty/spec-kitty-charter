package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.mappings;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The YAML form of the glossary context (ADR-0049). Keys are written in the shape's order: {@code schema_version, id,
 * name, description, tags, applies_to, terms}, and per term {@code id, name, definition, examples, aliases, avoid,
 * status, see_also}; an empty list or an {@code applies_to} that states nothing is omitted. The rule the schema cannot
 * state, a term id repeated within the context, is reported as a {@code uniqueItems} violation at the repeat's id, so
 * validate mode and the load refuse the same file with a pointer.
 */
enum GlossaryYaml implements YamlFormat<GlossaryDso> {
    /** The format. */
    INSTANCE;

    private static final String APPLIES_TO = "applies_to";

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.GLOSSARY;
    }

    @Override
    public Class<GlossaryDso> dsoType() {
        return GlossaryDso.class;
    }

    @Override
    public GlossaryDso read(Map<?, ?> root) {
        List<GlossaryDso.Term> terms = new ArrayList<>();
        for (Map<?, ?> t : mappings(root, "terms")) {
            terms.add(new GlossaryDso.Term(
                    required(t, "id"),
                    required(t, "name"),
                    required(t, "definition"),
                    strings(t, "examples"),
                    strings(t, "aliases"),
                    strings(t, "avoid"),
                    required(t, "status"),
                    strings(t, "see_also")));
        }
        Map<?, ?> appliesTo = root.get(APPLIES_TO) instanceof Map<?, ?> a ? a : Map.of();
        return new GlossaryDso(
                required(root, "schema_version"),
                required(root, "id"),
                required(root, "name"),
                required(root, "description"),
                strings(root, "tags"),
                new GlossaryDso.AppliesTo(
                        strings(appliesTo, "paths"), strings(appliesTo, "languages"), strings(appliesTo, "stacks")),
                terms);
    }

    /** Each term id repeated in a schema-valid root, once, as a violation at {@code /terms/<n>/id} of its first repeat. */
    @Override
    public List<SchemaViolation> rules(Map<?, ?> root, String file) {
        List<Map<?, ?>> terms = mappings(root, "terms");
        Set<String> seen = new HashSet<>();
        Set<String> reported = new HashSet<>();
        List<SchemaViolation> violations = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            String id = required(terms.get(i), "id");
            if (!seen.add(id) && reported.add(id)) {
                violations.add(new SchemaViolation(
                        file,
                        List.of("terms", String.valueOf(i), "id"),
                        "uniqueItems",
                        Optional.empty(),
                        Optional.of(id),
                        Map.of()));
            }
        }
        return violations;
    }

    @Override
    public String write(GlossaryDso context) {
        Map<String, Object> appliesTo = YamlDumpEmitter.mapping();
        YamlDumpEmitter.stringsInto(appliesTo, "paths", context.appliesTo().paths());
        YamlDumpEmitter.stringsInto(appliesTo, "languages", context.appliesTo().languages());
        YamlDumpEmitter.stringsInto(appliesTo, "stacks", context.appliesTo().stacks());
        List<Map<String, Object>> terms = new ArrayList<>();
        for (GlossaryDso.Term term : context.terms()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("id", term.id());
            out.put("name", term.name());
            out.put("definition", term.definition());
            YamlDumpEmitter.stringsInto(out, "examples", term.examples());
            YamlDumpEmitter.stringsInto(out, "aliases", term.aliases());
            YamlDumpEmitter.stringsInto(out, "avoid", term.avoid());
            out.put("status", term.status());
            YamlDumpEmitter.stringsInto(out, "see_also", term.seeAlso());
            terms.add(out);
        }
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("schema_version", context.schemaVersion())
                .scalar("id", context.id())
                .scalar("name", context.name())
                .scalar("description", context.description())
                .strings("tags", context.tags());
        if (!appliesTo.isEmpty()) {
            emitter.present(APPLIES_TO, appliesTo);
        }
        return emitter.mappings("terms", terms).dump();
    }
}
