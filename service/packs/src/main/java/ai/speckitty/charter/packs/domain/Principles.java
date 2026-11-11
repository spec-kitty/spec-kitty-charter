package ai.speckitty.charter.packs.domain;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The one validator of a list of {@link Principle}s (ADR-0067), used by {@link Paradigm}, {@link Styleguide} and the
 * pack reader, so a pack file and a registration body are refused by the same rule.
 */
public final class Principles {

    /** The constraint text of a list that breaks the element shape. */
    public static final String SHAPE = "A principle needs a name of 1 to " + Principle.NAME_MAX
            + " characters and a description of 1 to " + Principle.DESCRIPTION_MAX + " characters, neither blank.";

    /** The constraint text of a list that repeats a name. */
    public static final String UNIQUE =
            "Principle names must be unique within their list, ignoring case and surrounding whitespace.";

    private Principles() {}

    /**
     * Whether every principle has a name and a description within the caps, neither blank.
     *
     * @param principles the list
     * @return {@code true} when every item is well formed
     */
    public static boolean wellFormed(List<Principle> principles) {
        return principles.stream()
                .allMatch(p -> !Principle.isBlank(p.name())
                        && codePoints(p.name()) <= Principle.NAME_MAX
                        && !Principle.isBlank(p.description())
                        && codePoints(p.description()) <= Principle.DESCRIPTION_MAX);
    }

    /** Characters as the schema's {@code maxLength} counts them: code points, not UTF-16 units. */
    private static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    /**
     * The index of the first name that repeats an earlier one, ignoring case and surrounding whitespace.
     *
     * @param names the names, in list order
     * @return the index of the first repeat, or empty when every name is unique
     */
    public static OptionalInt firstRepeatedName(List<String> names) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < names.size(); i++) {
            if (!seen.add(Principle.key(names.get(i)))) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Whether every name in the list is unique.
     *
     * @param principles the list
     * @return {@code true} when no name repeats
     */
    public static boolean uniqueNames(List<Principle> principles) {
        return firstRepeatedName(principles.stream().map(Principle::name).toList())
                .isEmpty();
    }
}
