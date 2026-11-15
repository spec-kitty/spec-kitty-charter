package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One substep of a {@link MissionStep}: an entry of the step's flat, ordered {@code substeps} list, one level deep
 * (operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9, FR-004). It holds what a step of the retired step contract held: what
 * to do, the command that does it with its inputs, the artifacts it delegates to, and free guidance.
 *
 * @param id the substep's id, unique within its step: a letter-first kebab slug of at most 64 characters
 * @param description what the substep does; not blank
 * @param command the command that runs it, when it has one; not blank
 * @param inputs the command's declared inputs, in authored order
 * @param delegatesTo the artifacts that could concretize it, when it delegates
 * @param guidance free guidance for the agent, when there is any; not blank
 */
public record Substep(
        String id,
        String description,
        Optional<String> command,
        List<SubstepInput> inputs,
        Optional<SubstepDelegation> delegatesTo,
        Optional<String> guidance) {

    private static final ModularRuleset<String> ID_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A substep id is a letter-first kebab slug of at most 64 characters.",
                    IdentifierGrammar::isLetterFirstSlug));
    private static final ModularRuleset<String> TEXT_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.notBlank().described("A substep's description, command and guidance are not blank."));

    /**
     * Checks every component and copies the inputs.
     *
     * @param id the id
     * @param description the description
     * @param command the command, possibly empty
     * @param inputs the inputs
     * @param delegatesTo the delegation, possibly empty
     * @param guidance the guidance, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the id breaks its grammar or a text
     *     is blank
     */
    public Substep {
        ID_RULES.guard(Objects.requireNonNull(id, "id"));
        TEXT_RULES.guard(Objects.requireNonNull(description, "description"));
        Objects.requireNonNull(command, "command").ifPresent(TEXT_RULES::guard);
        inputs = List.copyOf(inputs);
        Objects.requireNonNull(delegatesTo, "delegatesTo");
        Objects.requireNonNull(guidance, "guidance").ifPresent(TEXT_RULES::guard);
    }
}
