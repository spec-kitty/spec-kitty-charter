package ai.speckitty.charter.contracts.identifiers;

import java.util.regex.Pattern;

/**
 * A JSON-Schema {@code pattern}, evaluated as JSON Schema does: ECMA-262, not anchored (a {@code find}, not a whole
 * match). Two ECMA rules differ from {@link java.util.regex}, and are rewritten outside a character class: {@code $}
 * matches only at the end of the input (Java's also matches before a final line terminator), so it becomes {@code \z};
 * and {@code .} excludes exactly {@code \n}, {@code \r}, U+2028 and U+2029 (Java's also excludes U+0085). Every other
 * construct the identifier patterns use means the same in both.
 */
final class EcmaPattern {

    private final Pattern compiled;

    EcmaPattern(String ecma) {
        this.compiled = Pattern.compile(toJava(ecma));
    }

    /** Whether the pattern finds a match anywhere in the text, as JSON Schema's {@code pattern} keyword asks. */
    boolean accepts(String text) {
        return compiled.matcher(text).find();
    }

    static String toJava(String ecma) {
        StringBuilder java = new StringBuilder(ecma.length() + 16);
        boolean inClass = false;
        boolean escaped = false;
        for (int i = 0; i < ecma.length(); i++) {
            char c = ecma.charAt(i);
            boolean literal = inClass || escaped || c == '\\';
            java.append(literal ? String.valueOf(c) : outsideClass(c));
            inClass = (!escaped && c == '[') || (inClass && (escaped || c != ']'));
            escaped = !escaped && c == '\\';
        }
        return java.toString();
    }

    private static String outsideClass(char c) {
        return switch (c) {
            case '$' -> "\\z";
            case '.' -> "[^\\n\\r\\u2028\\u2029]";
            default -> String.valueOf(c);
        };
    }
}
