package ai.speckitty.charter.profiles.domain.selection;

import java.util.List;

/** What selection answers for a role (ADR-0069 decision 4, ADR-0078): the picked profiles, or no match. */
public sealed interface SelectionAnswer permits SelectionAnswer.Matched, SelectionAnswer.NoMatch {

    /**
     * One or more profiles fill the role.
     *
     * @param picks the picked profiles: one, or every tied one sorted by closure position then id
     * @param tie whether more than one profile is answered
     */
    record Matched(List<Pick> picks, boolean tie) implements SelectionAnswer {

        /**
         * Copies the picks.
         *
         * @param picks the picked profiles
         * @param tie whether more than one is answered
         * @throws IllegalArgumentException when there is no pick, or {@code tie} disagrees with their number
         */
        public Matched {
            picks = List.copyOf(picks);
            if (picks.isEmpty() || tie != (picks.size() > 1)) {
                throw new IllegalArgumentException("A match holds one pick, or a tie of more than one.");
            }
        }
    }

    /** No profile fills the role and no fallback is answered. */
    record NoMatch() implements SelectionAnswer {}
}
