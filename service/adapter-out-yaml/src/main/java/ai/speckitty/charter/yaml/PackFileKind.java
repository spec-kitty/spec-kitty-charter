package ai.speckitty.charter.yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Classifies a pack-relative file path against the bundled {@code file-kinds.yaml}: the file is mapped to a schema,
 * excluded for a reviewed reason, or not classified at all.
 *
 * <p>A rule's {@code match} is a glob over the path relative to the directory that holds the pack directories, with
 * forward slashes. {@code *} matches within one path segment, {@code **}{@code /} matches zero or more whole
 * segments, and every other character is literal.
 */
final class PackFileKind {

    private static final String RESOURCE = "packs/file-kinds.yaml";
    private static final String SCHEMA_DIR = "packs/schemas/";
    private static final String SCHEMA_SUFFIX = ".schema.json";

    private static final PackFileKind BUNDLED = load();

    /** The outcome of classifying one path. */
    sealed interface Classification permits Mapped, Excluded, Unclassified {}

    /**
     * A file that a schema validates.
     *
     * @param kind the schema's kind name, as {@link PackSchemas#validate} takes it
     * @param schema the classpath location of the schema document
     */
    record Mapped(String kind, String schema) implements Classification {}

    /**
     * A YAML file that has no schema, for a reviewed reason.
     *
     * @param exclusionClass the exclusion's {@code match} glob, which names the class of files it covers
     * @param reason why the class has no schema
     */
    record Excluded(String exclusionClass, String reason) implements Classification {}

    /** A file that no rule covers. */
    enum Unclassified implements Classification {
        /** The only value. */
        INSTANCE
    }

    /**
     * A {@code files} rule.
     *
     * @param match the glob
     * @param schema the schema kind it maps to
     */
    record FileRule(String match, String schema) {}

    /**
     * An {@code exclusions} rule.
     *
     * @param match the glob
     * @param reason the reviewable reason
     */
    record ExclusionRule(String match, String reason) {}

    private record Compiled(String match, Pattern pattern) {}

    private final List<String> nodeKinds;
    private final Map<String, String> fragmentKinds;
    private final List<FileRule> fileRules;
    private final List<ExclusionRule> exclusionRules;
    private final List<Compiled> compiled;

    /**
     * A table read from the text of a {@code file-kinds.yaml}, which the tests use to classify with a changed copy.
     *
     * @param yaml the table's YAML text
     * @throws IllegalStateException when the text is not a well-formed table
     */
    PackFileKind(String yaml) {
        this(parse(yaml));
    }

    private PackFileKind(Table table) {
        this.nodeKinds = List.copyOf(table.nodeKinds());
        this.fragmentKinds = Map.copyOf(table.fragmentKinds());
        this.fileRules = List.copyOf(table.fileRules());
        this.exclusionRules = List.copyOf(table.exclusionRules());
        List<Compiled> all = new ArrayList<>();
        fileRules.forEach(r -> all.add(new Compiled(r.match(), glob(r.match()))));
        exclusionRules.forEach(r -> all.add(new Compiled(r.match(), glob(r.match()))));
        this.compiled = List.copyOf(all);
    }

    private record Table(
            List<String> nodeKinds,
            Map<String, String> fragmentKinds,
            List<FileRule> fileRules,
            List<ExclusionRule> exclusionRules) {}

    /** The table bundled with this adapter, read once. */
    static PackFileKind bundled() {
        return BUNDLED;
    }

    /** The service's DRG node kind tokens, in schema order. */
    List<String> nodeKinds() {
        return nodeKinds;
    }

    /** The fragment node kind plurals, each mapped to its node kind. */
    Map<String, String> fragmentKinds() {
        return fragmentKinds;
    }

    /** The rules that map a file to a schema, in table order. */
    List<FileRule> fileRules() {
        return fileRules;
    }

    /** The reviewed exclusions, in table order. */
    List<ExclusionRule> exclusionRules() {
        return exclusionRules;
    }

    /** Every rule glob, from either list, that matches {@code path}; the table promises at most one. */
    List<String> matchingRules(String path) {
        return compiled.stream()
                .filter(c -> c.pattern().matcher(path).matches())
                .map(Compiled::match)
                .toList();
    }

    /**
     * Classifies a pack-relative path.
     *
     * @param path the path relative to the directory that holds the pack directories, with forward slashes
     * @return how the path is handled
     */
    Classification classify(String path) {
        // `compiled` holds the files rules, then the exclusions, each compiled once when the table was built.
        for (int i = 0; i < compiled.size(); i++) {
            if (!compiled.get(i).pattern().matcher(path).matches()) {
                continue;
            }
            if (i < fileRules.size()) {
                FileRule rule = fileRules.get(i);
                return new Mapped(rule.schema(), SCHEMA_DIR + rule.schema() + SCHEMA_SUFFIX);
            }
            ExclusionRule rule = exclusionRules.get(i - fileRules.size());
            return new Excluded(rule.match(), rule.reason());
        }
        return Unclassified.INSTANCE;
    }

    private static Pattern glob(String match) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < match.length()) {
            if (match.startsWith("**/", i)) {
                regex.append("(?:.+/)?");
                i += 3;
            } else if (match.charAt(i) == '*') {
                regex.append("[^/]*");
                i++;
            } else {
                int next = i;
                while (next < match.length() && match.charAt(next) != '*') {
                    next++;
                }
                regex.append(Pattern.quote(match.substring(i, next)));
                i = next;
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static PackFileKind load() {
        return new PackFileKind(readResource());
    }

    private static Table parse(String yaml) {
        Object root = Yaml.load(yaml);
        Map<String, Object> table = map(root, RESOURCE);
        List<String> nodeKinds = strings(table.get("node_kinds"), "node_kinds");
        Map<String, String> fragments = new LinkedHashMap<>();
        map(table.get("fragment_kinds"), "fragment_kinds").forEach((k, v) -> fragments.put(k, String.valueOf(v)));
        List<FileRule> files = new ArrayList<>();
        for (Object entry : list(table.get("files"), "files")) {
            Map<String, Object> rule = map(entry, "files");
            files.add(new FileRule(string(rule, "match"), string(rule, "schema")));
        }
        List<ExclusionRule> exclusions = new ArrayList<>();
        for (Object entry : list(table.get("exclusions"), "exclusions")) {
            Map<String, Object> rule = map(entry, "exclusions");
            exclusions.add(new ExclusionRule(string(rule, "match"), string(rule, "reason")));
        }
        return new Table(nodeKinds, fragments, files, exclusions);
    }

    private static String readResource() {
        try (InputStream in = PackFileKind.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The bundled " + RESOURCE + " is missing from the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(@Nullable Object value, String where) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalStateException(RESOURCE + ": " + where + " must be a mapping");
    }

    private static List<?> list(@Nullable Object value, String where) {
        if (value instanceof List<?> l) {
            return l;
        }
        throw new IllegalStateException(RESOURCE + ": " + where + " must be a list");
    }

    private static List<String> strings(@Nullable Object value, String where) {
        return list(value, where).stream().map(String::valueOf).toList();
    }

    private static String string(Map<String, Object> rule, String key) {
        Object value = rule.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s.strip();
        }
        throw new IllegalStateException(RESOURCE + ": every rule needs a non-blank '" + key + "'");
    }
}
