package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.domain.Tactic;
import ai.speckitty.charter.domain.TacticReference;
import ai.speckitty.charter.domain.TacticStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reads one {@code *.tactic.yaml} through the bundled {@code tactic} schema. A file that is not YAML or breaks the
 * schema is {@link FileRead.Unreadable}; any other is a {@link Tactic} of what the file states. Scalars are
 * stringified, and an explicit null reads like an absent key. List order and duplicates are kept.
 */
final class TacticReader {

    private static final String KIND = "tactic";

    private TacticReader() {}

    static FileRead<Tactic> read(String text, String file) {
        ParsedYaml parsed = ParsedYaml.parse(text, file);
        if (parsed.failure() != null) {
            return new FileRead.Unreadable<>(file, parsed.failure(), List.of());
        }
        Object document = parsed.document();
        List<SchemaViolation> violations = PackSchemas.bundled().validate(KIND, file, document);
        if (!violations.isEmpty()) {
            return new FileRead.Unreadable<>(file, null, violations);
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalStateException("a tactic passed the schema but is no mapping");
        }
        List<TacticStep> steps = new ArrayList<>();
        for (Object step : list(root, "steps")) {
            if (step instanceof Map<?, ?> s) {
                steps.add(new TacticStep(
                        required(s, "title"), optional(s, "description"), strings(s, "examples"), references(s)));
            }
        }
        return new FileRead.Read<>(
                file,
                new Tactic(
                        required(root, "id"),
                        required(root, "schema_version"),
                        required(root, "name"),
                        steps,
                        optional(root, "purpose"),
                        optional(root, "notes"),
                        strings(root, "failure_modes"),
                        strings(root, "applies_to_languages"),
                        references(root)));
    }

    private static List<TacticReference> references(Map<?, ?> owner) {
        List<TacticReference> out = new ArrayList<>();
        for (Object entry : list(owner, "references")) {
            if (entry instanceof Map<?, ?> r) {
                out.add(new TacticReference(
                        required(r, "name"), required(r, "type"), required(r, "id"), required(r, "when")));
            }
        }
        return out;
    }

    private static List<String> strings(Map<?, ?> owner, String key) {
        List<String> out = new ArrayList<>();
        for (Object value : list(owner, key)) {
            text(value).ifPresent(out::add);
        }
        return out;
    }

    private static List<?> list(Map<?, ?> owner, String key) {
        return owner.get(key) instanceof List<?> l ? l : List.of();
    }

    private static String required(Map<?, ?> owner, String key) {
        return optional(owner, key)
                .orElseThrow(() -> new IllegalStateException("'" + key + "' passed the schema but is absent or null"));
    }

    private static Optional<String> optional(Map<?, ?> owner, String key) {
        return text(owner.get(key));
    }

    /** A scalar as its text; null and anything that is no scalar are empty. */
    private static Optional<String> text(@Nullable Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value));
    }
}
