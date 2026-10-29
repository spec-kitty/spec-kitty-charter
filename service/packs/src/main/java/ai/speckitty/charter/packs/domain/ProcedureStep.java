package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One step of a {@link Procedure}.
 *
 * <p>A step names the role that acts, never an agent profile (ADR-0032). The role is an open token: the domain
 * checks only its lexical form ({@code ^[a-z][a-z0-9-]*$}, as the schema does) and cannot tell a role from a profile
 * id, because it does not see the profile catalog. Profile ids are refused at the catalog boundary, when roles are
 * resolved against profiles; that resolution is out of scope here. An absent role means any role may act.
 *
 * @param title the step's {@code title}
 * @param description the step's {@code description}; empty when absent
 * @param role the step's {@code role}, an open role token such as {@code operator}; empty when absent
 * @param onSuccess the step's {@code on_success}; empty when absent
 * @param onFailure the step's {@code on_failure}; empty when absent
 */
public record ProcedureStep(
        String title,
        Optional<String> description,
        Optional<String> role,
        Optional<String> onSuccess,
        Optional<String> onFailure) {

    private static final Pattern ROLE = Pattern.compile("^[a-z][a-z0-9-]*$");

    private static final ModularRuleset<String> TITLE_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A procedure step needs a title that is not blank.", text -> !text.isBlank()));

    private static final ModularRuleset<Optional<String>> ROLE_RULES = ModularRuleset.<Optional<String>>emptyRules()
            .must(Constraints.of(
                    "A procedure step role must match ^[a-z][a-z0-9-]*$.",
                    token -> token.map(text -> ROLE.matcher(text).matches()).orElse(true)));

    /**
     * Checks that required fields are present and the role, when present, is a role token.
     *
     * @param title the step's title
     * @param description the description, possibly empty
     * @param role the role, possibly empty
     * @param onSuccess the on-success text, possibly empty
     * @param onFailure the on-failure text, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the title is blank or the role
     *     is not a role token
     */
    public ProcedureStep {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        TITLE_RULES.guard(title);
        ROLE_RULES.guard(role);
    }
}
