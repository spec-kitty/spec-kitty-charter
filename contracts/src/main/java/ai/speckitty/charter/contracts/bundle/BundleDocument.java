package ai.speckitty.charter.contracts.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The bundled contract as the contract's checks read it: each component reference read as the component it names.
 *
 * <p>The bundle keeps {@code components} and refers to them ({@code $ref: '#/components/schemas/Problem'}) instead of
 * inlining every use (#294). A check judges a response where an operation uses it, so {@link #inline} reads every
 * reference as a copy of the component it names, recursively; what the check then walks is the document as if every
 * reference were inlined, and every finding carries the pointer of the use. A reference is left in place, for the
 * check to name, when it is not a whole {@code $ref} to a component the document holds: an external file, a missing
 * component, a reference with sibling keys, or a component that refers back to itself where it recurs.
 *
 * <p>The bundle is read with snakeyaml's default limits: it stays far below the 3 MiB of code points the default
 * admits, so no reader of it raises that limit.
 */
public final class BundleDocument {

    /** The key of a reference. */
    public static final String REF = "$ref";

    private static final Pattern COMPONENT_REF = Pattern.compile("#/components/([^/]+)/([^/]+)");

    private BundleDocument() {}

    /**
     * Reads the bundle at {@code bundle}, a YAML or JSON OpenAPI document, and reads each component reference in it
     * as the component it names.
     *
     * @param bundle the path of the bundled contract
     * @return the loaded document, its component references inlined
     * @throws IOException when the file cannot be read
     */
    public static @Nullable Object load(Path bundle) throws IOException {
        try (InputStream in = Files.newInputStream(bundle)) {
            Object document =
                    new Load(LoadSettings.builder().setLabel(bundle.toString()).build()).loadFromInputStream(in);
            return inline(document);
        }
    }

    /**
     * A copy of {@code document} with every component reference replaced by a copy of the component it names, as
     * the class describes. The document itself is not changed.
     *
     * @param document a loaded OpenAPI document: mappings, lists and scalars as a YAML loader produces them
     * @return the copy
     */
    public static @Nullable Object inline(@Nullable Object document) {
        return new Inliner(document).copyDocument();
    }

    /**
     * The component a reference names, if {@code node} is a reference to a component of {@code document}: a mapping
     * whose only key is {@code $ref}, with a value {@code #/components/<section>/<name>} that {@code document} holds.
     *
     * @param document the document the reference is read in
     * @param node the node that may be a reference
     * @return the reference, or empty when the node is not such a reference
     */
    static Optional<Target> target(@Nullable Object document, @Nullable Object node) {
        if (!(node instanceof Map<?, ?> map) || map.size() != 1 || !(map.get(REF) instanceof String ref)) {
            return Optional.empty();
        }
        Matcher match = COMPONENT_REF.matcher(ref);
        if (!match.matches()
                || !(document instanceof Map<?, ?> root)
                || !(root.get("components") instanceof Map<?, ?> components)
                || !(components.get(unescape(match.group(1))) instanceof Map<?, ?> section)) {
            return Optional.empty();
        }
        String name = unescape(match.group(2));
        return section.containsKey(name) ? Optional.of(new Target(ref, section.get(name))) : Optional.empty();
    }

    /**
     * A reference and the component it names.
     *
     * @param ref the reference, {@code #/components/<section>/<name>}
     * @param component the component the document holds under that name
     */
    record Target(String ref, @Nullable Object component) {}

    /**
     * A reference token unescaped as RFC 6901 unescapes it.
     *
     * @param token the escaped token
     * @return the token
     */
    static String unescape(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    /**
     * A reference token escaped as RFC 6901 escapes it.
     *
     * @param token the token
     * @return the escaped token
     */
    static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    /**
     * Copies a document, inlining its component references. The stack holds the references being inlined, and
     * {@code copying} the mappings and lists being copied, so a node that contains itself through YAML aliases is
     * copied as a node that contains itself, for the check to name, instead of without end.
     */
    private static final class Inliner {

        private final @Nullable Object document;
        private final IdentityHashMap<Object, Object> copying = new IdentityHashMap<>();

        Inliner(@Nullable Object document) {
            this.document = document;
        }

        /** The document's copy; a component under {@code components} is read with its own reference on the stack. */
        @Nullable Object copyDocument() {
            if (!(document instanceof Map<?, ?> root)) {
                return copy(document, new ArrayDeque<>());
            }
            Map<Object, @Nullable Object> copied = new LinkedHashMap<>();
            root.forEach((key, value) -> copied.put(
                    key, "components".equals(key) ? copyComponents(value) : copy(value, new ArrayDeque<>())));
            return copied;
        }

        private @Nullable Object copyComponents(@Nullable Object components) {
            if (!(components instanceof Map<?, ?> sections)) {
                return copy(components, new ArrayDeque<>());
            }
            Map<Object, @Nullable Object> copied = new LinkedHashMap<>();
            sections.forEach((section, entries) -> {
                if (!(entries instanceof Map<?, ?> named)) {
                    copied.put(section, copy(entries, new ArrayDeque<>()));
                    return;
                }
                Map<Object, @Nullable Object> copiedEntries = new LinkedHashMap<>();
                named.forEach((name, component) -> {
                    Deque<String> inlining = new ArrayDeque<>();
                    inlining.push(
                            "#/components/" + escape(String.valueOf(section)) + "/" + escape(String.valueOf(name)));
                    copiedEntries.put(name, copy(component, inlining));
                });
                copied.put(section, copiedEntries);
            });
            return copied;
        }

        private @Nullable Object copy(@Nullable Object node, Deque<String> inlining) {
            Optional<Target> target = target(document, node);
            if (target.isPresent() && !inlining.contains(target.get().ref())) {
                inlining.push(target.get().ref());
                Object component = copy(target.get().component(), inlining);
                inlining.pop();
                return component;
            }
            if (node != null && copying.containsKey(node)) {
                return copying.get(node);
            }
            if (node instanceof Map<?, ?> map) {
                Map<Object, @Nullable Object> copied = new LinkedHashMap<>();
                copying.put(node, copied);
                map.forEach((key, value) -> copied.put(key, copy(value, inlining)));
                copying.remove(node);
                return copied;
            }
            if (node instanceof List<?> list) {
                List<@Nullable Object> copied = new ArrayList<>(list.size());
                copying.put(node, copied);
                list.forEach(item -> copied.add(copy(item, inlining)));
                copying.remove(node);
                return copied;
            }
            return node;
        }
    }
}
