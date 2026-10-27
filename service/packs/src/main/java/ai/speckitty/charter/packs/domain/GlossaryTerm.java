package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One canonical term of a {@link GlossaryPack}. The lists keep their authored order and duplicates; an empty list
 * stands for an absent one, as ADR-0036 decision 2 reads them alike.
 *
 * @param surface the term as written; unique within its glossary pack
 * @param definition what the term means
 * @param confidence how settled the definition is, a number as written
 * @param status the term's status, as text (core has no fixed vocabulary)
 * @param seeAlso the surfaces of related terms
 * @param introducedInMission the mission that introduced the term, when stated
 * @param synonymsToAvoid words not to use for the term
 * @param aliases other accepted spellings of the term
 * @param bannedSynonyms words refused for the term
 */
public record GlossaryTerm(
        String surface,
        String definition,
        double confidence,
        String status,
        List<String> seeAlso,
        Optional<String> introducedInMission,
        List<String> synonymsToAvoid,
        List<String> aliases,
        List<String> bannedSynonyms) {

    private static final ModularRuleset<String> SURFACE_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A glossary term needs a surface that is not blank.", text -> !text.isBlank()));

    private static final ModularRuleset<String> STATUS_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A glossary term needs a status that is not blank.", text -> !text.isBlank()));

    private static final ModularRuleset<Double> CONFIDENCE_RULES = ModularRuleset.<Double>emptyRules()
            .must(Constraints.of("A glossary term's confidence must be a finite number.", Double::isFinite));

    /**
     * Checks the fields and copies the lists.
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
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the surface or status is blank or
     *     the confidence is not finite
     */
    public GlossaryTerm {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(introducedInMission, "introducedInMission");
        seeAlso = List.copyOf(seeAlso);
        synonymsToAvoid = List.copyOf(synonymsToAvoid);
        aliases = List.copyOf(aliases);
        bannedSynonyms = List.copyOf(bannedSynonyms);
        SURFACE_RULES.guard(surface);
        STATUS_RULES.guard(status);
        CONFIDENCE_RULES.guard(confidence);
    }
}
