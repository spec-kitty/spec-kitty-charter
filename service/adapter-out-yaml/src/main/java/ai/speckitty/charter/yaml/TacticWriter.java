package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.Tactic;
import ai.speckitty.charter.domain.TacticReference;
import ai.speckitty.charter.domain.TacticStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serializes a {@link Tactic} to {@code *.tactic.yaml} text from scratch, through {@link YamlDumpEmitter}. Order and
 * duplicates are exactly those the model holds; absent optionals are omitted, never written as null.
 */
final class TacticWriter {

    private TacticWriter() {}

    static String write(Tactic tactic) {
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("id", tactic.id())
                .scalar("schema_version", tactic.schemaVersion())
                .scalar("name", tactic.name())
                .scalar("purpose", tactic.purpose());
        List<Map<String, Object>> steps = new ArrayList<>();
        for (TacticStep step : tactic.steps()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("title", step.title());
            YamlDumpEmitter.scalarInto(out, "description", step.description());
            YamlDumpEmitter.stringsInto(out, "examples", step.examples());
            YamlDumpEmitter.mappingsInto(out, "references", references(step.references()));
            steps.add(out);
        }
        return emitter.mappings("steps", steps)
                .strings("failure_modes", tactic.failureModes())
                .strings("applies_to_languages", tactic.appliesToLanguages())
                .mappings("references", references(tactic.references()))
                .scalar("notes", tactic.notes())
                .dump();
    }

    private static List<Map<String, Object>> references(List<TacticReference> references) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TacticReference reference : references) {
            Map<String, Object> entry = YamlDumpEmitter.mapping();
            entry.put("name", reference.name());
            entry.put("type", reference.type());
            entry.put("id", reference.id());
            entry.put("when", reference.when());
            out.add(entry);
        }
        return out;
    }
}
