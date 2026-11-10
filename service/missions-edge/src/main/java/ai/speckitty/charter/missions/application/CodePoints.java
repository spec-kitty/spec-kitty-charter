package ai.speckitty.charter.missions.application;

import java.util.Arrays;

/** Code-point order, which {@link String#compareTo} is not beyond the Basic Multilingual Plane. */
final class CodePoints {

    private CodePoints() {}

    /**
     * Compares two strings by their code points.
     *
     * @param left one string
     * @param right the other
     * @return negative, zero or positive as {@code left} sorts before, with or after {@code right}
     */
    static int compare(String left, String right) {
        return Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());
    }
}
