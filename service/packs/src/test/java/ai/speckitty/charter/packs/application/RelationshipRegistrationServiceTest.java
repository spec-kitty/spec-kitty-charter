package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The kind-neutral relationship registration use case over in-memory ports (FR-009/012). Every refusal is paired with
 * a same-fixture positive control (SC-003, DIRECTIVE_041): a refused write commits nothing, and the matching accepted
 * write proves the fixture itself is sound.
 */
class RelationshipRegistrationServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private final FakeStore store = new FakeStore();
    private final RelationshipRegistrationService service =
            new RelationshipRegistrationService(store, store, new PermissiveWritePolicyGate());

    // ---- FR-009: relationship upsert idempotent on the unordered pair, reverse replaces -------------------------

    @Test
    void aRelationshipIsIdempotentOnThePairAndTheReverseReplaces() {
        seedDirective("DIR_A");
        seedDirective("DIR_B");
        Urn a = new Urn(NodeKind.DIRECTIVE, "DIR_A");
        Urn b = new Urn(NodeKind.DIRECTIVE, "DIR_B");
        String etag = store.currentRevision(PACK).token();

        service.registerRelationship(PACK, Relationship.of(a, b, RelationType.REQUIRES), etag);
        service.registerRelationship(
                PACK,
                Relationship.of(a, b, RelationType.REQUIRES),
                store.currentRevision(PACK).token());
        assertThat(store.relationships(PACK))
                .as("re-registering the same pair keeps one edge")
                .hasSize(1);

        // The reverse orientation replaces rather than adds (unordered-pair identity).
        RelationshipRegistrationService.Registered reverse = service.registerRelationship(
                PACK,
                Relationship.of(b, a, RelationType.SUGGESTS),
                store.currentRevision(PACK).token());
        assertThat(reverse.created()).isFalse();
        assertThat(store.relationships(PACK)).hasSize(1);
        assertThat(store.relationships(PACK).getFirst().type()).isEqualTo(RelationType.SUGGESTS);
    }

    @Test
    void aRelationshipWhoseFromDoesNotResolveIsAConflictWhileAResolvableOneSucceeds() {
        seedDirective("DIR_A");
        Urn resolvable = new Urn(NodeKind.DIRECTIVE, "DIR_A");
        Urn unresolvable = new Urn(NodeKind.DIRECTIVE, "GHOST");
        Urn external = new Urn(NodeKind.PROCEDURE, "external-proc");
        int before = store.commits;

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK,
                        Relationship.of(unresolvable, external, RelationType.REQUIRES),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).isEqualTo(before);

        // from resolves, to is external (allowed): accepted.
        assertThatCode(() -> service.registerRelationship(
                        PACK,
                        Relationship.of(resolvable, external, RelationType.REQUIRES),
                        store.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(before + 1);
    }

    @Test
    void anImpactsRelationshipIsRefusedBecauseItsStrengthIsNotOnTheWire() {
        seedDirective("DIR_A");
        int before = store.commits;

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK,
                        Relationship.of(
                                new Urn(NodeKind.DIRECTIVE, "DIR_A"),
                                new Urn(NodeKind.DIRECTIVE, "DIR_B"),
                                RelationType.IMPACTS),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(r.getMessage()).contains("impacts");
                });
        assertThat(store.commits).isEqualTo(before);
    }

    // ---- FR-012: optimistic concurrency, stale refused, fresh accepted, missing refused on sub-resource ---------

    @Test
    void aStaleIfMatchIsRefusedWhileAFreshOneSucceeds() {
        seedDirective("DIR_A");
        Relationship edge = Relationship.of(
                new Urn(NodeKind.DIRECTIVE, "DIR_A"), new Urn(NodeKind.PROCEDURE, "p"), RelationType.REQUIRES);

        assertThatThrownBy(() -> service.registerRelationship(PACK, edge, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));

        assertThatCode(() -> service.registerRelationship(
                        PACK, edge, store.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
    }

    @Test
    void aRelationshipWriteWithoutAPreconditionIsRefusedPreconditionRequired() {
        seedDirective("DIR_A");
        Relationship edge = Relationship.of(
                new Urn(NodeKind.DIRECTIVE, "DIR_A"), new Urn(NodeKind.PROCEDURE, "p"), RelationType.REQUIRES);
        assertThatThrownBy(() -> service.registerRelationship(PACK, edge, null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.MISSING));
    }

    // ---- fixtures and fakes --------------------------------------------------------------------------------------

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Directive directive(String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title(id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private void seedDirective(String id) {
        Pack current = store.packs.get(PACK);
        Pack.Builder builder = Pack.builder().id(PACK).descriptor(descriptor());
        current.directives().forEach(builder::directive);
        builder.directive(directive(id));
        store.packs.put(PACK, builder.build());
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
