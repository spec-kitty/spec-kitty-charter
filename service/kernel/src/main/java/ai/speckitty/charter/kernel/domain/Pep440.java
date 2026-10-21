package ai.speckitty.charter.kernel.domain;

import java.util.regex.Pattern;

/**
 * PEP 440 version specifier sets, as {@code packaging.specifiers.SpecifierSet} accepts them.
 *
 * <p>The grammar is the one in PEP 440 ("Version specifiers") and the {@code Specifier} regex of {@code packaging}
 * 26.3, the release core's venv pins. The test corpus ({@code scripts/packs/pep440_corpus.py}) is decided by that
 * library, quirks included: for example, {@code ~=} takes Unicode case folding where the other operators fold ASCII
 * only, and {@code ===} takes any text with no whitespace, semicolon or closing parenthesis.
 *
 * <p>A set is a comma-separated list of clauses. Each clause is trimmed; an empty clause is skipped, so the empty
 * set, a lone comma and a trailing comma are valid.
 */
public final class Pep440 {

    /** Python's {@code str.isspace} characters; {@code \s} in a Python pattern and {@code str.strip} use them. */
    private static final String SPACE =
            "\\t-\\r\\x1c-\\x20\\x85\\xa0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000";

    private static final String WS = "[" + SPACE + "]*";
    private static final String EPOCH = "v?(?:[0-9]+!)?";
    private static final String PRE = "(?:[-_.]?(?:alpha|beta|preview|pre|a|b|c|rc)[-_.]?[0-9]*)?";
    private static final String POST = "(?:(?:-[0-9]+)|(?:[-_.]?(?:post|rev|r)[-_.]?[0-9]*))?";
    private static final String DEV = "(?:[-_.]?dev[-_.]?[0-9]*)?";
    private static final String LOCAL = "(?:\\+[a-z0-9]++(?:[-_.][a-z0-9]++)*+)?";

    /**
     * The dotted release, {@code N(.N)*}. The repeated groups are possessive: Java's regex engine recurses once per
     * iteration of a greedy group and overflows the stack on a long value, but runs a possessive group in a loop.
     * Possessive is exact here, because no suffix can start with a digit or with {@code .} followed by a digit, so
     * giving a segment back never lets the rest match.
     */
    private static final String RELEASE = "[0-9]++(?:\\.[0-9]++)*+";

    /** The release of {@code ~=}: at least two segments. */
    private static final String RELEASE_2 = "[0-9]++(?:\\.[0-9]++)++";

    /** {@code ===}: the escape hatch, an arbitrary string. */
    private static final String IDENTITY = "===" + WS + "[^" + SPACE + ";)]*";

    /**
     * {@code ==} and {@code !=}: a wildcard, or the pre, post, dev and local suffixes. Python wraps the suffixes in
     * {@code (?a:)}, so case folding is ASCII only there.
     */
    private static final String EQUALITY =
            "(?:==|!=)" + WS + EPOCH + RELEASE + "(?:\\.\\*|(?-u:" + PRE + POST + DEV + LOCAL + "))?";

    /** {@code ~=}: at least two release segments, no wildcard and no local version; Unicode case folding. */
    private static final String COMPATIBLE = "~=" + WS + EPOCH + RELEASE_2 + PRE + POST + DEV;

    /** The ordering operators: no wildcard and no local version. */
    private static final String ORDERING = "(?:<=|>=|<|>)" + WS + EPOCH + RELEASE + "(?-u:" + PRE + POST + DEV + ")";

    private static final String SPECIFIER =
            "(?:" + IDENTITY + ")|(?:" + EQUALITY + ")|(?:" + COMPATIBLE + ")|(?:" + ORDERING + ")";

    private static final Pattern CLAUSE =
            Pattern.compile(WS + "(?:" + SPECIFIER + ")" + WS, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private Pep440() {}

    /**
     * Whether a text is a PEP 440 specifier set.
     *
     * @param text the text, for example {@code >=4.0.0rc7,<5}
     * @return whether {@code packaging.specifiers.SpecifierSet(text)} accepts it
     */
    public static boolean isValidSpecifierSet(String text) {
        int start = 0;
        while (start <= text.length()) {
            int comma = text.indexOf(',', start);
            int end = comma < 0 ? text.length() : comma;
            String clause = strip(text.substring(start, end));
            if (!clause.isEmpty() && !CLAUSE.matcher(clause).matches()) {
                return false;
            }
            if (comma < 0) {
                break;
            }
            start = comma + 1;
        }
        return true;
    }

    private static String strip(String text) {
        int from = 0;
        int to = text.length();
        while (from < to && isSpace(text.charAt(from))) {
            from++;
        }
        while (to > from && isSpace(text.charAt(to - 1))) {
            to--;
        }
        return text.substring(from, to);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85;
    }
}
