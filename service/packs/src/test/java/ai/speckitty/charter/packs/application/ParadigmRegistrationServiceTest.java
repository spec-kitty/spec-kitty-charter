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
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The paradigm registration use case over in-memory ports (FR-014). Every refusal is paired with a same-fixture
 * positive control (SC-003, DIRECTIVE_041): a refused write changes nothing, and the matching accepted write proves
 * the fixture itself is sound.
 */
class ParadigmRegistrationServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private final FakeStore store = new FakeStore();
    private final WritePolicyGate policy = new PermissiveWritePolicyGate();
    private final ParadigmRegistrationService service = new ParadigmRegistrationService(store, store, policy);

    // ---- FR-014 / SC-001: register with relationships, read back value-equal ------------------------------------

    @Test
    void registersAParadigmWithItsFieldsAndReadsItBackValueEqual() {
        Paradigm submitted = paradigm("clean-architecture", "Clean", "ALPHA");
        Relationship bodyEdge =
                Relationship.of(submitted.urn(), new Urn(NodeKind.DIRECTIVE, "ALPHA"), RelationType.REQUIRES);

        ParadigmRegistrationService.Registered result =
                service.registerParadigm(PACK, submitted, List.of(bodyEdge), false, null);

        assertThat(result.created()).isTrue();
        assertThat(result.target()).isEqualTo(submitted.urn());
        Paradigm persisted = paradigmIn(PACK, "clean-architecture");
        assertThat(persisted.name()).isEqualTo(submitted.name());
        assertThat(persisted.summary()).isEqualTo(submitted.summary());
        assertThat(persisted.directiveRefs()).isEqualTo(submitted.directiveRefs());
        assertThat(persisted.relationships()).isEqualTo(submitted.relationships());
        assertThat(store.relationships(PACK)).contains(bodyEdge);
    }

    // ---- duplicate-id POST refused, distinct id accepted on the same fixture ------------------------------------

    @Test
    void aDuplicateIdPostIsAConflictWhileADistinctIdPostSucceedsOnTheSameFixture() {
        service.registerParadigm(PACK, paradigm("clean-architecture", "first", null), List.of(), false, null);
        int commitsAfterFirst = store.commits;

        assertThatThrownBy(() -> service.registerParadigm(
                        PACK, paradigm("clean-architecture", "again", null), List.of(), false, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).as("the refused POST committed nothing").isEqualTo(commitsAfterFirst);

        // Same fixture, distinct id: accepted.
        assertThatCode(() -> service.registerParadigm(PACK, paradigm("hexagonal", "ok", null), List.of(), false, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(commitsAfterFirst + 1);
    }

    @Test
    void aPutReplacesIdempotentlyRatherThanConflicting() {
        service.registerParadigm(PACK, paradigm("clean-architecture", "first", null), List.of(), true, null);
        ParadigmRegistrationService.Registered replaced =
                service.registerParadigm(PACK, paradigm("clean-architecture", "second", null), List.of(), true, null);
        assertThat(replaced.created()).isFalse();
        assertThat(paradigmIn(PACK, "clean-architecture").name()).isEqualTo("second");
    }

    // ---- SC-003 control: dual-write violation refused (422), pack unchanged, well-formed succeeds ---------------

    @Test
    void aBodyRelationshipNotAuthoredFromTheParadigmIsRefusedAndTheWellFormedOneSucceeds() {
        Paradigm paradigm = paradigm("clean-architecture", "Clean", null);
        Relationship foreign = Relationship.of(
                new Urn(NodeKind.PARADIGM, "other"), new Urn(NodeKind.DIRECTIVE, "ALPHA"), RelationType.REQUIRES);
        int before = store.commits;

        assertThatThrownBy(() -> service.registerParadigm(PACK, paradigm, List.of(foreign), true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
        assertThat(store.commits)
                .as("a refused registration commits nothing (NFR-002)")
                .isEqualTo(before);

        Relationship own = Relationship.of(paradigm.urn(), new Urn(NodeKind.DIRECTIVE, "ALPHA"), RelationType.REQUIRES);
        assertThatCode(() -> service.registerParadigm(PACK, paradigm, List.of(own), true, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(before + 1);
    }

    // ---- FR-012: optimistic concurrency, stale refused, fresh accepted ------------------------------------------

    @Test
    void aParadigmWriteWithAStaleIfMatchIsRefusedWhileAFreshOneSucceeds() {
        service.registerParadigm(PACK, paradigm("clean-architecture", "x", null), List.of(), true, null);

        assertThatThrownBy(() -> service.registerParadigm(
                        PACK, paradigm("clean-architecture", "y", null), List.of(), true, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));

        assertThatCode(() -> service.registerParadigm(
                        PACK,
                        paradigm("clean-architecture", "z", null),
                        List.of(),
                        true,
                        store.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnknownPackIsANotFound() {
        assertThatThrownBy(() -> service.registerParadigm(
                        new PackIdentifier("ghost"), paradigm("clean-architecture", "x", null), List.of(), true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    // ---- fixtures and fakes --------------------------------------------------------------------------------------

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Paradigm paradigm(String id, String name, String directiveRef) {
        Paradigm.Builder builder = Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .name(name)
                .summary("Summary of " + id);
        if (directiveRef != null) {
            builder.directiveRef(directiveRef);
        }
        return builder.build();
    }

    private Paradigm paradigmIn(PackIdentifier pack, String id) {
        return store.packs.get(pack).paradigms().stream()
                .filter(p -> p.id().equals(new ParadigmIdentifier(id)))
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
            return work.get();
        }

        @Override
        public PackRevision commit(
                PackIdentifier pack, String ifMatch, Pack mutated, List<Relationship> relationships) {
            packs.put(pack, mutated);
            if (relationships != null) { // null: the registration left the shard untouched
                edges.put(pack, new ArrayList<>(relationships));
            }
            commits++;
            return currentRevision(pack);
        }

        List<Relationship> relationships(PackIdentifier pack) {
            return List.copyOf(edges.getOrDefault(pack, List.of()));
        }
    }
}
