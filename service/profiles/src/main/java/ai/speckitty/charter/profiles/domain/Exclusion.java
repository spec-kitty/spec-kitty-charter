package ai.speckitty.charter.profiles.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What an {@link AgentProfile} removes from the profile it is resolved over: its {@code excluding}, in one of the
 * schema's two forms. It is held as authored; applying it is resolution, which is not this slice's job yet (ADR-0032).
 */
public sealed interface Exclusion {

    /**
     * The list form: whole fields to remove.
     *
     * @param fields the field names, in authored order with duplicates kept
     */
    record WholeFields(List<String> fields) implements Exclusion {
        /**
         * Copies the list.
         *
         * @param fields the field names
         */
        public WholeFields {
            fields = List.copyOf(fields);
        }
    }

    /**
     * The map form: values to remove from list fields. The map is data, not fields: its keys are not checked, and an
     * entry with no values is kept.
     *
     * @param values the values to remove per field name, in authored key order
     */
    record FieldValues(Map<String, List<String>> values) implements Exclusion {
        /**
         * Copies the map and its lists, keeping the key order.
         *
         * @param values the values per field name
         */
        public FieldValues {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            values.forEach((field, removed) -> copy.put(Objects.requireNonNull(field, "field"), List.copyOf(removed)));
            values = Collections.unmodifiableMap(copy);
        }
    }
}
