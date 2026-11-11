package ai.speckitty.charter.packs.examples;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Relates the bundled pack schemas and the file-kind table to the example packs (WP03, FR-003): which declared
 * property no example sets, and which file kind no example file has.
 *
 * <p>It reads the schemas and {@code file-kinds.yaml} from the classpath, where {@code :packs} bundles them, and the
 * example files from disk. A property is named by its instance pointer: {@code /a/b} for nested properties and
 * {@code *} for an array item or a map value, so the {@code id} of any step is {@code /steps}, then {@code *}, then {@code id}.
 */
final class ExampleCoverage {

    static final String SCHEMA_DIR = "packs/schemas/";
    static final String FILE_KINDS = "packs/file-kinds.yaml";
    static final String ANY = "*";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final List<String> COMBINATORS = List.of("allOf", "anyOf", "oneOf");
    private static final List<String> CONDITIONALS = List.of("if", "then", "else");

    /** A {@code files} rule: the glob and the schema that validates what it matches. */
    record FileRule(String match, String schema, boolean markdown) {}

    /** A {@code payloads} rule: the glob and the kind of the bytes it matches. */
    record PayloadRule(String match, String kind) {}

    private ExampleCoverage() {}

    /** The {@code files} rules of the bundled table, in table order. */
    static List<FileRule> fileRules() {
        List<FileRule> rules = new ArrayList<>();
        for (Map<?, ?> rule : section("files")) {
            rules.add(new FileRule(
                    (String) rule.get("match"),
                    (String) rule.get("schema"),
                    "markdown".equals(rule.get("content_form"))));
        }
        return rules;
    }

    /** The {@code payloads} rules of the bundled table, in table order. */
    static List<PayloadRule> payloadRules() {
        List<PayloadRule> rules = new ArrayList<>();
        for (Map<?, ?> rule : section("payloads")) {
            rules.add(new PayloadRule((String) rule.get("match"), (String) rule.get("kind")));
        }
        return rules;
    }

    /** The bundled schema of one kind. */
    static JsonNode schema(String kind) {
        return MAPPER.readTree(resource(SCHEMA_DIR + kind + ".schema.json"));
    }

    /** Every file under {@code packsDir}, as the path relative to it with forward slashes. */
    static List<String> files(Path packsDir) {
        try (Stream<Path> walk = Files.walk(packsDir)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> packsDir.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Whether a table glob matches a relative path: {@code **}{@code /} spans whole segments, {@code *} one. */
    static boolean matches(String glob, String path) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            if (glob.startsWith("**/", i)) {
                regex.append("(?:[^/]*/)*");
                i += 3;
            } else {
                char c = glob.charAt(i);
                regex.append(c == '*' ? "[^/]*" : Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return Pattern.matches(regex.toString(), path);
    }

    /**
     * Every property pointer a schema declares, nested properties, array items, map values and local {@code $ref}
     * targets included. A recursive definition is walked once per path.
     */
    static Set<String> declaredPointers(JsonNode schema) {
        Set<String> pointers = new TreeSet<>();
        walk(schema, schema, "", pointers, new ArrayDeque<>());
        return pointers;
    }

    private static void walk(JsonNode root, JsonNode node, String at, Set<String> out, Deque<String> refs) {
        if (!node.isObject()) {
            return;
        }
        JsonNode ref = node.get("$ref");
        if (ref != null && ref.asString().startsWith("#/")) {
            String target = ref.asString();
            if (!refs.contains(target)) {
                refs.push(target);
                walk(root, root.at(target.substring(1)), at, out, refs);
                refs.pop();
            }
        }
        JsonNode properties = node.get("properties");
        if (properties != null) {
            for (String name : properties.propertyNames()) {
                String child = at + "/" + name;
                out.add(child);
                walk(root, properties.get(name), child, out, refs);
            }
        }
        JsonNode patterns = node.get("patternProperties");
        if (patterns != null) {
            for (String name : patterns.propertyNames()) {
                walk(root, patterns.get(name), at + "/" + ANY, out, refs);
            }
        }
        for (String key : List.of("additionalProperties", "items", "contains")) {
            JsonNode child = node.get(key);
            if (child != null && child.isObject()) {
                walk(root, child, at + "/" + ANY, out, refs);
            }
        }
        JsonNode prefix = node.get("prefixItems");
        if (prefix instanceof ArrayNode items) {
            for (JsonNode item : items) {
                walk(root, item, at + "/" + ANY, out, refs);
            }
        }
        for (String key : COMBINATORS) {
            JsonNode branches = node.get(key);
            if (branches instanceof ArrayNode items) {
                for (JsonNode branch : items) {
                    walk(root, branch, at, out, refs);
                }
            }
        }
        for (String key : CONDITIONALS) {
            JsonNode child = node.get(key);
            if (child != null) {
                walk(root, child, at, out, refs);
            }
        }
    }

    /** Every property pointer a document sets, with array indices and map keys also written as {@code *}. */
    static Set<String> setPointers(JsonNode document) {
        Set<String> pointers = new LinkedHashSet<>();
        collect(document, "", pointers);
        return pointers;
    }

    private static void collect(JsonNode node, String at, Set<String> out) {
        if (node instanceof ObjectNode object) {
            for (String name : object.propertyNames()) {
                for (String segment : List.of(name, ANY)) {
                    String child = at + "/" + segment;
                    out.add(child);
                    collect(object.get(name), child, out);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode item : array) {
                collect(item, at + "/" + ANY, out);
            }
        }
    }

    /**
     * The gaps of one schema: each declared pointer no example sets, as {@code <kind>: <pointer> has no example}.
     *
     * @param kind the schema kind, for the message
     * @param schema the schema
     * @param examples the example documents it validates
     * @return the gaps, sorted
     */
    static List<String> gaps(String kind, JsonNode schema, List<JsonNode> examples) {
        Set<String> set = new LinkedHashSet<>();
        examples.forEach(example -> set.addAll(setPointers(example)));
        return declaredPointers(schema).stream()
                .filter(pointer -> !set.contains(pointer))
                .map(pointer -> kind + ": " + pointer + " has no example")
                .toList();
    }

    /** The example documents of one {@code files} rule under {@code packsDir}, as the schema sees them. */
    static List<JsonNode> examples(Path packsDir, FileRule rule) {
        List<JsonNode> documents = new ArrayList<>();
        for (String file : files(packsDir)) {
            if (matches(rule.match(), file)) {
                String text = read(packsDir.resolve(file));
                documents.add(rule.markdown() ? markdownTree(text) : MAPPER.valueToTree(yaml(text)));
            }
        }
        return documents;
    }

    /**
     * The Markdown model as a structure schema validates it: {@code frontmatter} and {@code title} when present,
     * {@code preamble}, and {@code sections} of {@code {title, content}} (the H2 headings).
     */
    static JsonNode markdownTree(String text) {
        Map<String, Object> tree = new LinkedHashMap<>();
        String body = text;
        if (text.startsWith("---\n")) {
            int end = text.indexOf("\n---\n", 3);
            if (end > 0) {
                tree.put("frontmatter", yaml(text.substring(4, end + 1)));
                body = text.substring(end + 5);
            }
        }
        List<Map<String, String>> sections = new ArrayList<>();
        StringBuilder preamble = new StringBuilder();
        StringBuilder current = preamble;
        for (String line : body.split("\n", -1)) {
            if (line.startsWith("# ") && sections.isEmpty() && !tree.containsKey("title")) {
                tree.put("title", line.substring(2).strip());
            } else if (line.startsWith("## ")) {
                Map<String, String> section = new LinkedHashMap<>();
                section.put("title", line.substring(3).strip());
                section.put("content", "");
                sections.add(section);
                current = new StringBuilder();
            } else {
                current.append(line).append('\n');
                if (!sections.isEmpty()) {
                    sections.get(sections.size() - 1).put("content", current.toString());
                }
            }
        }
        tree.put("preamble", preamble.toString());
        tree.put("sections", sections);
        return MAPPER.valueToTree(tree);
    }

    private static @Nullable Object yaml(String text) {
        return new Load(LoadSettings.builder().build()).loadFromString(text);
    }

    private static List<Map<?, ?>> section(String name) {
        Map<?, ?> table = (Map<?, ?>) yaml(resource(FILE_KINDS));
        List<Map<?, ?>> rules = new ArrayList<>();
        Object entries = table == null ? null : table.get(name);
        if (entries instanceof List<?> list) {
            for (Object entry : list) {
                rules.add((Map<?, ?>) entry);
            }
        }
        return rules;
    }

    private static String resource(String name) {
        try (InputStream in = ExampleCoverage.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
