package ai.speckitty.charter.kernel.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A check of a subject that returns every {@link Reason} it finds, because one rule can find many faults (ADR-0075
 * decision 1). A {@link Constraint} is the rule of one predicate, and a {@link ModularRuleset} is a rule made of
 * rules.
 *
 * @param <T> the type of subject the rule checks
 */
@FunctionalInterface
public interface Rule<T> {

    /**
     * Applies the rule.
     *
     * @param subject the subject to check
     * @return every reason found, in order; empty when the rule does not apply
     */
    List<Reason> apply(T subject);

    /**
     * Collect-all: applies the rule and keeps every reason.
     *
     * @param subject the subject to check
     * @return the reasons, as a rationale
     */
    default Rationale check(T subject) {
        return new Rationale(apply(subject));
    }

    /**
     * Fail-fast for value objects: throws on the first {@link Severity#VIOLATION}. A warning or a notice passes.
     *
     * @param subject the subject to check
     * @throws ConstraintBreachException carrying the first violation
     */
    default void guard(T subject) {
        for (Reason reason : apply(subject)) {
            if (reason.isFailing(Severity.VIOLATION)) {
                throw new ConstraintBreachException(reason);
            }
        }
    }

    /**
     * Narrows this rule to a part of a larger subject.
     *
     * @param focus picks the part out of the larger subject
     * @param <S> the larger subject
     * @return the rule over the larger subject
     */
    default <S> Rule<S> on(Function<? super S, ? extends T> focus) {
        return subject -> apply(focus.apply(subject));
    }

    /**
     * Applies this rule to each part of a larger subject, keeping every reason in the order of the parts.
     *
     * @param parts picks the parts out of the larger subject
     * @param <S> the larger subject
     * @return the rule over the larger subject
     */
    default <S> Rule<S> forEach(Function<? super S, ? extends Iterable<? extends T>> parts) {
        return subject -> {
            List<Reason> reasons = new ArrayList<>();
            for (T part : parts.apply(subject)) {
                reasons.addAll(apply(part));
            }
            return List.copyOf(reasons);
        };
    }

    /**
     * Applies this rule only to a subject the condition accepts; any other subject gets no reason.
     *
     * @param applicable whether the rule applies to a subject
     * @return the conditional rule
     */
    default Rule<T> when(Predicate<? super T> applicable) {
        return subject -> applicable.test(subject) ? apply(subject) : List.of();
    }
}
