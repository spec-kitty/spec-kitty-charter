package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one walker that runs the rules a predicate selects (ADR-0082 decisions 2, 3 and 4, #638): each phase runs its
 * rules on every subject in one traversal, in the order given; a rule runs only where its predicate holds; the faults
 * accumulate in one ledger in phase, subject and rule order; and once a phase finds a fault, the later phases do not
 * run (the phase gate).
 */
@DisplayName("ValidationWalker: rules a predicate selects, one ledger, a phase gate (#638)")
class ValidationWalkerTest {

    private static ClosureFault fault(String detail) {
        return ClosureFault.of(PackOrigins.of(new PackIdentifier("p")), RefusalCode.ROLE_PIN_UNKNOWN, detail, List.of());
    }

    private static ValidationWalker.Selected<String> rule(String name, String... details) {
        return ValidationWalker.Selected.always(
                name,
                subject -> List.of(details).stream()
                        .map(detail -> fault(subject + ":" + detail))
                        .toList());
    }

    @Test
    @DisplayName("one phase: every rule on every subject, the ledger in subject then rule order")
    void onePhaseCollectsAll() {
        ValidationWalker<String> walker =
                ValidationWalker.of(List.of(new ValidationWalker.Phase<>("one", List.of(rule("r1", "x"), rule("r2", "y")))));

        assertThat(walker.walk(List.of("a", "b")))
                .extracting(ClosureFault::detail)
                .containsExactly("a:x", "a:y", "b:x", "b:y");
    }

    @Test
    @DisplayName("a rule runs only on the subjects its predicate selects")
    void thePredicateSelects() {
        ValidationWalker.Selected<String> onlyB = new ValidationWalker.Selected<>(
                "only-b", "b"::equals, subject -> List.of(fault(subject + ":selected")));

        assertThat(ValidationWalker.of(List.of(new ValidationWalker.Phase<>("one", List.of(onlyB))))
                        .walk(List.of("a", "b", "c")))
                .extracting(ClosureFault::detail)
                .containsExactly("b:selected");
    }

    @Test
    @DisplayName("a rule a predicate leaves out is never called")
    void anUnselectedRuleIsNotCalled() {
        List<String> called = new ArrayList<>();
        ValidationWalker.Selected<String> never = new ValidationWalker.Selected<>("never", subject -> false, subject -> {
            called.add(subject);
            return List.of();
        });

        assertThat(ValidationWalker.of(List.of(new ValidationWalker.Phase<>("one", List.of(never))))
                        .walk(List.of("a")))
                .isEmpty();
        assertThat(called).isEmpty();
    }

    @Test
    @DisplayName("the phase gate: a phase that finds a fault stops the later phases")
    void thePhaseGateStopsLaterPhases() {
        List<String> called = new ArrayList<>();
        ValidationWalker.Selected<String> later = ValidationWalker.Selected.always("later", subject -> {
            called.add(subject);
            return List.of(fault("later"));
        });
        ValidationWalker<String> walker = ValidationWalker.of(List.of(
                new ValidationWalker.Phase<>("first", List.of(rule("r1", "x"))),
                new ValidationWalker.Phase<>("second", List.of(later))));

        assertThat(walker.walk(List.of("a", "b"))).extracting(ClosureFault::detail).containsExactly("a:x", "b:x");
        assertThat(called).isEmpty();
    }

    @Test
    @DisplayName("a passing phase lets the next one run")
    void aPassingPhaseContinues() {
        ValidationWalker<String> walker = ValidationWalker.of(List.of(
                new ValidationWalker.Phase<>("first", List.of(rule("quiet"))),
                new ValidationWalker.Phase<>("second", List.of(rule("r2", "y")))));

        assertThat(walker.walk(List.of("a"))).extracting(ClosureFault::detail).containsExactly("a:y");
    }

    @Test
    @DisplayName("one subject walks as a list of one, and nothing to walk passes")
    void oneSubjectAndNone() {
        ValidationWalker<String> walker =
                ValidationWalker.of(List.of(new ValidationWalker.Phase<>("one", List.of(rule("r1", "x")))));

        assertThat(walker.walk("a")).extracting(ClosureFault::detail).containsExactly("a:x");
        assertThat(walker.walk(List.of())).isEmpty();
    }

    @Test
    @DisplayName("a walker has at least one phase, and a phase at least one rule")
    void emptyIsRefused() {
        assertThatThrownBy(() -> ValidationWalker.of(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidationWalker.Phase<String>("empty", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
