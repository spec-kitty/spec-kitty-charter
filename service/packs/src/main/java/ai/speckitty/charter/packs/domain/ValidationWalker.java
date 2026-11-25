package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Descent;
import ai.speckitty.charter.kernel.domain.GraphWalker;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The one walker that runs pack rules (ADR-0082 decisions 2 to 4, #638): a visitor of the kernel's contract that a
 * driver ({@link PackGraph#walk}) feeds, never a driver itself. Rules plug in as checks, each with the node kind and
 * the predicate that select where it runs. A phase runs all its checks in one traversal; each check keeps a bucket of
 * its own, and the ledger joins the buckets in the phase's check order, so findings come rule first and then in the
 * order the nodes were visited, as the refusal code needs. A whole-closure check ({@link OnClose}) runs once on the
 * selected nodes after the traversal, in its declared place. Once a phase records a {@link Severity#VIOLATION}, the
 * later phases do not run (the phase gate), so a client reads the root cause and not its knock-on faults. Every
 * closure-check code is a violation today, so the gate stops at the first phase with any fault, as the hand-written
 * gates it replaces did.
 *
 * <p>The walker knows no storage format, no scope and no refusal mapping: the checks come from the rule factory
 * ({@code PackRulesets}), the nodes from the driver, and the ledger goes to the edge's mapper. The ledger entries are
 * {@link ClosureFault}s, whose {@link ClosureFault#reason()} is the kernel's {@code Reason}; slice 7 of epic #636
 * settles the ledger on {@code Rationale}.
 *
 * @param <N> the node type the checks judge
 */
public final class ValidationWalker<N> implements GraphWalker<N, N> {

    /**
     * One check of a phase.
     *
     * @param <N> the node type
     */
    public sealed interface Check<N> permits OnNode, OnClose {

        /**
         * The check's name, for a reader of the phase.
         *
         * @return the name
         */
        String name();
    }

    /**
     * A check that runs on each visited node of a kind its predicate selects.
     *
     * @param name the check's name
     * @param kind the node kind it judges
     * @param when the predicate a node meets for the check to run on it
     * @param rule every fault the check finds on a node, in its order
     * @param <N> the node type
     * @param <K> the node kind
     */
    public record OnNode<N, K extends N>(
            String name, Class<K> kind, Predicate<? super K> when, Function<? super K, List<ClosureFault>> rule)
            implements Check<N> {

        /**
         * Checks the fields.
         *
         * @param name the name
         * @param kind the kind
         * @param when the predicate
         * @param rule the rule
         */
        public OnNode {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(rule, "rule");
        }

        private List<ClosureFault> judge(N node) {
            if (!kind.isInstance(node)) {
                return List.of();
            }
            K selected = kind.cast(node);
            return when.test(selected) ? rule.apply(selected) : List.of();
        }
    }

    /**
     * A whole-closure check: it runs once, after the traversal, on every node the traversal handed over, in visit
     * order.
     *
     * @param name the check's name
     * @param rule every fault the check finds, in its order
     * @param <N> the node type
     */
    public record OnClose<N>(String name, Function<List<N>, List<ClosureFault>> rule) implements Check<N> {

        /**
         * Checks the fields.
         *
         * @param name the name
         * @param rule the rule
         */
        public OnClose {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(rule, "rule");
        }
    }

    /**
     * One phase: checks that run together, collect-all, before the gate.
     *
     * @param name the phase's name
     * @param checks its checks, in the order their findings are reported
     * @param <N> the node type
     */
    public record Phase<N>(String name, List<Check<N>> checks) {

        /**
         * Checks and copies the fields.
         *
         * @param name the phase's name
         * @param checks its checks; at least one
         */
        public Phase {
            Objects.requireNonNull(name, "name");
            checks = List.copyOf(checks);
            if (checks.isEmpty()) {
                throw new IllegalArgumentException("the phase " + name + " has no check");
            }
        }
    }

    /**
     * How a walker's nodes reach it: a driver walk with the walker as its visitor.
     *
     * @param <N> the node type
     */
    @FunctionalInterface
    public interface Traversal<N> {

        /**
         * Walks, calling the visitor at each node.
         *
         * @param visitor the visitor
         */
        void walk(GraphWalker<? super N, ? super N> visitor);
    }

    private final List<Phase<N>> phases;
    private List<List<ClosureFault>> buckets = List.of();
    private List<N> visited = List.of();
    private Phase<N> current;

    private ValidationWalker(List<Phase<N>> phases) {
        this.phases = phases;
        this.current = phases.getFirst();
    }

    /**
     * A walker over phases.
     *
     * @param phases the phases, in the order they run; at least one
     * @param <N> the node type
     * @return the walker
     */
    public static <N> ValidationWalker<N> of(List<Phase<N>> phases) {
        List<Phase<N>> copied = List.copyOf(phases);
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("a walker has at least one phase");
        }
        return new ValidationWalker<>(copied);
    }

    /**
     * Runs the phases, one traversal each, until one records a violation.
     *
     * @param traversal how the nodes reach the walker
     * @return the ledger: each phase's findings in check order, up to and including the first phase with a violation
     */
    public Findings run(Traversal<N> traversal) {
        Objects.requireNonNull(traversal, "traversal");
        List<ClosureFault> ledger = new ArrayList<>();
        for (Phase<N> phase : phases) {
            current = phase;
            buckets = new ArrayList<>();
            phase.checks().forEach(check -> buckets.add(new ArrayList<>()));
            visited = new ArrayList<>();
            traversal.walk(this);
            List<N> closed = List.copyOf(visited);
            for (int at = 0; at < phase.checks().size(); at++) {
                if (phase.checks().get(at) instanceof OnClose<N> whole) {
                    buckets.get(at).addAll(whole.rule().apply(closed));
                }
            }
            List<ClosureFault> found = new ArrayList<>();
            buckets.forEach(found::addAll);
            ledger.addAll(found);
            if (found.stream().anyMatch(fault -> fault.reason().isFailing(Severity.VIOLATION))) {
                break;
            }
        }
        return new Findings(ledger);
    }

    @Override
    public Descent onNode(N node) {
        dispatch(node);
        return Descent.CONTINUE;
    }

    @Override
    public Descent onRelationship(N relationship) {
        dispatch(relationship);
        return Descent.CONTINUE;
    }

    private void dispatch(N node) {
        visited.add(node);
        for (int at = 0; at < current.checks().size(); at++) {
            if (current.checks().get(at) instanceof OnNode<N, ?> each) {
                buckets.get(at).addAll(each.judge(node));
            }
        }
    }
}
