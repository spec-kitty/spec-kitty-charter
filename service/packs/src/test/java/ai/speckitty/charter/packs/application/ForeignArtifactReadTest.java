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
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The read of a pack's artifact of a kind the generic route does not serve (T019): the item, the pack revision and a
 * profile's pack-scoped governance, all taken in one verified read (fold W5). Every case builds a real closure over
 * in-memory packs, as {@link ClosureViewGovernanceTest} does.
 */
class ForeignArtifactReadTest {

    private static final PackIdentifier PROJECT = new PackIdentifier("project");

    private final List<ConfiguredPack> configured = new ArrayList<>();
    private final Map<String, PackDescriptor> descriptors = new HashMap<>();
    private final Map<String, PackCompositionFacts> facts = new HashMap<>();
    private final Map<String, Pack> stored = new HashMap<>();
    private final AtomicInteger whole = new AtomicInteger();
    private final AtomicInteger revisionReads = new AtomicInteger();
    private String digest = "1";

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

    private static AgentProfile profile(String pack, String id) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(new PackIdentifier(pack))
                .name("Name of " + id)
                .role("implementer")
                .purpose("Purpose of " + id)
                .specialization(new Specialization("Focus", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
    }

    private static Directive directive(String pack, String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(new PackIdentifier(pack))
                .schemaVersion("1.0")
                .title("Title of " + id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private void pack(
            String name,
            PackLevel level,
            List<String> dependsOn,
            List<String> profiles,
            List<String> directives,
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
        List<String> held = Stream.concat(
                        profiles.stream().map(id -> "agent_profile:" + id),
                        directives.stream().map(id -> "directive:" + id))
                .toList();
        facts.put(
                name,
                new PackCompositionFacts(
                        List.of(), held, List.of(), MutabilityPolicySource.empty(), List.of(), List.of(edges)));
        Pack.Builder pack = Pack.builder().id(new PackIdentifier(name)).descriptor(descriptor);
        profiles.forEach(id -> pack.artifact(profile(name, id)));
        directives.forEach(id -> pack.artifact(directive(name, id)));
        stored.put(name, pack.build());
    }

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

    private OfferingReadService offering;

    /** The configured name a request addresses, by that name or by the pack_id the pack declares, as the storage does. */
    private String nameOf(PackIdentifier pack) {
        return configured.stream()
                .map(entry -> entry.id().value())
                .filter(name -> name.equals(pack.value())
                        || descriptors.get(name).packId().equals(pack.value()))
                .findFirst()
                .orElseThrow(() ->
                        new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found."));
    }

    private ForeignArtifactRead service() {
        ConsistentRead read = new ConsistentRead(sources());
        PackDescriptorRepository repository = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.ofNullable(descriptors.get(nameOf(pack)));
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException("a read never writes");
            }
        };
        PackCatalogRepository catalog = () -> List.copyOf(configured);
        offering = new OfferingReadService(catalog, repository, pack -> facts.get(pack.value()), read);
        PackRepository packs = pack -> stored.get(nameOf(pack));
        PackRevisions revisions = pack -> {
            revisionReads.incrementAndGet();
            return PackRevision.compute(List.of(pack.value(), digest));
        };
        ClosureQueries queries = new ClosureQueries(offering, catalog, read, packs, (pack, type) -> Map.of());
        return new ForeignArtifactRead(packs, repository, catalog, read, revisions, new ProfileGovernance(queries));
    }

    /** built-in holds Q, which requires D; project holds P, which specializes from Q and suggests nothing. */
    private void projectOverBuiltIn() {
        pack(
                "builtin",
                PackLevel.BUILT_IN,
                List.of(),
                List.of("Q"),
                List.of("D"),
                edge("agent_profile:Q", RelationType.REQUIRES, "directive:D"));
        pack(
                "project",
                PackLevel.PROJECT,
                List.of("builtin"),
                List.of("P"),
                List.of(),
                edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "builtin::agent_profile:Q"));
    }

    @Test
    void theProfileItsRevisionAndItsGovernanceAreOneVerifiedReadWithTheWholeSourceFingerprint() {
        projectOverBuiltIn();
        ForeignArtifactRead service = service();

        ConsistentRead.Verified<ForeignArtifactRead.Foreign> verified =
                service.read(PROJECT, ArtifactKind.AGENT_PROFILE, "P", 1);

        assertThat(verified.value().artifact().localId()).isEqualTo("P");
        assertThat(verified.value().artifact().packId()).isEqualTo(PROJECT);
        assertThat(verified.value().revision()).isEqualTo(PackRevision.compute(List.of("project", "1")));
        assertThat(verified.value().governance().orElseThrow().entries())
                .extracting(
                        entry -> entry.urn().toString(), ProfileGovernance.Entry::step, ProfileGovernance.Entry::held)
                .containsExactly(
                        tuple("agent_profile:Q", GovernanceWalk.Step.LINEAGE, true),
                        tuple("directive:D", GovernanceWalk.Step.REQUIRES, true));
        assertThat(verified.fingerprint())
                .as("the whole-source digest the read verified, not the closure's own")
                .isEqualTo("sha256:" + "1".repeat(64));
        assertThat(whole).as("one verified read: two whole-source digests").hasValue(2);
        assertThat(revisionReads)
                .as("the revision is read once, inside the read")
                .hasValue(1);
    }

    @Test
    void theServedKindsAreTheGenericRoutesSoTheApiRingCanTellWhichEntriesLink() {
        projectOverBuiltIn();

        ForeignArtifactRead.Foreign read =
                service().read(PROJECT, ArtifactKind.AGENT_PROFILE, "P", 0).value();

        assertThat(read.servedKinds()).containsExactlyElementsOf(ArtifactReadService.listed());
        assertThat(read.servedKinds()).contains(ArtifactKind.ASSET, ArtifactKind.SKILL);
    }

    @Test
    void aPackAddressedByItsDeclaredPackIdIsWalkedAsTheConfiguredPackItIs() {
        projectOverBuiltIn();
        ForeignArtifactRead service = service();

        ForeignArtifactRead.Foreign read = service.read(
                        new PackIdentifier("id-project"), ArtifactKind.AGENT_PROFILE, "P", 0)
                .value();

        assertThat(read.governance().orElseThrow().entries())
                .extracting(entry -> entry.urn().toString())
                .containsExactly("agent_profile:Q", "directive:D");
    }

    @Test
    void aProfileOnlyTheClosureHoldsIsNotThePacksOwnAndIsArtifactNotFound() {
        projectOverBuiltIn();
        ForeignArtifactRead service = service();

        assertThatThrownBy(() -> service.read(PROJECT, ArtifactKind.AGENT_PROFILE, "Q", 0))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                    assertThat(refusal.getMessage()).isEqualTo("Pack project has no agent_profile Q.");
                });
    }

    @Test
    void aProfileNoPackHoldsIsArtifactNotFound() {
        projectOverBuiltIn();
        ForeignArtifactRead service = service();

        assertThatThrownBy(() -> service.read(PROJECT, ArtifactKind.AGENT_PROFILE, "nobody", 0))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND));
    }

    @Test
    void aPackThatIsNotConfiguredIsPackNotFoundWhateverTheProfile() {
        projectOverBuiltIn();
        ForeignArtifactRead service = service();

        assertThatThrownBy(() -> service.read(new PackIdentifier("nope"), ArtifactKind.AGENT_PROFILE, "P", 0))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void aConfiguredPackWithoutAReadablePackYamlIsPackUnreadableBeforeAnyArtifactIsLooked() {
        projectOverBuiltIn();
        descriptors.remove("project");
        ForeignArtifactRead service = service();

        assertThatThrownBy(() -> service.read(PROJECT, ArtifactKind.AGENT_PROFILE, "P", 0))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void aClosureThatCannotComposeRefusesWithTheCodeTheComposeRefusesWith() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), List.of("Q"), List.of());
        pack("broken", PackLevel.ORG, List.of("nowhere"), List.of("P"), List.of());
        ForeignArtifactRead service = service();
        RefusalCode composeCode = ((CharterRefusal) catchThrowable(() -> offering.composed())).code();

        assertThatThrownBy(() -> service.read(new PackIdentifier("broken"), ArtifactKind.AGENT_PROFILE, "P", 0))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(composeCode));
        assertThat(composeCode).isEqualTo(RefusalCode.PACK_DEPENDENCY_UNKNOWN);
    }

    @Test
    void aChangeOfThePackFilesDuringTheReadIsStaleInput() {
        projectOverBuiltIn();
        ForeignArtifactRead moving = movingWhileLoading();

        assertThatThrownBy(() -> moving.read(PROJECT, ArtifactKind.AGENT_PROFILE, "P", 0))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    /** A read whose pack port moves the digest the moment the pack is loaded. */
    private ForeignArtifactRead movingWhileLoading() {
        ConsistentRead read = new ConsistentRead(sources());
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
        OfferingReadService composing =
                new OfferingReadService(catalog, repository, pack -> facts.get(pack.value()), read);
        PackRepository packs = pack -> {
            Pack found = stored.get(pack.value());
            digest = "2";
            return found;
        };
        ClosureQueries queries = new ClosureQueries(composing, catalog, read, packs, (pack, type) -> Map.of());
        return new ForeignArtifactRead(
                packs,
                repository,
                catalog,
                read,
                pack -> PackRevision.compute(List.of(pack.value())),
                new ProfileGovernance(queries));
    }

    @Test
    void aNegativeDepthIsRefusedBeforeAnythingIsRead() {
        projectOverBuiltIn();
        ForeignArtifactRead service = service();

        assertThatThrownBy(() -> service.read(PROJECT, ArtifactKind.AGENT_PROFILE, "P", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(whole).as("nothing was digested").hasValue(0);
    }

    @Test
    void anArtifactOfAnotherKindIsReadWithItsRevisionAndNoGovernance() {
        pack("builtin", PackLevel.BUILT_IN, List.of(), List.of(), List.of("D"));
        ForeignArtifactRead service = service();

        ForeignArtifactRead.Foreign read = service.read(new PackIdentifier("builtin"), ArtifactKind.DIRECTIVE, "D", 0)
                .value();

        CharterArtifact artifact = read.artifact();
        assertThat(artifact.kind()).isEqualTo(ArtifactKind.DIRECTIVE);
        assertThat(read.governance()).isEmpty();
    }

    private static Throwable catchThrowable(Runnable run) {
        try {
            run.run();
        } catch (RuntimeException thrown) {
            return thrown;
        }
        throw new AssertionError("expected a refusal");
    }
}
