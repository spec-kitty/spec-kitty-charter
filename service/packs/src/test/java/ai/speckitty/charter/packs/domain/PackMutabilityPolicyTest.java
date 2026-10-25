package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.DirectiveRegistrationService;
import ai.speckitty.charter.packs.application.PackMutabilityPolicy;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.domain.WritePolicyGate.Operation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The ADR-0012 mutability write gate (FR-011). Every refusal is paired with a same-fixture positive control
 * (SC, DIRECTIVE_041): an over-level write refused with {@code governance_policy_violation} is matched by a
 * within-level write on the very same fixture, proving the fixture is sound and the gate non-vacuous.
 *
 * <p>Mapping under test (ADR-0012 §Decision 1 table, applied to the API operations):
 *
 * <pre>
 *                 REGISTER   REPLACE    RELATIONSHIP
 *   open          allow      allow      allow
 *   extendable    allow      REFUSE     allow
 *   locked        REFUSE     REFUSE     REFUSE
 * </pre>
 */
class PackMutabilityPolicyTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    // ---- T035: level -> operation matrix (ADR-0012) --------------------------------------------------------------

    @Test
    void openPermitsEveryOperation() {
        assertThat(MutabilityLevel.OPEN.permits(Operation.REGISTER)).isTrue();
        assertThat(MutabilityLevel.OPEN.permits(Operation.REPLACE)).isTrue();
        assertThat(MutabilityLevel.OPEN.permits(Operation.RELATIONSHIP)).isTrue();
    }

    @Test
    void extendablePermitsAdditionsAndRelationshipsButRefusesReplace() {
        assertThat(MutabilityLevel.EXTENDABLE.permits(Operation.REGISTER)).isTrue();
        assertThat(MutabilityLevel.EXTENDABLE.permits(Operation.RELATIONSHIP)).isTrue();
        assertThat(MutabilityLevel.EXTENDABLE.permits(Operation.REPLACE)).isFalse();
    }

    @Test
    void lockedRefusesEveryMutatingOperation() {
        assertThat(MutabilityLevel.LOCKED.permits(Operation.REGISTER)).isFalse();
        assertThat(MutabilityLevel.LOCKED.permits(Operation.REPLACE)).isFalse();
        assertThat(MutabilityLevel.LOCKED.permits(Operation.RELATIONSHIP)).isFalse();
    }

    @Test
    void levelTokensRoundTrip() {
        for (MutabilityLevel level : MutabilityLevel.values()) {
            assertThat(MutabilityLevel.fromToken(level.token())).contains(level);
        }
        assertThat(MutabilityLevel.OPEN.token()).isEqualTo("open");
        assertThat(MutabilityLevel.EXTENDABLE.token()).isEqualTo("extendable");
        assertThat(MutabilityLevel.LOCKED.token()).isEqualTo("locked");
        assertThat(MutabilityLevel.fromToken("enforced")).isEmpty();
    }

    // ---- T036: policy source resolution order (URN entry > kind default > open) -----------------------------------

    @Test
    void sourceResolvesUrnEntryThenKindDefaultThenOpen() {
        Urn locked = new Urn(NodeKind.DIRECTIVE, "LOCKED_ONE");
        Urn otherDirective = new Urn(NodeKind.DIRECTIVE, "OTHER");
        Urn tactic = new Urn(NodeKind.TACTIC, "a-tactic");
        MutabilityPolicySource source = MutabilityPolicySource.builder()
                .kindDefault(NodeKind.DIRECTIVE, MutabilityLevel.EXTENDABLE)
                .entry(locked, MutabilityLevel.LOCKED)
                .build();
        Pack pack = packWithDescriptor();

        assertThat(source.levelOf(pack, locked)).as("URN entry wins").isEqualTo(MutabilityLevel.LOCKED);
        assertThat(source.levelOf(pack, otherDirective))
                .as("kind default when no URN entry")
                .isEqualTo(MutabilityLevel.EXTENDABLE);
        assertThat(source.levelOf(pack, tactic))
                .as("open when neither names it")
                .isEqualTo(MutabilityLevel.OPEN);
    }

    @Test
    void anEmptySourceResolvesEverythingToOpen() {
        MutabilityPolicySource empty = MutabilityPolicySource.empty();
        assertThat(empty.levelOf(packWithDescriptor(), new Urn(NodeKind.DIRECTIVE, "ANY")))
                .isEqualTo(MutabilityLevel.OPEN);
    }

    // ---- T037/T038: the gate refuses an over-level write, allows a within-level one on the SAME fixture -----------

    @Test
    void extendableTargetRefusesReplaceButAllowsRelationshipOnTheSameFixture() {
        Urn target = new Urn(NodeKind.DIRECTIVE, "EXT");
        MutabilityPolicySource source = MutabilityPolicySource.builder()
                .entry(target, MutabilityLevel.EXTENDABLE)
                .build();
        WritePolicyGate gate = new PackMutabilityPolicy(source);
        Pack pack = packWithDescriptor();

        assertThatThrownBy(() -> gate.authorize(pack, Operation.REPLACE, target))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
                    assertThat(refusal.getMessage()).contains("extendable").contains(target.toString());
                });

        // Same fixture, within level: a relationship edge is enhance-level and is allowed.
        assertThatCode(() -> gate.authorize(pack, Operation.RELATIONSHIP, target))
                .doesNotThrowAnyException();
    }

    @Test
    void lockedTargetRefusesEveryWriteWhileAnOpenTargetIsAllowedUnderTheSameSource() {
        Urn locked = new Urn(NodeKind.DIRECTIVE, "LOCKED");
        Urn open = new Urn(NodeKind.DIRECTIVE, "OPEN");
        MutabilityPolicySource source = MutabilityPolicySource.builder()
                .entry(locked, MutabilityLevel.LOCKED)
                .build();
        WritePolicyGate gate = new PackMutabilityPolicy(source);
        Pack pack = packWithDescriptor();

        for (Operation operation : Operation.values()) {
            assertThatThrownBy(() -> gate.authorize(pack, operation, locked))
                    .as("locked refuses " + operation)
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION));
        }

        // Same source, an unlisted target defaults to open: every operation is allowed.
        for (Operation operation : Operation.values()) {
            assertThatCode(() -> gate.authorize(pack, operation, open))
                    .as("open target allows " + operation)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void aTargetWithNoDeclaredLevelDefaultsToOpenAndEveryWriteIsAuthorized() {
        WritePolicyGate gate = new PackMutabilityPolicy(MutabilityPolicySource.empty());
        Pack pack = packWithDescriptor();
        Urn target = new Urn(NodeKind.DIRECTIVE, "UNDECLARED");
        for (Operation operation : Operation.values()) {
            assertThatCode(() -> gate.authorize(pack, operation, target)).doesNotThrowAnyException();
        }
    }

    @Test
    void theDefaultBeanIsPermissiveSoExistingOpenPacksKeepWriting() {
        // The no-arg (Spring) constructor wires an empty source: ADR-0012's migration reality is that no pack ships
        // a policy file yet, so every artifact is open and today's writes keep working (tighten never loosen).
        WritePolicyGate gate = new PackMutabilityPolicy();
        Pack pack = packWithDescriptor();
        assertThatCode(() -> gate.authorize(pack, Operation.REPLACE, new Urn(NodeKind.DIRECTIVE, "ANY")))
                .doesNotThrowAnyException();
    }

    // ---- T038: a thin integration through WP05's real registration service over the real gate ---------------------

    @Test
    void aLockedTargetIsRefusedThroughTheRealRegistrationServiceWhileAnOpenOneSucceeds() {
        Urn locked = new Urn(NodeKind.DIRECTIVE, "LOCKED");
        MutabilityPolicySource source = MutabilityPolicySource.builder()
                .entry(locked, MutabilityLevel.LOCKED)
                .build();
        FakeStore store = new FakeStore();
        DirectiveRegistrationService service =
                new DirectiveRegistrationService(store, store, new PackMutabilityPolicy(source));
        int before = store.commits;

        assertThatThrownBy(() -> service.registerDirective(PACK, directive("LOCKED"), List.of(), false, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION));
        assertThat(store.commits).as("a refused write commits nothing").isEqualTo(before);

        // Same fixture, an open (unlisted) target: the write lands.
        assertThatCode(() -> service.registerDirective(PACK, directive("OPEN"), List.of(), false, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(before + 1);
    }

    // ---- fixtures and fakes --------------------------------------------------------------------------------------

    private static Pack packWithDescriptor() {
        return Pack.builder().id(PACK).descriptor(descriptor()).build();
    }

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Directive directive(String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    /** A minimal store realizing both the load port and the write-path port, counting commits. */
    private static final class FakeStore implements PackRepository, PackWriteStore {
        private final Map<PackIdentifier, Pack> packs = new HashMap<>(
                Map.of(PACK, Pack.builder().id(PACK).descriptor(descriptor()).build()));
        private final Map<PackIdentifier, List<Relationship>> edges = new HashMap<>();
        private int commits;

        @Override
        public Pack load(PackIdentifier pack) {
            Pack found = packs.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + pack.value());
            }
            return found;
        }

        @Override
        public PackRevision currentRevision(PackIdentifier pack) {
            return PackRevision.compute(List.of(pack.value() + ":" + commits));
        }

        @Override
        public List<Relationship> registeredRelationships(PackIdentifier pack) {
            return List.copyOf(edges.getOrDefault(pack, List.of()));
        }

        @Override
        public PackRevision commit(PackIdentifier pack, Pack mutated, List<Relationship> relationships) {
            packs.put(pack, mutated);
            if (relationships != null) { // null: the registration left the shard untouched
                edges.put(pack, new ArrayList<>(relationships));
            }
            commits++;
            return currentRevision(pack);
        }
    }
}
