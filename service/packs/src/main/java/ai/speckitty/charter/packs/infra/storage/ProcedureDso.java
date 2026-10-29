package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a procedure: a flat mirror of the fields of a sanitized {@code *.procedure.yaml}, not the
 * domain entity. Its record equality is the round-trip fidelity assertion: lists are order- and duplicate-sensitive,
 * and an absent optional differs from a present one.
 *
 * @param id the procedure's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param title the {@code title}
 * @param description the {@code description}
 * @param entryCondition the {@code entry_condition}
 * @param exitCondition the {@code exit_condition}
 * @param steps the {@code steps}, in authored order
 * @param antiPatterns the {@code anti_patterns}, in authored order
 * @param references the {@code references}, in authored order with duplicates kept
 * @param notes the {@code notes}, when stated
 * @param appliesToLanguages the {@code applies_to_languages}, in authored order
 */
record ProcedureDso(
        String id,
        String schemaVersion,
        String title,
        String description,
        String entryCondition,
        String exitCondition,
        List<Step> steps,
        List<AntiPattern> antiPatterns,
        List<Reference> references,
        Optional<String> notes,
        List<String> appliesToLanguages) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param title the title
     * @param description the description
     * @param entryCondition the entry condition
     * @param exitCondition the exit condition
     * @param steps the steps
     * @param antiPatterns the anti-patterns
     * @param references the references
     * @param notes the notes
     * @param appliesToLanguages the languages
     */
    ProcedureDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(entryCondition, "entryCondition");
        Objects.requireNonNull(exitCondition, "exitCondition");
        Objects.requireNonNull(notes, "notes");
        steps = List.copyOf(steps);
        antiPatterns = List.copyOf(antiPatterns);
        references = List.copyOf(references);
        appliesToLanguages = List.copyOf(appliesToLanguages);
    }

    /**
     * One step of a procedure.
     *
     * @param title the step's {@code title}
     * @param description the step's {@code description}, when stated
     * @param role the step's {@code role}, when stated
     * @param onSuccess the step's {@code on_success}, when stated
     * @param onFailure the step's {@code on_failure}, when stated
     */
    record Step(
            String title,
            Optional<String> description,
            Optional<String> role,
            Optional<String> onSuccess,
            Optional<String> onFailure) {
        /**
         * Checks that every component is present.
         *
         * @param title the title
         * @param description the description
         * @param role the role
         * @param onSuccess the on-success text
         * @param onFailure the on-failure text
         */
        Step {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(onSuccess, "onSuccess");
            Objects.requireNonNull(onFailure, "onFailure");
        }
    }

    /**
     * One anti-pattern entry of a procedure.
     *
     * @param title the entry's {@code title}
     * @param content the entry's {@code content}, when stated
     * @param badExample the entry's {@code bad_example}, when stated
     * @param goodExample the entry's {@code good_example}, when stated
     */
    record AntiPattern(
            String title, Optional<String> content, Optional<String> badExample, Optional<String> goodExample) {
        /**
         * Checks that every component is present.
         *
         * @param title the title
         * @param content the content
         * @param badExample the bad example
         * @param goodExample the good example
         */
        AntiPattern {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(badExample, "badExample");
            Objects.requireNonNull(goodExample, "goodExample");
        }
    }

    /**
     * One reference, carried verbatim.
     *
     * @param type the artifact-kind token, as plain text
     * @param id the referenced id
     * @param when the {@code when} text, when stated
     * @param reason the {@code reason} text, when stated
     */
    record Reference(String type, String id, Optional<String> when, Optional<String> reason) {
        /**
         * Checks that every component is present.
         *
         * @param type the type
         * @param id the id
         * @param when the when text
         * @param reason the reason text
         */
        Reference {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
