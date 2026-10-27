package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;

/**
 * The {@code delegates_to} of a {@link ContractStep}: the artifacts that could concretize the step. It is a value of
 * the step, not a relationship; the charter's selections decide which candidate applies.
 *
 * @param kind the kind of the candidate artifacts
 * @param candidates the candidate ids, in authored order with duplicates kept; never empty
 */
public record StepDelegation(ArtifactKind kind, List<String> candidates) {

    private static final ModularRuleset<List<String>> RULES = ModularRuleset.<List<String>>emptyRules()
            .must(Constraints.of(
                    "A mission step contract delegation needs at least one candidate.", list -> !list.isEmpty()))
            .must(Constraints.of(
                    "A mission step contract delegation's candidates must not be blank.",
                    list -> list.stream().noneMatch(String::isBlank)));

    /**
     * Checks the candidates and copies them.
     *
     * @param kind the candidates' kind
     * @param candidates the candidate ids
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when there is no candidate or one is
     *     blank
     * @throws NullPointerException when the kind, the list or a candidate is null
     */
    public StepDelegation {
        Objects.requireNonNull(kind, "kind");
        candidates = List.copyOf(candidates);
        RULES.guard(candidates);
    }
}
