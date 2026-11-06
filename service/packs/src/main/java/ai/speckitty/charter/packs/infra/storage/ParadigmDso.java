package ai.speckitty.charter.packs.infra.storage;

import java.util.Objects;

/**
 * The persistence shape of a paradigm: a flat mirror of the fields of a {@code *.paradigm.yaml}, not the domain
 * entity. Its record equality is the round-trip fidelity assertion. A paradigm carries no relationship fields
 * (ADR-0063).
 *
 * @param id the paradigm's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param name the {@code name}
 * @param summary the {@code summary}
 */
record ParadigmDso(String id, String schemaVersion, String name, String summary) {

    /**
     * Checks that the required fields are present.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param name the name
     * @param summary the summary
     */
    ParadigmDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(summary, "summary");
    }
}
