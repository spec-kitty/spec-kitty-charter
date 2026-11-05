package ai.speckitty.charter.activations.domain;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The RFC 8785 JSON Canonicalization Scheme (JCS), over the subset the activation document uses: objects, arrays,
 * strings, integers and booleans. It gives the document one byte form on the JVM and in the native image, which the
 * revision hashes (FR-005).
 *
 * <p>Research R3 has the canonicaliser walk Jackson's {@code JsonNode}. The activation domain names no library
 * (ADR-0006), so this one serialises the domain's own value tree instead: a {@link Map} with {@link String} keys, a
 * {@link List}, a {@link String}, an {@link Integer} or {@link Long}, or a {@link Boolean}. The inbound adapter maps its
 * body to the domain and the domain computes its revision from {@link ActivationDocument#toCanonicalTree()}; the bytes
 * are those RFC 8785 gives for the same JSON value.
 *
 * <ul>
 *   <li>Member names sort by UTF-16 code unit ({@link String#compareTo}), as RFC 8785 section 3.2.3 asks; a sort by
 *       code point would differ for the supplementary planes.
 *   <li>Strings escape as section 3.2.2.2 does: {@code "} and {@code \}, the short forms {@code \b \t \n \f \r}, and
 *       every other control character as a lowercase {@code \}{@code u00xx}; everything else, {@code /} and non-ASCII
 *       included, is written raw in UTF-8. A lone surrogate has no UTF-8 form and is refused.
 *   <li>Integers are written in ECMAScript form, and only those of magnitude at most 2<sup>53</sup> − 1, which I-JSON
 *       holds exactly. No other number type is accepted.
 *   <li>{@code null} is refused: the document never holds it.
 * </ul>
 */
public final class Jcs {

    /** The largest integer magnitude an I-JSON number holds exactly: 2<sup>53</sup> − 1. */
    public static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private static final HexFormat HEX = HexFormat.of();

    private Jcs() {}

    /**
     * The canonical UTF-8 bytes of a value tree.
     *
     * @param value a map with string keys, a list, a string, an integer, a long or a boolean, nested freely
     * @return the RFC 8785 serialization, as UTF-8
     * @throws IllegalArgumentException when the tree holds {@code null}, another type, a non-string member name, a lone
     *     surrogate or an integer beyond {@link #MAX_SAFE_INTEGER}
     */
    public static byte[] canonical(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void write(@Nullable Object value, StringBuilder out) {
        switch (value) {
            case null -> throw new IllegalArgumentException("A canonical JSON value is never null.");
            case Map<?, ?> map -> writeObject(map, out);
            case List<?> list -> writeArray(list, out);
            case String text -> writeString(text, out);
            case Boolean bool -> out.append(bool.booleanValue());
            case Integer number -> writeInteger(number, out);
            case Long number -> writeInteger(number, out);
            default ->
                throw new IllegalArgumentException(
                        "A canonical JSON value has no " + value.getClass().getSimpleName() + " form.");
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out) {
        TreeMap<String, @Nullable Object> sorted = new TreeMap<>();
        map.forEach((key, member) -> sorted.put(memberName(key), member));
        out.append('{');
        String separator = "";
        for (Map.Entry<String, @Nullable Object> member : sorted.entrySet()) {
            out.append(separator);
            writeString(member.getKey(), out);
            out.append(':');
            write(member.getValue(), out);
            separator = ",";
        }
        out.append('}');
    }

    private static String memberName(@Nullable Object key) {
        if (key instanceof String name) {
            return name;
        }
        throw new IllegalArgumentException("A canonical JSON member name is a string, not " + key + ".");
    }

    private static void writeArray(List<?> list, StringBuilder out) {
        out.append('[');
        String separator = "";
        for (Object element : list) {
            out.append(separator);
            write(element, out);
            separator = ",";
        }
        out.append(']');
    }

    private static void writeString(String text, StringBuilder out) {
        if (text.codePoints().anyMatch(Jcs::isSurrogate)) {
            throw new IllegalArgumentException("A canonical JSON string holds no lone surrogate: " + escaped(text));
        }
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            escape(text.charAt(i), out);
        }
        out.append('"');
    }

    /** {@link String#codePoints()} yields a paired surrogate as one code point, so any surrogate left is lone. */
    private static boolean isSurrogate(int codePoint) {
        return codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE;
    }

    private static String escaped(String text) {
        StringBuilder out = new StringBuilder();
        text.chars().forEach(c -> out.append(c < 0x80 ? Character.toString(c) : "\\u" + HEX.toHexDigits((char) c)));
        return out.toString();
    }

    private static void escape(char c, StringBuilder out) {
        switch (c) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\b' -> out.append("\\b");
            case '\t' -> out.append("\\t");
            case '\n' -> out.append("\\n");
            case '\f' -> out.append("\\f");
            case '\r' -> out.append("\\r");
            default -> appendPlain(c, out);
        }
    }

    private static void appendPlain(char c, StringBuilder out) {
        if (c < 0x20) {
            out.append("\\u00").append(HEX.toHexDigits((byte) c));
        } else {
            out.append(c);
        }
    }

    private static void writeInteger(long number, StringBuilder out) {
        if (number > MAX_SAFE_INTEGER || number < -MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException(
                    "A canonical JSON integer is at most 2^53 - 1 in magnitude, not " + number + ".");
        }
        out.append(number);
    }
}
