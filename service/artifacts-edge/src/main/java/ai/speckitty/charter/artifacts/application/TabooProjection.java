package ai.speckitty.charter.artifacts.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The taboo list of the vocabulary in force under an activation (ADR-0076 decision 7; ADR-0049 decision 15): every
 * avoided word of the active terms, a pure function of them with no I/O. There is one entry per avoided word per term,
 * so a word two terms avoid appears twice: the list states facts and leaves the gate to decide. A term that avoids
 * nothing gives no entry. The entries are ordered by word, then by term URN.
 */
public final class TabooProjection {

    private static final Comparator<Entry> ORDER =
            Comparator.comparing(Entry::word).thenComparing(Entry::term);

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
