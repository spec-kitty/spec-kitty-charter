package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;

/**
 * The persistence shape of a paradigm: a flat mirror of the fields of a {@code *.paradigm.yaml}, not the domain
 * entity. Its record equality is the round-trip fidelity assertion. A paradigm carries no relationship fields
 * (ADR-0064).
 *
 * @param id the paradigm's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param name the {@code name}
 * @param summary the {@code summary}
 */
record ParadigmDso(String id, String schemaVersion, String name, String summary, List<Principle> principles) {

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
        principles = List.copyOf(principles);
    }

    /**
     * A paradigm that states no principles.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param name the name
     * @param summary the summary
     */
    ParadigmDso(String id, String schemaVersion, String name, String summary) {
        this(id, schemaVersion, name, summary, List.of());
    }

    /**
     * One {@code principles} item as written (ADR-0067).
     *
     * @param name the principle's name
     * @param description its description
     */
    record Principle(String name, String description) {
        Principle {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
        }
    }
}
