package ai.speckitty.charter.contracts.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;
import org.snakeyaml.engine.v2.schema.Schema;

/**
 * The bundle core vendors, made from vacuum's composed bundle (#294): one file that keeps {@code components} and
 * refers to them, instead of one that inlines every reference.
 *
 * <p>{@code vacuum bundle --composed} moves every file the split contract references into {@code components}, named
 * after the file, and rewrites each reference to point there. Its output is then normalized here:
 *
 * <ol>
 *   <li><b>Path items go back under {@code paths}.</b> vacuum moves each path file into {@code components/pathItems}
 *       and leaves {@code paths} as references; each path's item is moved back, and the section is dropped. The path
 *       map is where a reader looks for the operations, and {@code oasdiff validate} warns on the section.</li>
 *   <li><b>A renamed clash that holds the same content is merged.</b> vacuum names a component {@code
 *       <name>__<suffix>} when the name is taken, which happens when two files reach one file by different relative
 *       paths ({@code Problem} and {@code Problem__schemas}). When the renamed component holds what the named one
 *       holds (once the references inside both are merged the same way), its references are rewritten to the name and
 *       it is dropped. A renamed component whose content differs is two source files sharing a name; it is refused,
 *       since the generated name would be a client type name nobody chose.</li>
 *   <li><b>Every reference names a component of the bundle.</b> Any other {@code $ref} is refused: a file reference,
 *       a missing component, and a reference with sibling keys, which {@link BundleDocument} could not read as the
 *       component it names. So is a component that refers back to itself: the inlined bundle could not hold one,
 *       so resolving every reference still gives what the inlined bundle held.</li>
 *   <li><b>It is written without anchors.</b> vacuum writes YAML anchors on repeated path-item headers and redefines
 *       them; the bundle is written as plain mappings, each repeated node written in full, and a string a YAML 1.1
 *       reader would take for another type ({@code yes}, a date) is quoted.</li>
 * </ol>
 *
 * <p>The command line, run by the contract build's bundling step, takes the composed file and the bundle to write.
 * It exits 0 when it wrote the bundle, 1 with one line per problem when it refuses (and writes nothing), and 2 when
 * the composed file cannot be read.
 */
public final class ComposedBundle {

    /** The separator vacuum puts between a clashing name and its suffix (its {@code --delimiter}, by default). */
    public static final String DELIMITER = "__";

    private static final String COMPONENTS = "components";
    private static final String PATH_ITEMS = "pathItems";
    private static final String PREFIX = "#/" + COMPONENTS + "/";
    private static final String NOT_A_MAPPING = "/: the composed bundle is not a mapping";

    private ComposedBundle() {}

    /**
     * The normalized bundle and what stops it from being one.
     *
     * @param document the normalized document
     * @param problems one line per problem, its JSON Pointer first; empty when the bundle may be written
     */
    public record Normalized(Map<Object, @Nullable Object> document, List<String> problems) {

        /** Copies the problems, so a result never changes. */
        public Normalized {
            problems = List.copyOf(problems);
        }
    }

    /**
     * Normalizes vacuum's composed bundle as the class describes.
     *
     * @param composed the loaded composed bundle
     * @return the normalized document and its problems
     */
    public static Normalized normalize(@Nullable Object composed) {
        if (!(composed instanceof Map<?, ?> root)) {
            return new Normalized(new LinkedHashMap<>(), List.of(NOT_A_MAPPING));
        }
        List<String> problems = new ArrayList<>();
        Map<Object, @Nullable Object> document = copyMap(root);
        movePathItemsBack(document);
        mergeRenamedClashes(document, problems);
        checkReferences(document, "", document, problems);
        if (problems.isEmpty()) {
            refuseRecursion(document, problems);
        }
        return new Normalized(document, problems);
    }

    /**
     * Writes a document as block YAML, without anchors: a node used twice is written twice.
     *
     * @param document the document
     * @return the YAML text
     */
    public static String dump(Object document) {
        Schema base = DumpSettings.builder().build().getSchema();
        return new Dump(DumpSettings.builder()
                        .setDefaultFlowStyle(FlowStyle.BLOCK)
                        .setDereferenceAliases(true)
                        .setSplitLines(false)
                        .setSchema(new Yaml11SafeSchema(base))
                        .build())
                .dumpToString(document);
    }

    /**
     * The writer's schema, but a string that a YAML 1.1 reader (PyYAML, which core may read the bundle with) would
     * take for another type is not written plain, so it is quoted: {@code yes}, {@code off}, {@code ~}, a date, a
     * sexagesimal or underscored number, {@code =} and {@code <<}. vacuum's writer quoted them too. A value that is
     * not a string is written as before.
     */
    private record Yaml11SafeSchema(Schema base) implements Schema {

        private static final Pattern YAML_11_TYPED = Pattern.compile(String.join(
                "|",
                "y|Y|n|N|yes|Yes|YES|no|No|NO|true|True|TRUE|false|False|FALSE|on|On|ON|off|Off|OFF",
                "~|null|Null|NULL|=|<<",
                "[-+]?0b[0-1_]+|[-+]?0[0-7_]+|[-+]?(?:0|[1-9][0-9_]*)|[-+]?0x[0-9a-fA-F_]+",
                "[-+]?[1-9][0-9_]*(?::[0-5]?[0-9])+",
                "[-+]?(?:[0-9][0-9_]*)?\\.[0-9_]*(?:[eE][-+]?[0-9]+)?|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\\.[0-9_]*",
                "[-+]?\\.(?:inf|Inf|INF)|\\.(?:nan|NaN|NAN)",
                "[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}(?:(?:[Tt]|[ \\t]+)[0-9]{1,2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]*)?"
                        + "(?:[ \\t]*(?:Z|[-+][0-9]{1,2}(?::[0-9]{2})?))?)?"));
        private static final Tag YAML_11_TYPE = new Tag("tag:yaml.org,2002:yaml-1.1-typed");

        @Override
        public ScalarResolver getScalarResolver() {
            ScalarResolver resolver = base.getScalarResolver();
            return (value, implicit) -> {
                Tag tag = resolver.resolve(value, implicit);
                // Only a plain scalar is resolved implicitly; a quoted one is a string whatever it holds.
                return Boolean.TRUE.equals(implicit)
                                && Tag.STR.equals(tag)
                                && YAML_11_TYPED.matcher(value).matches()
                        ? YAML_11_TYPE
                        : tag;
            };
        }

        @Override
        public Map<Tag, ConstructNode> getSchemaTagConstructors() {
            return base.getSchemaTagConstructors();
        }
    }

    /**
     * Normalizes the composed bundle the first argument names into the bundle the second names, and exits with the
     * code the class describes.
     *
     * @param args the composed bundle and the bundle to write
     */
    public static void main(String[] args) {
        int code = run(List.of(args), System.out);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(List<String> args, PrintStream out) {
        if (args.size() != 2) {
            out.println("usage: ComposedBundle <vacuum-composed.yaml> <bundle.yaml>");
            return 2;
        }
        Object composed;
        try (InputStream in = Files.newInputStream(Path.of(args.getFirst()))) {
            composed = new Load(LoadSettings.builder().setLabel(args.getFirst()).build()).loadFromInputStream(in);
        } catch (IOException | YamlEngineException e) {
            out.println("Cannot read " + args.getFirst() + ": " + e.getMessage());
            return 2;
        }
        Normalized normalized = normalize(composed);
        if (!normalized.problems().isEmpty()) {
            int count = normalized.problems().size();
            out.println(count + (count == 1 ? " problem" : " problems") + " with the composed bundle:");
            normalized.problems().forEach(out::println);
            return 1;
        }
        try {
            Files.writeString(Path.of(args.get(1)), dump(normalized.document()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            out.println("Cannot write " + args.get(1) + ": " + e.getMessage());
            return 2;
        }
        return 0;
    }

    /** Replaces each path that is a reference to a path item with that item, then drops {@code pathItems}. */
    private static void movePathItemsBack(Map<Object, @Nullable Object> document) {
        if (!(document.get(COMPONENTS) instanceof Map<?, ?> components)
                || !(components.get(PATH_ITEMS) instanceof Map<?, ?> pathItems)) {
            return;
        }
        if (document.get("paths") instanceof Map<?, ?> paths) {
            Map<Object, @Nullable Object> moved = new LinkedHashMap<>();
            paths.forEach((path, item) -> moved.put(path, pathItem(item, pathItems)));
            document.put("paths", moved);
        }
        Map<Object, @Nullable Object> rest = copyMap(components);
        rest.remove(PATH_ITEMS);
        if (rest.isEmpty()) {
            document.remove(COMPONENTS);
        } else {
            document.put(COMPONENTS, rest);
        }
    }

    private static @Nullable Object pathItem(@Nullable Object item, Map<?, ?> pathItems) {
        if (item instanceof Map<?, ?> map
                && map.size() == 1
                && map.get(BundleDocument.REF) instanceof String ref
                && ref.startsWith(PREFIX + PATH_ITEMS + "/")) {
            String name = BundleDocument.unescape(ref.substring((PREFIX + PATH_ITEMS + "/").length()));
            if (pathItems.containsKey(name)) {
                return pathItems.get(name);
            }
        }
        return item;
    }

    /**
     * Merges each {@code <name>__<suffix>} component into {@code <name>} when both hold the same content, judged
     * with the merges found so far applied to both, until no further merge is found; refuses the ones left.
     */
    private static void mergeRenamedClashes(Map<Object, @Nullable Object> document, List<String> problems) {
        if (!(document.get(COMPONENTS) instanceof Map<?, ?> components)) {
            return;
        }
        Map<String, String> merged = new TreeMap<>();
        boolean found = true;
        while (found) {
            found = false;
            for (Map.Entry<?, ?> section : components.entrySet()) {
                if (!(section.getValue() instanceof Map<?, ?> entries)) {
                    continue;
                }
                for (Object key : entries.keySet()) {
                    String name = String.valueOf(key);
                    String renamed = ref(section.getKey(), name);
                    String base = baseName(name);
                    if (base == null || merged.containsKey(renamed) || !entries.containsKey(base)) {
                        continue;
                    }
                    if (Objects.equals(rewrite(entries.get(base), merged), rewrite(entries.get(name), merged))) {
                        merged.put(renamed, ref(section.getKey(), base));
                        found = true;
                    }
                }
            }
        }
        Map<Object, @Nullable Object> kept = new LinkedHashMap<>();
        components.forEach((sectionName, section) -> {
            if (!(section instanceof Map<?, ?> entries)) {
                kept.put(sectionName, section);
                return;
            }
            Map<Object, @Nullable Object> entriesKept = new LinkedHashMap<>();
            entries.forEach((name, component) -> {
                String reference = ref(sectionName, String.valueOf(name));
                if (merged.containsKey(reference)) {
                    return;
                }
                String base = baseName(String.valueOf(name));
                if (base != null) {
                    String at = "/components/" + BundleDocument.escape(String.valueOf(sectionName)) + "/"
                            + BundleDocument.escape(String.valueOf(name));
                    problems.add(
                            entries.containsKey(base)
                                    ? at + ": two source files share the name " + base
                                            + "; give one of them another file name"
                                    : at + ": the name holds " + DELIMITER + ", which vacuum puts in the name of a"
                                            + " clash; give the source file a name without it");
                }
                entriesKept.put(name, component);
            });
            kept.put(sectionName, entriesKept);
        });
        document.put(COMPONENTS, kept);
        document.replaceAll((key, value) -> rewrite(value, merged));
    }

    private static @Nullable String baseName(String name) {
        int at = name.indexOf(DELIMITER);
        return at > 0 ? name.substring(0, at) : null;
    }

    private static String ref(Object section, String name) {
        return PREFIX + BundleDocument.escape(String.valueOf(section)) + "/" + BundleDocument.escape(name);
    }

    /** A copy of {@code node} with each reference {@code merged} maps rewritten to its target. */
    private static @Nullable Object rewrite(@Nullable Object node, Map<String, String> merged) {
        if (node instanceof Map<?, ?> map) {
            Map<Object, @Nullable Object> copied = new LinkedHashMap<>();
            map.forEach((key, value) -> copied.put(
                    key,
                    BundleDocument.REF.equals(key) && value instanceof String ref
                            ? merged.getOrDefault(ref, ref)
                            : rewrite(value, merged)));
            return copied;
        }
        if (node instanceof List<?> list) {
            List<@Nullable Object> copied = new ArrayList<>(list.size());
            list.forEach(item -> copied.add(rewrite(item, merged)));
            return copied;
        }
        return node;
    }

    /** Refuses every {@code $ref} that is not a whole reference to a component the document holds. */
    private static void checkReferences(
            Map<Object, @Nullable Object> document, String pointer, @Nullable Object node, List<String> problems) {
        if (node instanceof Map<?, ?> map) {
            if (map.containsKey(BundleDocument.REF)) {
                String at = pointer + "/" + BundleDocument.escape(BundleDocument.REF);
                Object ref = map.get(BundleDocument.REF);
                if (!(ref instanceof String text) || !text.startsWith(PREFIX)) {
                    problems.add(at + ": " + ref + " is not a reference to a component of the bundle; the bundle must"
                            + " be self-contained");
                } else if (map.size() > 1) {
                    List<String> siblings = map.keySet().stream()
                            .map(String::valueOf)
                            .filter(key -> !BundleDocument.REF.equals(key))
                            .sorted()
                            .toList();
                    problems.add(at + ": a reference with sibling keys (" + String.join(", ", siblings)
                            + "); the checks read a reference as the component it names, nothing more");
                } else if (BundleDocument.target(document, map).isEmpty()) {
                    problems.add(at + ": " + text + " names no component of the bundle");
                }
            }
            map.forEach((key, value) -> {
                if (!BundleDocument.REF.equals(key)) {
                    checkReferences(
                            document, pointer + "/" + BundleDocument.escape(String.valueOf(key)), value, problems);
                }
            });
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                checkReferences(document, pointer + "/" + i, list.get(i), problems);
            }
        }
    }

    /**
     * Refuses a component that refers back to itself, directly or through others. The inlined bundle could not hold
     * one (its every reference was inlined, and a reference left in it was refused), so resolving every reference of
     * this bundle gives what the inlined bundle held; a recursive schema is a contract decision of its own.
     */
    private static void refuseRecursion(Map<Object, @Nullable Object> document, List<String> problems) {
        if (!(document.get(COMPONENTS) instanceof Map<?, ?> components)) {
            return;
        }
        components.forEach((section, entries) -> {
            if (entries instanceof Map<?, ?> named) {
                named.forEach((name, component) -> {
                    String self = ref(section, String.valueOf(name));
                    if (reaches(document, component, self, new HashSet<>())) {
                        problems.add(self.substring(1) + ": the component refers back to itself; the bundle holds no"
                                + " recursive component");
                    }
                });
            }
        });
    }

    /** Whether {@code node} reaches the component {@code self}, following each component reference once. */
    private static boolean reaches(
            Map<Object, @Nullable Object> document, @Nullable Object node, String self, Set<String> followed) {
        if (node instanceof Map<?, ?> map) {
            if (map.get(BundleDocument.REF) instanceof String ref) {
                if (ref.equals(self)) {
                    return true;
                }
                if (!followed.add(ref)) {
                    return false;
                }
                return BundleDocument.target(document, map)
                        .map(target -> reaches(document, target.component(), self, followed))
                        .orElse(false);
            }
            return map.values().stream().anyMatch(value -> reaches(document, value, self, followed));
        }
        if (node instanceof List<?> list) {
            return list.stream().anyMatch(item -> reaches(document, item, self, followed));
        }
        return false;
    }

    private static Map<Object, @Nullable Object> copyMap(Map<?, ?> map) {
        Map<Object, @Nullable Object> copied = new LinkedHashMap<>();
        map.forEach(copied::put);
        return copied;
    }
}
