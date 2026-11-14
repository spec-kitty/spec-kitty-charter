package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.List;
import java.util.Objects;

/**
 * One term in force under an activation (ADR-0076 decisions 2, 3 and 5): a term the winning copy of a context in force
 * holds, whatever its status, with the pack and the level of that copy. A term has no state of its own.
 *
 * @param urn the term's URN, {@code term:<context-id>/<term-id>}
 * @param contextId the id of the context that holds it
 * @param id the term's id within its context
 * @param name the name
 * @param definition the definition
 * @param status the status token, served whatever it is
 * @param examples the examples; empty when the term has none
 * @param aliases the aliases; empty when the term has none
 * @param avoid the forms to avoid; empty when the term has none
 * @param seeAlso the related terms, each a pack-qualified reference as written; empty when the term has none
 * @param packId the pack that holds the winning copy of the context
 * @param level that pack's level
 */
public record VocabularyTerm(
        String urn,
        String contextId,
        String id,
        String name,
        String definition,
        String status,
        List<String> examples,
        List<String> aliases,
        List<String> avoid,
        List<String> seeAlso,
        PackIdentifier packId,
        PackLevel level) {

    /**
     * Checks and copies the fields.
     *
     * @param urn the URN
     * @param contextId the context's id
     * @param id the term's id within its context
     * @param name the name
     * @param definition the definition
     * @param status the status token
     * @param examples the examples
     * @param aliases the aliases
     * @param avoid the forms to avoid
     * @param seeAlso the related terms
     * @param packId the holding pack
     * @param level its level
     */
    public VocabularyTerm {
        Objects.requireNonNull(urn, "urn");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(status, "status");
        examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
        aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
        avoid = List.copyOf(Objects.requireNonNull(avoid, "avoid"));
        seeAlso = List.copyOf(Objects.requireNonNull(seeAlso, "seeAlso"));
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(level, "level");
    }

    /**
     * The term as the winning copy of its context holds it.
     *
     * @param winner the winning copy of the context in force
     * @param term one of its terms
     * @return the term in force
     */
    static VocabularyTerm of(ActivationArtifacts.Held winner, GlossaryView.Term term) {
        return new VocabularyTerm(
                term.urn(),
                winner.id(),
                term.id(),
                term.name(),
                term.definition(),
                term.status(),
                term.examples(),
                term.aliases(),
                term.avoid(),
                term.seeAlso(),
                winner.packId(),
                winner.level());
    }
}
