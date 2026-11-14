package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;

/**
 * The persistence shape of a glossary context: a flat mirror of the fields of a {@code glossaries/<id>.glossary.yaml},
 * in snake case, not the domain entity. Its record equality is the round-trip fidelity assertion: lists are order- and
 * duplicate-sensitive. An explicit {@code null}, an absent key and an empty list read alike and are written as an
 * omitted key, which ADR-0036 counts as the same meaning.
 *
 * @param schemaVersion the {@code schema_version}
 * @param id the context's {@code id}
 * @param name the {@code name}
 * @param description the {@code description}
 * @param tags the {@code tags}, in authored order
 * @param appliesTo the {@code applies_to}; every list empty when the file states none
 * @param terms the {@code terms}, in authored order
 */
record GlossaryDso(
        String schemaVersion,
        String id,
        String name,
        String description,
        List<String> tags,
        AppliesTo appliesTo,
        List<Term> terms) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param schemaVersion the schema version
     * @param id the id
     * @param name the name
     * @param description the description
     * @param tags the tags
     * @param appliesTo the applies-to lists
     * @param terms the terms
     */
    GlossaryDso {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(appliesTo, "appliesTo");
        tags = List.copyOf(tags);
        terms = List.copyOf(terms);
    }

    /**
     * The {@code applies_to} mapping.
     *
     * @param paths its {@code paths}
     * @param languages its {@code languages}
     * @param stacks its {@code stacks}
     */
    record AppliesTo(List<String> paths, List<String> languages, List<String> stacks) {
        /**
         * Copies the lists.
         *
         * @param paths the paths
         * @param languages the languages
         * @param stacks the stacks
         */
        AppliesTo {
            paths = List.copyOf(paths);
            languages = List.copyOf(languages);
            stacks = List.copyOf(stacks);
        }
    }

    /**
     * One term.
     *
     * @param id the term's {@code id}
     * @param name the {@code name}
     * @param definition the {@code definition}
     * @param examples the {@code examples}, in authored order
     * @param aliases the {@code aliases}, in authored order
     * @param avoid the {@code avoid}, in authored order
     * @param status the {@code status}
     * @param seeAlso the {@code see_also} term references, bare or pack-qualified, in authored order
     */
    record Term(
            String id,
            String name,
            String definition,
            List<String> examples,
            List<String> aliases,
            List<String> avoid,
            String status,
            List<String> seeAlso) {
        /**
         * Checks that the required fields are present and copies the lists.
         *
         * @param id the id
         * @param name the name
         * @param definition the definition
         * @param examples the examples
         * @param aliases the aliases
         * @param avoid the forms to avoid
         * @param status the status
         * @param seeAlso the related terms' references
         */
        Term {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(status, "status");
            examples = List.copyOf(examples);
            aliases = List.copyOf(aliases);
            avoid = List.copyOf(avoid);
            seeAlso = List.copyOf(seeAlso);
        }
    }
}
