package ai.speckitty.charter.packs.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The one walker that runs pack rules (ADR-0082 decisions 2 to 4, #638): rules plug in, each with the predicate that
 * selects the subjects it runs on, and never drive. The walker runs its phases in order; a phase runs every rule it
 * holds on every subject in one traversal, subjects in the order given and rules in the order of the phase; and every
 * fault lands in one ledger, in that order. Once a phase finds a fault, the later phases do not run (the phase gate),
 * so a client reads the root cause and not its knock-on faults.
 *
 * <p>The walker knows no storage format, no scope and no refusal mapping: the rules come from
 * {@link ai.speckitty.charter.packs.domain.rules.PackRulesets}, the subjects from the caller, and the ledger goes to the
 * edge's mapper. The ledger entries are {@link ClosureFault}s, whose {@link ClosureFault#reason()} is the kernel's
 * {@code Reason}; slice 7 of epic #636 settles the ledger on {@code Rationale}.
 *
 * @param <S> the subject the rules judge
 */
public final class ValidationWalker<S> {

    /**
     * One rule of a phase and the predicate that selects where it runs.
     *
     * @param name the rule's name, for a reader of the phase
     * @param when the predicate a subject meets for the rule to run on it
     * @param rule the rule: every fault it finds on a subject, in its order
     * @param <S> the subject the rule judges
     */
    public record Selected<S>(String name, Predicate<? super S> when, Function<? super S, List<ClosureFault>> rule) {

        /**
         * Checks the fields.
         *
         * @param name the rule's name
         * @param when the predicate
         * @param rule the rule
         */
        public Selected {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(rule, "rule");
        }

        /**
         * A rule that runs on every subject.
         *
         * @param name the rule's name
         * @param rule the rule
         * @param <S> the subject
         * @return the rule, selected everywhere
         */
        public static <S> Selected<S> always(String name, Function<? super S, List<ClosureFault>> rule) {
            return new Selected<>(name, subject -> true, rule);
        }
    }

    /**
     * One phase: rules that run together, collect-all, before the gate.
     *
     * @param name the phase's name
     * @param rules its rules, in the order they run on each subject
     * @param <S> the subject the rules judge
     */
    public record Phase<S>(String name, List<Selected<S>> rules) {

        /**
         * Checks and copies the fields.
         *
         * @param name the phase's name
         * @param rules its rules; at least one
         */
        public Phase {
            Objects.requireNonNull(name, "name");
            rules = List.copyOf(rules);
            if (rules.isEmpty()) {
                throw new IllegalArgumentException("the phase " + name + " has no rule");
            }
        }
    }

    private final List<Phase<S>> phases;

    private ValidationWalker(List<Phase<S>> phases) {
        this.phases = phases;
    }

    /**
     * A walker over phases.
     *
     * @param phases the phases, in the order they run; at least one
     * @param <S> the subject the rules judge
     * @return the walker
     */
    public static <S> ValidationWalker<S> of(List<Phase<S>> phases) {
        List<Phase<S>> copied = List.copyOf(phases);
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("a walker has at least one phase");
        }
        return new ValidationWalker<>(copied);
    }

    /**
     * Walks one subject.
     *
     * @param subject the subject
     * @return the ledger: every fault of the first phase that finds any; empty when every phase passes
     */
    public List<ClosureFault> walk(S subject) {
        return walk(List.of(Objects.requireNonNull(subject, "subject")));
    }

    /**
     * Walks the subjects, each phase in one traversal over them, in the order given.
     *
     * @param subjects the subjects
     * @return the ledger: the faults of each phase in subject and then rule order, up to and including the first phase
     *     that finds any; empty when every phase passes
     */
    public List<ClosureFault> walk(Collection<? extends S> subjects) {
        Objects.requireNonNull(subjects, "subjects");
        List<ClosureFault> ledger = new ArrayList<>();
        for (Phase<S> phase : phases) {
            for (S subject : subjects) {
                for (Selected<S> selected : phase.rules()) {
                    if (selected.when().test(subject)) {
                        ledger.addAll(selected.rule().apply(subject));
                    }
                }
            }
            if (!ledger.isEmpty()) {
                break;
            }
        }
        return List.copyOf(ledger);
    }
}
