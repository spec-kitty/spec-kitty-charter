package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The directive registration use case over in-memory ports (FR-007/008/009/010/012). Every refusal is paired with a
 * same-fixture positive control (SC-003, DIRECTIVE_041): a refused write changes nothing, and the matching accepted
 * write proves the fixture itself is sound.
 */
class DirectiveRegistrationServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private final FakeStore store = new FakeStore();
    private final WritePolicyGate policy = new PermissiveWritePolicyGate();
    private final DirectiveRegistrationService service = new DirectiveRegistrationService(store, store, policy);

    // ---- FR-007 / SC-001: register with relationships, read back value-equal ------------------------------------

    @Test
    void registersADirectiveWithItsFieldsAndReadsItBackValueEqual() {
        Directive submitted = directive("GUARD", "Guard the gate", "proc-1");
        Relationship bodyEdge =
                Relationship.of(submitted.urn(), new Urn(NodeKind.PROCEDURE, "proc-1"), RelationType.REQUIRES);

        DirectiveRegistrationService.Registered result =
                service.registerDirective(PACK, submitted, List.of(bodyEdge), false, null);

        assertThat(result.created()).isTrue();
        assertThat(result.target()).isEqualTo(submitted.urn());
        Directive persisted = directiveIn(PACK, "GUARD");
        assertThat(persisted.title()).isEqualTo(submitted.title());
        assertThat(persisted.intent()).isEqualTo(submitted.intent());
        assertThat(persisted.procedures()).isEqualTo(submitted.procedures());
        assertThat(persisted.relationships()).isEqualTo(submitted.relationships());
        assertThat(store.relationships(PACK)).contains(bodyEdge);
    }

    // ---- FR-008 control: duplicate-id POST refused, distinct id accepted on the same fixture --------------------

    @Test
    void aDuplicateIdPostIsAConflictWhileADistinctIdPostSucceedsOnTheSameFixture() {
        service.registerDirective(PACK, directive("GUARD", "first", null), List.of(), false, null);
        int commitsAfterFirst = store.commits;

        assertThatThrownBy(() ->
                        service.registerDirective(PACK, directive("GUARD", "again", null), List.of(), false, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).as("the refused POST committed nothing").isEqualTo(commitsAfterFirst);

        // Same fixture, distinct id: accepted.
        assertThatCode(() -> service.registerDirective(PACK, directive("CHECK", "ok", null), List.of(), false, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(commitsAfterFirst + 1);
    }

    @Test
    void aPutReplacesIdempotentlyRatherThanConflicting() {
        service.registerDirective(PACK, directive("GUARD", "first", null), List.of(), true, null);
        DirectiveRegistrationService.Registered replaced =
                service.registerDirective(PACK, directive("GUARD", "second", null), List.of(), true, null);
        assertThat(replaced.created()).isFalse();
        assertThat(directiveIn(PACK, "GUARD").title()).isEqualTo("second");
    }

    // ---- FR-010 / SC-003 control: dual-write violation refused (422), pack unchanged, well-formed succeeds ------

    @Test
    void aBodyRelationshipNotAuthoredFromTheDirectiveIsRefusedAndTheWellFormedOneSucceeds() {
        Directive directive = directive("GUARD", "Guard", null);
        Relationship foreign = Relationship.of(
                new Urn(NodeKind.DIRECTIVE, "OTHER"), new Urn(NodeKind.PROCEDURE, "p"), RelationType.REQUIRES);
        int before = store.commits;

        assertThatThrownBy(() -> service.registerDirective(PACK, directive, List.of(foreign), true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
        assertThat(store.commits)
                .as("a refused registration commits nothing (NFR-002)")
                .isEqualTo(before);

        Relationship own = Relationship.of(directive.urn(), new Urn(NodeKind.PROCEDURE, "p"), RelationType.REQUIRES);
        assertThatCode(() -> service.registerDirective(PACK, directive, List.of(own), true, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(before + 1);
    }

    // ---- FR-012: optimistic concurrency on an artifact write (the relationship door:
    // RelationshipRegistrationServiceTest)

    @Test
    void aDirectiveWriteWithAStaleIfMatchIsRefused() {
        assertThatThrownBy(() -> service.registerDirective(
                        PACK, directive("GUARD", "x", null), List.of(), true, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));
    }

    @Test
    void anUnknownPackIsANotFound() {
        assertThatThrownBy(() -> service.registerDirective(
                        new PackIdentifier("ghost"), directive("GUARD", "x", null), List.of(), true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    // ---- fixtures and fakes --------------------------------------------------------------------------------------

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Directive directive(String id, String title, String procedure) {
        Directive.Builder builder = Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title(title)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED);
        if (procedure != null) {
            builder.procedure(procedure);
        }
        return builder.build();
    }

    private Directive directiveIn(PackIdentifier pack, String id) {
        return store.packs.get(pack).directives().stream()
                .filter(d -> d.id().equals(new DirectiveIdentifier(id)))
                .findFirst()
                .orElseThrow();
    }

    /** One fake that realizes both the load port and the write-path port, recording what a commit persisted. */
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
        public <T> T exclusively(PackIdentifier pack, Supplier<T> work) {
            if (!packs.containsKey(pack)) {
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
            }
            return work.get();
        }

        @Override
        public PackRevision commit(PackIdentifier pack, String ifMatch, PackWriteStore.Change change) {
            packs.put(pack, change.revalidated());
            change.relationships().ifPresent(persisted -> edges.put(pack, new ArrayList<>(persisted)));
            commits++;
            return currentRevision(pack);
        }

        List<Relationship> relationships(PackIdentifier pack) {
            return List.copyOf(edges.getOrDefault(pack, List.of()));
        }
    }
}
