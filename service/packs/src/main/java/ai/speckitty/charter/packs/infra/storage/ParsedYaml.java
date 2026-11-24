package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.events.CollectionEndEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;

/**
 * A pack file's text parsed as YAML: the document, or the failure reduced to what may be shown.
 *
 * <p>A file counts as not YAML, with the parser's first line as the reason, when the parser refuses it, when a
 * mapping has a key that is not a string (a schema validator cannot tell {@code 1} from {@code "1"}), or when the
 * document is nested too deeply, expands past {@link #MAX_NODES} nodes through aliases, expands past
 * {@link Yaml#MAX_CODE_POINTS} code points of strings and digits through aliases, holds a number whose text is past
 * {@link #MAX_NUMBER_TEXT}, or contains itself, which no
 * validator can walk, or when it holds a scalar that is
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

    /**
     * The most nodes a document may expand to, a shared (aliased) node counted each time it is reached. The loader
     * shares an aliased node, so a document of a few hundred bytes can chain aliases into billions of paths and make
     * every walk over it, the validator's included, run for minutes or exhaust the heap. Real packs hold under two
     * thousand nodes a file (the largest open-packs file has about 1 700); 100 000 leaves fifty times that and keeps
     * the walk to milliseconds.
     */
    static final int MAX_NODES = 100_000;

    /**
     * The walk's running counts: the nodes reached, and the code points of every string reached, a key or a value, a
     * shared (aliased) one counted each time it is reached.
     */
    private static final class Budget {
        private int nodes;
        private long codePoints;
    }

    /**
     * The most code points a number scalar's text may hold (Tier R B3'). The loader converts a number's digits to a
     * {@code BigInteger} or {@code BigDecimal} in time quadratic in their count (500 000 digits take about 4 s), and
     * every later rendering of the value as text costs as much again, so the text is judged on the parser's events,
     * before the loader converts it. A pack's numbers are versions' parts, counts and weights, a few digits each; 1 000
     * digits leaves far more than any of them needs and converts in microseconds. The same digits quoted are a string,
     * bounded by the cap like any other.
     */
    static final int MAX_NUMBER_TEXT = 1_000;

    /** log10(2): the decimal digits a binary digit is worth, for a lower bound of a big number's digit count. */
    private static final double DIGITS_PER_BIT = 0.30102999566398120;

    private static final String TOO_DEEP = "the document is nested too deeply";
    private static final String TOO_LARGE = "the document is too large";
    private static final String NOT_PLAIN = "a value is not a string, a number, a boolean or null";

    /**
     * The document of YAML text put through the guards of {@link #parse}, or {@code null} when they refuse it: for a
     * reader that only looks at a text the pack readers have already judged (the round trip's), so no load of pack
     * content goes unguarded.
     *
     * @param text the YAML text
     * @return the document; {@code null} for an empty stream, a YAML null, and a text the guards refuse
     */
    static @Nullable Object documentOrNull(String text) {
        return check(text).document();
    }

    /**
     * Parses a file's text.
     *
     * @param text the file's YAML text
     * @param file the pack-relative path, named in a failure
     * @return the document or the failure; never throws on content
     */
    static ParsedYaml parse(String text, String file) {
        Checked checked = check(text);
        String problem = checked.problem();
        return problem == null ? new ParsedYaml(checked.document(), null) : failed(file, problem);
    }

    /**
     * YAML text put through the guards of {@link #parse}: the document, or the first problem and where the parser
     * found it. The guards are the same; only the line is kept, for a reader that names lines (a Markdown file's
     * frontmatter).
     *
     * @param document the loaded document; {@code null} for an empty stream or a YAML null, and when there is a problem
     * @param problem the failure's first line, as {@link #parse} reports it; {@code null} when there is none
     * @param line the 1-based line of the text the parser names for its own refusal; 0 for a guard's problem, which
     *     names no line
     */
    record Checked(@Nullable Object document, @Nullable String problem, int line) {}

    /**
     * Puts text through the guards of {@link #parse}.
     *
     * @param text the YAML text
     * @return the document or the problem; never throws on content
     */
    static Checked check(String text) {
        if (text.codePointCount(0, text.length()) > Yaml.MAX_CODE_POINTS) {
            return new Checked(null, TOO_LARGE, 0);
        }
        String eventProblem = eventProblem(text);
        if (eventProblem != null) {
            return new Checked(null, eventProblem, 0);
        }
        Object document;
        try {
            document = Yaml.load(text);
        } catch (RuntimeException e) {
            int line = e instanceof MarkedYamlEngineException marked
                    ? marked.getProblemMark().map(mark -> mark.getLine() + 1).orElse(0)
                    : 0;
            return new Checked(null, firstLine(e), line);
        }
        String problem = problem(document, 0, Collections.newSetFromMap(new IdentityHashMap<>()), new Budget());
        return problem == null ? new Checked(document, null, 0) : new Checked(null, problem, 0);
    }

    /**
     * What the parser's events show before the document is composed: collections nested deeper than
     * {@link #MAX_DEPTH} ({@code TOO_DEEP}), or a number scalar whose text is past {@link #MAX_NUMBER_TEXT}
     * ({@code TOO_LARGE}), which the loader would convert in quadratic time. A text the parser refuses part-way has no
     * problem here: the loader reports it with the parser's own words.
     */
    private static @Nullable String eventProblem(String text) {
        int depth = 0;
        ScalarResolver resolver = Yaml.schema().getScalarResolver();
        try {
            for (Event event : new Parse(Yaml.settings()).parseString(text)) {
                if (event instanceof CollectionStartEvent) {
                    depth++;
                    if (depth > MAX_DEPTH) {
                        return TOO_DEEP;
                    }
                } else if (event instanceof CollectionEndEvent) {
                    depth--;
                } else if (event instanceof ScalarEvent scalar
                        && (longNumber(scalar, resolver) || longPlainForm(scalar, resolver))) {
                    return TOO_LARGE;
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /**
     * Whether a scalar is a number, by its explicit tag or, plain, by the loading schema's resolver, whose text is past
     * {@link #MAX_NUMBER_TEXT}. Only a text past the bound is resolved, so a short scalar costs a length check.
     */
    private static boolean longNumber(ScalarEvent scalar, ScalarResolver resolver) {
        String value = scalar.getValue();
        if (value.length() <= MAX_NUMBER_TEXT || value.codePointCount(0, value.length()) <= MAX_NUMBER_TEXT) {
            return false;
        }
        Optional<String> tag = scalar.getTag();
        if (tag.isPresent()) {
            return Tag.INT.getValue().equals(tag.get()) || Tag.FLOAT.getValue().equals(tag.get());
        }
        if (!scalar.isPlain()) {
            return false;
        }
        Tag resolved = resolver.resolve(value, true);
        return Tag.INT.equals(resolved) || Tag.FLOAT.equals(resolved);
    }

    private static ParsedYaml failed(String file, String firstLine) {
        return new ParsedYaml(null, new ParseFailure(file, firstLine));
    }

    /**
     * The first reason a loaded document cannot be validated, or {@code null} when it can.
     *
     * <p>The strings it holds, keys and values, and the digits of its numbers, a shared one counted each time it is
     * reached, may hold at most {@link Yaml#MAX_CODE_POINTS} code points in all (Tier R B3, B3'). The loader shares an aliased scalar, so a text inside
     * the cap and the node budget can name one long scalar thousands of times, and every serialization of the document
     * (the canonical writer's, a JSON body's) materializes each reach. A string is never longer than the text that
     * writes it, so a document without aliases is inside this bound whenever its text is inside the cap: the bound
     * refuses only an expansion, and keeps every serialization of an accepted document proportional to the cap. The
     * count stops at the first string past it, so the walk costs at most the cap plus one string.
     */
    private static @Nullable String problem(@Nullable Object value, int depth, Set<Object> path, Budget budget) {
        if (++budget.nodes > MAX_NODES) {
            return TOO_LARGE;
        }
        if (!(value instanceof Map<?, ?>) && !(value instanceof Collection<?>)) {
            return scalarProblem(value, budget);
        }
        if (depth >= MAX_DEPTH) {
            return TOO_DEEP;
        }
        if (!path.add(value)) {
            return "the document contains itself";
        }
        try {
            return value instanceof Map<?, ?> map
                    ? entriesProblem(map, depth, path, budget)
                    : itemsProblem((Collection<?>) value, depth, path, budget);
        } finally {
            path.remove(value);
        }
    }

    /**
     * The first reason a scalar cannot be validated: no plain value, or a string or a number past the expanded budget.
     * A number counts its digits as a string counts its code points (Tier R B3'): a {@code BigInteger} or
     * {@code BigDecimal} by a lower bound from its bit length, never by rendering it, so a reach costs no conversion
     * and a document without aliases is never counted past its text. A boolean, a null and a non-finite float carry no
     * text of their own length; the node budget bounds them.
     */
    private static @Nullable String scalarProblem(@Nullable Object scalar, Budget budget) {
        if (!plain(scalar)) {
            return NOT_PLAIN;
        }
        if (scalar instanceof String text) {
            return expandsPastTheCap(text, budget) ? TOO_LARGE : null;
        }
        return numberExpandsPastTheCap(scalar, budget) ? TOO_LARGE : null;
    }

    /**
     * Whether a float scalar's plain form, the text the canonical writer renders and every exact sum aligns to, is past
     * {@link #MAX_NUMBER_TEXT} (Tier R B3'', F1). A float's size lives in its exponent too: {@code 1e-999999999} is 12
     * characters and a billion digits written plain. The length is worked out from the scalar's text, never by
     * converting it. Only a text with an exponent can be longer plain than written, so a scalar that does not start as
     * a number or holds no {@code e} or {@code E} costs a character scan. A float past the double range stays a non-finite {@code Double}
     * ({@link Yaml}'s {@code ExactFloat}) and has no plain form, so it is not judged here.
     */
    private static boolean longPlainForm(ScalarEvent scalar, ScalarResolver resolver) {
        String value = scalar.getValue();
        if (value.isEmpty()
                || "-+.0123456789".indexOf(value.charAt(0)) < 0
                || (value.indexOf('e') < 0 && value.indexOf('E') < 0)) {
            return false;
        }
        Optional<String> tag = scalar.getTag();
        boolean isFloat = tag.isPresent()
                ? Tag.FLOAT.getValue().equals(tag.get())
                : scalar.isPlain() && Tag.FLOAT.equals(resolver.resolve(value, true));
        return isFloat && Double.isFinite(parsedDouble(value)) && plainLength(value) > MAX_NUMBER_TEXT;
    }

    /** The double a float's text reads as; a text no double reads as is not finite, and the loader refuses it. */
    private static double parsedDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException notADouble) {
            return Double.NaN;
        }
    }

    /**
     * The length of a decimal text's plain form, {@code BigDecimal.toPlainString()}'s, worked out from its mantissa and
     * exponent without converting it ({@link #plainLength(long, long, boolean)}): {@code 1e-998} is 1 000
     * ({@code "0."}, 997 zeros and {@code "1"}), {@code 1.5e3} is 4 ({@code "1500"}). An exponent of more than nine
     * digits is past any bound.
     */
    static long plainLength(String value) {
        int e = Math.max(value.indexOf('e'), value.indexOf('E'));
        String mantissa = e < 0 ? value : value.substring(0, e);
        String exponentText = e < 0 ? "0" : value.substring(e + 1);
        boolean negative = mantissa.startsWith("-");
        String unsigned = mantissa.replaceFirst("^[-+]", "");
        int point = unsigned.indexOf('.');
        String digits = point < 0 ? unsigned : unsigned.substring(0, point) + unsigned.substring(point + 1);
        long fraction = point < 0 ? 0 : unsigned.length() - point - 1;
        String exponentDigits = exponentText.replaceFirst("^[-+]", "").replaceFirst("^0+(?=.)", "");
        if (exponentDigits.length() > 9) {
            return Long.MAX_VALUE;
        }
        long exponent = Long.parseLong(exponentText.replaceFirst("^\\+", ""));
        String significant = digits.replaceFirst("^0+", "");
        long scale = fraction - exponent;
        if (significant.isEmpty() && scale < 0) {
            return 1; // a zero with a negative scale is "0", whatever its exponent: BigDecimal keeps no trailing zeros
        }
        return plainLength(Math.max(1, significant.length()), scale, negative && !significant.isEmpty());
    }

    /**
     * The length of {@code BigDecimal.toPlainString()} for a precision, a scale and a sign (Tier R F1): with a scale of
     * 0 or less, the digits then {@code -scale} zeros; with a scale below the precision, the digits with a point among
     * them; with a scale at or past the precision, {@code "0."}, then {@code scale - precision} zeros, then the digits,
     * so {@code scale + 2}. A negative value adds its sign.
     *
     * @param precision the unscaled value's digits, at least 1
     * @param scale the scale
     * @param negative whether the value is below zero
     * @return the plain form's length
     */
    static long plainLength(long precision, long scale, boolean negative) {
        long unsigned;
        if (scale <= 0) {
            unsigned = precision - scale;
        } else if (scale < precision) {
            unsigned = precision + 1;
        } else {
            unsigned = scale + 2;
        }
        return unsigned + (negative ? 1 : 0);
    }

    /** Adds a reached number's digits to the budget: whether the scalars reached so far are past the cap. */
    private static boolean numberExpandsPastTheCap(@Nullable Object scalar, Budget budget) {
        long digits = switch (scalar) {
            case Integer i -> Integer.toString(i).length();
            case Long l -> Long.toString(l).length();
            case BigInteger big -> digitsAtLeast(big.bitLength());
            case BigDecimal decimal ->
                plainLength(digitsAtLeast(decimal.unscaledValue().bitLength()), decimal.scale(), decimal.signum() < 0);
            case null, default -> 0;
        };
        budget.codePoints += digits;
        return budget.codePoints > Yaml.MAX_CODE_POINTS;
    }

    /** The fewest decimal digits a magnitude of {@code bits} binary digits has: at least 2^(bits-1). */
    private static long digitsAtLeast(int bits) {
        return bits <= 1 ? 1 : (long) ((bits - 1) * DIGITS_PER_BIT) + 1;
    }

    /** The first reason a mapping's entries cannot be validated: a key that is no string, or past the budget. */
    private static @Nullable String entriesProblem(Map<?, ?> map, int depth, Set<Object> path, Budget budget) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                return "a mapping key is not a string";
            }
            if (expandsPastTheCap(key, budget)) {
                return TOO_LARGE;
            }
            String problem = problem(entry.getValue(), depth + 1, path, budget);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    /** The first reason a sequence's items cannot be validated. */
    private static @Nullable String itemsProblem(Collection<?> items, int depth, Set<Object> path, Budget budget) {
        for (Object item : items) {
            String problem = problem(item, depth + 1, path, budget);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    /** Adds a reached string's code points to the budget: whether the strings reached so far are past the cap. */
    private static boolean expandsPastTheCap(String text, Budget budget) {
        budget.codePoints += text.codePointCount(0, text.length());
        return budget.codePoints > Yaml.MAX_CODE_POINTS;
    }

    /** What the loader makes of a YAML scalar a pack may hold; {@code !!binary} makes a {@code byte[]}, which is not. */
    private static boolean plain(@Nullable Object scalar) {
        return scalar == null
                || scalar instanceof String
                || scalar instanceof Integer
                || scalar instanceof Long
                || scalar instanceof BigInteger
                || scalar instanceof BigDecimal
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
