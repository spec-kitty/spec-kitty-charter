package ai.speckitty.charter.packs.infra.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

/**
 * The one serialization primitive of the pack writers: a value built as an ordered map is dumped as block-style YAML
 * through SnakeYAML's {@link Dump}, from scratch. It builds documents; it never edits one (that is
 * {@link YamlDocument}'s job on the read side).
 *
 * <p>Two decisions live here so both writers share them: an absent optional (empty {@link Optional}, empty list) omits
 * its key, so no {@code key: null} is ever written; and lists keep the order and duplicates they are given. The dump
 * uses {@link Yaml#dumpSchema()}, so a string that this service's reader or a YAML 1.1 reader (core's) would read back
 * as a number, boolean, null or date is quoted.
 */
final class YamlDumpEmitter {

    private final Map<String, Object> root = new LinkedHashMap<>();

    /** An emitter with an empty root mapping. */
    YamlDumpEmitter() {}

    /** A fresh ordered mapping, for a list entry. */
    static Map<String, Object> mapping() {
        return new LinkedHashMap<>();
    }

    /** Adds {@code key: value}. */
    YamlDumpEmitter scalar(String key, String value) {
        root.put(key, value);
        return this;
    }

    /** Adds {@code key: value} when the optional is present; omits the key otherwise. */
    YamlDumpEmitter scalar(String key, Optional<String> value) {
        scalarInto(root, key, value);
        return this;
    }

    /** Adds a list of strings when it is not empty; omits the key otherwise. */
    YamlDumpEmitter strings(String key, List<String> values) {
        stringsInto(root, key, values);
        return this;
    }

    /** Adds a list of mappings when it is not empty; omits the key otherwise. */
    YamlDumpEmitter mappings(String key, List<Map<String, Object>> values) {
        mappingsInto(root, key, values);
        return this;
    }

    /**
     * Adds {@code key: value} verbatim, bypassing the omit-empty rule: a present-but-empty mapping is written as
     * {@code {}} and nested nulls are written as {@code null}. Callers omit the key themselves when the value is
     * absent.
     *
     * @param key the key
     * @param value a raw scalar (possibly null), a list, or a mapping, as {@code ConfigValue.toParsed()} yields
     */
    YamlDumpEmitter present(String key, @Nullable Object value) {
        root.put(key, value);
        return this;
    }

    /** Adds {@code key: value} to {@code target} when the optional is present. */
    static Map<String, Object> scalarInto(Map<String, Object> target, String key, Optional<String> value) {
        value.ifPresent(v -> target.put(key, v));
        return target;
    }

    /** Adds a list of strings to {@code target} when it is not empty. */
    static void stringsInto(Map<String, Object> target, String key, List<String> values) {
        if (!values.isEmpty()) {
            target.put(key, new ArrayList<Object>(values));
        }
    }

    /** Adds a list of mappings to {@code target} when it is not empty. */
    static void mappingsInto(Map<String, Object> target, String key, List<Map<String, Object>> values) {
        if (!values.isEmpty()) {
            target.put(key, new ArrayList<Object>(values));
        }
    }

    /** The document as YAML text. */
    String dump() {
        DumpSettings settings = DumpSettings.builder()
                .setDefaultFlowStyle(FlowStyle.BLOCK)
                .setWidth(4096)
                .setSchema(Yaml.dumpSchema())
                .build();
        return new Dump(settings).dumpToString(root);
    }
}
