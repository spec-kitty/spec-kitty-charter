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
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.AssetIdentifier;
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
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.PromptFragment;
import ai.speckitty.charter.packs.domain.StepEnhancement;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The kind-neutral relationship registration use case over in-memory ports (FR-009/012), and, for the {@code asset:}
 * and {@code template:} endpoints, over the real storage edge. Every refusal is paired with
 * a same-fixture positive control (SC-003, DIRECTIVE_041): a refused write commits nothing, and the matching accepted
 * write proves the fixture itself is sound.
 */
class RelationshipRegistrationServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private final FakeStore store = new FakeStore();
    private final RelationshipRegistrationService service = new RelationshipRegistrationService(
            store, store, new PermissiveWritePolicyGate(), SinglePackCompose.check(PACK, () -> store.packs.get(PACK)));

    // ---- FR-009: relationship upsert idempotent on the unordered pair, reverse replaces -------------------------

    @Test
    void aRelationshipIsIdempotentOnThePairAndTheReverseReplaces() {
        seedDirective("DIR_A");
        seedDirective("DIR_B");
        Urn a = new Urn(NodeKind.DIRECTIVE, "DIR_A");
        Urn b = new Urn(NodeKind.DIRECTIVE, "DIR_B");
        String etag = store.currentRevision(PACK).token();

        service.registerRelationship(PACK, RelationshipDraft.of(Relationship.of(a, b, RelationType.REQUIRES)), etag);
        service.registerRelationship(
                PACK,
                RelationshipDraft.of(Relationship.of(a, b, RelationType.REQUIRES)),
                store.currentRevision(PACK).token());
        assertThat(store.relationships(PACK))
                .as("re-registering the same pair keeps one edge")
                .hasSize(1);

        // Same orientation, new payload: last write wins (US1 scenario 3).
        service.registerRelationship(
                PACK,
                RelationshipDraft.of(
                        Relationship.of(a, b, RelationType.SUGGESTS, Optional.of("second"), Optional.of("because"))),
                store.currentRevision(PACK).token());
        assertThat(store.relationships(PACK)).singleElement().satisfies(r -> {
            assertThat(r.type()).isEqualTo(RelationType.SUGGESTS);
            assertThat(r.when()).contains("second");
            assertThat(r.rationale()).contains("because");
        });

        // The reverse orientation replaces rather than adds (unordered-pair identity).
        RelationshipRegistrationService.Registered reverse = service.registerRelationship(
                PACK,
                RelationshipDraft.of(Relationship.of(b, a, RelationType.SUGGESTS)),
                store.currentRevision(PACK).token());
        assertThat(reverse.created()).isFalse();
        assertThat(store.relationships(PACK)).hasSize(1);
        assertThat(store.relationships(PACK).getFirst().type()).isEqualTo(RelationType.SUGGESTS);
    }

    /**
     * A target the compose cannot resolve is refused before anything is written: the next read of the offering would
     * refuse the pack that holds the edge. The accepted bare external target of the tests around it is the control.
     */
    @Test
    void aTargetQualifiedToAPackTheComposeDoesNotHoldIsRefusedAndChangesNothing() {
        seedDirective("DIR_A");
        Urn from = new Urn(NodeKind.DIRECTIVE, "DIR_A");
        Relationship toUnknownPack = Relationship.of(
                from,
                Optional.empty(),
                new Urn(NodeKind.PROCEDURE, "p"),
                Optional.of("ghost"),
                RelationType.REQUIRES,
                Optional.empty(),
                Optional.empty());
        int before = store.commits;

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK,
                        RelationshipDraft.of(toUnknownPack),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.PACK_REFERENCE_PACK_MISSING);
                    assertThat(r.packs())
                            .extracting("packId", "name")
                            .containsExactly(org.assertj.core.groups.Tuple.tuple("pack-a", "pack-a"));
                });
        assertThat(store.commits).isEqualTo(before);
        assertThat(store.relationships(PACK)).isEmpty();
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
                        RelationshipDraft.of(Relationship.of(unresolvable, external, RelationType.REQUIRES)),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).isEqualTo(before);

        // from resolves, to is external (allowed): accepted.
        assertThatCode(() -> service.registerRelationship(
                        PACK,
                        RelationshipDraft.of(Relationship.of(resolvable, external, RelationType.REQUIRES)),
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
                        RelationshipDraft.of(Relationship.of(ghost, ivan, RelationType.SPECIALIZES_FROM)),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).isEqualTo(before);

        assertThatCode(() -> service.registerRelationship(
                        PACK,
                        RelationshipDraft.of(Relationship.of(jenny, ivan, RelationType.SPECIALIZES_FROM)),
                        store.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
        assertThat(store.commits).isEqualTo(before + 1);
    }

    // ---- FR-012: optimistic concurrency, stale refused, fresh accepted, missing refused on sub-resource ---------

    @Test
    void aStaleIfMatchIsRefusedWhileAFreshOneSucceeds() {
        seedDirective("DIR_A");
        Relationship edge = Relationship.of(
                new Urn(NodeKind.DIRECTIVE, "DIR_A"), new Urn(NodeKind.PROCEDURE, "p"), RelationType.REQUIRES);

        assertThatThrownBy(() ->
                        service.registerRelationship(PACK, RelationshipDraft.of(edge), "sha256:" + "0".repeat(64)))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));

        assertThatCode(() -> service.registerRelationship(
                        PACK,
                        RelationshipDraft.of(edge),
                        store.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
    }

    @Test
    void aRelationshipWriteWithoutAPreconditionIsRefusedPreconditionRequired() {
        seedDirective("DIR_A");
        Relationship edge = Relationship.of(
                new Urn(NodeKind.DIRECTIVE, "DIR_A"), new Urn(NodeKind.PROCEDURE, "p"), RelationType.REQUIRES);
        assertThatThrownBy(() -> service.registerRelationship(PACK, RelationshipDraft.of(edge), null))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.MISSING));
    }

    // ---- FR-012 / R6: asset: and template: URNs as endpoints, through the real store (not the fake) ----------------

    private static final Urn LOGO = new Urn(NodeKind.ASSET, "logo");
    private static final Urn BANNER = new Urn(NodeKind.ASSET, "banner");
    private static final Urn MINUTES = new Urn(NodeKind.TEMPLATE, "writing/minutes.md");

    /**
     * Over the real {@code DomainRepositoryService} and commit store on a temp pack holding two sidecars, their payloads
     * and a template: an edge from a held asset to the other, one from a directive to the template, and one to an asset
     * register and read back from the shard; the real ADR-0012 gate (every target open) accepts the new kinds. A
     * template is the source of no relation, and an asset requires only an asset (ADR-0064 decision 13).
     */
    @Test
    void relationshipsFromAndToAHeldAssetAndToATemplateRegisterThroughTheRealStore(@TempDir Path host)
            throws IOException {
        RealPack real = RealPack.over(host, new PackMutabilityPolicy());
        Pack loaded = real.repository.load(PACK);
        assertThat(loaded.assets()).extracting(Asset::urn).containsExactlyInAnyOrder(BANNER, LOGO);
        assertThat(loaded.templates()).extracting(Template::urn).containsExactly(MINUTES);
        real.directives.registerDirective(
                PACK,
                directive("GUARD"),
                false,
                real.writeStore.currentRevision(PACK).token());
        Urn guard = new Urn(NodeKind.DIRECTIVE, "GUARD");
        Relationship assetToAsset = Relationship.of(LOGO, BANNER, RelationType.REQUIRES);
        Relationship directiveToTemplate = Relationship.of(guard, MINUTES, RelationType.SUGGESTS);
        Relationship directiveToAsset = Relationship.of(guard, LOGO, RelationType.REQUIRES);

        for (Relationship edge : List.of(assetToAsset, directiveToTemplate, directiveToAsset)) {
            PackRevision before = real.writeStore.currentRevision(PACK);
            RelationshipRegistrationService.Registered registered =
                    real.relationships.registerRelationship(PACK, RelationshipDraft.of(edge), before.token());
            assertThat(registered.created()).as("%s is new", edge).isTrue();
            assertThat(registered.revision())
                    .as("%s advanced the revision", edge)
                    .isNotEqualTo(before);
        }

        assertThat(real.writeStore.registeredRelationships(PACK))
                .containsExactlyInAnyOrder(assetToAsset, directiveToTemplate, directiveToAsset);
        assertThat(real.repository.load(PACK).assetFiles())
                .as("the relationship commits kept the asset files")
                .extracting(AssetFile::path)
                .containsExactlyInAnyOrder("debrief/banner.png", "debrief/logo.png");
    }

    /** An {@code asset:} URN the pack does not hold is refused as before; the held one is the same-fixture control. */
    @Test
    void anAssetTheRealPackDoesNotHoldIsRefusedWhileTheHeldOneRegisters(@TempDir Path host) throws IOException {
        RealPack real = RealPack.over(host, new PackMutabilityPolicy());
        PackRevision before = real.writeStore.currentRevision(PACK);

        assertThatThrownBy(() -> real.relationships.registerRelationship(
                        PACK,
                        RelationshipDraft.of(
                                Relationship.of(new Urn(NodeKind.ASSET, "ghost"), BANNER, RelationType.REQUIRES)),
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
                        PACK,
                        RelationshipDraft.of(Relationship.of(LOGO, BANNER, RelationType.REQUIRES)),
                        before.token()))
                .doesNotThrowAnyException();
        assertThat(real.writeStore.registeredRelationships(PACK))
                .containsExactly(Relationship.of(LOGO, BANNER, RelationType.REQUIRES));
    }

    /**
     * The mutability gate resolves the new kinds by their kind: a source locking {@code asset} refuses the edge from
     * the asset, while an edge from a directive to the asset on the same fixture and gate registers (a template is the
     * source of no relation, ADR-0064 decision 13).
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
                        PACK,
                        RelationshipDraft.of(Relationship.of(LOGO, BANNER, RelationType.REQUIRES)),
                        before.token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        r -> assertThat(r.code()).isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION));
        assertThat(real.writeStore.currentRevision(PACK)).isEqualTo(before);

        real.directives.registerDirective(
                PACK,
                directive("GUARD"),
                false,
                real.writeStore.currentRevision(PACK).token());
        Relationship directiveToAsset =
                Relationship.of(new Urn(NodeKind.DIRECTIVE, "GUARD"), LOGO, RelationType.REQUIRES);
        assertThatCode(() -> real.relationships.registerRelationship(
                        PACK,
                        RelationshipDraft.of(directiveToAsset),
                        real.writeStore.currentRevision(PACK).token()))
                .doesNotThrowAnyException();
        assertThat(real.writeStore.registeredRelationships(PACK)).containsExactly(directiveToAsset);
    }

    // ---- FR-014: an overrides edge comes from an activatable artifact (#447, option 1) ------------------------------

    /**
     * An {@code overrides} edge from the held template, and one from the held asset, is refused {@code 422
     * validation_failed} with the violation {@code overrides_source_not_activatable} at {@code /from}, and nothing is
     * written; the same edge from a held directive, the twin, registers on the same fixture.
     */
    @Test
    void anOverridesEdgeFromATemplateOrAnAssetIsRefusedAndTheSameEdgeFromADirectiveRegisters(@TempDir Path host)
            throws IOException {
        RealPack real = RealPack.over(host, new PackMutabilityPolicy());
        real.directives.registerDirective(
                PACK,
                directive("T"),
                false,
                real.writeStore.currentRevision(PACK).token());
        real.directives.registerDirective(
                PACK,
                directive("E"),
                false,
                real.writeStore.currentRevision(PACK).token());
        Urn target = new Urn(NodeKind.DIRECTIVE, "T");
        PackRevision before = real.writeStore.currentRevision(PACK);

        for (Urn source : List.of(MINUTES, LOGO)) {
            assertThatThrownBy(() -> real.relationships.registerRelationship(
                            PACK, draft(source, target, RelationType.OVERRIDES), before.token()))
                    .as("%s overrides %s", source, target)
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                        assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                            assertThat(violation.pointer()).isEqualTo("/from");
                            assertThat(violation.code()).isEqualTo("overrides_source_not_activatable");
                            assertThat(violation.detail()).contains(source.toString());
                        });
                    });
            assertThat(real.writeStore.currentRevision(PACK))
                    .as("the refusal committed nothing")
                    .isEqualTo(before);
            assertThat(real.writeStore.registeredRelationships(PACK)).isEmpty();
        }

        Relationship twin = Relationship.of(new Urn(NodeKind.DIRECTIVE, "E"), target, RelationType.OVERRIDES);
        assertThatCode(() -> real.relationships.registerRelationship(PACK, RelationshipDraft.of(twin), before.token()))
                .doesNotThrowAnyException();
        assertThat(real.writeStore.registeredRelationships(PACK)).containsExactly(twin);
    }

    /**
     * The load meets an {@code overrides} edge from a template in a graph document of a stored pack: a violation of the
     * pack at the document, so a composing read refuses it and a registration lists it; the same document with the
     * edge from a directive loads clean.
     */
    @Test
    void aStoredOverridesEdgeFromATemplateIsALoadViolationAtItsGraphDocument(@TempDir Path host) throws IOException {
        RealPack real = RealPack.over(host, new PackMutabilityPolicy());
        Path shard = host.resolve(PACK.value()).resolve("overrides.graph.yaml");
        String document = """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - source: %s
                  target: directive:T
                  relation: overrides
                """;
        Files.writeString(shard, document.formatted("template:writing/minutes.md"), StandardCharsets.UTF_8);

        assertThat(real.repository.load(PACK).violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo("overrides.graph.yaml");
            assertThat(violation.kind()).isEqualTo("overrides_source_not_activatable");
            assertThat(violation.reason()).contains("template:writing/minutes.md");
        });

        Files.writeString(shard, document.formatted("asset:logo"), StandardCharsets.UTF_8);
        assertThat(real.repository.load(PACK).violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.kind()).isEqualTo("overrides_source_not_activatable"));

        Files.writeString(shard, document.formatted("directive:E"), StandardCharsets.UTF_8);
        assertThat(real.repository.load(PACK).violations()).isEmpty();
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
                    "pack_id: 01M5DEH8RE1AT10NSH1PASSET0\npack_version: 0.1.0\nname: pack-a\npack_level: org\n");
            write(pack, "assets/debrief/logo.png.asset.yaml", "id: logo\nmime: image/png\npath: debrief/logo.png\n");
            Files.write(
                    pack.resolve("assets/debrief/logo.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, '\r', '\n'});
            write(
                    pack,
                    "assets/debrief/banner.png.asset.yaml",
                    "id: banner\nmime: image/png\npath: debrief/banner.png\n");
            Files.write(
                    pack.resolve("assets/debrief/banner.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, '\r', '\n'});
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
                    new RelationshipRegistrationService(repository, writeStore, policy, (source, target) -> {}));
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
        Urn external = new Urn(NodeKind.of(ArtifactKind.MISSION_STEP_CONTRACT), "upstream");
        int before = store.commits;

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK,
                        RelationshipDraft.of(Relationship.of(ghost, external, RelationType.OVERRIDES)),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).isEqualTo(before);

        assertThatCode(() -> service.registerRelationship(
                        PACK,
                        RelationshipDraft.of(Relationship.of(implement, external, RelationType.OVERRIDES)),
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

    // ---- R-REL: the edge rules of a held step enhancement, judged before anything is written
    // ----------------------------

    private static StepEnhancement enhancement(String id) {
        return new StepEnhancement(
                id,
                PACK,
                StepEnhancement.SCHEMA_VERSION,
                List.of(new StepEnhancement.Entry(
                        "spdd:reasons-block",
                        new PromptFragment(
                                id + ".prompt-fragment.md",
                                new MarkdownDocument(Optional.empty(), Optional.empty(), "Fill it.", List.of())))));
    }

    private static final Urn ENHANCEMENT = new Urn(NodeKind.STEP_ENHANCEMENT, "spdd-plan");
    private static final Urn STEP = new Urn(NodeKind.ACTION, "software-dev/plan");
    private static final Urn OTHER_STEP = new Urn(NodeKind.ACTION, "software-dev/build");
    private static final Urn TYPE = new Urn(NodeKind.MISSION_TYPE, "software-dev");

    private void seedEnhancementAndDirectives() {
        Pack pack = store.packs
                .get(PACK)
                .with(enhancement("spdd-plan"))
                .with(directive("A"))
                .with(directive("B"));
        store.packs.put(PACK, pack);
    }

    /** A draft as a write asks for it, whether or not the endpoint table allows its kinds. */
    private static RelationshipDraft draft(Urn from, Urn to, RelationType type) {
        return new RelationshipDraft(from, to, Optional.empty(), type, Optional.empty(), Optional.empty());
    }

    private void assertRefusedByTheEndpointTableAndNothingWritten(RelationshipDraft draft) {
        int before = store.commits;
        List<Relationship> shard = store.relationships(PACK);

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK, draft, store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                        assertThat(violation.pointer()).isEqualTo("/type");
                        assertThat(violation.code()).isEqualTo("relationship_endpoint_not_allowed");
                    });
                });
        assertThat(store.commits).isEqualTo(before);
        assertThat(store.relationships(PACK)).isEqualTo(shard);
    }

    private void assertRefusedAndNothingWritten(RelationshipDraft draft, String named) {
        int before = store.commits;
        List<Relationship> shard = store.relationships(PACK);

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK, draft, store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PROMPT_FRAGMENT_INVALID);
                    assertThat(refusal.getMessage())
                            .contains(ENHANCEMENT.toString())
                            .contains(named);
                });
        assertThat(store.commits).isEqualTo(before);
        assertThat(store.relationships(PACK)).isEqualTo(shard);
    }

    @Test
    void anOverridesEdgeFromAHeldStepEnhancementIsRefusedAndTheShardIsUnchanged() {
        seedEnhancementAndDirectives();

        assertRefusedAndNothingWritten(draft(ENHANCEMENT, TYPE, RelationType.OVERRIDES), "overrides");
    }

    @Test
    void theTwinOfTheOverridesRefusalIsAnOverridesEdgeFromADirectiveAndItIsRegistered() {
        seedEnhancementAndDirectives();
        Urn a = new Urn(NodeKind.DIRECTIVE, "A");
        Urn b = new Urn(NodeKind.DIRECTIVE, "B");
        int before = store.commits;

        service.registerRelationship(
                PACK,
                RelationshipDraft.of(Relationship.of(a, b, RelationType.OVERRIDES)),
                store.currentRevision(PACK).token());

        assertThat(store.commits).isEqualTo(before + 1);
        assertThat(store.relationships(PACK))
                .singleElement()
                .satisfies(r -> assertThat(r.type()).isEqualTo(RelationType.OVERRIDES));
    }

    @Test
    void aSecondEnhancesEdgeFromAHeldStepEnhancementIsRefusedAndTheShardIsUnchanged() {
        seedEnhancementAndDirectives();

        assertRefusedAndNothingWritten(draft(ENHANCEMENT, STEP, RelationType.ENHANCES), "enhances");
    }

    /** The step-enhancement rules come before the endpoint table, so their code names the fault (WP05 order). */
    @Test
    void anEnhancesEdgeToANonStepIsRefusedBySameRule() {
        seedEnhancementAndDirectives();

        assertRefusedAndNothingWritten(
                draft(ENHANCEMENT, new Urn(NodeKind.DIRECTIVE, "A"), RelationType.ENHANCES), "enhances");
    }

    @Test
    void reRegisteringTheEnhancesEdgeTheShardAlreadyHoldsIsAnIdempotentUpsert() {
        seedEnhancementAndDirectives();
        store.edges.put(PACK, new ArrayList<>(List.of(Relationship.of(ENHANCEMENT, STEP, RelationType.ENHANCES))));
        int before = store.commits;

        service.registerRelationship(
                PACK,
                RelationshipDraft.of(Relationship.of(
                        ENHANCEMENT, STEP, RelationType.ENHANCES, Optional.empty(), Optional.of("a new rationale"))),
                store.currentRevision(PACK).token());

        assertThat(store.commits).isEqualTo(before + 1);
        assertThat(store.relationships(PACK))
                .singleElement()
                .satisfies(r -> assertThat(r.rationale()).contains("a new rationale"));
    }

    @Test
    void aSecondEnhancesEdgeIsStillRefusedWhenTheShardHoldsAnother() {
        seedEnhancementAndDirectives();
        store.edges.put(PACK, new ArrayList<>(List.of(Relationship.of(ENHANCEMENT, STEP, RelationType.ENHANCES))));

        assertRefusedAndNothingWritten(draft(ENHANCEMENT, OTHER_STEP, RelationType.ENHANCES), "enhances");
    }

    /** The edge over the enhances pair is judged by the enhancement rules before the endpoint table (WP05 order). */
    @Test
    void aRelationshipOfAnotherTypeOverTheEnhancesPairOfAHeldEnhancementWouldReplaceItAndIsRefused() {
        seedEnhancementAndDirectives();
        store.edges.put(PACK, new ArrayList<>(List.of(Relationship.of(ENHANCEMENT, STEP, RelationType.ENHANCES))));

        assertRefusedAndNothingWritten(draft(ENHANCEMENT, STEP, RelationType.REQUIRES), "leave it with none");
    }

    @Test
    void aSuggestsEdgeOverTheEnhancesPairOfAHeldEnhancementAndItsStepWouldReplaceItAndIsRefused() {
        seedEnhancementAndDirectives();
        store.edges.put(PACK, new ArrayList<>(List.of(Relationship.of(ENHANCEMENT, STEP, RelationType.ENHANCES))));

        assertRefusedAndNothingWritten(draft(ENHANCEMENT, STEP, RelationType.SUGGESTS), "enhances");
    }

    /**
     * The fault order of the write (WP05): the step-enhancement rules, then the source rule of slice C, then the endpoint
     * table. Each row is an edge two rules refuse, and the earlier rule's code names it. No row pairs the step-enhancement
     * rules with slice C's: a step enhancement is activatable, so the source rule never refuses its edge.
     */
    @ParameterizedTest(name = "{0} {2} {1}: {3}")
    @CsvSource({
        // step-enhancement rule before the table (overrides across kinds)
        "step_enhancement:spdd-plan, mission_type:software-dev, overrides, prompt_fragment_invalid",
        // step-enhancement rule before the table (enhances to a non-step)
        "step_enhancement:spdd-plan, directive:A, enhances, prompt_fragment_invalid",
        // slice C's source rule before the table (overrides across kinds from an asset)
        "asset:seal, directive:A, overrides, overrides_source_not_activatable",
        // the table alone
        "directive:A, tactic:t, overrides, relationship_endpoint_not_allowed"
    })
    void theFaultOrderNamesTheEarlierRule(String from, String to, String type, String code) {
        seedEnhancementAndDirectives();
        store.packs.put(
                PACK,
                store.packs.get(PACK).toBuilder()
                        .assetFile(new AssetFile("seal.png", Payload.of(new byte[] {1})))
                        .build()
                        .with(Asset.builder()
                                .id(new AssetIdentifier("seal"))
                                .packId(PACK)
                                .mime("image/png")
                                .path("seal.png")
                                .build()));
        int before = store.commits;

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK,
                        draft(
                                Urn.parse(from).orElseThrow(),
                                Urn.parse(to).orElseThrow(),
                                RelationType.fromToken(type).orElseThrow()),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    if ("prompt_fragment_invalid".equals(code)) {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.PROMPT_FRAGMENT_INVALID);
                    } else {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                        assertThat(refusal.violations())
                                .singleElement()
                                .satisfies(violation ->
                                        assertThat(violation.code()).isEqualTo(code));
                    }
                });
        assertThat(store.commits).isEqualTo(before);
    }

    @Test
    void aStepEnhancementUrnThePackDoesNotHoldIsNotJudgedByTheEnhancementRulesAndIsRefusedAsUnresolved() {
        seedEnhancementAndDirectives();
        Urn notHeld = new Urn(NodeKind.STEP_ENHANCEMENT, "not-held");
        int before = store.commits;

        assertThatThrownBy(() -> service.registerRelationship(
                        PACK,
                        RelationshipDraft.of(Relationship.of(notHeld, STEP, RelationType.ENHANCES)),
                        store.currentRevision(PACK).token()))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT));
        assertThat(store.commits).isEqualTo(before);
    }

    @Test
    void theTwinOfTheReplaceRefusalIsTheSameTypeBetweenTwoDirectivesAndItIsRegistered() {
        seedEnhancementAndDirectives();
        Urn a = new Urn(NodeKind.DIRECTIVE, "A");
        Urn b = new Urn(NodeKind.DIRECTIVE, "B");
        store.edges.put(PACK, new ArrayList<>(List.of(Relationship.of(a, b, RelationType.ENHANCES))));
        int before = store.commits;

        service.registerRelationship(
                PACK,
                RelationshipDraft.of(Relationship.of(b, a, RelationType.SUGGESTS)),
                store.currentRevision(PACK).token());

        assertThat(store.commits).isEqualTo(before + 1);
        assertThat(store.relationships(PACK))
                .singleElement()
                .satisfies(r -> assertThat(r.type()).isEqualTo(RelationType.SUGGESTS));
    }

    /**
     * An edge that does not start at a held step enhancement is not judged by the enhancement rules: an edge to one is
     * refused by the endpoint table alone, since no relation targets a step enhancement (decision 13).
     */
    @Test
    void anEdgeThatDoesNotStartAtAHeldStepEnhancementIsNotJudgedByTheEnhancementRules() {
        seedEnhancementAndDirectives();
        Urn a = new Urn(NodeKind.DIRECTIVE, "A");

        assertRefusedByTheEndpointTableAndNothingWritten(draft(a, ENHANCEMENT, RelationType.SUGGESTS));
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
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
            }
            return found;
        }

        @Override
        public PackRevision currentRevision(PackIdentifier pack) {
            return PackRevision.compute(List.of(pack.value() + ":" + commits));
        }

        @Override
        public List<Relationship> authoredRelationships(PackIdentifier pack) {

            return List.of();
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
