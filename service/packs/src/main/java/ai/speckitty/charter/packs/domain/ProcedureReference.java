package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;

/**
 * A reference carried verbatim on a {@link Procedure}. It is stored, never minted into a relationship: see
 * {@link Procedure#relationships()}.
 *
 * @param type the referenced artifact's kind
 * @param id the referenced artifact's {@code id}
 * @param when the {@code when} text; empty when absent
 * @param reason the {@code reason} text; empty when absent
 */
public record ProcedureReference(ArtifactKind type, String id, Optional<String> when, Optional<String> reason) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A procedure reference needs an id that is not blank.", text -> !text.isBlank()));

    /**
     * Checks that every component is present and the id is not blank.
     *
     * @param type the referenced kind
     * @param id the referenced id
     * @param when the when text, possibly empty
     * @param reason the reason text, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the id is blank
     */
    public ProcedureReference {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(reason, "reason");
        RULES.guard(id);
    }
}
