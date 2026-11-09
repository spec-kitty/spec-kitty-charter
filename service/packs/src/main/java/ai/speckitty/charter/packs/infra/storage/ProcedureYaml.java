package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.mappings;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The YAML form of the procedure, in the sanitized shape only. The schema has already refused unknown and retired
 * keys before {@link #read(Map)} runs, so this format reads no retired spelling and has no fallback for one.
 */
enum ProcedureYaml implements YamlFormat<ProcedureDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.PROCEDURE;
    }

    @Override
    public Class<ProcedureDso> dsoType() {
        return ProcedureDso.class;
    }

    @Override
    public ProcedureDso read(Map<?, ?> root) {
        List<ProcedureDso.Step> steps = new ArrayList<>();
        for (Map<?, ?> s : mappings(root, "steps")) {
            steps.add(new ProcedureDso.Step(
                    required(s, "title"),
                    optional(s, "description"),
                    optional(s, "role"),
                    optional(s, "on_success"),
                    optional(s, "on_failure")));
        }
        List<ProcedureDso.AntiPattern> antiPatterns = new ArrayList<>();
        for (Map<?, ?> a : mappings(root, "anti_patterns")) {
            antiPatterns.add(new ProcedureDso.AntiPattern(
                    required(a, "title"),
                    optional(a, "content"),
                    optional(a, "bad_example"),
                    optional(a, "good_example")));
        }
        List<ProcedureDso.Reference> references = new ArrayList<>();
        for (Map<?, ?> r : mappings(root, "references")) {
            references.add(new ProcedureDso.Reference(
                    required(r, "type"), required(r, "id"), optional(r, "when"), optional(r, "reason")));
        }
        return new ProcedureDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "title"),
                required(root, "description"),
                required(root, "entry_condition"),
                required(root, "exit_condition"),
                steps,
                antiPatterns,
                references,
                optional(root, "notes"),
                strings(root, "applies_to_languages"));
    }

    @Override
    public String write(ProcedureDso procedure) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (ProcedureDso.Step step : procedure.steps()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("title", step.title());
            YamlDumpEmitter.scalarInto(out, "description", step.description());
            YamlDumpEmitter.scalarInto(out, "role", step.role());
            YamlDumpEmitter.scalarInto(out, "on_success", step.onSuccess());
            YamlDumpEmitter.scalarInto(out, "on_failure", step.onFailure());
            steps.add(out);
        }
        List<Map<String, Object>> antiPatterns = new ArrayList<>();
        for (ProcedureDso.AntiPattern antiPattern : procedure.antiPatterns()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("title", antiPattern.title());
            YamlDumpEmitter.scalarInto(out, "content", antiPattern.content());
            YamlDumpEmitter.scalarInto(out, "bad_example", antiPattern.badExample());
            YamlDumpEmitter.scalarInto(out, "good_example", antiPattern.goodExample());
            antiPatterns.add(out);
        }
        List<Map<String, Object>> references = new ArrayList<>();
        for (ProcedureDso.Reference reference : procedure.references()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("type", reference.type());
            out.put("id", reference.id());
            YamlDumpEmitter.scalarInto(out, "when", reference.when());
            YamlDumpEmitter.scalarInto(out, "reason", reference.reason());
            references.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("schema_version", procedure.schemaVersion())
                .scalar("id", procedure.id())
                .scalar("title", procedure.title())
                .scalar("description", procedure.description())
                .scalar("entry_condition", procedure.entryCondition())
                .scalar("exit_condition", procedure.exitCondition())
                .mappings("steps", steps)
                .mappings("anti_patterns", antiPatterns)
                .mappings("references", references)
                .scalar("notes", procedure.notes())
                .strings("applies_to_languages", procedure.appliesToLanguages())
                .dump();
    }
}
