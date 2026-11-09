package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a directive: a flat mirror of the fields of a {@code *.directive.yaml}, not the domain
 * entity. Its record equality is the round-trip fidelity assertion.
 *
 * @param id the directive's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param title the {@code title}
 * @param intent the {@code intent}
 * @param enforcement the {@code enforcement} token as stored, for example {@code lenient-adherence}
 * @param scope the {@code scope}, when stated
 * @param integrityRules the {@code integrity_rules}, in authored order
 * @param validationCriteria the {@code validation_criteria}, in authored order
 * @param explicitAllowances the {@code explicit_allowances}, in authored order
 */
record DirectiveDso(
        String id,
        String schemaVersion,
        String title,
        String intent,
        String enforcement,
        Optional<String> scope,
        List<String> integrityRules,
        List<String> validationCriteria,
        List<String> explicitAllowances) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param title the title
     * @param intent the intent
     * @param enforcement the enforcement token
     * @param scope the scope
     * @param integrityRules the integrity rules
     * @param validationCriteria the validation criteria
     * @param explicitAllowances the explicit allowances
     */
    DirectiveDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(enforcement, "enforcement");
        Objects.requireNonNull(scope, "scope");
        integrityRules = List.copyOf(integrityRules);
        validationCriteria = List.copyOf(validationCriteria);
        explicitAllowances = List.copyOf(explicitAllowances);
    }
}
