package ai.speckitty.charter.packs.infra.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reads the fields of a schema-valid YAML mapping, shared by the {@link YamlFormat}s. Scalars are read as their text,
 * an explicit null reads like an absent key, and list order and duplicates are kept.
 */
final class YamlFields {

    private YamlFields() {}

    static List<String> strings(Map<?, ?> owner, String key) {
        List<String> out = new ArrayList<>();
        for (Object value : list(owner, key)) {
            text(value).ifPresent(out::add);
        }
        return out;
    }

    static List<?> list(Map<?, ?> owner, String key) {
        return owner.get(key) instanceof List<?> l ? l : List.of();
    }

    /**
     * The entries of a list of mappings. The schema has refused any other entry, so one is a reader's error, thrown
     * rather than skipped: no entry is ever dropped in silence.
     *
     * @param owner the mapping that holds the list
     * @param key the list's key
     * @return the entries, in order; empty when the key is absent
     * @throws IllegalStateException when an entry is not a mapping
     */
    static List<Map<?, ?>> mappings(Map<?, ?> owner, String key) {
        List<Map<?, ?>> out = new ArrayList<>();
        for (Object entry : list(owner, key)) {
            if (!(entry instanceof Map<?, ?> mapping)) {
                throw new IllegalStateException("an entry of '" + key + "' passed the schema but is not a mapping");
            }
            out.add(mapping);
        }
        return out;
    }

    static String required(Map<?, ?> owner, String key) {
        return optional(owner, key)
                .orElseThrow(() -> new IllegalStateException("'" + key + "' passed the schema but is absent or null"));
    }

    static Optional<String> optional(Map<?, ?> owner, String key) {
        return text(owner.get(key));
    }

    /** A scalar as its text; null and anything that is no scalar are empty. */
    static Optional<String> text(@Nullable Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value));
    }
}
