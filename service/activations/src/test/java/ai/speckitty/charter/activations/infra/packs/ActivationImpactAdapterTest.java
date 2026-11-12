package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.FileActivationRepository;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.application.ActivationImpact.ClosureChange;
import ai.speckitty.charter.packs.application.ActivationImpact.ClosureChanges;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.EdgeIndex;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.PromptFacts;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * The adapter that tells the registration impact check which packs the stored activations root at (IC-15, FR-025): each
 * stored activation's {@code packs} list, in the order written, and an empty optional for one that lists none.
 */
class ActivationImpactAdapterTest {

    private static ActivationDocument listing(PackRef... packs) {
        return ActivationDocument.builder()
                .packs(List.of(packs))
                .selected(ArtifactKind.TACTIC, List.of("t-one"))
                .build()
                .document();
    }

    private static ActivationDocument listingNone() {
        return ActivationDocument.builder()
                .selected(ArtifactKind.TACTIC, List.of("t-one"))
                .build()
                .document();
    }

    @Test
    void noStoredActivationHasNoRootSet() {
        assertThat(new ActivationImpactAdapter(new InMemoryActivationRepository()).activationRoots())
                .isEmpty();
    }

    @Test
    void eachStoredActivationGivesItsPacksInTheOrderWrittenAndOneWithNoneGivesEmpty() {
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        repository.create(new ActivationId("one"), listing(PackRef.of("beta"), PackRef.pinned("alpha", "1.2")));
        repository.create(new ActivationId("two"), listingNone());

        assertThat(new ActivationImpactAdapter(repository).activationRoots())
                .containsExactlyInAnyOrder(Optional.of(List.of("beta", "alpha")), Optional.empty());
    }

    @Test
    void theFileStoreIsListedToo(@TempDir Path root) {
        FileActivationRepository repository = new FileActivationRepository(root.resolve("activations"));
        repository.create(new ActivationId("file-one"), listing(PackRef.of("main")));

        assertThat(new ActivationImpactAdapter(repository).activationRoots())
                .containsExactly(Optional.of(List.of("main")));
    }

    @Test
    void aListingThatFailsIsLoggedAsTheOperatorsFaultAndNotSwallowed() {
        ActivationRepository unlistable = new ActivationRepository() {
            @Override
            public List<StoredActivation> list() {
                throw new IllegalStateException("injected: the store cannot be listed");
            }

            @Override
            public Optional<StoredActivation> find(ActivationId id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<StoredActivation> create(ActivationId id, ActivationDocument document) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CasResult replace(ActivationId id, Optional<Revision> expected, ActivationDocument document) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DeleteResult delete(ActivationId id, Optional<Revision> expected) {
                throw new UnsupportedOperationException();
            }
        };
        Logger logger = (Logger) LoggerFactory.getLogger(ActivationImpactAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> new ActivationImpactAdapter(unlistable).activationRoots())
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains("registration impact check")
                    .contains("cannot list the stored activations")
                    .contains("internal_error");
        });
    }

    // ---- C38: the registration gate (squad A2)
    // -----------------------------------------------------------------------

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    private static PromptFacts.DeclaredSlot slot(String id) {
        return new PromptFacts.DeclaredSlot(new SlotId(id), false);
    }

    /**
     * A closure merged for real: base (org) holds M, its step s, whose prompt declares spdd:reasons-block, and the
     * enhancement x that fills it; with {@code overridden}, proj (project) replaces M and s with a prompt that declares
     * the given slots.
     */
    private static ClosureView closure(boolean overridden, String... projSlots) {
        List<PackUnderResolution> packs = new java.util.ArrayList<>();
        Map<String, PackCompositionFacts> facts = new java.util.LinkedHashMap<>();
        add(
                packs,
                facts,
                "base",
                PackLevel.ORG,
                List.of(new Relationship(
                        u("step_enhancement:x"),
                        Optional.empty(),
                        u("action:M/s"),
                        Optional.empty(),
                        RelationType.ENHANCES,
                        Optional.empty(),
                        Optional.empty())),
                new PromptFacts(
                        Map.of(u("action:M/s"), List.of(slot("spdd:reasons-block"))),
                        Map.of(
                                u("step_enhancement:x"),
                                List.of(new PromptFacts.Fill(new SlotId("spdd:reasons-block"), "Fill it.\n")))),
                "mission_type:M",
                "action:M/s",
                "step_enhancement:x");
        if (overridden) {
            add(
                    packs,
                    facts,
                    "proj",
                    PackLevel.PROJECT,
                    List.of(),
                    new PromptFacts(
                            Map.of(
                                    u("action:M/s"),
                                    java.util.Arrays.stream(projSlots)
                                            .map(ActivationImpactAdapterTest::slot)
                                            .toList()),
                            Map.of()),
                    "mission_type:M",
                    "action:M/s");
        }
        return new ClosureView(
                List.of(),
                Map.of(),
                Map.of(),
                new RequiredFloor(Map.of()),
                new PerPackPolicy(Map.of()),
                packs.stream().map(pack -> pack.descriptor().packId()).toList(),
                List.of(),
                Map.of(),
                "sha256:merged",
                EdgeIndex.of(packs, facts),
                new ClosureView.MergeInputs(packs, facts),
                "sha256:closure");
    }

    private static void add(
            List<PackUnderResolution> packs,
            Map<String, PackCompositionFacts> facts,
            String name,
            PackLevel level,
            List<Relationship> edges,
            PromptFacts prompts,
            String... held) {
        PackDescriptor descriptor = new PackDescriptor(
                "id-" + name,
                "1.0.0",
                Optional.empty(),
                name,
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.of());
        List<String> urns = List.of(held);
        packs.add(new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), urns));
        facts.put(
                name,
                new PackCompositionFacts(
                        List.of(),
                        urns,
                        List.of(),
                        MutabilityPolicySource.empty(),
                        List.of(),
                        edges,
                        Map.of(),
                        Map.of(),
                        prompts));
    }

    private static ActivationDocument selecting(String... enhancements) {
        return ActivationDocument.builder()
                .selected(ArtifactKind.MISSION_TYPE, List.of("M"))
                .selected(ArtifactKind.STEP_ENHANCEMENT, List.of(enhancements))
                .build()
                .document();
    }

    private static ClosureChanges every(ClosureChange change) {
        return roots -> Optional.of(change);
    }

    @Test
    void aChangeThatLeavesAnEnhancementInForceWithoutItsSlotIsRefusedNamingTheActivation() {
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        repository.create(new ActivationId("a-1"), selecting("x"));
        ActivationImpactAdapter adapter = new ActivationImpactAdapter(repository);

        assertThatThrownBy(() ->
                        adapter.requireComposable(every(new ClosureChange(Optional.of(closure(false)), closure(true)))))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PROMPT_SLOT_MISSING);
                    assertThat(refused.getMessage())
                            .contains("a-1")
                            .contains("step_enhancement:x")
                            .contains("spdd:reasons-block");
                    assertThat(refused.packs()).extracting(RefusalPack::packId).containsExactly("base", "proj");
                });
    }

    @Test
    void theTwinTheSameChangeWhereNoStoredActivationPutsTheEnhancementInForcePasses() {
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        repository.create(new ActivationId("a-1"), selecting());

        new ActivationImpactAdapter(repository)
                .requireComposable(every(new ClosureChange(Optional.of(closure(false)), closure(true))));
    }

    @Test
    void theTwinAChangeWhoseOverrideDeclaresTheSlotPasses() {
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        repository.create(new ActivationId("a-1"), selecting("x"));

        new ActivationImpactAdapter(repository)
                .requireComposable(
                        every(new ClosureChange(Optional.of(closure(false)), closure(true, "spdd:reasons-block"))));
    }

    @Test
    void anActivationWhoseClosureTheChangeDoesNotJoinIsNotJudged() {
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        repository.create(new ActivationId("a-1"), selecting("x"));

        new ActivationImpactAdapter(repository).requireComposable(roots -> Optional.empty());
    }

    @Test
    void anActivationThatCouldNotBeReadBeforeTheChangeIsNotBlamedOnIt() {
        // G21: a closure that did not compose before the change has no before view; the write is not refused for it.
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        repository.create(new ActivationId("a-1"), selecting("x"));

        new ActivationImpactAdapter(repository)
                .requireComposable(every(new ClosureChange(Optional.empty(), closure(true))));
    }

    @Test
    void theNoticesNeverRefuseWhereTheGateDoes() {
        // The notices name the activation whose in-force versions the change moves; only the gate refuses.
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        repository.create(new ActivationId("a-1"), selecting("x"));

        assertThat(new ActivationImpactAdapter(repository)
                        .activationsAffected(every(new ClosureChange(Optional.of(closure(false)), closure(true)))))
                .containsExactly("a-1");
    }
}
