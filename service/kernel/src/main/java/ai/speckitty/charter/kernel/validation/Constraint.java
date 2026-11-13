package ai.speckitty.charter.kernel.validation;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The {@link Rule} of one predicate: it gives exactly one {@link Reason} per subject.
 *
 * <p>A breach's location and detail are functions of the subject, the dynamic descriptions upstream declared but never
 * wired. Neither runs when the predicate holds: a held reason carries {@link Location#NOWHERE} and the description.
 * Without a location function a breach is at {@link Location#NOWHERE}, and without a detail function its detail is the
 * description. Both are left out, not defaulted to a shared function, so that equality stays that of the parts given.
 *
 * <p>Equality is that of a record: every part must be equal, and a predicate or function is compared by reference, so
 * two calls of a factory that builds a lambda, such as {@code IdentifierGrammar.slug(63)}, give unequal rules.
 * Upstream ({@code modular-validators}) compared on the description alone, so two different rules with the same
 * wording were "equal"; that defect is deliberately not reproduced.
 *
 * @param description the rule's description, the same for every subject
 * @param predicate the rule; true means the subject is acceptable
 * @param code the code of a breach, which owns its severity
 * @param location where a breach points, from the subject; {@code null} for {@link Location#NOWHERE}
 * @param detail what a breach says, from the subject; {@code null} for the description
 * @param <T> the type of subject the rule applies to
 */
public record Constraint<T>(
        String description,
        Predicate<? super T> predicate,
        ReasonCode code,
        @Nullable Function<? super T, Location> location,
        @Nullable Function<? super T, String> detail)
        implements Rule<T> {

    /** Rejects a null part other than the location and the detail. */
    public Constraint {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(code, "code");
    }

    /**
     * A value object's invariant: code {@link ReasonCode#INVARIANT}, a {@link Severity#VIOLATION}, at no location,
     * whose detail is its description.
     *
     * @param description the rule's description
     * @param predicate the rule; true means the subject is acceptable
     */
    Constraint(String description, Predicate<? super T> predicate) {
        this(description, predicate, ReasonCode.INVARIANT, null, null);
    }

    /**
     * Applies the rule to one subject.
     *
     * @param subject the subject to test
     * @return the one reason: held, or breached with its location and detail
     */
    public Reason applyTo(T subject) {
        if (predicate.test(subject)) {
            return new Reason(description, Evaluation.PASS, code, Location.NOWHERE, description);
        }
        String said = detail == null ? description : detail.apply(subject);
        Location where = location == null ? Location.NOWHERE : location.apply(subject);
        return new Reason(description, Evaluation.FAIL, code, where, said);
    }

    @Override
    public List<Reason> apply(T subject) {
        return List.of(applyTo(subject));
    }

    /**
     * This rule with another code, and so the code's severity.
     *
     * @param newCode the code of a breach
     * @return the recoded rule; this one is unchanged
     */
    public Constraint<T> coded(ReasonCode newCode) {
        return new Constraint<>(description, predicate, newCode, location, detail);
    }

    /**
     * This rule with a breach located from the subject.
     *
     * @param where where a breach points
     * @return the located rule; this one is unchanged
     */
    public Constraint<T> locatedBy(Function<? super T, Location> where) {
        return new Constraint<>(description, predicate, code, where, detail);
    }

    /**
     * This rule with a breach described from the subject.
     *
     * @param what what a breach says
     * @return the described rule; this one is unchanged
     */
    public Constraint<T> detailedBy(Function<? super T, String> what) {
        return new Constraint<>(description, predicate, code, location, what);
    }
}
