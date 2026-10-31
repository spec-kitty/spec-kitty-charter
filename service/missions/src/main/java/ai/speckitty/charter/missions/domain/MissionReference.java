package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A reference to guidance carried verbatim on a {@link MissionType} or a {@link MissionStep}. It is stored, never minted
 * into a relationship (#189): see {@link MissionType#relationships()}. A type's references are its recipe
 * ingredients; a step's are its action-scoped guidance.
 *
 * @param type the referenced artifact's kind, one of {@link #GUIDANCE_KINDS}
 * @param id the referenced artifact's {@code id}
 * @param when the {@code when} text; empty when absent
 * @param reason the {@code reason} text; empty when absent
 */
public record MissionReference(ArtifactKind type, String id, Optional<String> when, Optional<String> reason) {

    /** The kinds a mission may reference: guidance, never an agent profile (a mission type names roles, ADR-0032). */
    public static final Set<ArtifactKind> GUIDANCE_KINDS = Set.copyOf(EnumSet.of(
            ArtifactKind.DIRECTIVE,
            ArtifactKind.TACTIC,
            ArtifactKind.STYLEGUIDE,
            ArtifactKind.TOOLGUIDE,
            ArtifactKind.PARADIGM,
            ArtifactKind.PROCEDURE));

    private static final ModularRuleset<ArtifactKind> KIND_RULES = ModularRuleset.<ArtifactKind>emptyRules()
            .must(Constraints.of("A mission references guidance only.", GUIDANCE_KINDS::contains));
    private static final ModularRuleset<String> TEXT_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A mission reference's id and texts are not blank.", text -> !text.isBlank()));

    /**
     * Checks every component.
     *
     * @param type the referenced kind
     * @param id the referenced id
     * @param when the when text, possibly empty
     * @param reason the reason text, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the kind is not guidance or a text
     *     is blank
     */
    public MissionReference {
        KIND_RULES.guard(Objects.requireNonNull(type, "type"));
        TEXT_RULES.guard(Objects.requireNonNull(id, "id"));
        Objects.requireNonNull(when, "when").ifPresent(TEXT_RULES::guard);
        Objects.requireNonNull(reason, "reason").ifPresent(TEXT_RULES::guard);
    }
}
