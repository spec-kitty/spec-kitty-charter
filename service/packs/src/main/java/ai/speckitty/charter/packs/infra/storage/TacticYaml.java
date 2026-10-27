package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The YAML form of the tactic. */
enum TacticYaml implements YamlFormat<TacticDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.TACTIC;
    }

    @Override
    public Class<TacticDso> dsoType() {
        return TacticDso.class;
    }

    @Override
    public TacticDso read(Map<?, ?> root) {
        List<TacticDso.Step> steps = new ArrayList<>();
        for (Object step : list(root, "steps")) {
            if (step instanceof Map<?, ?> s) {
                steps.add(new TacticDso.Step(
                        required(s, "title"), optional(s, "description"), strings(s, "examples"), references(s)));
            }
        }
        return new TacticDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "name"),
                steps,
                optional(root, "purpose"),
                optional(root, "notes"),
                strings(root, "failure_modes"),
                strings(root, "applies_to_languages"),
                references(root));
    }

    @Override
    public String write(TacticDso tactic) {
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("id", tactic.id())
                .scalar("schema_version", tactic.schemaVersion())
                .scalar("name", tactic.name())
                .scalar("purpose", tactic.purpose());
        List<Map<String, Object>> steps = new ArrayList<>();
        for (TacticDso.Step step : tactic.steps()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("title", step.title());
            YamlDumpEmitter.scalarInto(out, "description", step.description());
            YamlDumpEmitter.stringsInto(out, "examples", step.examples());
            YamlDumpEmitter.mappingsInto(out, "references", referencesOut(step.references()));
            steps.add(out);
        }
        return emitter.mappings("steps", steps)
                .strings("failure_modes", tactic.failureModes())
                .strings("applies_to_languages", tactic.appliesToLanguages())
                .mappings("references", referencesOut(tactic.references()))
                .scalar("notes", tactic.notes())
                .dump();
    }

    private static List<Map<String, Object>> referencesOut(List<TacticDso.Reference> references) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TacticDso.Reference reference : references) {
            Map<String, Object> entry = YamlDumpEmitter.mapping();
            entry.put("name", reference.name());
            entry.put("type", reference.type());
            entry.put("id", reference.id());
            entry.put("when", reference.when());
            out.add(entry);
        }
        return out;
    }

    private static List<TacticDso.Reference> references(Map<?, ?> owner) {
        List<TacticDso.Reference> out = new ArrayList<>();
        for (Object entry : list(owner, "references")) {
            if (entry instanceof Map<?, ?> r) {
                out.add(new TacticDso.Reference(
                        required(r, "name"), required(r, "type"), required(r, "id"), required(r, "when")));
            }
        }
        return out;
    }
}
