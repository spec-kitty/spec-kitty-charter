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
 * <p>A composed rule keeps the fail-fast of the rule it wraps: its {@code guard} delegates to that rule's
 * {@code guard}, part by part, so a ruleset wrapped by {@code on}, {@code forEach} or {@code when} still stops at its
 * first violation.
 *
 * @param <T> the type of subject the rule checks
 */
@FunctionalInterface
public interface Rule<T> {

    /**
     * Applies the rule. A rule is total over its subjects: it never throws on a subject of its type. A rule that only
     * makes sense once another has held says so with {@link #when}, because {@link #check} applies every rule.
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
        Rule<T> rule = this;
        return new Rule<>() {
            @Override
            public List<Reason> apply(S subject) {
                return rule.apply(focus.apply(subject));
            }

            @Override
            public void guard(S subject) {
                rule.guard(focus.apply(subject));
            }
        };
    }

    /**
     * Applies this rule to each part of a larger subject, keeping every reason in the order of the parts.
     *
     * @param parts picks the parts out of the larger subject
     * @param <S> the larger subject
     * @return the rule over the larger subject
     */
    default <S> Rule<S> forEach(Function<? super S, ? extends Iterable<? extends T>> parts) {
        Rule<T> rule = this;
        return new Rule<>() {
            @Override
            public List<Reason> apply(S subject) {
                List<Reason> reasons = new ArrayList<>();
                for (T part : parts.apply(subject)) {
                    reasons.addAll(rule.apply(part));
                }
                return List.copyOf(reasons);
            }

            @Override
            public void guard(S subject) {
                for (T part : parts.apply(subject)) {
                    rule.guard(part);
                }
            }
        };
    }

    /**
     * Applies this rule only to a subject the condition accepts; any other subject gets no reason.
     *
     * @param applicable whether the rule applies to a subject
     * @return the conditional rule
     */
    default Rule<T> when(Predicate<? super T> applicable) {
        Rule<T> rule = this;
        return new Rule<>() {
            @Override
            public List<Reason> apply(T subject) {
                return applicable.test(subject) ? rule.apply(subject) : List.of();
            }

            @Override
            public void guard(T subject) {
                if (applicable.test(subject)) {
                    rule.guard(subject);
                }
            }
        };
    }

    /**
     * This rule with every breach it finds placed by the subject, for a shared rule reused on a part of a file: {@code
     * IdentifierGrammar.slug(63).on(Descriptor::id).locatedAt(d -> Location.in("pack.yaml").at("/id"))}.
     *
     * @param where where a breach on this subject points
     * @return the located rule
     */
    default Rule<T> locatedAt(Function<? super T, Location> where) {
        Rule<T> rule = this;
        return new Rule<>() {
            @Override
            public List<Reason> apply(T subject) {
                return rule.apply(subject).stream()
                        .map(reason -> reason.isFailing() ? reason.at(where.apply(subject)) : reason)
                        .toList();
            }

            @Override
            public void guard(T subject) {
                try {
                    rule.guard(subject);
                } catch (ConstraintBreachException breach) {
                    var located = new ConstraintBreachException(breach.reason().at(where.apply(subject)));
                    located.initCause(breach);
                    throw located;
                }
            }
        };
    }
}
