package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Refusal;
import ai.speckitty.charter.packs.domain.rules.CompositionRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ClosureFault: one fault of a closure check, on a kernel reason (#556)")
class ClosureFaultTest {

    private static final RefusalPack ACME =
            new RefusalPack(new PackUlid("01ACME00000000000000000000"), new PackIdentifier("acme"), "Acme");

    @ParameterizedTest
    @EnumSource(RefusalCode.class)
    @DisplayName("a fault takes every closure-check code, its reason carrying the code's one token, and no other code")
    void aFaultTakesExactlyTheClosureCheckCodes(RefusalCode code) {
        if (PackCodes.isClosureCheck(code)) {
            ClosureFault fault = ClosureFault.of(ClosureFault.ORIGIN, code, "what is wrong", List.of(ACME));

            assertThat(fault.code()).isEqualTo(code);
            assertThat(fault.reason().code()).isSameAs(PackCodes.closure(code));
            assertThat(fault.reason().code().value()).isEqualTo(code.token());
            assertThat(fault.detail()).isEqualTo("what is wrong");
            assertThat(fault.reason().location()).isEqualTo(Location.NOWHERE);
        } else {
            assertThatThrownBy(() -> ClosureFault.of(ClosureFault.ORIGIN, code, "what is wrong", List.of(ACME)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(code.token());
        }
    }

    @Test
    @DisplayName("a passing reason is no fault")
    void aPassingReasonIsNoFault() {
        Reason passing = new Reason(
                ClosureFault.RULE,
                ClosureFault.ORIGIN,
                Evaluation.PASS,
                PackCodes.closure(RefusalCode.SELF_OVERRIDE),
                Location.NOWHERE,
                "fine");

        assertThatThrownBy(() -> new ClosureFault(passing, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a fault placed in its closure keeps its code, violations and requiring packs (PR #618 review B1)")
    void aFaultTakesAnotherDetailAndPacksKeepingTheRest() {
        RefusalViolation own = new RefusalViolation("mission-types/a.yaml", "substep_delegation_unresolved", "why");
        ClosureFault alone = ClosureFault.of(
                ClosureFault.ORIGIN, RefusalCode.VALIDATION_FAILED, "why", List.of(), List.of(own), List.of());

        ClosureFault placed = alone.with("In the closure rooted at acme: why", List.of(ACME));

        assertThat(placed.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(placed.detail()).isEqualTo("In the closure rooted at acme: why");
        assertThat(placed.packs()).containsExactly(ACME);
        assertThat(placed.violations()).containsExactly(own);
        assertThat(placed.requiredBy()).isEmpty();
        assertThat(alone.detail())
                .as("the fault it was placed from is unchanged")
                .isEqualTo("why");
    }

    @Test
    @DisplayName("a refusal lifts with its code, detail, packs, violations and the packs that require it")
    void aRefusalLiftsWhole() {
        RefusalViolation own = new RefusalViolation("mission-types/a.yaml", "substep_delegation_unresolved", "why");
        CharterRefusal refused = CharterRefusal.withPacksAndViolations(
                RefusalCode.VALIDATION_FAILED, "In the closure rooted at acme: why", List.of(ACME), List.of(own));

        ClosureFault fault = ClosureFault.lift(refused).orElseThrow();

        assertThat(fault.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(fault.detail()).isEqualTo("In the closure rooted at acme: why");
        assertThat(fault.packs()).containsExactly(ACME);
        assertThat(fault.violations()).containsExactly(own);
        assertThat(fault.requiredBy()).isEmpty();
    }

    @Test
    @DisplayName("a read failure never lifts: pack_unreadable with no violations of its own propagates")
    void aReadFailureNeverLifts() {
        CharterRefusal unreadable = new CharterRefusal(RefusalCode.PACK_UNREADABLE, "cannot read acme");

        assertThat(ClosureFault.isReadFailure(unreadable)).isTrue();
        assertThat(ClosureFault.lift(unreadable)).isEmpty();
    }

    @Test
    @DisplayName("the substep delegation's pack_unreadable names its violation, and is a fault")
    void theDelegationsPackUnreadableIsAFault() {
        CharterRefusal delegation = CharterRefusal.withPacksAndViolations(
                RefusalCode.PACK_UNREADABLE,
                "pack acme: why",
                List.of(ACME),
                List.of(new RefusalViolation("mission-types/a.yaml", "substep_delegation_unresolved", "why")));

        assertThat(ClosureFault.isReadFailure(delegation)).isFalse();
        assertThat(ClosureFault.lift(delegation)).isPresent();
        assertThat(ClosureFault.lift(delegation).orElseThrow().isUnresolvedDelegation())
                .isTrue();
    }

    @Test
    @DisplayName("a refusal of another check never lifts")
    void aRefusalOfAnotherCheckNeverLifts() {
        assertThat(ClosureFault.lift(new CharterRefusal(RefusalCode.PROMPT_SLOT_MISSING, "slot")))
                .isEmpty();
    }

    @Test
    @DisplayName("a resolver's CLOSURE failure is a fault of its CLOSURE code, from the check that found it")
    void aResolverFailureIsAFaultOfItsClosureCode() {
        Refusal refusal = new Refusal(Failure.CYCLE, List.of(ACME), "acme depends on itself");

        ClosureFault fault =
                ClosureFault.of(CompositionRules.ORIGIN, refusal.failure().code(), refusal.detail(), refusal.packs());

        assertThat(fault.code()).isEqualTo(RefusalCode.PACK_DEPENDENCY_CYCLE);
        assertThat(fault.reason().code()).isSameAs(CompositionRules.codeOf(Failure.CYCLE));
        assertThat(fault.origin()).isEqualTo(CompositionRules.ORIGIN);
    }

    @Test
    @DisplayName("a fault records the check that found it, its reason carrying the check's name as its rule (#614)")
    void aFaultRecordsItsOrigin() {
        ClosureFault preset = ClosureFault.of(
                CompositionRules.PRESETS_ORIGIN, RefusalCode.PACK_SIBLING_CONFLICT, "both hold team", List.of(ACME));

        assertThat(preset.origin()).isEqualTo(new Origin("closure.presets"));
        assertThat(preset.reason().rule()).isEqualTo(CompositionRules.PRESETS_RULE);
        assertThat(preset.with("placed", List.of()).origin())
                .as("a placed fault keeps its origin")
                .isEqualTo(CompositionRules.PRESETS_ORIGIN);
        // control: a lifted refusal names no phase, so it is the closure checks' own origin
        assertThat(ClosureFault.lift(new CharterRefusal(RefusalCode.SELF_OVERRIDE, "self"))
                        .orElseThrow()
                        .origin())
                .isEqualTo(ClosureFault.ORIGIN);
    }

    @Test
    @DisplayName("a pack_unreadable with a violation that is no substep delegation's is a read failure")
    void aPackUnreadableWithAnotherViolationIsAReadFailure() {
        CharterRefusal other = CharterRefusal.withPacksAndViolations(
                RefusalCode.PACK_UNREADABLE,
                "acme cannot be read",
                List.of(ACME),
                List.of(new RefusalViolation("pack.yaml", "pack_unreadable", "not YAML")));

        assertThat(ClosureFault.isReadFailure(other)).isTrue();
        assertThat(ClosureFault.lift(other)).isEmpty();
    }
}
