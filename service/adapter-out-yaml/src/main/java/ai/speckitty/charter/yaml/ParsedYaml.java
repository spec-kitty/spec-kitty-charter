package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.PackContents.ParseFailure;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.events.CollectionEndEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.Event;

/**
 * A pack file's text parsed as YAML: the document, or the failure reduced to what may be shown.
 *
 * <p>A file counts as not YAML, with the parser's first line as the reason, when the parser refuses it, when a
 * mapping has a key that is not a string (a schema validator cannot tell {@code 1} from {@code "1"}), or when the
 * document is nested too deeply or contains itself, which no validator can walk, or when it holds a scalar that is
 * no string, number, boolean or null ({@code !!binary} is the one the parser makes), which a validator cannot judge.
 * The depth is counted over the parser's event stream, which is read with an explicit stack, before the document is
 * composed: the composer recurses once per level, so a document nested deeper than the JVM stack would overflow it. No
 * {@link StackOverflowError} is ever caught. The first line of the parser's
 * message says what it was doing or found, as {@code while parsing a block mapping}; its later lines hold the position
 * and a snippet of the document, so nothing of the host reaches the reason. The parser is given no file name, only
 * the label {@code charter}.
 *
 * @param document the parsed document; {@code null} for an empty stream or a YAML null, and when the text did not
 *     parse
 * @param failure why the text did not parse; {@code null} when it did
 */
record ParsedYaml(@Nullable Object document, @Nullable ParseFailure failure) {

    /** The most collections that may be nested inside one another; real packs nest fewer than ten. */
    private static final int MAX_DEPTH = 200;

    private static final String TOO_DEEP = "the document is nested too deeply";
    private static final String NOT_PLAIN = "a value is not a string, a number, a boolean or null";

    /**
     * Parses a file's text.
     *
     * @param text the file's YAML text
     * @param file the pack-relative path, named in a failure
     * @return the document or the failure; never throws on content
     */
    static ParsedYaml parse(String text, String file) {
        if (nestedTooDeeply(text)) {
            return failed(file, TOO_DEEP);
        }
        Object document;
        try {
            document = Yaml.load(text);
        } catch (RuntimeException e) {
            return failed(file, firstLine(e));
        }
        String problem = problem(document, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
        return problem == null ? new ParsedYaml(document, null) : failed(file, problem);
    }

    /**
     * Whether the text nests collections deeper than {@link #MAX_DEPTH}, counted over the parser's events. A text the
     * parser refuses part-way is not too deep: the loader reports it with the parser's own words.
     */
    private static boolean nestedTooDeeply(String text) {
        int depth = 0;
        try {
            for (Event event : new Parse(Yaml.settings()).parseString(text)) {
                if (event instanceof CollectionStartEvent) {
                    depth++;
                    if (depth > MAX_DEPTH) {
                        return true;
                    }
                } else if (event instanceof CollectionEndEvent) {
                    depth--;
                }
            }
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }

    private static ParsedYaml failed(String file, String firstLine) {
        return new ParsedYaml(null, new ParseFailure(file, firstLine));
    }

    /** The first reason a loaded document cannot be validated, or {@code null} when it can. */
    private static @Nullable String problem(@Nullable Object value, int depth, Set<Object> path) {
        if (!(value instanceof Map<?, ?>) && !(value instanceof Collection<?>)) {
            return plain(value) ? null : NOT_PLAIN;
        }
        if (depth >= MAX_DEPTH) {
            return TOO_DEEP;
        }
        if (!path.add(value)) {
            return "the document contains itself";
        }
        try {
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        return "a mapping key is not a string";
                    }
                    String problem = problem(entry.getValue(), depth + 1, path);
                    if (problem != null) {
                        return problem;
                    }
                }
            } else if (value instanceof Collection<?> items) {
                for (Object item : items) {
                    String problem = problem(item, depth + 1, path);
                    if (problem != null) {
                        return problem;
                    }
                }
            }
            return null;
        } finally {
            path.remove(value);
        }
    }

    /** What the loader makes of a YAML scalar a pack may hold; {@code !!binary} makes a {@code byte[]}, which is not. */
    private static boolean plain(@Nullable Object scalar) {
        return scalar == null
                || scalar instanceof String
                || scalar instanceof Integer
                || scalar instanceof Long
                || scalar instanceof BigInteger
                || scalar instanceof Double
                || scalar instanceof Boolean;
    }

    /** The first non-blank line of the parser's message. */
    private static String firstLine(RuntimeException e) {
        return String.valueOf(e.getMessage())
                .lines()
                .filter(l -> !l.isBlank())
                .findFirst()
                .orElse(e.getClass().getSimpleName());
    }
}
