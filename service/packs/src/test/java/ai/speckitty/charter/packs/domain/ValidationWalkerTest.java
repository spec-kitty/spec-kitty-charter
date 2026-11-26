package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one walker that runs the rules a predicate selects (ADR-0082 decisions 2 to 4, #638): one traversal per phase,
 * one bucket per check joined in check order (rule first, then the subjects in visit order), a whole-closure check at
 * its declared place, a check sees only its node kind, and a phase that records a VIOLATION stops the later phases.
 */
@DisplayName("ValidationWalker: rule-first buckets, kinds, whole-closure checks and a VIOLATION gate (#638)")
class ValidationWalkerTest {

    private sealed interface Node permits Big, Small {
        String name();
    }

    private record Big(String name) implements Node {}

    private record Small(String name) implements Node {}

    private static ClosureFault fault(String detail) {
        return ClosureFault.of(
                PackOrigins.of(new PackIdentifier("p")), RefusalCode.ROLE_PIN_UNKNOWN, detail, List.of());
    }

    private static ValidationWalker.Check<Node> each(String name, String detail) {
        return new ValidationWalker.OnNode<>(
                name, Node.class, node -> true, node -> List.of(fault(node.name() + detail)));
    }

    /** A traversal that hands over the nodes in order, and counts how often it runs. */
    private static final class Nodes implements ValidationWalker.Traversal<Node> {
        private final List<Node> nodes;
        private int runs;

        Nodes(Node... nodes) {
            this.nodes = List.of(nodes);
        }

        @Override
        public void walk(ai.speckitty.charter.kernel.domain.GraphWalker<? super Node, ? super Node> visitor) {
            runs++;
            nodes.forEach(visitor::onNode);
        }
    }

    private static List<String> details(Ledger findings) {
        return findings.faults().stream().map(ClosureFault::detail).toList();
    }

    @Test
    @DisplayName("2 nodes x 2 checks: the first check's findings for both nodes, then the second's (rule first)")
    void ruleFirstThenVisitOrder() {
        ValidationWalker<Node> walker = ValidationWalker.of(
                List.of(new ValidationWalker.Phase<>("one", List.of(each("r1", ":r1"), each("r2", ":r2")))));

        assertThat(details(walker.run(new Nodes(new Big("s1"), new Big("s2")))))
                .containsExactly("s1:r1", "s2:r1", "s1:r2", "s2:r2");
    }

    @Test
    @DisplayName("a check runs only on its node kind, and only where its predicate holds")
    void kindAndPredicateSelect() {
        ValidationWalker.Check<Node> bigOnly = new ValidationWalker.OnNode<>(
                "big", Big.class, big -> !big.name().equals("skipped"), big -> List.of(fault(big.name())));

        assertThat(details(ValidationWalker.of(List.of(new ValidationWalker.Phase<>("one", List.of(bigOnly))))
                        .run(new Nodes(new Big("a"), new Small("b"), new Big("skipped"), new Big("c")))))
                .containsExactly("a", "c");
    }

    @Test
    @DisplayName("a whole-closure check sees the selected nodes in visit order and reports at its declared place")
    void aWholeClosureCheckKeepsItsPlace() {
        ValidationWalker.Check<Node> closing = new ValidationWalker.OnClose<>(
                "closing",
                nodes -> List.of(
                        fault(String.join("+", nodes.stream().map(Node::name).toList()))));

        assertThat(details(ValidationWalker.of(List.of(new ValidationWalker.Phase<>(
                                "one", List.of(each("r1", ":r1"), closing, each("r3", ":r3")))))
                        .run(new Nodes(new Big("a"), new Small("b")))))
                .containsExactly("a:r1", "b:r1", "a+b", "a:r3", "b:r3");
    }

    @Test
    @DisplayName("a phase that records a VIOLATION stops the later phases, which never traverse")
    void aViolationGates() {
        List<String> later = new ArrayList<>();
        ValidationWalker.Check<Node> laterCheck =
                new ValidationWalker.OnNode<>("later", Node.class, node -> true, node -> {
                    later.add(node.name());
                    return List.of();
                });
        Nodes nodes = new Nodes(new Big("a"));

        Ledger found = ValidationWalker.of(List.of(
                        new ValidationWalker.Phase<>("first", List.of(each("r1", ":r1"))),
                        new ValidationWalker.Phase<>("second", List.of(laterCheck))))
                .run(nodes);

        assertThat(details(found)).containsExactly("a:r1");
        assertThat(found.violated()).isTrue();
        assertThat(later).isEmpty();
        assertThat(nodes.runs).as("one traversal per phase that runs").isEqualTo(1);
    }

    @Test
    @DisplayName("every closure-check code is a VIOLATION, so the gate stops at the first phase with any fault")
    void everyClosureCodeIsAViolation() {
        assertThat(PackCodes.closureChecks())
                .isNotEmpty()
                .allSatisfy(
                        code -> assertThat(PackCodes.closure(code).severity()).isEqualTo(Severity.VIOLATION));
    }

    @Test
    @DisplayName("the ledger projects onto the kernel's rationale in its order")
    void theRationaleKeepsTheOrder() {
        Ledger found = ValidationWalker.of(
                        List.of(new ValidationWalker.Phase<>("one", List.of(each("r1", ":r1"), each("r2", ":r2")))))
                .run(new Nodes(new Big("a")));

        assertThat(found.rationale().details()).extracting(Reason::detail).containsExactly("a:r1", "a:r2");
    }

    @Test
    @DisplayName("every phase passing gives an empty ledger that is not violated")
    void allPassing() {
        Ledger found = ValidationWalker.of(List.<ValidationWalker.Phase<Node>>of(new ValidationWalker.Phase<>(
                        "one",
                        List.of(new ValidationWalker.OnNode<>("quiet", Node.class, node -> true, node -> List.of())))))
                .run(new Nodes(new Big("a")));

        assertThat(found.faults()).isEmpty();
        assertThat(found.violated()).isFalse();
    }

    @Test
    @DisplayName("a relationship the driver hands over is judged like a node, in visit order")
    void aRelationshipIsJudged() {
        ValidationWalker<Node> walker =
                ValidationWalker.of(List.of(new ValidationWalker.Phase<>("one", List.of(each("r1", ":r1")))));

        assertThat(details(walker.run(visitor -> {
                    visitor.onNode(new Big("a"));
                    visitor.onRelationship(new Small("edge"));
                    visitor.onNode(new Big("b"));
                })))
                .containsExactly("a:r1", "edge:r1", "b:r1");
    }

    @Test
    @DisplayName("a walker is visited only inside its run, and runs one traversal at a time")
    void aWalkerHoldsOneRun() {
        ValidationWalker<Node> walker =
                ValidationWalker.of(List.of(new ValidationWalker.Phase<>("one", List.of(each("r1", ":r1")))));

        assertThatThrownBy(() -> walker.onNode(new Big("outside"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> walker.run(visitor -> walker.run(new Nodes(new Big("nested")))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(details(walker.run(new Nodes(new Big("after")))))
                .as("a refused run leaves the walker usable")
                .containsExactly("after:r1");
    }

    @Test
    @DisplayName("a walker has at least one phase, and a phase at least one check")
    void emptyIsRefused() {
        assertThatThrownBy(() -> ValidationWalker.<Node>of(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidationWalker.Phase<Node>("empty", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
