package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.application.GuideFetchRefusal.Reason;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService.GuideSource;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The toolguide registration use case over in-memory ports (FR-010/015, NFR-005, SC-001/003). Every refusal is paired
 * with a same-fixture positive control (SC-003, DIRECTIVE_041): a refused write changes nothing, and the matching
 * accepted write proves the fixture itself is sound.
 */
class ToolguideRegistrationServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final URI GUIDE_URI = URI.create("https://guides.example.org/git.guide.md");
    private static final String OVERVIEW = "How to use the tool.";
    private static final String INVALID = "not a guide";

    private final FakeStore store = new FakeStore();
    private final StubFetcher fetcher = new StubFetcher(store);
    private final WritePolicyGate policy = new PermissiveWritePolicyGate();
    private final ToolguideRegistrationService service =
            new ToolguideRegistrationService(store, store, policy, fetcher, new OverviewFormat());

    // ---- FR-015 / SC-001: register (PUT), read it back value-equal, optional round-trips both ways ---------------

    @Test
    void registersAToolguideWithItsFieldsAndReadsItBackValueEqual() {
        Toolguide submitted = toolguide("git", "2026-02-02", Guide.of(OVERVIEW));

        ToolguideRegistrationService.Registered result = register(PACK, "git", "2026-02-02", false, null);

        assertThat(result.created()).isTrue();
        assertThat(result.target()).isEqualTo(submitted.urn());
        Toolguide persisted = toolguideIn(PACK, "git");
        assertThat(persisted.tool()).isEqualTo(submitted.tool());
        assertThat(persisted.title()).isEqualTo(submitted.title());
        assertThat(persisted.guide()).isEqualTo(Guide.of(OVERVIEW));
        assertThat(result.toolguide().guide()).isEqualTo(persisted.guide());
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
        register(PACK, "make", null, false, null);
        assertThat(toolguideIn(PACK, "make").lastUpdated()).isEmpty();
    }

    // ---- FR-015 / SC-003 control: duplicate-id POST refused via aggregate revalidation, distinct id accepted -----

    @Test
    void aDuplicateIdPostIsAConflictWhileADistinctIdPostSucceedsOnTheSameFixture() {
        register(PACK, "git", null, false, null);
        int commitsAfterFirst = store.commits;

        assertThatThrownBy(() -> register(PACK, "git", null, false, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).as("the refused POST committed nothing").isEqualTo(commitsAfterFirst);
        assertThat(store.packs.get(PACK).toolguides()).hasSize(1);

        // Same fixture, distinct id: accepted.
        assertThatCode(() -> register(PACK, "make", null, false, null)).doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(commitsAfterFirst + 1);
        assertThat(store.packs.get(PACK).toolguides()).hasSize(2);
    }

    @Test
    void aPutReplacesIdempotentlyRatherThanConflicting() {
        register(PACK, "git", "first", true, null);
        ToolguideRegistrationService.Registered replaced = register(PACK, "git", "second", true, null);
        assertThat(replaced.created()).isFalse();
        assertThat(toolguideIn(PACK, "git").lastUpdated()).contains("second");
        assertThat(store.packs.get(PACK).toolguides()).hasSize(1);
    }

    // ---- FR-012: optimistic concurrency, stale refused, fresh accepted ------------------------------------------

    @Test
    void aStaleIfMatchIsRefusedWhileAFreshOneSucceeds() {
        assertThatThrownBy(() -> register(PACK, "git", null, true, "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));

        assertThatCode(() -> register(
                        PACK, "git", null, true, store.currentRevision(PACK).token()))
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

        register(PACK, "git", null, false, null);

        assertThat(store.relationships(PACK)).containsExactly(edge);
    }

    @Test
    void anUnknownPackIsANotFound() {
        assertThatThrownBy(() -> register(new PackIdentifier("ghost"), "git", null, true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void aRegistrationRefusedWithoutItsGuideFetchesNothing() {
        fetcher.serve(GUIDE_URI, OVERVIEW);
        GuideSource byUri = new GuideSource.Location(GUIDE_URI);

        assertThatThrownBy(() -> register(new PackIdentifier("ghost"), "git", null, byUri, false, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
        register(PACK, "git", null, true, null);
        assertThatThrownBy(() -> register(PACK, "git", null, byUri, false, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThatThrownBy(() -> register(PACK, "git", null, byUri, true, "\"sha256:" + "0".repeat(64) + "\""))
                .isInstanceOf(PreconditionException.class);

        assertThat(fetcher.fetched)
                .as("no refused registration fetched its guide")
                .isEmpty();
        assertThat(store.commits).isEqualTo(1);
    }

    // ---- FR-010 / NFR-005: the guide by URI, fetched before the write lock -------------------------------------

    @Test
    void aGuideUriIsFetchedOutsideTheWriteLockAndStoredAsIfSentAsContent() {
        fetcher.serve(GUIDE_URI, "Fetched overview.");

        ToolguideRegistrationService.Registered result =
                register(PACK, "git", null, new GuideSource.Location(GUIDE_URI), true, null);

        assertThat(fetcher.fetchedWhileLocked)
                .as("no fetch ran under the write lock")
                .isEmpty();
        assertThat(fetcher.fetched).containsExactly(GUIDE_URI);
        assertThat(store.locks)
                .as("the registration took the write lock for its checks before the fetch and for its commit after")
                .isEqualTo(2);
        assertThat(toolguideIn(PACK, "git").guide()).isEqualTo(Guide.of("Fetched overview."));
        assertThat(result.toolguide().guide()).isEqualTo(Guide.of("Fetched overview."));
    }

    @Test
    void aPutWithTheSameUriFetchesAgain() {
        fetcher.serve(GUIDE_URI, "First overview.");
        register(PACK, "git", null, new GuideSource.Location(GUIDE_URI), true, null);
        fetcher.serve(GUIDE_URI, "Second overview.");

        register(PACK, "git", null, new GuideSource.Location(GUIDE_URI), true, null);

        assertThat(fetcher.fetched).containsExactly(GUIDE_URI, GUIDE_URI);
        assertThat(toolguideIn(PACK, "git").guide()).isEqualTo(Guide.of("Second overview."));
    }

    @Test
    void aRefusedFetchIsAValidationRefusalThatStoresNothingWhileAnAllowedOneIsStored() {
        fetcher.refuse(GUIDE_URI, Reason.HOST_NOT_ALLOWED);

        assertThatThrownBy(() -> register(PACK, "git", null, new GuideSource.Location(GUIDE_URI), true, null))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(r.getMessage()).contains("HOST_NOT_ALLOWED", "guides.example.org");
                    assertThat(r.getMessage()).doesNotContain("git.guide.md");
                });
        assertThat(store.commits).as("the refused fetch committed nothing").isZero();
        assertThat(store.locks)
                .as("only the checks before the fetch took the write lock; the refused fetch never reached the commit")
                .isEqualTo(1);

        // Same fixture, the host served: accepted.
        fetcher.serve(GUIDE_URI, OVERVIEW);
        assertThatCode(() -> register(PACK, "git", null, new GuideSource.Location(GUIDE_URI), true, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(1);
    }

    @Test
    void anInvalidGuideIsAValidationRefusalThatStoresNothingWhetherSentOrFetched() {
        assertThatThrownBy(() -> register(PACK, "git", null, new GuideSource.Content(INVALID), true, null))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(r.getMessage()).isEqualTo("The guide has no ## Overview.");
                });
        fetcher.serve(GUIDE_URI, INVALID);
        assertThatThrownBy(() -> register(PACK, "git", null, new GuideSource.Location(GUIDE_URI), true, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
        assertThat(store.commits).as("neither invalid guide was committed").isZero();

        // Same fixture, a valid guide: accepted.
        assertThatCode(() -> register(PACK, "git", null, new GuideSource.Content(OVERVIEW), true, null))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(1);
    }

    // ---- fixtures and fakes --------------------------------------------------------------------------------------

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty());
    }

    private ToolguideRegistrationService.Registered register(
            PackIdentifier pack, String id, @Nullable String lastUpdated, boolean replace, @Nullable String ifMatch) {
        return register(pack, id, lastUpdated, new GuideSource.Content(OVERVIEW), replace, ifMatch);
    }

    private ToolguideRegistrationService.Registered register(
            PackIdentifier pack,
            String id,
            @Nullable String lastUpdated,
            GuideSource guide,
            boolean replace,
            @Nullable String ifMatch) {
        return service.registerToolguide(
                pack,
                new ToolguideIdentifier(id),
                "How to use " + id,
                guide,
                resolved -> toolguide(id, lastUpdated, resolved),
                replace,
                ifMatch);
    }

    private static Toolguide toolguide(String id, @Nullable String lastUpdated, Guide guide) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool(id)
                .title("How to use " + id)
                .guide(guide)
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

    /** Reads a guide as its overview; refuses {@link #INVALID}, as the Markdown adapter refuses a guide-less text. */
    private static final class OverviewFormat implements GuideFormat {
        @Override
        public Guide read(String title, String markdown) {
            if (markdown.equals(INVALID)) {
                throw new IllegalArgumentException("the guide has no ## Overview");
            }
            return Guide.of(markdown);
        }

        @Override
        public String write(String title, Guide guide) {
            return guide.overview();
        }
    }

    /** A fetcher that serves or refuses a URI as a test arms it, and records whether the write lock was held. */
    private static final class StubFetcher implements GuideFetcher {
        private final FakeStore store;
        private final Map<URI, String> served = new HashMap<>();
        private final Map<URI, Reason> refused = new HashMap<>();
        private final List<URI> fetched = new ArrayList<>();
        private final List<URI> fetchedWhileLocked = new ArrayList<>();

        StubFetcher(FakeStore store) {
            this.store = store;
        }

        void serve(URI uri, String text) {
            refused.remove(uri);
            served.put(uri, text);
        }

        void refuse(URI uri, Reason reason) {
            served.remove(uri);
            refused.put(uri, reason);
        }

        @Override
        public String fetch(URI uri) throws GuideFetchRefusal {
            fetched.add(uri);
            if (store.locked) {
                fetchedWhileLocked.add(uri);
            }
            Reason reason = refused.get(uri);
            if (reason != null) {
                throw new GuideFetchRefusal(reason, uri.getHost(), "refused by the test");
            }
            String text = served.get(uri);
            if (text == null) {
                throw new GuideFetchRefusal(Reason.HTTP_STATUS, uri.getHost(), "nothing served");
            }
            return text;
        }
    }

    /** One fake that realizes both the load port and the write-path port, recording what a commit persisted. */
    private static final class FakeStore implements PackRepository, PackWriteStore {
        private final Map<PackIdentifier, Pack> packs = new HashMap<>(
                Map.of(PACK, Pack.builder().id(PACK).descriptor(descriptor()).build()));
        private final Map<PackIdentifier, List<Relationship>> edges = new HashMap<>();
        private int commits;
        private int locks;
        private boolean locked;

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
            locks++;
            locked = true;
            try {
                return work.get();
            } finally {
                locked = false;
            }
        }

        @Override
        public PackRevision commit(PackIdentifier pack, String ifMatch, PackWriteStore.Change change) {
            packs.put(pack, change.revalidated());
            change.relationships().ifPresent(persisted -> edges.put(pack, List.copyOf(persisted)));
            commits++;
            return currentRevision(pack);
        }

        List<Relationship> relationships(PackIdentifier pack) {
            return List.copyOf(edges.getOrDefault(pack, List.of()));
        }
    }
}
