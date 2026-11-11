package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.mappings;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.Principle;
import ai.speckitty.charter.packs.domain.Principles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The one reader and writer of a {@code principles} list (ADR-0067), shared by the paradigm and styleguide formats,
 * and the rule the schema cannot state: a repeated name, reported as a {@code uniqueItems} violation at the repeated
 * item's name so the author gets a pointer.
 */
final class PrincipleYaml {

    static final String KEY = "principles";

    private PrincipleYaml() {}

    /** The principles of a schema-valid root, in file order; empty when the key is absent. */
    static List<PrincipleDso> read(Map<?, ?> root) {
        return mappings(root, KEY).stream()
                .map(item -> new PrincipleDso(required(item, "name"), required(item, "description")))
                .toList();
    }

    /** The principles as the mappings a writer emits, in order. */
    static List<Map<String, Object>> write(List<PrincipleDso> principles) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PrincipleDso principle : principles) {
            Map<String, Object> item = YamlDumpEmitter.mapping();
            item.put("name", principle.name());
            item.put("description", principle.description());
            out.add(item);
        }
        return out;
    }

    /** A repeated name of a schema-valid root, as a violation at {@code /principles/<n>/name}. */
    static List<SchemaViolation> rules(Map<?, ?> root, String file) {
        List<String> names = read(root).stream().map(PrincipleDso::name).toList();
        OptionalInt repeat = Principles.firstRepeatedName(names);
        if (repeat.isEmpty()) {
            return List.of();
        }
        String name = names.get(repeat.getAsInt());
        return List.of(new SchemaViolation(
                file,
                List.of(KEY, String.valueOf(repeat.getAsInt()), "name"),
                "uniqueItems",
                Optional.empty(),
                Optional.of(name),
                Map.of()));
    }

    /** The domain principles of a list of items. */
    static List<Principle> toDomain(List<PrincipleDso> principles) {
        return principles.stream()
                .map(item -> new Principle(item.name(), item.description()))
                .toList();
    }

    /** The items of a list of domain principles. */
    static List<PrincipleDso> toDso(List<Principle> principles) {
        return principles.stream()
                .map(item -> new PrincipleDso(item.name(), item.description()))
                .toList();
    }
}
