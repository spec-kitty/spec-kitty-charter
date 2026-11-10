package ai.speckitty.charter.profiles.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
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
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackRevisions;
import ai.speckitty.charter.packs.application.ProfileGovernance;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.profiles.application.GovernanceEntry;
import ai.speckitty.charter.profiles.application.ProfileFixtures;
import ai.speckitty.charter.profiles.application.ProfileRead;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The adapter over the packs slice, through the real packs reads on in-memory packs: the profile, the revision, the
 * fingerprint and the governance arrive as the profiles slice's own types, and the packs refusals arrive unchanged.
 */
class PacksProfileSourceTest {

    private static final PackIdentifier PROJECT = new PackIdentifier("project");

    private final List<ConfiguredPack> configured = new ArrayList<>();
    private final Map<String, PackDescriptor> descriptors = new HashMap<>();
    private final Map<String, PackCompositionFacts> facts = new HashMap<>();
    private final Map<String, Pack> stored = new HashMap<>();

    private static Relationship edge(
            String from, RelationType type, String to, Optional<String> when, Optional<String> reason) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return new Relationship(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                type,
                when,
                reason);
    }

    private static Relationship edge(String from, RelationType type, String to) {
        return edge(from, type, to, Optional.empty(), Optional.empty());
    }

    private void pack(
            String name,
            PackLevel level,
            List<String> dependsOn,
            List<String> profiles,
            List<String> held,
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
        facts.put(
                name,
                new PackCompositionFacts(
                        List.of(),
                        Stream.concat(profiles.stream().map(id -> "agent_profile:" + id), held.stream())
                                .toList(),
                        List.of(),
                        MutabilityPolicySource.empty(),
                        List.of(),
                        List.of(edges)));
        Pack.Builder pack = Pack.builder().id(new PackIdentifier(name)).descriptor(descriptor);
        profiles.forEach(id -> pack.artifact(ProfileFixtures.leanProfile(name, id)));
        stored.put(name, pack.build());
    }

    private PacksProfileSource source() {
        PackSources sources = new PackSources() {
            @Override
            public String fingerprint() {
                return "sha256:" + "1".repeat(64);
            }

            @Override
            public String fingerprintOf(Collection<String> packNames) {
                return "sha256:" + "2".repeat(64);
            }
        };
        ConsistentRead read = new ConsistentRead(sources);
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
                throw new UnsupportedOperationException("a read never writes");
            }
        };
        PackCatalogRepository catalog = () -> List.copyOf(configured);
        OfferingReadService offering =
                new OfferingReadService(catalog, repository, pack -> facts.get(pack.value()), read);
        PackRepository packs = pack -> stored.get(pack.value());
        PackRevisions revisions = pack -> PackRevision.compute(List.of(pack.value()));
        ClosureQueries queries = new ClosureQueries(offering, catalog, read, packs);
        return new PacksProfileSource(
                new ForeignArtifactRead(packs, repository, catalog, read, revisions, new ProfileGovernance(queries)));
    }

    /** One profile that takes every step: a parent, a requirement, a suggestion and what it requires, a vocabulary. */
    private void everyStep() {
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                List.of(),
                List.of("Q"),
                List.of("directive:D", "directive:S", "directive:R", "glossary_pack:G"),
                edge("directive:S", RelationType.REQUIRES, "directive:R"));
        pack(
                "project",
                PackLevel.PROJECT,
                List.of("builtin"),
                List.of("P"),
                List.of(),
                edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "builtin::agent_profile:Q"),
                edge("agent_profile:P", RelationType.REQUIRES, "builtin::directive:D"),
                edge(
                        "agent_profile:P",
                        RelationType.SUGGESTS,
                        "builtin::directive:S",
                        Optional.of("when tidying"),
                        Optional.of("the hub applies")),
                edge("agent_profile:P", RelationType.VOCABULARY, "builtin::glossary_pack:G"));
    }

    @Test
    void theProfileTheRevisionAndTheVerifiedWholeSourceFingerprintArriveAsTheProfilesSlicesOwn() {
        everyStep();

        ProfileRead read = source().read(PROJECT, "P", 1);

        assertThat(read.profile().localId()).isEqualTo("P");
        assertThat(read.profile().packId()).isEqualTo(PROJECT);
        assertThat(read.revision())
                .isEqualTo(PackRevision.compute(List.of("project")).token());
        assertThat(read.sourceFingerprint())
                .as("the digest the verified read agreed on, not the closure's own")
                .isEqualTo("sha256:" + "1".repeat(64));
        assertThat(read.servedKinds()).contains(ArtifactKind.DIRECTIVE, ArtifactKind.TACTIC);
    }

    @Test
    void everyStepOfTheWalkIsMappedToTheProfilesSlicesOwnStepWithItsParentAndHops() {
        everyStep();

        ProfileRead read = source().read(PROJECT, "P", 1);

        assertThat(read.governance())
                .extracting(
                        entry -> entry.urn().toString(),
                        GovernanceEntry::step,
                        entry -> entry.via().toString(),
                        GovernanceEntry::hops)
                .containsExactly(
                        tuple("agent_profile:Q", GovernanceEntry.Step.LINEAGE, "agent_profile:P", 1),
                        tuple("directive:D", GovernanceEntry.Step.REQUIRES, "agent_profile:P", 1),
                        tuple("directive:R", GovernanceEntry.Step.REQUIRES_OF_SUGGESTED, "directive:S", 2),
                        tuple("directive:S", GovernanceEntry.Step.SUGGESTS, "agent_profile:P", 1),
                        tuple("glossary_pack:G", GovernanceEntry.Step.VOCABULARY, "agent_profile:P", 1));
    }

    @Test
    void aSuggestsEntryCarriesTheConditionAndTheReasonOfItsEdgeAndNoOtherEntryDoes() {
        everyStep();

        ProfileRead read = source().read(PROJECT, "P", 1);

        assertThat(read.governance())
                .filteredOn(entry -> entry.suggestion().isPresent())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.urn().toString()).isEqualTo("directive:S");
                    assertThat(entry.suggestion().orElseThrow().when()).contains("when tidying");
                    assertThat(entry.suggestion().orElseThrow().reason()).contains("the hub applies");
                });
    }

    @Test
    void theDepthIsTheNumberOfSuggestsHopsTheWalkFollows() {
        everyStep();

        ProfileRead read = source().read(PROJECT, "P", 0);

        assertThat(read.governance())
                .extracting(entry -> entry.urn().toString())
                .containsExactly("agent_profile:Q", "directive:D", "glossary_pack:G");
    }

    @Test
    void eachHeldEntryNamesTheConfiguredPackThatHoldsIt() {
        everyStep();

        ProfileRead read = source().read(PROJECT, "P", 0);

        assertThat(read.governance())
                .extracting(entry -> entry.holder().orElseThrow().value(), GovernanceEntry::held)
                .containsOnly(tuple("builtin", true));
    }

    @Test
    void aParentNoPackOfTheClosureHoldsIsNotHeldAndNamesNoPack() {
        pack(
                "project",
                PackLevel.PROJECT,
                List.of(),
                List.of("P"),
                List.of(),
                edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "agent_profile:Gone"));

        ProfileRead read = source().read(PROJECT, "P", 0);

        assertThat(read.governance()).singleElement().satisfies(entry -> {
            assertThat(entry.urn().toString()).isEqualTo("agent_profile:Gone");
            assertThat(entry.held()).isFalse();
            assertThat(entry.holder()).isEmpty();
        });
    }

    @Test
    void theRefusalsOfThePacksSliceAreTheAdaptersRefusalsUnchanged() {
        everyStep();
        PacksProfileSource source = source();

        assertThatThrownBy(() -> source.read(PROJECT, "nobody", 0))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
        assertThatThrownBy(() -> source.read(new PackIdentifier("builtin"), "P", 0))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
    }

    @Test
    void theSourceIsRequiredToHaveTheRead() {
        assertThatThrownBy(() -> new PacksProfileSource(null)).isInstanceOf(NullPointerException.class);
    }
}
