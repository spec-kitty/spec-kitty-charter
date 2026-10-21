package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a tactic: a flat mirror of the fields of a {@code *.tactic.yaml}, not the domain entity.
 * Its record equality is the round-trip fidelity assertion: lists are order- and duplicate-sensitive, and an absent
 * optional differs from a present one.
 *
 * @param id the tactic's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param name the {@code name}
 * @param steps the steps, in authored order
 * @param purpose the {@code purpose}, when stated
 * @param notes the {@code notes}, when stated
 * @param failureModes the {@code failure_modes}, in authored order
 * @param appliesToLanguages the {@code applies_to_languages}, in authored order
 * @param references the tactic-level {@code references}, in authored order with duplicates kept
 */
record TacticDso(
        String id,
        String schemaVersion,
        String name,
        List<Step> steps,
        Optional<String> purpose,
        Optional<String> notes,
        List<String> failureModes,
        List<String> appliesToLanguages,
        List<Reference> references) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param name the name
     * @param steps the steps
     * @param purpose the purpose
     * @param notes the notes
     * @param failureModes the failure modes
     * @param appliesToLanguages the languages
     * @param references the references
     */
    TacticDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(notes, "notes");
        steps = List.copyOf(steps);
        failureModes = List.copyOf(failureModes);
        appliesToLanguages = List.copyOf(appliesToLanguages);
        references = List.copyOf(references);
    }

    /**
     * One step of a tactic.
     *
     * @param title the step's title
     * @param description the step's description, when stated
     * @param examples the examples, in authored order
     * @param references the step's references, in authored order with duplicates kept
     */
    record Step(String title, Optional<String> description, List<String> examples, List<Reference> references) {
        /**
         * Checks the fields and copies the lists.
         *
         * @param title the title
         * @param description the description
         * @param examples the examples
         * @param references the references
         */
        Step {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            examples = List.copyOf(examples);
            references = List.copyOf(references);
        }
    }

    /**
     * One reference, carried verbatim.
     *
     * @param name the reference's name
     * @param type the artifact-kind token, as plain text
     * @param id the referenced id
     * @param when the when text
     */
    record Reference(String name, String type, String id, String when) {
        /**
         * Checks that every field is present.
         *
         * @param name the name
         * @param type the type
         * @param id the id
         * @param when the when text
         */
        Reference {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(when, "when");
        }
    }
}
