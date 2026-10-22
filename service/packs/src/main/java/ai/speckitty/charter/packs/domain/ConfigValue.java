package ai.speckitty.charter.packs.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An arbitrary nested configuration tree (a styleguide's {@code structural_lint_config}) that keeps what the shared
 * YAML loader parsed: scalars are held as the raw parsed object, never coerced to text, so an integer stays an
 * integer and a quoted number stays a string. Equality is structural.
 */
public sealed interface ConfigValue permits ConfigValue.Scalar, ConfigValue.Seq, ConfigValue.Mapping {

    /**
     * Wraps a parse result recursively.
     *
     * @param parsed a scalar (possibly null), a {@link List}, or a {@link Map} with string keys
     * @return the equivalent tree
     * @throws ClassCastException when a map key is not a string
     */
    static ConfigValue of(@Nullable Object parsed) {
        if (parsed instanceof Map<?, ?> map) {
            LinkedHashMap<String, ConfigValue> entries = new LinkedHashMap<>();
            map.forEach((key, value) -> entries.put((String) key, of(value)));
            return new Mapping(entries);
        }
        if (parsed instanceof List<?> list) {
            List<ConfigValue> items = new ArrayList<>(list.size());
            list.forEach(item -> items.add(of(item)));
            return new Seq(items);
        }
        return new Scalar(parsed);
    }

    /**
     * The inverse of {@link #of}: plain values ready for the emitter.
     *
     * @return a raw scalar (possibly null), a {@link List}, or a {@link LinkedHashMap}
     */
    @Nullable Object toParsed();

    /**
     * A scalar, held as parsed.
     *
     * @param raw the raw parsed scalar; null for a YAML null
     */
    record Scalar(@Nullable Object raw) implements ConfigValue {
        @Override
        public @Nullable Object toParsed() {
            return raw;
        }
    }

    /**
     * A sequence; order and duplicates are significant.
     *
     * @param items the elements
     */
    record Seq(List<ConfigValue> items) implements ConfigValue {
        /** Copies the elements. */
        public Seq {
            items = List.copyOf(items);
        }

        @Override
        public Object toParsed() {
            List<@Nullable Object> out = new ArrayList<>(items.size());
            items.forEach(item -> out.add(item.toParsed()));
            return out;
        }
    }

    /**
     * A mapping; insertion order is kept for deterministic emission, equality is by key and value.
     *
     * @param entries the entries
     */
    record Mapping(LinkedHashMap<String, ConfigValue> entries) implements ConfigValue {
        /** Copies the entries. */
        public Mapping {
            Objects.requireNonNull(entries, "entries");
            entries = new LinkedHashMap<>(entries);
        }

        @Override
        public Object toParsed() {
            LinkedHashMap<String, @Nullable Object> out = new LinkedHashMap<>();
            entries.forEach((key, value) -> out.put(key, value.toParsed()));
            return out;
        }
    }
}
