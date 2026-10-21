package ai.speckitty.charter.packs.infra.storage;

import org.snakeyaml.engine.v2.common.ScalarStyle;

/** Writes one scalar value as source text, keeping the original style when it can carry the value. */
final class ScalarWriter {

    private ScalarWriter() {}

    /** The characters that end a plain scalar inside a flow collection, though not in a block one. */
    private static final String FLOW_INDICATORS = ",[]{}";

    /**
     * Writes {@code value} in {@code style} where that style can carry it, and double-quoted otherwise.
     *
     * @param value the new value
     * @param style the style of the scalar the value replaces
     * @param original the source text of the scalar the value replaces
     * @param inFlow whether the scalar sits inside a flow collection, {@code [ ]} or {@code { }}
     * @return the scalar's new source text
     */
    static String write(String value, ScalarStyle style, String original, boolean inFlow) {
        return switch (style) {
            case PLAIN -> plainSafe(value, original, inFlow) ? value : doubleQuoted(value);
            case SINGLE_QUOTED ->
                value.indexOf('\n') < 0 && carriedAsWritten(value)
                        ? "'" + value.replace("'", "''") + "'"
                        : doubleQuoted(value);
            case LITERAL ->
                (carriedAsWritten(value) ? literal(value, original) : java.util.Optional.<String>empty())
                        .orElseGet(() -> doubleQuoted(value) + lineBreakSuffix(original));
            // Folding a value back into a folded block needs the emitter of open question 6; until then the
            // value is written double-quoted, keeping the line break the block owned.
            default -> doubleQuoted(value) + lineBreakSuffix(original);
        };
    }

    /**
     * Writes a literal block with the original header and indentation, when the header's chomping can
     * carry the value's trailing line breaks and no line would need an indentation indicator.
     */
    private static java.util.Optional<String> literal(String value, String original) {
        int headerEnd = original.indexOf('\n');
        if (headerEnd < 0) {
            return java.util.Optional.empty();
        }
        String header = original.substring(0, headerEnd + 1);
        String indent = original.substring(headerEnd + 1)
                .lines()
                .filter(l -> !l.isBlank())
                .findFirst()
                .map(l -> l.substring(0, l.length() - l.stripLeading().length()))
                .orElse("");
        String body = value;
        int trailing = 0;
        while (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);
            trailing++;
        }
        boolean strip = header.contains("-");
        boolean keep = header.contains("+");
        boolean chompingFits = strip ? trailing == 0 : keep ? trailing >= 1 : trailing == 1;
        if (indent.isEmpty()
                || !chompingFits
                || body.isEmpty()
                || body.startsWith(" ")
                || body.contains("\r")
                || header.chars().anyMatch(Character::isDigit)) {
            return java.util.Optional.empty();
        }
        StringBuilder out = new StringBuilder(header);
        for (String line : body.split("\n", -1)) {
            out.append(line.isEmpty() ? "" : indent + line).append('\n');
        }
        for (int i = 1; i < trailing; i++) {
            out.append('\n');
        }
        return java.util.Optional.of(out.toString());
    }

    /** The line breaks and indentation a block scalar token owns after its content. */
    private static String lineBreakSuffix(String original) {
        int end = original.length();
        while (end > 0
                && (original.charAt(end - 1) == '\n'
                        || original.charAt(end - 1) == '\r'
                        || original.charAt(end - 1) == ' ')) {
            end--;
        }
        return original.substring(end);
    }

    /**
     * Whether a single-quoted or block scalar can hold {@code value} as written: every code point is printable
     * (a tab and a line feed are, though {@link #printable} leaves them to the escapes), none is a lone
     * surrogate, and there is no carriage return, which a reader folds into a line feed. Only a double-quoted
     * scalar escapes the others.
     */
    private static boolean carriedAsWritten(String value) {
        return value.codePoints().allMatch(c -> printable(c) || c == '\t' || c == '\n');
    }

    /**
     * A plain scalar is kept plain only when writing the value back re-reads as the same string: the
     * value must equal the original text (a single-line plain scalar) or be a single line the YAML
     * reader takes as a string unchanged, read in the context the scalar sits in. Inside a flow collection the
     * value must also hold none of the flow indicators {@code ,[]{}}, which end a plain scalar there, and must
     * re-read unchanged as a sequence entry and as a mapping value of a flow collection.
     */
    private static boolean plainSafe(String value, String original, boolean inFlow) {
        if (value.equals(original)) {
            return true;
        }
        if (value.isEmpty() || value.indexOf('\n') >= 0 || !value.strip().equals(value)) {
            return false;
        }
        if (inFlow && value.chars().anyMatch(c -> FLOW_INDICATORS.indexOf(c) >= 0)) {
            return false;
        }
        return rereadsAsValue("k: " + value + "\n", value)
                && (!inFlow
                        || (rereadsAsValue("[" + value + "]", value) && rereadsAsValue("{k: " + value + "}", value)));
    }

    /** Whether {@code source} loads as a one-entry sequence or mapping whose single value is {@code value}. */
    private static boolean rereadsAsValue(String source, String value) {
        try {
            return switch (Yaml.load(source)) {
                case java.util.Map<?, ?> m -> m.size() == 1 && value.equals(m.get("k"));
                case java.util.List<?> l -> l.size() == 1 && value.equals(l.get(0));
                case null, default -> false;
            };
        } catch (RuntimeException notPlain) {
            return false;
        }
    }

    static String doubleQuoted(String value) {
        StringBuilder out = new StringBuilder("\"");
        value.codePoints().forEach(c -> {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> {
                    if (printable(c)) {
                        out.appendCodePoint(c);
                    } else if (c <= 0xff) {
                        out.append(String.format("\\x%02x", c));
                    } else {
                        out.append(String.format("\\u%04x", c));
                    }
                }
            }
        });
        return out.append('"').toString();
    }

    /**
     * Whether YAML 1.2 lets {@code c} stand for itself in a stream (production [1] c-printable, less tab, line
     * feed and carriage return, which a double-quoted scalar escapes anyway). Every non-printable code point is
     * below U+10000, so a four-digit escape carries it.
     */
    private static boolean printable(int c) {
        return (c >= 0x20 && c <= 0x7e)
                || c == 0x85
                || (c >= 0xa0 && c <= 0xd7ff)
                || (c >= 0xe000 && c <= 0xfffd)
                || (c >= 0x10000 && c <= 0x10ffff);
    }
}
