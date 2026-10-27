package ai.speckitty.charter.packs.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Who may invoke a {@link Skill}, kept exactly as written. Core forces {@code model_invocable} off when a skill
 * declares side effects; that is a runtime reading, not what the file says, so it is not applied here (ADR-0036:
 * the round trip compares with the file).
 *
 * @param userInvocable the {@code user_invocable} flag, when written
 * @param modelInvocable the {@code model_invocable} flag, when written
 * @param sideEffects the {@code side_effects}, in authored order
 */
public record SkillInvocation(
        Optional<Boolean> userInvocable, Optional<Boolean> modelInvocable, List<String> sideEffects) {

    /**
     * Checks the fields and copies the list.
     *
     * @param userInvocable the user flag, possibly empty
     * @param modelInvocable the model flag, possibly empty
     * @param sideEffects the side effects
     */
    public SkillInvocation {
        Objects.requireNonNull(userInvocable, "userInvocable");
        Objects.requireNonNull(modelInvocable, "modelInvocable");
        sideEffects = List.copyOf(sideEffects);
    }
}
