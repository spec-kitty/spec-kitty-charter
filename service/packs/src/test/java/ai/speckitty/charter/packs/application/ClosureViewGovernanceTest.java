package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A profile's governance over a real {@link ClosureQueries} read: the activation-scoped walk over the merged closure
 * ({@link ClosureView#governance}), the winner read ({@link ClosureView#effectiveArtifact}) and the pack-scoped walk
 * ({@link ProfileGovernance}), each with the pack that holds every entry. Every case builds its own two-level closure.
 */
class ClosureViewGovernanceTest {

    private static final Urn P = urn("agent_profile:P");
    private static final Urn Q = urn("agent_profile:Q");
    private static final Urn D = urn("directive:D");

    private final List<ConfiguredPack> configured = new ArrayList<>();
    private final Map<String, PackDescriptor> descriptors = new HashMap<>();
    private final Map<String, PackCompositionFacts> facts = new HashMap<>();
    private final Map<String, Pack> stored = new HashMap<>();
    private final AtomicInteger whole = new AtomicInteger();
    private String digest = "1";

    private static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    private static Relationship edge(String from, RelationType type, String to) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return new Relationship(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                type,
                Optional.empty(),
                Optional.empty());
    }

    private static AgentProfile profile(String pack, String id, String name) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(new PackIdentifier(pack))
                .name(name)
                .role("implementer")
                .purpose("Purpose of " + id)
                .specialization(new Specialization("Focus", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
    }

    /** A pack that holds profiles (with their bodies) and other artifacts, and writes edges. */
    private void pack(
            String name,
            PackLevel level,
            List<String> dependsOn,
            List<String> profiles,
            List<String> others,
            Relationship... edges) {
        configured.add(new ConfiguredPack(new PackIdentifier(name), level));
        PackDescriptor descriptor = new PackDescriptor(
                "id-" + name,
                "1.0.0",
                Optional.empty(),
                name,
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                dependsOn.stream()
                        .map(other -> new PackDependency("id-" + other, ">=0"))
                        .toList());
        descriptors.put(name, descriptor);
        List<String> held = Stream.concat(profiles.stream().map(id -> "agent_profile:" + id), others.stream())
                .toList();
        facts.put(
                name,
                new PackCompositionFacts(
                        List.of(), held, List.of(), MutabilityPolicySource.empty(), List.of(), List.of(edges)));
        Pack.Builder pack = Pack.builder().id(new PackIdentifier(name)).descriptor(descriptor);
        profiles.forEach(id -> pack.artifact(profile(name, id, "Copy of " + id + " in " + name)));
        stored.put(name, pack.build());
    }

    /** The inputs' digest, which tests move; each whole-source digest is counted, a closure's own is not. */
    private PackSources sources() {
        return new PackSources() {
            @Override
            public String fingerprint() {
                whole.incrementAndGet();
                return "sha256:" + digest.repeat(64);
            }

            @Override
            public String fingerprintOf(Collection<String> packNames) {
                return "sha256:" + digest.repeat(64);
            }
        };
    }

    private ClosureQueries queries() {
        return queries(true);
    }

    private ClosureQueries queries(boolean withPackStore) {
        PackDescriptorRepository repository = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return descriptors.get(pack.value());
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.ofNullable(descriptors.get(pack.value()));
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException("the closure never writes");
            }
        };
        ConsistentRead read = new ConsistentRead(sources());
        PackCatalogRepository catalog = () -> List.copyOf(configured);
        OfferingReadService offering =
                new OfferingReadService(catalog, repository, pack -> facts.get(pack.value()), read);
        PackRepository packs = pack -> stored.get(pack.value());
        return withPackStore
                ? new ClosureQueries(offering, catalog, read, packs)
                : new ClosureQueries(offering, catalog, read);
    }

    private static InForceStates selecting(ClosureView view, Urn... urns) {
        return view.inForce(InForceRequest.selecting(Set.of(urns)));
    }

    /** built-in holds Q, which requires D; project holds P, which specializes from the built-in Q. */
    private void qualifiedParentInAnotherPack() {
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                List.of(),
                List.of("Q"),
                List.of("directive:D"),
                edge("agent_profile:Q", RelationType.REQUIRES, "directive:D"));
        pack(
                "project",
                PackLevel.PROJECT,
                List.of("builtin"),
                List.of("P"),
                List.of(),
                edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "builtin::agent_profile:Q"));
    }

    private static List<String> listed(ProfileGovernance.Governance governance) {
        return governance.entries().stream()
                .map(entry -> entry.urn().toString())
                .toList();
    }

    // ---- activation-scoped -----------------------------------------------------------------------------------------

    @Test
    void theActivationScopedWalkResolvesAParentQualifiedWithAnotherPackAndNamesTheWinnerOfEachEntry() {
        qualifiedParentInAnotherPack();
        ClosureView view = queries().allConfigured();

        ProfileGovernance.Governance governance = view.governance(P, 1, selecting(view, P, Q, D));

        assertThat(governance.profile()).isEqualTo(P);
        assertThat(governance.entries())
                .extracting(
                        ProfileGovernance.Entry::urn,
                        ProfileGovernance.Entry::step,
                        ProfileGovernance.Entry::via,
                        entry -> entry.holder().orElseThrow().packId().value(),
                        entry -> entry.holder().orElseThrow().level())
                .containsExactly(
                        tuple(Q, GovernanceWalk.Step.LINEAGE, P, "builtin", PackLevel.BUILT_IN),
                        tuple(D, GovernanceWalk.Step.REQUIRES, Q, "builtin", PackLevel.BUILT_IN));
    }

    @Test
    void anAncestorThePredicateRefusesIsNotListedAndSeedsNothingUnderAnActivation() {
        qualifiedParentInAnotherPack();
        ClosureView view = queries().allConfigured();

        ProfileGovernance.Governance governance = view.governance(P, 1, selecting(view, P, D));

        assertThat(listed(governance)).isEmpty();
    }

    @Test
    void aProfileOutOfForceIsStillWalkedUnderAnActivation() {
        qualifiedParentInAnotherPack();
        ClosureView view = queries().allConfigured();

        ProfileGovernance.Governance governance = view.governance(P, 1, selecting(view, Q, D));

        assertThat(listed(governance)).containsExactly("agent_profile:Q", "directive:D");
    }

    @Test
    void whereAHigherLevelPackHoldsACopyOfTheProfileItsEdgesAreTheOnesWalked() {
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                List.of(),
                List.of("P"),
                List.of("directive:Old", "directive:New"),
                edge("agent_profile:P", RelationType.REQUIRES, "directive:Old"));
        pack(
                "project",
                PackLevel.PROJECT,
                List.of("builtin"),
                List.of("P"),
                List.of(),
                edge("agent_profile:P", RelationType.REQUIRES, "builtin::directive:New"));
        ClosureView view = queries().allConfigured();
        Urn old = urn("directive:Old");
        Urn fresh = urn("directive:New");

        ProfileGovernance.Governance governance = view.governance(P, 0, selecting(view, P, old, fresh));
        ProfileGovernance.Governance pack = view.packGovernance(P, 0);

        assertThat(listed(governance)).containsExactly("directive:New");
        assertThat(governance.entries().get(0).holder().orElseThrow().packId())
                .isEqualTo(new PackIdentifier("builtin"));
        assertThat(listed(pack))
                .as("the pack-scoped walk walks the merged view too")
                .containsExactly("directive:New");
    }

    // ---- the winner read -------------------------------------------------------------------------------------------

    @Test
    void theWinnerReadAnswersTheWinningEntityItsHolderAndForAProfileTheGovernance() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), List.of("P"), List.of("directive:D"));
        pack(
                "project",
                PackLevel.PROJECT,
                List.of("builtin"),
                List.of("P"),
                List.of(),
                edge("agent_profile:P", RelationType.REQUIRES, "builtin::directive:D"));
        ClosureView view = queries().allConfigured();

        Optional<ClosureView.EffectiveArtifactView> found =
                view.effectiveArtifact(ArtifactKind.AGENT_PROFILE, "P", 1, selecting(view, P, D));

        ClosureView.EffectiveArtifactView winner = found.orElseThrow();
        CharterArtifact artifact = winner.artifact();
        assertThat(artifact).isInstanceOf(AgentProfile.class);
        assertThat(artifact.label()).as("the project's copy wins").isEqualTo("Copy of P in project");
        assertThat(winner.holder().packId()).isEqualTo(new PackIdentifier("project"));
        assertThat(winner.holder().level()).isEqualTo(PackLevel.PROJECT);
        assertThat(winner.governance()).isPresent();
        assertThat(listed(winner.governance().orElseThrow())).containsExactly("directive:D");
    }

    @Test
    void theWinnerReadOfAnotherKindCarriesNoGovernanceAndAnAbsentArtifactIsEmpty() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), List.of("P"), List.of("directive:D"));
        ClosureView view = queries().allConfigured();
        InForceStates states = selecting(view, P, D);

        assertThat(view.effectiveArtifact(ArtifactKind.AGENT_PROFILE, "Missing", 1, states))
                .isEmpty();
        assertThat(view.effectiveArtifact(ArtifactKind.DIRECTIVE, "Absent", 1, states))
                .isEmpty();
        assertThat(view.effectiveArtifact(ArtifactKind.AGENT_PROFILE, "not a valid id!", 1, states))
                .isEmpty();
    }

    @Test
    void theWinnerEntityIsReadAgainstTheDigestOfTheClosureTheViewWasVerifiedAt() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), List.of("P"), List.of());
        ClosureView view = queries().allConfigured();
        digest = "2";

        assertThatThrownBy(() -> view.effectiveArtifact(ArtifactKind.AGENT_PROFILE, "P", 0, selecting(view, P)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aWinnerThePackStoreCannotProduceIsPackUnreadableWhileTheClosureDigestIsUnchanged() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), List.of("P"), List.of());
        stored.put(
                "builtin",
                Pack.builder()
                        .id(new PackIdentifier("builtin"))
                        .descriptor(descriptors.get("builtin"))
                        .build());
        ClosureView view = queries().allConfigured();

        assertThatThrownBy(() -> view.effectiveArtifact(ArtifactKind.AGENT_PROFILE, "P", 0, selecting(view, P)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refusal.getMessage()).contains("builtin").contains("agent_profile:P");
                });
    }

    @Test
    void aViewBuiltWithoutAPackStoreFailsTheWinnerReadAsAWiringFaultNotAsAnUnreadablePack() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), List.of("P"), List.of());
        ClosureView view = queries(false).allConfigured();

        assertThatThrownBy(() -> view.effectiveArtifact(ArtifactKind.AGENT_PROFILE, "P", 0, selecting(view, P)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without a pack store");
    }

    // ---- pack-scoped -----------------------------------------------------------------------------------------------

    @Test
    void thePackScopedWalkResolvesAQualifiedParentAndNamesTheFirstHolderInClosureOrder() {
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                List.of(),
                List.of("Q"),
                List.of("directive:D"),
                edge("agent_profile:Q", RelationType.REQUIRES, "directive:D"));
        pack(
                "project",
                PackLevel.PROJECT,
                List.of("builtin"),
                List.of("P"),
                List.of("directive:D"),
                edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "builtin::agent_profile:Q"));
        ClosureView view = queries().allConfigured();

        ProfileGovernance.Governance governance = view.packGovernance(P, 1);

        assertThat(governance.entries())
                .extracting(
                        ProfileGovernance.Entry::urn,
                        ProfileGovernance.Entry::held,
                        entry -> entry.holder().orElseThrow().packId().value())
                .containsExactly(tuple(Q, true, "builtin"), tuple(D, true, "builtin"));
        assertThat(view.governance(P, 1, selecting(view, P, Q, D)).entries())
                .as("under an activation the holder is the winner: the project's copy of directive D")
                .extracting(entry -> entry.holder().orElseThrow().packId().value())
                .containsExactly("builtin", "project");
    }

    @Test
    void aParentNoPackOfThePackClosureHoldsIsListedNotHeldWithNoHolderAndNothingBelowIt() {
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                List.of(),
                List.of("P"),
                List.of("directive:D"),
                edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "agent_profile:Gone"),
                edge("agent_profile:Gone", RelationType.REQUIRES, "directive:D"));
        ClosureView view = queries().allConfigured();

        ProfileGovernance.Governance governance = view.packGovernance(P, 1);

        assertThat(governance.entries())
                .extracting(
                        ProfileGovernance.Entry::urn, ProfileGovernance.Entry::held, ProfileGovernance.Entry::holder)
                .containsExactly(tuple(urn("agent_profile:Gone"), false, Optional.empty()));
    }

    @Test
    void theProfileServiceWalksTheClosureOfOnePackInsideTheCallersVerifiedReadAndNeverReadsAgain() {
        qualifiedParentInAnotherPack();
        pack("unrelated", PackLevel.ORG, List.of(), List.of("Z"), List.of());
        ConsistentRead callerRead = new ConsistentRead(sources());
        ClosureQueries queries = queries();
        ProfileGovernance service = new ProfileGovernance(queries);
        AtomicInteger runs = new AtomicInteger();

        ConsistentRead.Verified<Optional<ProfileGovernance.Governance>> verified = callerRead.read("stale", () -> {
            runs.incrementAndGet();
            return service.ofPack(new PackIdentifier("project"), "P", 1);
        });

        assertThat(runs).hasValue(1);
        assertThat(verified.value().orElseThrow().entries())
                .extracting(entry -> entry.urn().toString())
                .containsExactly("agent_profile:Q", "directive:D");
        assertThat(whole.get())
                .as("the caller's two digests are the only whole-source digests: the walk opened no read of its own")
                .isEqualTo(2);
    }

    @Test
    void aChangeDuringTheCallersSupplierIsTheCallersStaleInputAndTheWalkIsNotRetried() {
        qualifiedParentInAnotherPack();
        ProfileGovernance service = new ProfileGovernance(queries());
        AtomicInteger runs = new AtomicInteger();
        ConsistentRead callerRead = new ConsistentRead(sources());

        assertThatThrownBy(() -> callerRead.read("stale", () -> {
                    runs.incrementAndGet();
                    Optional<ProfileGovernance.Governance> walked =
                            service.ofPack(new PackIdentifier("project"), "P", 1);
                    digest = "3";
                    return walked;
                }))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
        assertThat(runs).as("no inner retry").hasValue(1);
    }

    @Test
    void aProfileThePackClosureDoesNotHoldIsEmptyAndAClosureThatCannotComposeRefusesAsTheComposeDoes() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), List.of("Q"), List.of());
        pack("broken", PackLevel.ORG, List.of("nowhere"), List.of("P"), List.of());
        ProfileGovernance service = new ProfileGovernance(queries());

        assertThat(service.ofPack(new PackIdentifier("builtin"), "P", 0)).isEmpty();
        assertThatThrownBy(() -> service.ofPack(new PackIdentifier("broken"), "P", 0))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_DEPENDENCY_UNKNOWN));
    }
}
