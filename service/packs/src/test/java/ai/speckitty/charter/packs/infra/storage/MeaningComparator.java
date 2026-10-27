package ai.speckitty.charter.packs.infra.storage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The oracle of the whole-pack round-trip gate: does a written YAML document mean the same as its source? Both texts
 * are parsed with the readers' own YAML settings and compared as trees, so quoting, comments, whitespace and key order
 * do not count, while a missing, extra or changed key or value does.
 *
 * <p>The rules are ADR-0036's (Decision 2 and 4, and its per-kind table), and they live only here:
 *
 * <ul>
 *   <li>maps compare by key set, then per key; lists are sequences, so order and repetition count;
 *   <li>for a declared field, an absent field, an explicit {@code null} and an empty list or map mean the same (E2,
 *       E16); inside an open map (styleguide {@code tooling} and {@code structural_lint_config}) they are data and
 *       differ (E14);
 *   <li>scalars compare by resolved value: a string is not a number ({@code '1.0'} is not {@code 1.0}), but numbers
 *       compare by value ({@code 1} equals {@code 1.0}, E13);
 *   <li>in a graph shard, the root {@code edges} are a set of canonical edges over every declared field (order and
 *       identical repeats do not count, E5), and the root {@code nodes} a set keyed by {@code urn}.
 * </ul>
 *
 * <p>Not implemented: ADR-0036's reading that an absent field equals its schema default (no corpus file needs it).
 */
final class MeaningComparator {

    /** The gate kind of a {@code <kind>.graph.yaml} file. */
    static final String GRAPH_SHARD = "graph-shard";

    private static final String ABSENT = "<absent>";
    private static final String ROOT = "";
    private static final int MAX_RENDERED = 80;

    /**
     * Where a written document first departs from its source.
     *
     * @param path the path of the departing value, for example {@code steps[2].title} or {@code nodes{<urn>}.label}
     * @param source the source value, rendered, or {@code <absent>}
     * @param written the written value, rendered, or {@code <absent>}
     */
    record Difference(String path, String source, String written) {}

    /** ADR-0036's open maps per gate kind: top-level keys whose content is data, not fields. */
    private static final Map<String, Set<String>> OPEN_MAPS =
            Map.of("styleguide", Set.of("tooling", "structural_lint_config"));

    private MeaningComparator() {}

    /** The rules of ADR-0036 (Proposed; its readings stand until the operator overrules them). */
    static MeaningComparator adr0036() {
        return new MeaningComparator();
    }

    /**
     * Parses both texts as the readers do, then compares them.
     *
     * @param kind the gate kind of the file, which selects the graph-shard rules
     * @param sourceText the source file's text
     * @param writtenText the written file's text
     * @return the first difference, or empty when both mean the same
     */
    Optional<Difference> compareTexts(String kind, String sourceText, String writtenText) {
        return compare(kind, Yaml.load(sourceText), Yaml.load(writtenText));
    }

    /**
     * Compares two parsed documents.
     *
     * @param kind the gate kind of the file, which selects the graph-shard rules
     * @param source the parsed source document
     * @param written the parsed written document
     * @return the first difference, or empty when both mean the same
     */
    Optional<Difference> compare(String kind, @Nullable Object source, @Nullable Object written) {
        return differences(kind, source, written).stream().findFirst();
    }

    /**
     * Parses both texts as the readers do, then lists every difference.
     *
     * @param kind the gate kind of the file, which selects the graph-shard rules
     * @param sourceText the source file's text
     * @param writtenText the written file's text
     * @return every difference, in the order {@link #compareTexts} meets them; empty when both mean the same
     */
    List<Difference> differencesOfTexts(String kind, String sourceText, String writtenText) {
        return differences(kind, Yaml.load(sourceText), Yaml.load(writtenText));
    }

    /**
     * Lists every difference between two parsed documents, so a loss behind the first one is not hidden.
     *
     * @param kind the gate kind of the file, which selects the graph-shard rules
     * @param source the parsed source document
     * @param written the parsed written document
     * @return every difference, deterministic in order; empty when both mean the same
     */
    List<Difference> differences(String kind, @Nullable Object source, @Nullable Object written) {
        List<Difference> out = new ArrayList<>();
        if (GRAPH_SHARD.equals(kind) && source instanceof Map<?, ?> s && written instanceof Map<?, ?> w) {
            compareShard(s, w, out);
        } else if (source instanceof Map<?, ?> s && written instanceof Map<?, ?> w) {
            Set<String> open = OPEN_MAPS.getOrDefault(kind, Set.of());
            for (String key : keys(s, w)) {
                // The open map's own key is a declared field; only what is inside it is data.
                entry(ROOT, key, s, w, false, open.contains(key), out);
            }
        } else {
            value(ROOT, source, written, false, out);
        }
        return List.copyOf(out);
    }

    /**
     * The edges a graph shard lists more than once, as information: duplicates do not count as a difference.
     *
     * @param kind the gate kind of the file; anything but a graph shard has none
     * @param text the shard's text
     * @return the canonical form of every edge that occurs more than once, sorted
     */
    List<String> duplicateEdges(String kind, String text) {
        if (!GRAPH_SHARD.equals(kind) || !(Yaml.load(text) instanceof Map<?, ?> document)) {
            return List.of();
        }
        SortedMap<String, Integer> counts = new TreeMap<>();
        for (Object edge : listAt(document, "edges")) {
            counts.merge(canonical(edge), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ---- graph shards --------------------------------------------------------------------------

    private void compareShard(Map<?, ?> source, Map<?, ?> written, List<Difference> out) {
        for (String key : keys(source, written)) {
            switch (key) {
                case "edges" -> edges(source, written, out);
                case "nodes" -> nodes(source, written, out);
                default -> entry(ROOT, key, source, written, false, false, out);
            }
        }
    }

    private void edges(Map<?, ?> source, Map<?, ?> written, List<Difference> out) {
        if (!(source.get("edges") instanceof List<?>) || !(written.get("edges") instanceof List<?>)) {
            entry(ROOT, "edges", source, written, false, false, out);
            return;
        }
        SortedSet<String> sourceEdges = canonicalEdges(listAt(source, "edges"));
        SortedSet<String> writtenEdges = canonicalEdges(listAt(written, "edges"));
        // Lost source edges are reported before edges the writer added.
        for (String edge : sourceEdges) {
            if (!writtenEdges.contains(edge)) {
                out.add(new Difference("edges{" + edge + "}", "present", ABSENT));
            }
        }
        for (String edge : writtenEdges) {
            if (!sourceEdges.contains(edge)) {
                out.add(new Difference("edges{" + edge + "}", ABSENT, "present"));
            }
        }
    }

    private void nodes(Map<?, ?> source, Map<?, ?> written, List<Difference> out) {
        if (!(source.get("nodes") instanceof List<?>) || !(written.get("nodes") instanceof List<?>)) {
            entry(ROOT, "nodes", source, written, false, false, out);
            return;
        }
        Map<String, List<Object>> sourceNodes = nodesByUrn(listAt(source, "nodes"));
        Map<String, List<Object>> writtenNodes = nodesByUrn(listAt(written, "nodes"));
        SortedSet<String> urns = new TreeSet<>(sourceNodes.keySet());
        urns.addAll(writtenNodes.keySet());
        for (String urn : urns) {
            String at = "nodes{" + urn + "}";
            List<Object> inSource = sourceNodes.getOrDefault(urn, List.of());
            List<Object> inWritten = writtenNodes.getOrDefault(urn, List.of());
            // Nodes are keyed by URN, so a URN listed twice is never collapsed: the ruling frees edges only.
            if (inSource.size() > 1 || inWritten.size() > 1) {
                out.add(new Difference(at, inSource.size() + " node(s)", inWritten.size() + " node(s)"));
            } else if (inSource.isEmpty() || inWritten.isEmpty()) {
                out.add(new Difference(
                        at,
                        inSource.isEmpty() ? ABSENT : render(inSource.get(0)),
                        inWritten.isEmpty() ? ABSENT : render(inWritten.get(0))));
            } else {
                value(at, inSource.get(0), inWritten.get(0), false, out);
            }
        }
    }

    private static SortedSet<String> canonicalEdges(List<?> edges) {
        return edges.stream().map(MeaningComparator::canonical).collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * A value in one unambiguous, typed text form: maps with their keys sorted, strings quoted and escaped, and every
     * other scalar tagged with its type, so {@code true} is not {@code 'true'} and a comma inside a {@code reason}
     * cannot make two different edges render alike.
     */
    static String canonical(@Nullable Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            // An edge's or node's fields are declared fields: an empty one means the same as an absent one (E16).
            SortedMap<String, String> entries = new TreeMap<>();
            map.forEach((key, item) -> {
                if (!isEmpty(item)) {
                    entries.put(canonical(key), canonical(item));
                }
            });
            return entries.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(MeaningComparator::canonical).collect(Collectors.joining(",", "[", "]"));
        }
        if (value instanceof String text) {
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (value instanceof Number number && isFinite(number)) {
            return "Number:"
                    + new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        return value.getClass().getSimpleName() + ":" + value;
    }

    /** Nodes grouped by {@code urn}; a node without one is keyed by its full canonical form so it still takes part. */
    private static Map<String, List<Object>> nodesByUrn(List<?> nodes) {
        Map<String, List<Object>> byUrn = new TreeMap<>();
        for (Object node : nodes) {
            String urn = node instanceof Map<?, ?> map && map.get("urn") != null
                    ? String.valueOf(map.get("urn"))
                    : "<no urn> " + canonical(node);
            byUrn.computeIfAbsent(urn, key -> new ArrayList<>()).add(Objects.requireNonNullElse(node, "null"));
        }
        return byUrn;
    }

    // ---- the general rules ---------------------------------------------------------------------

    /**
     * @param open true inside an open map, where null and empty values are data rather than an absent field
     */
    private void value(
            String path, @Nullable Object source, @Nullable Object written, boolean open, List<Difference> out) {
        if (source instanceof Map<?, ?> s && written instanceof Map<?, ?> w) {
            for (String key : keys(s, w)) {
                entry(path, key, s, w, open, open, out);
            }
        } else if (source instanceof List<?> s && written instanceof List<?> w) {
            for (int i = 0; i < Math.max(s.size(), w.size()); i++) {
                String at = path + "[" + i + "]";
                if (i >= s.size()) {
                    out.add(new Difference(at, ABSENT, render(w.get(i))));
                } else if (i >= w.size()) {
                    out.add(new Difference(at, render(s.get(i)), ABSENT));
                } else {
                    value(at, s.get(i), w.get(i), open, out);
                }
            }
        } else if (!sameScalar(source, written)) {
            out.add(new Difference(path.isEmpty() ? "$" : path, render(source), render(written)));
        }
    }

    /**
     * @param open true inside an open map: an absent, null or empty value of this key is data
     * @param openBelow true when this key's own value is an open map, so what is inside it is data
     */
    private void entry(
            String path,
            String key,
            Map<?, ?> source,
            Map<?, ?> written,
            boolean open,
            boolean openBelow,
            List<Difference> out) {
        String at = path.isEmpty() ? key : path + "." + key;
        Map<String, @Nullable Object> s = stringKeys(source);
        Map<String, @Nullable Object> w = stringKeys(written);
        boolean inSource = s.containsKey(key);
        boolean inWritten = w.containsKey(key);
        if (!open && (!inSource || isEmpty(s.get(key))) && (!inWritten || isEmpty(w.get(key)))) {
            return; // absent, null and empty mean the same for a declared field (ADR-0036 Decision 2)
        }
        if (inSource && !inWritten) {
            out.add(new Difference(at, render(s.get(key)), ABSENT));
        } else if (!inSource && inWritten) {
            out.add(new Difference(at, ABSENT, render(w.get(key))));
        } else {
            value(at, s.get(key), w.get(key), openBelow, out);
        }
    }

    /** A number BigDecimal can hold; the schemas refuse NaN and infinities, but the comparator must not throw on one. */
    private static boolean isFinite(Number number) {
        return !(number instanceof Double d && (d.isNaN() || d.isInfinite()))
                && !(number instanceof Float f && (f.isNaN() || f.isInfinite()));
    }

    private static boolean isEmpty(@Nullable Object value) {
        return value == null
                || (value instanceof List<?> list && list.isEmpty())
                || (value instanceof Map<?, ?> map && map.isEmpty());
    }

    /** Resolved values: numbers by value ({@code 1} equals {@code 1.0}), anything else by equality and type. */
    private static boolean sameScalar(@Nullable Object source, @Nullable Object written) {
        if (source instanceof Number s && written instanceof Number w && isFinite(s) && isFinite(w)) {
            return new BigDecimal(s.toString()).compareTo(new BigDecimal(w.toString())) == 0;
        }
        return Objects.equals(source, written);
    }

    private static SortedSet<String> keys(Map<?, ?> source, Map<?, ?> written) {
        SortedSet<String> keys = new TreeSet<>(stringKeys(source).keySet());
        keys.addAll(stringKeys(written).keySet());
        return keys;
    }

    /**
     * A YAML map's keys as strings: a string key as written, any other key in its typed canonical form, so {@code 1}
     * and {@code "1"} stay two keys.
     */
    private static Map<String, @Nullable Object> stringKeys(Map<?, ?> map) {
        Map<String, @Nullable Object> result = new TreeMap<>();
        map.forEach((key, value) -> result.put(key instanceof String text ? text : canonical(key), value));
        return result;
    }

    private static List<?> listAt(Map<?, ?> document, String key) {
        return document.get(key) instanceof List<?> list ? list : new ArrayList<>();
    }

    private static String render(@Nullable Object value) {
        String text = String.valueOf(value);
        return text.length() <= MAX_RENDERED ? text : text.substring(0, MAX_RENDERED) + "…";
    }
}
