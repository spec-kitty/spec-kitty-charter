package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackMutabilityPolicy.Member;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import ai.speckitty.charter.packs.domain.WritePolicyGate.Operation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The mutability gate judges a target at the highest level along the composition closure of the pack written
 * (ADR-0063 decision 3), over a given closure and policy port: the package-private seam that plants a dependency's
 * lock without a compose.
 */
class PackMutabilityPolicyClosureTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    @Test
    void aLockDeclaredByADependencyRefusesARegistrationIntoTheDependentPackAndNamesTheLockingPack() {
        Urn locked = new Urn(NodeKind.DIRECTIVE, "X");
        PackIdentifier q = new PackIdentifier("q");
        Map<PackIdentifier, MutabilityPolicySource> policies = Map.of(
                q,
                        MutabilityPolicySource.builder()
                                .entry(locked, MutabilityLevel.LOCKED)
                                .build(),
                PACK, MutabilityPolicySource.empty());
        List<Member> closure = List.of(new Member(q, "Q"), new Member(PACK, "P"));
        WritePolicyGate gate = new PackMutabilityPolicy(policies::get, pack -> closure);
        Pack pack = packWithDescriptor();

        assertThatThrownBy(() -> gate.authorize(pack, Operation.REGISTER, locked))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
                    assertThat(refusal.getMessage())
                            .contains("Q")
                            .contains("locked")
                            .contains(locked.toString());
                });

        // Same closure, a target Q does not name: the registration is allowed.
        assertThatCode(() -> gate.authorize(pack, Operation.REGISTER, new Urn(NodeKind.DIRECTIVE, "Y")))
                .doesNotThrowAnyException();
    }

    @Test
    void theDependentPacksOwnOpenEntryDoesNotLowerTheDependencysLock() {
        Urn target = new Urn(NodeKind.DIRECTIVE, "X");
        PackIdentifier q = new PackIdentifier("q");
        Map<PackIdentifier, MutabilityPolicySource> policies = Map.of(
                q,
                        MutabilityPolicySource.builder()
                                .entry(target, MutabilityLevel.EXTENDABLE)
                                .build(),
                PACK,
                        MutabilityPolicySource.builder()
                                .entry(target, MutabilityLevel.OPEN)
                                .build());
        List<Member> closure = List.of(new Member(q, "Q"), new Member(PACK, "P"));
        WritePolicyGate gate = new PackMutabilityPolicy(policies::get, pack -> closure);

        assertThatThrownBy(() -> gate.authorize(packWithDescriptor(), Operation.REPLACE, target))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
                    assertThat(refusal.getMessage()).contains("extendable").contains("Q");
                });
        assertThatCode(() -> gate.authorize(packWithDescriptor(), Operation.RELATIONSHIP, target))
                .doesNotThrowAnyException();
    }

    @Test
    void withNoPolicyAnywhereAlongTheClosureEveryOperationIsPermitted() {
        PackIdentifier q = new PackIdentifier("q");
        List<Member> closure = List.of(new Member(q, "Q"), new Member(PACK, "P"));
        WritePolicyGate gate = new PackMutabilityPolicy(any -> MutabilityPolicySource.empty(), pack -> closure);

        for (Operation operation : Operation.values()) {
            assertThatCode(() -> gate.authorize(packWithDescriptor(), operation, new Urn(NodeKind.DIRECTIVE, "X")))
                    .doesNotThrowAnyException();
        }
    }

    private static Pack packWithDescriptor() {
        return Pack.builder()
                .id(PACK)
                .descriptor(new PackDescriptor(
                        PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty()))
                .build();
    }
}
