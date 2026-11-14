package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;
import java.util.Objects;

/**
 * One glossary context of a pack as the edge speaks of it (#106, ADR-0049 decisions 1 to 6): the fields its file
 * declares, in the edge's own words, so that the inbound adapter names no packs type. The packs adapter maps the packs
 * slice's context into it. A list the file does not state is empty.
 *
 * @param id the context's id within its pack
 * @param packId the pack that holds the context, by the identifier the request named it with
 * @param schemaVersion the schema version the file declares
 * @param name the context's name
 * @param description where the context applies
 * @param tags the tags, in authored order
 * @param paths the path globs it applies to, in authored order
 * @param languages the languages it applies to, in authored order
 * @param stacks the stacks it applies to, in authored order
 * @param terms every term, in authored order; never empty
 */
public record GlossaryView(
        String id,
        PackIdentifier packId,
        String schemaVersion,
        String name,
        String description,
        List<String> tags,
        List<String> paths,
        List<String> languages,
        List<String> stacks,
        List<Term> terms) {

    /**
     * Checks and copies the fields.
     *
     * @param id the id
     * @param packId the pack
     * @param schemaVersion the schema version
     * @param name the name
     * @param description the description
     * @param tags the tags
     * @param paths the path globs
     * @param languages the languages
     * @param stacks the stacks
     * @param terms the terms
     */
    public GlossaryView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        paths = List.copyOf(Objects.requireNonNull(paths, "paths"));
        languages = List.copyOf(Objects.requireNonNull(languages, "languages"));
        stacks = List.copyOf(Objects.requireNonNull(stacks, "stacks"));
        terms = List.copyOf(Objects.requireNonNull(terms, "terms"));
    }

    /**
     * One term of the context, as the file declares it, with its address. A list the file does not state is empty.
     *
     * @param id the term's id within its context
     * @param name the canonical surface form
     * @param definition what the term means
     * @param status the status's token: {@code draft}, {@code active} or {@code deprecated}
     * @param examples the example uses, in authored order
     * @param aliases the accepted alternative forms, in authored order
     * @param avoid the forms not to use, in authored order
     * @param seeAlso the related terms, term references, bare or pack-qualified, in authored order
     * @param urn the term's own URN, {@code term:<context-id>/<term-id>}
     */
    public record Term(
            String id,
            String name,
            String definition,
            String status,
            List<String> examples,
            List<String> aliases,
            List<String> avoid,
            List<String> seeAlso,
            String urn) {

        /**
         * Checks and copies the fields.
         *
         * @param id the id
         * @param name the name
         * @param definition the definition
         * @param status the status token
         * @param examples the examples
         * @param aliases the aliases
         * @param avoid the forms to avoid
         * @param seeAlso the related terms
         * @param urn the URN
         */
        public Term {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(status, "status");
            examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
            aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
            avoid = List.copyOf(Objects.requireNonNull(avoid, "avoid"));
            seeAlso = List.copyOf(Objects.requireNonNull(seeAlso, "seeAlso"));
            Objects.requireNonNull(urn, "urn");
        }
    }
}
