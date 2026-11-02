package ai.speckitty.charter.packs.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The closed list of built-in guards (ADR-0054 decision 6, open question 1 as ruled 2026-11-02): the checks a client's
 * own code implements, named {@code builtin:<id>} in a guard's {@code implemented_by}. Each is one or more of core's
 * lane predicates ({@code status/wp_state.py}) or its one registered gate. The list is versioned with the contract, and
 * {@code guard.schema.json} spells the same ids; a client refuses an id it does not know.
 */
public enum BuiltinGuard {
    /** The transition's context is complete: an actor, a workspace context, and a reason where the edge needs one. */
    TRANSITION_CONTEXT_COMPLETE,
    /** The work package's dependencies are ready. */
    DEPENDENCY_READY,
    /** The subtasks are complete and implementation evidence is present. */
    IMPLEMENTATION_READY,
    /** A review approved the work: reviewer approval, the review result and its consistency. */
    REVIEW_APPROVED,
    /** A review reference is present, as a move back from review needs. */
    REVIEW_REF,
    /** Core's pre-review gate: the configured tests pass on the changed files. */
    SPEC_KITTY_PRE_REVIEW;

    /**
     * The id after {@code builtin:}.
     *
     * @return the kebab-case id, for example {@code review-ref}
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Looks a built-in up by its id.
     *
     * @param id an id, for example {@code dependency-ready}
     * @return the built-in; empty when the id is not on the list
     */
    public static Optional<BuiltinGuard> fromId(String id) {
        return Arrays.stream(values()).filter(b -> b.id().equals(id)).findFirst();
    }
}
