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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The mutability gate judges a target at the highest level along the composition closure of the pack written
 * (ADR-0063 decision 3, rule C25), over a given closure and policy port: the package-private seam that plants a
 * dependency's lock without a compose. A pack's declaration counts only for what it or a pack it builds on holds (A15,
 * rule C22), and the written pack's own policy never binds the write (B8).
 */
class PackMutabilityPolicyClosureTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private static Member member(
            PackIdentifier configured, String descriptorPackId, List<String> buildsOn, Urn... held) {
        return new Member(configured, descriptorPackId, buildsOn, Set.of(held));
    }

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
        List<Member> closure = List.of(member(q, "Q", List.of(), locked), member(PACK, "P", List.of("Q")));
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
        List<Member> closure = List.of(member(q, "Q", List.of(), target), member(PACK, "P", List.of("Q")));
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
        Urn x = new Urn(NodeKind.DIRECTIVE, "X");
        List<Member> closure = List.of(member(q, "Q", List.of(), x), member(PACK, "P", List.of("Q")));
        WritePolicyGate gate = new PackMutabilityPolicy(any -> MutabilityPolicySource.empty(), pack -> closure);

        for (Operation operation : Operation.values()) {
            assertThatCode(() -> gate.authorize(packWithDescriptor(), operation, x))
                    .doesNotThrowAnyException();
        }
    }

    // ---- A15 (FR-033, DM-01M6RA97220XBR7RR447FE2JXD, rule C22): the lock scope across a three-level closure -------

    /**
     * pack-a builds on E and U; E builds on B. E declares {@code kind_defaults: {directive: locked}}. E's lock reaches
     * what E and B hold (an enterprise pack enforcing over the pack it builds on), never what the unrelated U holds,
     * and never an id no pack of E's closure holds.
     */
    @Test
    void a15APacksLockReachesWhatItAndThePacksItBuildsOnHoldAcrossAThreeLevelClosureAndNothingElse() {
        Urn ofB = new Urn(NodeKind.DIRECTIVE, "B-HELD");
        Urn ofE = new Urn(NodeKind.DIRECTIVE, "E-HELD");
        Urn ofU = new Urn(NodeKind.DIRECTIVE, "U-HELD");
        Urn fresh = new Urn(NodeKind.DIRECTIVE, "NEW");
        PackIdentifier b = new PackIdentifier("b");
        PackIdentifier e = new PackIdentifier("e");
        PackIdentifier u = new PackIdentifier("u");
        Map<PackIdentifier, MutabilityPolicySource> policies = Map.of(
                b, MutabilityPolicySource.empty(),
                e,
                        MutabilityPolicySource.builder()
                                .kindDefault(NodeKind.DIRECTIVE, MutabilityLevel.LOCKED)
                                .build(),
                u, MutabilityPolicySource.empty(),
                PACK, MutabilityPolicySource.empty());
        List<Member> closure = List.of(
                member(b, "B", List.of(), ofB),
                member(e, "E", List.of("B"), ofE),
                member(u, "U", List.of(), ofU),
                member(PACK, "P", List.of("E", "U")));
        WritePolicyGate gate = new PackMutabilityPolicy(policies::get, pack -> closure);
        Pack pack = packWithDescriptor();

        for (Urn enforced : List.of(ofB, ofE)) {
            assertThatThrownBy(() -> gate.authorize(pack, Operation.REGISTER, enforced))
                    .as("E's lock reaches %s, which E or a pack E builds on holds", enforced)
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
                        assertThat(refusal.getMessage()).contains("pack E").contains("locked");
                    });
        }
        assertThatCode(() -> gate.authorize(pack, Operation.REPLACE, ofU))
                .as("U is unrelated to E: E's lock does not reach what U holds")
                .doesNotThrowAnyException();
        assertThatCode(() -> gate.authorize(pack, Operation.REGISTER, fresh))
                .as("no pack of E's closure holds the id, so E declares nothing for it")
                .doesNotThrowAnyException();
    }

    // ---- B8 (FR-033, DM-01M6RAA6Y7NVVGKVKQV05GZYES, rule C25): the own policy never binds the pack itself --------

    @Test
    void b8APacksOwnPolicyLockingItsArtifactDoesNotRefuseThePacksOwnPerArtifactWrite() {
        Urn own = new Urn(NodeKind.DIRECTIVE, "X");
        Map<PackIdentifier, MutabilityPolicySource> policies = Map.of(
                PACK,
                MutabilityPolicySource.builder()
                        .entry(own, MutabilityLevel.LOCKED)
                        .build());
        List<Member> closure = List.of(member(PACK, "P", List.of(), own));
        WritePolicyGate gate = new PackMutabilityPolicy(policies::get, pack -> closure);

        for (Operation operation : Operation.values()) {
            assertThatCode(() -> gate.authorize(packWithDescriptor(), operation, own))
                    .as("the pack's own lock does not bind its own %s", operation)
                    .doesNotThrowAnyException();
        }
    }

    /**
     * B8 under a whole-pack replace (prepares WP14): every artifact of the replaced pack is a {@code replace} into it.
     * The pack's own {@code kind_defaults: locked} binds none of them, while a lock of the pack it builds on still
     * refuses the artifact that pack holds.
     */
    @Test
    void b8AWholePackReplaceIsNotBoundByTheOwnPolicyButStillByThePolicyOfThePackItBuildsOn() {
        Urn ownFirst = new Urn(NodeKind.DIRECTIVE, "OWN-1");
        Urn ownSecond = new Urn(NodeKind.TACTIC, "own-2");
        Urn inherited = new Urn(NodeKind.DIRECTIVE, "BASE");
        PackIdentifier base = new PackIdentifier("base");
        Map<PackIdentifier, MutabilityPolicySource> policies = Map.of(
                base,
                        MutabilityPolicySource.builder()
                                .entry(inherited, MutabilityLevel.EXTENDABLE)
                                .build(),
                PACK,
                        MutabilityPolicySource.builder()
                                .kindDefault(NodeKind.DIRECTIVE, MutabilityLevel.LOCKED)
                                .kindDefault(NodeKind.TACTIC, MutabilityLevel.LOCKED)
                                .build());
        List<Member> closure = List.of(
                member(base, "BASE", List.of(), inherited), member(PACK, "P", List.of("BASE"), ownFirst, ownSecond));
        WritePolicyGate gate = new PackMutabilityPolicy(policies::get, pack -> closure);
        Pack pack = packWithDescriptor();

        for (Urn replaced : List.of(ownFirst, ownSecond)) {
            assertThatCode(() -> gate.authorize(pack, Operation.REPLACE, replaced))
                    .as("the own lock does not bind the replace of %s", replaced)
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> gate.authorize(pack, Operation.REPLACE, inherited))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
                    assertThat(refusal.getMessage()).contains("pack BASE").contains("extendable");
                });
    }

    private static Pack packWithDescriptor() {
        return Pack.builder()
                .id(PACK)
                .descriptor(new PackDescriptor(
                        "P", "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty()))
                .build();
    }
}
