package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The toolguide registration use case over in-memory ports (FR-015, SC-001/003). Every refusal is paired with a
 * same-fixture positive control (SC-003, DIRECTIVE_041): a refused write changes nothing, and the matching accepted
 * write proves the fixture itself is sound.
 */
class ToolguideRegistrationServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private final FakeStore store = new FakeStore();
    private final WritePolicyGate policy = new PermissiveWritePolicyGate();
    private final ToolguideRegistrationService service = new ToolguideRegistrationService(store, store, policy);

    // ---- FR-015 / SC-001: register (PUT), read it back value-equal, optional round-trips both ways ---------------

    @Test
    void registersAToolguideWithItsFieldsAndReadsItBackValueEqual() {
        Toolguide submitted = toolguide("git", "2026-02-02");

        ToolguideRegistrationService.Registered result = service.registerToolguide(PACK, submitted, false, null);

        assertThat(result.created()).isTrue();
        assertThat(result.target()).isEqualTo(submitted.urn());
        Toolguide persisted = toolguideIn(PACK, "git");
        assertThat(persisted.tool()).isEqualTo(submitted.tool());
        assertThat(persisted.title()).isEqualTo(submitted.title());
        assertThat(persisted.guidePath()).isEqualTo(submitted.guidePath());
        assertThat(persisted.summary()).isEqualTo(submitted.summary());
        assertThat(persisted.commands()).isEqualTo(submitted.commands());
        assertThat(persisted.appliesToLanguages()).isEqualTo(submitted.appliesToLanguages());
        assertThat(persisted.references()).isEqualTo(submitted.references());
        assertThat(persisted.lastUpdated()).contains("2026-02-02");
        assertThat(persisted.relationships()).isEmpty();
    }

    @Test
    void registersAToolguideWithAnAbsentLastUpdatedAndReadsItBackEmpty() {
        // SC-001 control: the absent optional survives the write path too.
        service.registerToolguide(PACK, toolguide("make", null), false, null);
        assertThat(toolguideIn(PACK, "make").lastUpdated()).isEmpty();
    }

    // ---- FR-015 / SC-003 control: duplicate-id POST refused via aggregate revalidation, distinct id accepted -----

    @Test
    void aDuplicateIdPostIsAConflictWhileADistinctIdPostSucceedsOnTheSameFixture() {
        service.registerToolguide(PACK, toolguide("git", null), false, null);
        int commitsAfterFirst = store.commits;

        assertThatThrownBy(() -> service.registerToolguide(PACK, toolguide("git", null), false, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).as("the refused POST committed nothing").isEqualTo(commitsAfterFirst);
        assertThat(store.packs.get(PACK).toolguides()).hasSize(1);

        // Same fixture, distinct id: accepted.
        assertThatCode(() -> service.registerToolguide(PACK, toolguide("make", null), false, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(commitsAfterFirst + 1);
        assertThat(store.packs.get(PACK).toolguides()).hasSize(2);
    }

    @Test
    void aPutReplacesIdempotentlyRatherThanConflicting() {
        service.registerToolguide(PACK, toolguide("git", "first"), true, null);
        ToolguideRegistrationService.Registered replaced =
                service.registerToolguide(PACK, toolguide("git", "second"), true, null);
        assertThat(replaced.created()).isFalse();
        assertThat(toolguideIn(PACK, "git").lastUpdated()).contains("second");
        assertThat(store.packs.get(PACK).toolguides()).hasSize(1);
    }

    // ---- FR-012: optimistic concurrency, stale refused, fresh accepted ------------------------------------------

    @Test
    void aStaleIfMatchIsRefusedWhileAFreshOneSucceeds() {
        assertThatThrownBy(
                        () -> service.registerToolguide(PACK, toolguide("git", null), true, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));

        assertThatCode(() -> service.registerToolguide(
                        PACK,
                        toolguide("git", null),
                        true,
                        store.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
    }

    // ---- the registration preserves the standalone relationship shard unchanged ---------------------------------

    @Test
    void aToolguideWritePreservesTheExistingRelationshipShard() {
        Relationship edge = Relationship.of(
                new ai.speckitty.charter.kernel.domain.Urn(
                        ai.speckitty.charter.kernel.domain.NodeKind.DIRECTIVE, "GUARD"),
                new ai.speckitty.charter.kernel.domain.Urn(ai.speckitty.charter.kernel.domain.NodeKind.PROCEDURE, "p"),
                ai.speckitty.charter.kernel.domain.RelationType.REQUIRES);
        store.edges.put(PACK, List.of(edge));

        service.registerToolguide(PACK, toolguide("git", null), false, null);

        assertThat(store.relationships(PACK)).containsExactly(edge);
    }

    @Test
    void anUnknownPackIsANotFound() {
        assertThatThrownBy(() ->
                        service.registerToolguide(new PackIdentifier("ghost"), toolguide("git", null), true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    // ---- fixtures and fakes --------------------------------------------------------------------------------------

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Toolguide toolguide(String id, String lastUpdated) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool(id)
                .title("How to use " + id)
                .guidePath(id + ".md")
                .summary("Using " + id + " under governance.")
                .command(id + " status")
                .appliesToLanguage("java")
                .lastUpdated(lastUpdated)
                .reference("docs/" + id + ".md")
                .build();
    }

    private Toolguide toolguideIn(PackIdentifier pack, String id) {
        return store.packs.get(pack).toolguides().stream()
                .filter(t -> t.id().equals(new ToolguideIdentifier(id)))
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
        public PackRevision commit(PackIdentifier pack, Pack mutated, List<Relationship> relationships) {
            packs.put(pack, mutated);
            edges.put(pack, List.copyOf(relationships));
            commits++;
            return currentRevision(pack);
        }

        List<Relationship> relationships(PackIdentifier pack) {
            return List.copyOf(edges.getOrDefault(pack, List.of()));
        }
    }
}
