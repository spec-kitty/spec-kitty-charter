package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a glossary pack: a flat mirror of the fields of a {@code *.glossary-pack.yaml}, not the
 * domain entity. Its record equality is the round-trip fidelity assertion: lists are order- and duplicate-sensitive,
 * and an absent optional differs from a present one. An explicit {@code null}, an absent key and an empty list read
 * alike and are written as an omitted key, which ADR-0036 counts as the same meaning.
 *
 * @param id the glossary pack's {@code id}
 * @param provenance the {@code provenance}
 * @param description the {@code description}, when stated
 * @param terms the {@code terms}, in authored order
 */
record GlossaryPackDso(String id, String provenance, Optional<String> description, List<Term> terms) {

    /**
     * Checks that the required fields are present and copies the list.
     *
     * @param id the id
     * @param provenance the provenance
     * @param description the description, possibly empty
     * @param terms the terms
     */
    GlossaryPackDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(description, "description");
        terms = List.copyOf(terms);
    }

    /**
     * One term.
     *
     * @param surface the term's {@code surface}
     * @param definition the {@code definition}
     * @param confidence the {@code confidence}, a number as written
     * @param status the {@code status}
     * @param seeAlso the {@code see_also}, in authored order
     * @param introducedInMission the {@code introduced_in_mission}, when stated
     * @param synonymsToAvoid the {@code synonyms_to_avoid}, in authored order
     * @param aliases the {@code aliases}, in authored order
     * @param bannedSynonyms the {@code banned_synonyms}, in authored order
     */
    record Term(
            String surface,
            String definition,
            double confidence,
            String status,
            List<String> seeAlso,
            Optional<String> introducedInMission,
            List<String> synonymsToAvoid,
            List<String> aliases,
            List<String> bannedSynonyms) {
        /**
         * Checks that the required fields are present and copies the lists.
         *
         * @param surface the surface
         * @param definition the definition
         * @param confidence the confidence
         * @param status the status
         * @param seeAlso the related surfaces
         * @param introducedInMission the introducing mission, possibly empty
         * @param synonymsToAvoid the synonyms to avoid
         * @param aliases the aliases
         * @param bannedSynonyms the banned synonyms
         */
        Term {
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(introducedInMission, "introducedInMission");
            seeAlso = List.copyOf(seeAlso);
            synonymsToAvoid = List.copyOf(synonymsToAvoid);
            aliases = List.copyOf(aliases);
            bannedSynonyms = List.copyOf(bannedSynonyms);
        }
    }
}
