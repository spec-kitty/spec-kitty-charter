package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.Pack;
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

    // ---- FR-014 / SC-001: register, read back value-equal; the registration writes no edge (ADR-0063) -----------

    @Test
    void registersAParadigmWithItsFieldsAndReadsItBackValueEqual() {
        Paradigm submitted = paradigm("clean-architecture", "Clean");

        ParadigmRegistrationService.Registered result = service.registerParadigm(PACK, submitted, false, null);

        assertThat(result.created()).isTrue();
        assertThat(result.target()).isEqualTo(submitted.urn());
        Paradigm persisted = paradigmIn(PACK, "clean-architecture");
        assertThat(persisted.name()).isEqualTo(submitted.name());
        assertThat(persisted.summary()).isEqualTo(submitted.summary());
        assertThat(store.relationshipChanges)
                .as(
                        "a paradigm registration never writes the relationship shard: putPackRelationship is the only edge write")
                .isZero();
    }

    // ---- duplicate-id POST refused, distinct id accepted on the same fixture ------------------------------------

    @Test
    void aDuplicateIdPostIsAConflictWhileADistinctIdPostSucceedsOnTheSameFixture() {
        service.registerParadigm(PACK, paradigm("clean-architecture", "first"), false, null);
        int commitsAfterFirst = store.commits;

        assertThatThrownBy(() -> service.registerParadigm(PACK, paradigm("clean-architecture", "again"), false, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).as("the refused POST committed nothing").isEqualTo(commitsAfterFirst);

        // Same fixture, distinct id: accepted.
        assertThatCode(() -> service.registerParadigm(PACK, paradigm("hexagonal", "ok"), false, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(commitsAfterFirst + 1);
    }

    @Test
    void aPutReplacesIdempotentlyRatherThanConflicting() {
        service.registerParadigm(PACK, paradigm("clean-architecture", "first"), true, null);
        ParadigmRegistrationService.Registered replaced =
                service.registerParadigm(PACK, paradigm("clean-architecture", "second"), true, null);
        assertThat(replaced.created()).isFalse();
        assertThat(paradigmIn(PACK, "clean-architecture").name()).isEqualTo("second");
    }

    // ---- FR-012: optimistic concurrency, stale refused, fresh accepted ------------------------------------------

    @Test
    void aParadigmWriteWithAStaleIfMatchIsRefusedWhileAFreshOneSucceeds() {
        service.registerParadigm(PACK, paradigm("clean-architecture", "x"), true, null);

        assertThatThrownBy(() -> service.registerParadigm(
                        PACK, paradigm("clean-architecture", "y"), true, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));

        assertThatCode(() -> service.registerParadigm(
                        PACK,
                        paradigm("clean-architecture", "z"),
                        true,
                        store.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnknownPackIsANotFound() {
        assertThatThrownBy(() -> service.registerParadigm(
                        new PackIdentifier("ghost"), paradigm("clean-architecture", "x"), true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    // ---- fixtures and fakes --------------------------------------------------------------------------------------

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty());
    }

    private static Paradigm paradigm(String id, String name) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .name(name)
                .summary("Summary of " + id)
                .build();
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
        private int relationshipChanges;

        @Override
        public Pack load(PackIdentifier pack) {
            Pack found = packs.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
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
            change.relationships().ifPresent(persisted -> {
                edges.put(pack, new ArrayList<>(persisted));
                relationshipChanges++;
            });
            commits++;
            return currentRevision(pack);
        }
    }
}
