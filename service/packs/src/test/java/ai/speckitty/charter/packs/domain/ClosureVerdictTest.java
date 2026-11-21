package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClosureVerdict: every fault of a write's closure checks, in order (#556)")
class ClosureVerdictTest {

    private static final ClosureFault SELF = ClosureFault.of(RefusalCode.SELF_OVERRIDE, "self", List.of());
    private static final ClosureFault CYCLE = ClosureFault.of(RefusalCode.ENHANCES_CYCLE, "cycle", List.of());

    @Test
    @DisplayName("no fault passes, and its rationale passes too")
    void noFaultPasses() {
        assertThat(ClosureVerdict.PASSING.isPassing()).isTrue();
        assertThat(ClosureVerdict.PASSING.rationale().isPassing()).isTrue();
    }

    @Test
    @DisplayName("the rationale is one failing reason per fault, in order")
    void theRationaleIsOneReasonPerFault() {
        ClosureVerdict verdict = new ClosureVerdict(List.of(SELF, CYCLE));

        assertThat(verdict.isPassing()).isFalse();
        assertThat(verdict.rationale().violations()).extracting(Reason::detail).containsExactly("self", "cycle");
    }

    @Test
    @DisplayName("and keeps this verdict's faults first")
    void andKeepsTheOrder() {
        ClosureVerdict first = new ClosureVerdict(List.of(SELF));
        ClosureVerdict second = new ClosureVerdict(List.of(CYCLE));

        assertThat(first.and(second).faults()).containsExactly(SELF, CYCLE);
        assertThat(first.and(ClosureVerdict.PASSING)).isSameAs(first);
        assertThat(ClosureVerdict.PASSING.and(second)).isSameAs(second);
    }
}
