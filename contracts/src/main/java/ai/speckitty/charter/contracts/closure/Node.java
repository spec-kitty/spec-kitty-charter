package ai.speckitty.charter.contracts.closure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A value of the loaded bundle and where it sits.
 *
 * @param value   a mapping, list, boolean or scalar as the YAML loader produced it; {@code null} for a YAML null
 * @param pointer where the value sits in the document
 */
record Node(@Nullable Object value, JsonPointer pointer) {

    boolean isMap() {
        return value instanceof Map<?, ?>;
    }

    /** A JSON Schema is a mapping or a boolean. */
    boolean isSchema() {
        return value instanceof Boolean || isMap();
    }

    /** The keys of a mapping, as strings, in document order; none for any other value. */
    List<String> keys() {
        return value instanceof Map<?, ?> map
                ? map.keySet().stream().map(String::valueOf).toList()
                : List.of();
    }

    boolean has(String key) {
        return value instanceof Map<?, ?> map && map.containsKey(key);
    }

    Optional<Node> child(String key) {
        return value instanceof Map<?, ?> map && map.containsKey(key)
                ? Optional.of(new Node(map.get(key), pointer.child(key)))
                : Optional.empty();
    }

    /** The values of a mapping, each with its pointer; none for any other value. */
    List<Node> members() {
        List<Node> members = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, member) -> members.add(new Node(member, pointer.child(String.valueOf(key)))));
        }
        return members;
    }

    /** The values of the mapping under {@code key}; a value there that is not a mapping is reported. */
    List<Node> entries(String key, Findings findings) {
        return child(key)
                .map(node -> {
                    if (!node.isMap()) {
                        findings.malformed(node.pointer(), "must be a mapping");
                    }
                    return node.members();
                })
                .orElse(List.of());
    }

    /** The elements of the list under {@code key}; a value there that is not a non-empty list is reported. */
    List<Node> elements(String key, Findings findings) {
        Optional<Node> node = child(key);
        if (node.isEmpty()) {
            return List.of();
        }
        if (!(node.get().value() instanceof List<?> list) || list.isEmpty()) {
            findings.malformed(node.get().pointer(), "must be a non-empty list of schemas");
            return List.of();
        }
        List<Node> elements = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            elements.add(new Node(list.get(i), node.get().pointer().child(i)));
        }
        return elements;
    }
}
