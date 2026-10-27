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
