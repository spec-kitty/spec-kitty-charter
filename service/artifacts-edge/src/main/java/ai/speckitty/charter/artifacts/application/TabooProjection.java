package ai.speckitty.charter.artifacts.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The taboo list of the vocabulary in force under an activation (ADR-0076 decision 7; ADR-0049 decision 15): every
 * avoided word of the active terms, a pure function of them with no I/O. There is one entry per avoided word per term,
 * so a word two terms avoid appears twice: the list states facts and leaves the gate to decide. A term that avoids
 * nothing gives no entry. The entries are ordered by word, then by term URN, each in Unicode code point order:
 * case-sensitive, with no locale, so a client in any language sorts them alike. A URN is ASCII, where code point order
 * and Java's string order agree; a word need not be.
 */
public final class TabooProjection {

    private static final Comparator<Entry> ORDER =
            Comparator.comparing(Entry::word, TabooProjection::byCodePoint).thenComparing(Entry::term);

    private TabooProjection() {}

    /**
     * The taboo list of the active terms.
     *
     * @param terms the terms in force
     * @return the entries, by word and then term URN
     */
    public static List<Entry> of(List<ActiveTerm> terms) {
        return terms.stream()
                .flatMap(term -> term.avoid().stream().map(word -> new Entry(word, term.urn(), term.name())))
                .sorted(ORDER)
                .toList();
    }

    /**
     * The Unicode code point order of two strings. Java's {@link String#compareTo} compares UTF-16 code units, which
     * puts a character beyond U+FFFF (a surrogate pair) before one from U+E000 to U+FFFF; this does not.
     *
     * @param left a string
     * @param right another
     * @return negative, zero or positive as {@code left} sorts before, with or after {@code right}
     */
    static int byCodePoint(String left, String right) {
        int at = 0;
        while (at < left.length() && at < right.length()) {
            int leftPoint = left.codePointAt(at);
            int rightPoint = right.codePointAt(at);
            if (leftPoint != rightPoint) {
                return Integer.compare(leftPoint, rightPoint);
            }
            at += Character.charCount(leftPoint);
        }
        return Integer.compare(left.length(), right.length());
    }

    /**
     * One avoided word and the term that avoids it.
     *
     * @param word the word to avoid
     * @param term the URN of the term to use instead
     * @param name that term's name
     */
    public record Entry(String word, String term, String name) {

        /**
         * Checks the fields.
         *
         * @param word the word
         * @param term the term's URN
         * @param name the term's name
         */
        public Entry {
            Objects.requireNonNull(word, "word");
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(name, "name");
        }
    }
}
