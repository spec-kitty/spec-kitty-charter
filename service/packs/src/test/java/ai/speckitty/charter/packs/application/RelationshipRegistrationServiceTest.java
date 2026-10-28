package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.MissionStepContractIdentifier;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import ai.speckitty.charter.packs.infra.storage.CommitStorePackWriteStore;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.PackCommitStore;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The kind-neutral relationship registration use case over in-memory ports (FR-009/012), and, for the {@code asset:}
 * and {@code template:} endpoints, over the real storage edge. Every refusal is paired with
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

        // Same orientation, new payload: last write wins (US1 scenario 3).
        service.registerRelationship(
                PACK,
                new Relationship(a, b, RelationType.SUGGESTS, Optional.of("second"), Optional.of("because")),
                store.currentRevision(PACK).token());
        assertThat(store.relationships(PACK)).singleElement().satisfies(r -> {
            assertThat(r.type()).isEqualTo(RelationType.SUGGESTS);
            assertThat(r.when()).contains("second");
            assertThat(r.rationale()).contains("because");
        });

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

    /**
     * #190: agent profiles are in the aggregate now, so a relationship from a profile of the pack resolves (it was a
     * conflict while the kind was not loaded), and one from a profile the pack does not hold stays a conflict.
     */
    @Test
    void aRelationshipFromAnAgentProfileOfThePackResolves() {
        store.packs.put(PACK, store.packs.get(PACK).with(profile("java-jenny")));
        Urn jenny = new Urn(NodeKind.of(ArtifactKind.AGENT_PROFILE), "java-jenny");
        Urn ghost = new Urn(NodeKind.of(ArtifactKind.AGENT_PROFILE), "ghost");
        Urn ivan = new Urn(NodeKind.of(ArtifactKind.AGENT_PROFILE), "implementer-ivan");
        int before = store.commits;

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK,
                        Relationship.of(ghost, ivan, RelationType.SPECIALIZES_FROM),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).isEqualTo(before);

        assertThatCode(() -> service.registerRelationship(
                        PACK,
                        Relationship.of(jenny, ivan, RelationType.SPECIALIZES_FROM),
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

    // ---- FR-012 / R6: asset: and template: URNs as endpoints, through the real store (not the fake) ----------------

    private static final Urn LOGO = new Urn(NodeKind.ASSET, "logo");
    private static final Urn MINUTES = new Urn(NodeKind.TEMPLATE, "writing/minutes.md");

    /**
     * Over the real {@code DomainRepositoryService} and commit store on a temp pack holding a sidecar, its payload and
     * a template: an edge from the held asset to the template, one from the template, and one to the asset register
     * and read back from the shard; the real ADR-0012 gate (every target open) accepts the new kinds.
     */
    @Test
    void relationshipsFromAndToAHeldAssetAndTemplateRegisterThroughTheRealStore(@TempDir Path host) throws IOException {
        RealPack real = RealPack.over(host, new PackMutabilityPolicy());
        Pack loaded = real.repository.load(PACK);
        assertThat(loaded.assets()).extracting(Asset::urn).containsExactly(LOGO);
        assertThat(loaded.templates()).extracting(Template::urn).containsExactly(MINUTES);
        real.directives.registerDirective(
                PACK,
                directive("GUARD"),
                List.of(),
                false,
                real.writeStore.currentRevision(PACK).token());
        Urn guard = new Urn(NodeKind.DIRECTIVE, "GUARD");
        Relationship assetToTemplate = Relationship.of(LOGO, MINUTES, RelationType.REQUIRES);
        Relationship templateToDirective = Relationship.of(MINUTES, guard, RelationType.SUGGESTS);
        Relationship directiveToAsset = Relationship.of(guard, LOGO, RelationType.REQUIRES);

        for (Relationship edge : List.of(assetToTemplate, templateToDirective, directiveToAsset)) {
            PackRevision before = real.writeStore.currentRevision(PACK);
            RelationshipRegistrationService.Registered registered =
                    real.relationships.registerRelationship(PACK, edge, before.token());
            assertThat(registered.created()).as("%s is new", edge).isTrue();
            assertThat(registered.revision())
                    .as("%s advanced the revision", edge)
                    .isNotEqualTo(before);
        }

        assertThat(real.writeStore.registeredRelationships(PACK))
                .containsExactlyInAnyOrder(assetToTemplate, templateToDirective, directiveToAsset);
        assertThat(real.repository.load(PACK).assetFiles())
                .as("the relationship commits kept the asset file")
                .extracting(AssetFile::path)
                .containsExactly("debrief/logo.png");
    }

    /** An {@code asset:} URN the pack does not hold is refused as before; the held one is the same-fixture control. */
    @Test
    void anAssetTheRealPackDoesNotHoldIsRefusedWhileTheHeldOneRegisters(@TempDir Path host) throws IOException {
        RealPack real = RealPack.over(host, new PackMutabilityPolicy());
        PackRevision before = real.writeStore.currentRevision(PACK);

        assertThatThrownBy(() -> real.relationships.registerRelationship(
                        PACK,
                        Relationship.of(new Urn(NodeKind.ASSET, "ghost"), MINUTES, RelationType.REQUIRES),
                        before.token()))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT);
                    assertThat(r.getMessage()).contains("asset:ghost");
                });
        assertThat(real.writeStore.currentRevision(PACK))
                .as("the refusal committed nothing")
                .isEqualTo(before);
        assertThat(real.writeStore.registeredRelationships(PACK)).isEmpty();

        assertThatCode(() -> real.relationships.registerRelationship(
                        PACK, Relationship.of(LOGO, MINUTES, RelationType.REQUIRES), before.token()))
                .doesNotThrowAnyException();
        assertThat(real.writeStore.registeredRelationships(PACK))
                .containsExactly(Relationship.of(LOGO, MINUTES, RelationType.REQUIRES));
    }

    /**
     * The mutability gate resolves the new kinds by their kind: a source locking {@code asset} refuses the edge from
     * the asset, while the template edge on the same fixture and gate registers.
     */
    @Test
    void aMutabilityPolicyLockingAssetsRefusesTheAssetEdgeWhileTheTemplateEdgeRegisters(@TempDir Path host)
            throws IOException {
        RealPack real = RealPack.over(
                host,
                new PackMutabilityPolicy(MutabilityPolicySource.builder()
                        .kindDefault(NodeKind.ASSET, MutabilityLevel.LOCKED)
                        .build()));
        PackRevision before = real.writeStore.currentRevision(PACK);

        assertThatThrownBy(() -> real.relationships.registerRelationship(
                        PACK, Relationship.of(LOGO, MINUTES, RelationType.REQUIRES), before.token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        r -> assertThat(r.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION));
        assertThat(real.writeStore.currentRevision(PACK)).isEqualTo(before);

        assertThatCode(() -> real.relationships.registerRelationship(
                        PACK, Relationship.of(MINUTES, LOGO, RelationType.REQUIRES), before.token()))
                .doesNotThrowAnyException();
        assertThat(real.writeStore.registeredRelationships(PACK))
                .containsExactly(Relationship.of(MINUTES, LOGO, RelationType.REQUIRES));
    }

    /** The production wiring over a temp pack named {@code pack-a} holding a sidecar, its payload and a template. */
    private record RealPack(
            DomainRepositoryService repository,
            CommitStorePackWriteStore writeStore,
            DirectiveRegistrationService directives,
            RelationshipRegistrationService relationships) {

        static RealPack over(Path host, WritePolicyGate policy) throws IOException {
            Path pack = host.resolve(PACK.value());
            write(
                    pack,
                    "pack.yaml",
                    "pack_id: 01M5DEH8RELATIONSHIPASSET0\npack_version: 0.1.0\nname: pack-a\npack_level: org\n");
            write(pack, "assets/debrief/logo.png.asset.yaml", "id: logo\nmime: image/png\npath: debrief/logo.png\n");
            Files.write(
                    pack.resolve("assets/debrief/logo.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, '\r', '\n'});
            write(pack, "templates/writing/minutes.md", "# Minutes\n");
            StorageRootLocations locations = new StorageRootLocations(host);
            YamlSerializationStrategy strategy = new YamlSerializationStrategy();
            DomainRepositoryService repository = new DomainRepositoryService(locations, strategy);
            CommitStorePackWriteStore writeStore =
                    new CommitStorePackWriteStore(locations, strategy, new PackCommitStore(locations, strategy));
            return new RealPack(
                    repository,
                    writeStore,
                    new DirectiveRegistrationService(repository, writeStore, policy),
                    new RelationshipRegistrationService(repository, writeStore, policy));
        }

        private static void write(Path pack, String relative, String text) throws IOException {
            Path file = pack.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        }
    }

    // ---- fixtures and fakes --------------------------------------------------------------------------------------

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                PACK.value(), "1.0.0", Optional.empty(), PACK.value(), Optional.empty(), Optional.empty());
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

    private static AgentProfile profile(String id) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(PACK)
                .name(id)
                .role("implementer")
                .purpose("Purpose of " + id)
                .specialization(new Specialization("Focus", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
    }

    private void seedDirective(String id) {
        Pack current = store.packs.get(PACK);
        Pack.Builder builder = Pack.builder().id(PACK).descriptor(descriptor());
        current.directives().forEach(builder::directive);
        builder.directive(directive(id));
        store.packs.put(PACK, builder.build());
    }

    /**
     * #191: mission step contracts are in the aggregate now, so a relationship from a contract of the pack resolves (it
     * was a conflict while the kind was not loaded), and one from a contract the pack does not hold stays a conflict.
     */
    @Test
    void aRelationshipFromAMissionStepContractOfThePackResolves() {
        store.packs.put(PACK, store.packs.get(PACK).with(contract("implement")));
        Urn implement = new Urn(NodeKind.of(ArtifactKind.MISSION_STEP_CONTRACT), "implement");
        Urn ghost = new Urn(NodeKind.of(ArtifactKind.MISSION_STEP_CONTRACT), "ghost");
        Urn external = new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_030");
        int before = store.commits;

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK,
                        Relationship.of(ghost, external, RelationType.REQUIRES),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).isEqualTo(before);

        assertThatCode(() -> service.registerRelationship(
                        PACK,
                        Relationship.of(implement, external, RelationType.REQUIRES),
                        store.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(before + 1);
    }

    private static MissionStepContract contract(String id) {
        return MissionStepContract.builder()
                .id(new MissionStepContractIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .action(id)
                .mission("software-dev")
                .step(new ContractStep(
                        "bootstrap",
                        "Load charter context",
                        Optional.empty(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty()))
                .build();
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
