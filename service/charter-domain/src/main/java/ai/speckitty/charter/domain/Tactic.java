package ai.speckitty.charter.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A tactic artifact, as the facts the file states. It carries no storage coordinate, so two identical tactics read
 * from different files are equal.
 *
 * @param id the tactic's {@code id}, its identity for replace-by-id
 * @param schemaVersion the tactic's {@code schema_version}, as text
 * @param name the tactic's {@code name}
 * @param steps the steps, in authored order; never empty
 * @param purpose the {@code purpose}; empty when absent or null
 * @param notes the {@code notes}; empty when absent or null
 * @param failureModes the failure modes, in authored order; empty when absent
 * @param appliesToLanguages the languages the tactic applies to, in authored order; empty when absent
 * @param references the references, in authored order with duplicates kept; empty when absent
 */
public record Tactic(
        String id,
        String schemaVersion,
        String name,
        List<TacticStep> steps,
        Optional<String> purpose,
        Optional<String> notes,
        List<String> failureModes,
        List<String> appliesToLanguages,
        List<TacticReference> references) {
    /**
     * Checks that required fields are present, that there is at least one step, and copies the lists.
     *
     * @param id the tactic's id
     * @param schemaVersion the schema version text
     * @param name the tactic's name
     * @param steps the steps
     * @param purpose the purpose, possibly empty
     * @param notes the notes, possibly empty
     * @param failureModes the failure modes
     * @param appliesToLanguages the applicable languages
     * @param references the references
     * @throws IllegalArgumentException when {@code steps} is empty
     */
    public Tactic {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(notes, "notes");
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("a tactic needs at least one step");
        }
        failureModes = List.copyOf(failureModes);
        appliesToLanguages = List.copyOf(appliesToLanguages);
        references = List.copyOf(references);
    }
}
