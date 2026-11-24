package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Urn;
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

    private static final Origin ACME_ORIGIN = PackOrigins.of(ACME.packId());

    @ParameterizedTest
    @EnumSource(RefusalCode.class)
    @DisplayName("a fault takes every closure-check code, its reason carrying the code's one token, and no other code")
    void aFaultTakesExactlyTheClosureCheckCodes(RefusalCode code) {
        if (PackCodes.isClosureCheck(code)) {
            ClosureFault fault = ClosureFault.of(ACME_ORIGIN, code, "what is wrong", List.of(ACME));

            assertThat(fault.code()).isEqualTo(code);
            assertThat(fault.reason().code()).isSameAs(PackCodes.closure(code));
            assertThat(fault.reason().code().value()).isEqualTo(code.token());
            assertThat(fault.detail()).isEqualTo("what is wrong");
            assertThat(fault.reason().location()).isEqualTo(Location.NOWHERE);
        } else {
            assertThatThrownBy(() -> ClosureFault.of(ACME_ORIGIN, code, "what is wrong", List.of(ACME)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(code.token());
        }
    }

    @Test
    @DisplayName("a passing reason is no fault")
    void aPassingReasonIsNoFault() {
        Reason passing = new Reason(
                ClosureFault.RULE,
                ACME_ORIGIN,
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
        ClosureFault alone =
                ClosureFault.of(Origin.NONE, RefusalCode.VALIDATION_FAILED, "why", List.of(), List.of(own), List.of());

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
    @DisplayName(
            "as registration refuses it: the delegation's pack_unreadable is validation_failed, another fault itself")
    void asRegistrationRefusesItTheDelegationIsTheAuthorsFault() {
        RefusalViolation own = new RefusalViolation("mission-types/a.yaml", "substep_delegation_unresolved", "why");
        ClosureFault delegation = ClosureFault.of(
                ACME_ORIGIN, RefusalCode.PACK_UNREADABLE, "pack acme: why", List.of(ACME), List.of(own), List.of());
        ClosureFault reference =
                ClosureFault.of(ACME_ORIGIN, RefusalCode.PACK_REFERENCE_PACK_MISSING, "gone", List.of(ACME));

        ClosureFault refused = delegation.asRegistrationRefuses();

        assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refused.detail()).isEqualTo("pack acme: why");
        assertThat(refused.packs()).containsExactly(ACME);
        assertThat(refused.violations()).containsExactly(own);
        assertThat(refused.origin()).isEqualTo(ACME_ORIGIN);
        assertThat(refused.reason().rule()).isEqualTo(delegation.reason().rule());
        assertThat(reference.asRegistrationRefuses()).isSameAs(reference);
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

        ClosureFault fault = ClosureFault.of(
                PackOrigins.firstOf(refusal.packs()), refusal.failure().code(), refusal.detail(), refusal.packs());

        assertThat(fault.code()).isEqualTo(RefusalCode.PACK_DEPENDENCY_CYCLE);
        assertThat(fault.reason().code()).isSameAs(CompositionRules.codeOf(Failure.CYCLE));
        assertThat(fault.origin()).isEqualTo(ACME_ORIGIN);
    }

    @Test
    @DisplayName(
            "a fault carries the element it is about, apart from its rule: the check's name, or the phase's own (#614, R-o)")
    void aFaultCarriesItsOriginApartFromItsRule() {
        Origin profile = PackOrigins.of(ACME.packId(), new Urn(NodeKind.AGENT_PROFILE, "generic-agent"));

        ClosureFault fault = ClosureFault.of(profile, RefusalCode.FALLBACK_PROFILE_UNKNOWN, "unknown", List.of());

        assertThat(fault.origin()).isEqualTo(profile);
        assertThat(fault.reason().origin()).isEqualTo(profile);
        assertThat(fault.reason().rule())
                .as("the rule is the check's name, never the origin's id")
                .isEqualTo(ClosureFault.RULE)
                .isNotEqualTo(profile.id());
        assertThat(fault.with("placed", List.of(ACME)).origin())
                .as("a placed fault keeps its origin")
                .isEqualTo(profile);

        ClosureFault phase = ClosureFault.of(
                profile,
                CompositionRules.PRESETS_RULE,
                RefusalCode.PACK_SIBLING_CONFLICT,
                "both hold team",
                List.of(ACME));
        assertThat(phase.origin()).isEqualTo(profile);
        assertThat(phase.reason().rule()).isEqualTo(CompositionRules.PRESETS_RULE);
        assertThat(phase.with("placed", List.of()).reason().rule())
                .as("a placed fault keeps the rule of the phase that named it")
                .isEqualTo(CompositionRules.PRESETS_RULE);
    }

    @Test
    @DisplayName("a lifted refusal is about the first pack it names, or no element when it names none (R-o)")
    void aLiftedRefusalIsAboutItsFirstPack() {
        CharterRefusal naming =
                CharterRefusal.withPacksAndViolations(RefusalCode.SELF_OVERRIDE, "self", List.of(ACME), List.of());

        assertThat(ClosureFault.lift(naming).orElseThrow().origin()).isEqualTo(ACME_ORIGIN);
        assertThat(ClosureFault.lift(naming).orElseThrow().reason().rule()).isEqualTo(ClosureFault.RULE);
        assertThat(ClosureFault.lift(new CharterRefusal(RefusalCode.SELF_OVERRIDE, "self"))
                        .orElseThrow()
                        .origin())
                .isEqualTo(Origin.NONE);
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
