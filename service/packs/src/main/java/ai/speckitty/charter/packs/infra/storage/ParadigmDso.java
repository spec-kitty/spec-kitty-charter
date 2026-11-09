package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;

/**
 * The persistence shape of a paradigm: a flat mirror of the fields of a {@code *.paradigm.yaml}, not the domain
 * entity. Its record equality is the round-trip fidelity assertion; lists are order- and duplicate-sensitive.
 *
 * @param id the paradigm's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param name the {@code name}
 * @param summary the {@code summary}
 * @param directiveRefs the {@code directive_refs}, in authored order
 * @param references the typed {@code references}, in authored order with duplicates kept
 */
record ParadigmDso(
        String id,
        String schemaVersion,
        String name,
        String summary,
        List<String> directiveRefs,
        List<Reference> references) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param name the name
     * @param summary the summary
     * @param directiveRefs the directive refs
     * @param references the references
     */
    ParadigmDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(summary, "summary");
        directiveRefs = List.copyOf(directiveRefs);
        references = List.copyOf(references);
    }

    /**
     * One typed reference, carried verbatim.
     *
     * @param type the artifact-kind token, as plain text
     * @param id the referenced id
     */
    record Reference(String type, String id) {
        /**
         * Checks that both fields are present.
         *
         * @param type the type
         * @param id the id
         */
        Reference {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
        }
    }
}
