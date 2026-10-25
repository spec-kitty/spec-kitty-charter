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
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The styleguide registration use case over in-memory ports (FR-013). Every refusal is paired with a same-fixture
 * positive control (SC-003, DIRECTIVE_041): a refused write changes nothing, and the matching accepted write proves
 * the fixture itself is sound. A faithful copy of {@code DirectiveRegistrationServiceTest}'s write discipline, without
 * the relationship endpoint (a styleguide mints no relationships).
 */
class StyleguideRegistrationServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final PackIdentifier OTHER = new PackIdentifier("other-pack");
    private final FakeStore store = new FakeStore();
    private final WritePolicyGate policy = new PermissiveWritePolicyGate();
    private final StyleguideRegistrationService service = new StyleguideRegistrationService(store, store, policy);

    // ---- FR-013 / SC-001: register, read back value-equal --------------------------------------------------------

    @Test
    void registersAStyleguideWithItsFieldsAndReadsItBackValueEqual() {
        Styleguide submitted = styleguide(PACK, "java-style", "Java style");

        StyleguideRegistrationService.Registered result = service.registerStyleguide(PACK, submitted, false, null);

        assertThat(result.created()).isTrue();
        assertThat(result.target()).isEqualTo(submitted.urn());
        Styleguide persisted = styleguideIn(PACK, "java-style");
        assertThat(persisted.title()).isEqualTo(submitted.title());
        assertThat(persisted.scope()).isEqualTo(submitted.scope());
        assertThat(persisted.principles()).isEqualTo(submitted.principles());
        assertThat(persisted.relationships()).isEmpty();
    }

    // ---- FR-008 control: duplicate-id POST refused, distinct id accepted on the same fixture --------------------

    @Test
    void aDuplicateIdPostIsAConflictWhileADistinctIdPostSucceedsOnTheSameFixture() {
        service.registerStyleguide(PACK, styleguide(PACK, "java-style", "first"), false, null);
        int commitsAfterFirst = store.commits;

        assertThatThrownBy(() -> service.registerStyleguide(PACK, styleguide(PACK, "java-style", "again"), false, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).as("the refused POST committed nothing").isEqualTo(commitsAfterFirst);

        // Same fixture, distinct id: accepted.
        assertThatCode(() -> service.registerStyleguide(PACK, styleguide(PACK, "docs-style", "ok"), false, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(commitsAfterFirst + 1);
    }

    @Test
    void aPutReplacesIdempotentlyRatherThanConflicting() {
        service.registerStyleguide(PACK, styleguide(PACK, "java-style", "first"), true, null);
        StyleguideRegistrationService.Registered replaced =
                service.registerStyleguide(PACK, styleguide(PACK, "java-style", "second"), true, null);
        assertThat(replaced.created()).isFalse();
        assertThat(styleguideIn(PACK, "java-style").title()).isEqualTo("second");
    }

    // ---- SC-003 control: an aggregate-invalid child refused (422), pack unchanged, well-formed succeeds ---------

    @Test
    void aStyleguideThatBreaksAnAggregateInvariantIsRefusedAndTheWellFormedOneSucceeds() {
        // A styleguide whose packId is another pack breaks the belongs-to-pack invariant on revalidation (422).
        Styleguide foreign = styleguide(OTHER, "java-style", "foreign");
        int before = store.commits;

        assertThatThrownBy(() -> service.registerStyleguide(PACK, foreign, true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
        assertThat(store.commits)
                .as("a refused registration commits nothing (NFR-002)")
                .isEqualTo(before);

        // Same fixture, well-formed (correct pack): accepted.
        assertThatCode(() -> service.registerStyleguide(PACK, styleguide(PACK, "java-style", "ok"), true, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(before + 1);
    }

    // ---- FR-012: optimistic concurrency, stale refused ----------------------------------------------------------

    @Test
    void aStyleguideWriteWithAStaleIfMatchIsRefused() {
        assertThatThrownBy(() -> service.registerStyleguide(
                        PACK, styleguide(PACK, "java-style", "x"), true, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));
    }

    @Test
    void aFreshIfMatchSucceeds() {
        service.registerStyleguide(PACK, styleguide(PACK, "java-style", "first"), true, null);
        assertThatCode(() -> service.registerStyleguide(
                        PACK,
                        styleguide(PACK, "java-style", "second"),
                        true,
                        store.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnknownPackIsANotFound() {
        assertThatThrownBy(() -> service.registerStyleguide(
                        new PackIdentifier("ghost"),
                        styleguide(new PackIdentifier("ghost"), "java-style", "x"),
                        true,
                        null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    // ---- fixtures and fakes --------------------------------------------------------------------------------------

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Styleguide styleguide(PackIdentifier pack, String id, String title) {
        return Styleguide.builder()
                .id(new StyleguideIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .title(title)
                .scope(StyleguideScope.CODE)
                .principle("Prefer clarity")
                .build();
    }

    private Styleguide styleguideIn(PackIdentifier pack, String id) {
        return store.packs.get(pack).styleguides().stream()
                .filter(s -> s.id().equals(new StyleguideIdentifier(id)))
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
    }
}
