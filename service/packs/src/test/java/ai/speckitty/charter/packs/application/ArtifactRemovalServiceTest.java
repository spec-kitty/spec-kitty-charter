package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.preconditionOf;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.RelationshipShard;
import ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The {@code DELETE} of one artifact (IC-06, FR-005, FR-007, FR-036): over the real stores, each refusal is paired with a
 * control on the same fixture and is shown to change no file; what the author wrote is never rewritten.
 */
class ArtifactRemovalServiceTest {

    private static final String QA_ID = PackRegistrationServiceTest.ulid('R');
    private static final String OTHER_ID = PackRegistrationServiceTest.ulid('S');
    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final String TEAM = """
            schema_version: "1.0"
            id: team
            description: The team.
            activated_directives:
              - id: D1
            activated_tactics:
              - id: t
            """;
    private static final String DEPENDS_ON_QA = "depends_on:\n  - pack_id: " + QA_ID + "\n    range: '>=1.0'\n";

    /** The org pack {@code qa}: three directives, a tactic and a preset that names a directive and the tactic. */
    private static Packs qa() {
        return Packs.of(QA_ID, "qa", "1.0.0", "org")
                .directive("D1", "D1")
                .directive("D2", "D2")
                .directive("D3", "D3")
                .tactic("t")
                .file("presets/team.yaml", TEAM);
    }

    private static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    private static Relationship edge(String from, String to, String toPack) {
        return new Relationship(
                urn(from),
                Optional.empty(),
                urn(to),
                Optional.ofNullable(toPack),
                RelationType.REQUIRES,
                Optional.empty(),
                Optional.empty());
    }

    /** The removal service over the rig's real stores and the gate the deployment wires. */
    private static final class Removals {
        final Rig rig;
        final ArtifactRemovalService service;

        Removals(Rig rig) {
            this(rig, null);
        }

        Removals(Rig rig, WritePolicyGate gate) {
            this.rig = rig;
            ConsistentRead read = new ConsistentRead(new FileSystemPackSources(rig.locations::all), rig.generation);
            WritePolicyGate effective = gate != null
                    ? gate
                    : new PackMutabilityPolicy(
                            new OfferingReadService(rig.repository, rig.repository, rig.repository, read),
                            rig.repository,
                            rig.repository,
                            read);
            this.service = new ArtifactRemovalService(
                    rig.repository,
                    rig.writeStore,
                    effective,
                    new InboundReferences(rig.repository, rig.repository, rig.repository),
                    new StagedPackViewFactory(rig.locations, new YamlSerializationStrategy()),
                    rig.impact);
        }

        PackRevision remove(String pack, String kind, String id) {
            return service.remove(new PackIdentifier(pack), kind, id, "*");
        }

        Map<String, String> filesOf(String pack) {
            return rig.filesOf(rig.packs.resolve(pack));
        }
    }

    // ---- what a removal removes
    // ---------------------------------------------------------------------------------------

    @Test
    void removesTheArtifactItsRegisteredEdgesAndItsPresetItemsAndKeepsTheRestOfThePack(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa().registeredEdge("D1", "D3")), Preconditions.none());
        Removals removals = new Removals(rig);
        PackRevision before = rig.writeStore.currentRevision(QA);

        PackRevision after = removals.remove("qa", "directive", "D1");

        Map<String, String> files = removals.filesOf("qa");
        assertThat(after).as("a new revision").isNotEqualTo(before).isEqualTo(rig.writeStore.currentRevision(QA));
        assertThat(files).doesNotContainKey("directives/D1.directive.yaml");
        assertThat(files.get("registered.graph.yaml"))
                .as("the edge that started at D1 went with it")
                .doesNotContain("directive:D1");
        assertThat(files.get("presets/team.yaml"))
                .as("the item that named D1 went, the tactic item stays")
                .doesNotContain("D1")
                .contains("activated_tactics");
        assertThat(files)
                .as("the control: the rest of the pack is there")
                .containsKeys(
                        "directives/D2.directive.yaml",
                        "directives/D3.directive.yaml",
                        "tactics/t.tactic.yaml",
                        "pack.yaml");
        Pack read = rig.repository.load(QA);
        assertThat(read.directives()).extracting(d -> d.id().value()).containsExactly("D2", "D3");
        assertThat(rig.repository.get(QA).packVersion())
                .as("the descriptor is untouched")
                .isEqualTo("1.0.0");
    }

    @Test
    void theTrailIsToldTheRevisionTheDeleteFoundAndTheOneItLeftAndNothingOnARefusal(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa().registeredEdge("D1", "D3")), Preconditions.none());
        Removals removals = new Removals(rig);
        PackRevision stored = rig.writeStore.currentRevision(QA);
        List<PackRevision[]> trail = new ArrayList<>();

        assertThat(refusal(() -> removals.service.remove(
                                QA, "directive", "D3", "*", (b, a) -> trail.add(new PackRevision[] {b, a})))
                        .code())
                .as("D3 is the target of D1's edge: refused")
                .isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertThat(trail).as("a refused delete tells the trail nothing").isEmpty();

        PackRevision after =
                removals.service.remove(QA, "directive", "D1", "*", (b, a) -> trail.add(new PackRevision[] {b, a}));

        assertThat(trail).singleElement().satisfies(moved -> {
            assertThat(moved[0]).as("found").isEqualTo(stored);
            assertThat(moved[1]).as("left").isEqualTo(after).isEqualTo(rig.writeStore.currentRevision(QA));
        });
    }

    @Test
    void aPresetKeyWhoseLastItemIsRemovedIsDeletedNotWrittenEmptyAndThePackStillReads(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        Removals removals = new Removals(rig);

        removals.remove("qa", "directive", "D1");

        String preset = removals.filesOf("qa").get("presets/team.yaml");
        assertThat(preset)
                .as("every preset list has minItems 1, so the key is gone and no [] is written")
                .doesNotContain("activated_directives")
                .doesNotContain("[]")
                .as("the control: the other key survives")
                .contains("activated_tactics");
        assertThat(rig.repository.load(QA).presets())
                .as("the pack reads back")
                .singleElement()
                .satisfies(read -> assertThat(read.artifacts()).hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
        "ghost, directive, D3, PACK_NOT_FOUND",
        "qa, procedure, D3, KIND_NOT_FOUND",
        "qa, directive, NOPE, DIRECTIVE_NOT_FOUND",
        "qa, tactic, NOPE, TACTIC_NOT_FOUND",
    })
    void aPathThatNamesNothingThePackHoldsIsA404WithItsOwnCodeAndTheNextDeleteStillWorks(
            String pack, String kind, String id, RefusalCode code, @TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.filesOf("qa");

        assertThat(refusal(() -> removals.remove(pack, kind, id)).code()).isEqualTo(code);
        assertThat(removals.filesOf("qa")).as("nothing changed").isEqualTo(before);

        removals.remove("qa", "directive", "D3");
        assertThat(removals.filesOf("qa")).as("the control: D3 goes").doesNotContainKey("directives/D3.directive.yaml");
    }

    @Test
    void aToolguideGoesWithItsGuideCompanionAndAnotherToolguideKeepsItsOwn(@TempDir Path host) throws IOException {
        String yaml = resource("toolguides/git-agent-commit-signing.toolguide.yaml");
        String guide = resource("toolguides/git-agent-commit-signing.guide.md");
        Rig rig = new Rig(host);
        rig.register(
                "qa",
                rig.stage(qa().file("toolguides/git-agent-commit-signing.toolguide.yaml", yaml)
                        .file("toolguides/git-agent-commit-signing.guide.md", guide)
                        .file(
                                "toolguides/other-guide.toolguide.yaml",
                                yaml.replace("git-agent-commit-signing", "other-guide"))
                        .file("toolguides/other-guide.guide.md", guide)),
                Preconditions.none());
        Removals removals = new Removals(rig);

        removals.remove("qa", "toolguide", "git-agent-commit-signing");

        assertThat(removals.filesOf("qa"))
                .as("the toolguide and the guide it names are gone")
                .doesNotContainKeys(
                        "toolguides/git-agent-commit-signing.toolguide.yaml",
                        "toolguides/git-agent-commit-signing.guide.md")
                .as("the control: the other toolguide keeps its file and its guide")
                .containsKeys("toolguides/other-guide.toolguide.yaml", "toolguides/other-guide.guide.md");
    }

    private static final String MISSION_TYPE = """
            schema_version: '1.0'
            id: software-dev
            title: Software Development
            description: Build software
            steps:
            - id: specify
              title: Specification
            """;
    private static final String STEP_CONTRACT = """
            schema_version: '1.0'
            id: specify
            action: specify
            mission: software-dev
            steps:
              - id: bootstrap
                description: Load charter context for this action
            """;

    /** The org pack {@code qa} with a mission type and a step contract bound to its step. */
    private static Packs qaWithMissionKinds() {
        return qa().file("missions/software-dev/software-dev.mission-type.yaml", MISSION_TYPE)
                .file("mission_step_contracts/specify.step-contract.yaml", STEP_CONTRACT);
    }

    @Test
    void aStepContractIsRemovedLikeTheOtherListedKindsAndItsMissionTypeStays(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qaWithMissionKinds()), Preconditions.none());
        Removals removals = new Removals(rig);

        removals.remove("qa", "mission_step_contract", "specify");

        assertThat(removals.filesOf("qa"))
                .doesNotContainKey("mission_step_contracts/specify.step-contract.yaml")
                .as("the control: the mission type stays")
                .containsKey("missions/software-dev/software-dev.mission-type.yaml");
        assertThat(rig.repository.load(QA).artifacts(ArtifactKind.MISSION_STEP_CONTRACT))
                .isEmpty();
        assertThat(refusal(() -> removals.remove("qa", "mission_step_contract", "specify"))
                        .code())
                .as("gone, so the next delete is its kind's not-found")
                .isEqualTo(RefusalCode.MISSION_STEP_CONTRACT_NOT_FOUND);
    }

    /**
     * A mission type's removal cascades its skeletons (D10), which is not built yet: until it is, the DELETE of a
     * mission type is kind_not_found and changes nothing, rather than leaving its skeletons behind.
     */
    @Test
    void aMissionTypeIsNotRemovedUntilItsCascadeIsBuilt(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qaWithMissionKinds()), Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.filesOf("qa");

        assertThat(refusal(() -> removals.remove("qa", "mission_type", "software-dev"))
                        .code())
                .isEqualTo(RefusalCode.KIND_NOT_FOUND);
        assertThat(removals.filesOf("qa")).as("nothing changed").isEqualTo(before);
        assertThat(ArtifactRemovalService.NOT_YET_REMOVABLE).containsExactly(ArtifactKind.MISSION_TYPE);
    }

    private static String resource(String path) throws IOException {
        try (var stream =
                ArtifactRemovalServiceTest.class.getResourceAsStream("/roundtrip-fixtures/toolguide/" + path)) {
            return new String(Objects.requireNonNull(stream, path).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- what refuses a removal
    // ---------------------------------------------------------------------------------------

    private static Packs otherPack() {
        return Packs.of(OTHER_ID, "other", "1.0.0", "org", DEPENDS_ON_QA).directive("R", "R");
    }

    @Test
    void anEdgeOfAnotherPackThatPointsAtTheArtifactRefusesAndNamesTheReferrer(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        rig.register(
                "other",
                rig.stage(otherPack()
                        .file(
                                "registered.graph.yaml",
                                RelationshipShard.write(List.of(edge("directive:R", "directive:D1", "qa"))))),
                Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.filesOf("qa");

        CharterRefusal refused = refusal(() -> removals.remove("qa", "directive", "D1"));

        assertThat(refused.code()).isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertThat(refused.packs()).extracting(RefusalPack::packId).containsExactly("other");
        assertThat(refused.violations())
                .extracting(RefusalViolation::detail)
                .anySatisfy(detail -> assertThat(detail).contains("directive:R"));
        assertThat(removals.filesOf("qa")).as("the refusal changed nothing").isEqualTo(before);

        removals.remove("qa", "directive", "D3");
        assertThat(removals.filesOf("qa"))
                .as("the control: an artifact nobody refers to")
                .doesNotContainKey("directives/D3.directive.yaml");
    }

    @Test
    void aPresetItemOfAnotherPackThatNamesTheArtifactRefusesAndNamesThePreset(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        rig.register("other", rig.stage(otherPack().file("presets/governing.yaml", """
                                schema_version: "1.0"
                                id: governing
                                description: D1 of qa.
                                activated_directives:
                                  - id: D1
                                    must_have: true
                                """)), Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.filesOf("qa");

        CharterRefusal refused = refusal(() -> removals.remove("qa", "directive", "D1"));

        assertThat(refused.code()).isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertThat(refused.packs()).extracting(RefusalPack::packId).containsExactly("other");
        assertThat(refused.violations())
                .extracting(RefusalViolation::detail)
                .anySatisfy(detail -> assertThat(detail).contains("governing"));
        assertThat(removals.filesOf("qa")).isEqualTo(before);

        removals.remove("qa", "directive", "D3");
        assertThat(removals.filesOf("qa")).doesNotContainKey("directives/D3.directive.yaml");
    }

    @Test
    void anArtifactOfTheSamePackThatPointsAtItBlocksTheDeleteUntilThatArtifactIsGone(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa().registeredEdge("D2", "D1")), Preconditions.none());
        Removals removals = new Removals(rig);

        CharterRefusal refused = refusal(() -> removals.remove("qa", "directive", "D1"));

        assertThat(refused.code()).isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertThat(refused.violations())
                .extracting(RefusalViolation::detail)
                .anySatisfy(detail -> assertThat(detail).contains("directive:D2"));
        assertThat(removals.filesOf("qa")).containsKey("directives/D1.directive.yaml");

        removals.remove("qa", "directive", "D2");
        removals.remove("qa", "directive", "D1");
        assertThat(removals.filesOf("qa"))
                .as("the control: the referrer the same pack removed first no longer blocks")
                .doesNotContainKey("directives/D1.directive.yaml");
    }

    @Test
    void anArtifactAnAuthorWrittenGraphDocumentNamesIsRefusedAndNoFileIsRewritten(@TempDir Path host) {
        Rig rig = new Rig(host);
        String lineage = """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'directive:D1', target: 'directive:D2', relation: suggests}
                """;
        rig.register("qa", rig.stage(qa().file("lineage.graph.yaml", lineage)), Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.filesOf("qa");

        CharterRefusal refused = refusal(() -> removals.remove("qa", "directive", "D1"));

        assertThat(refused.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT);
        assertThat(refused.getMessage()).contains("register the whole pack");
        assertThat(removals.filesOf("qa"))
                .as("byte-identical, the author's shard included")
                .isEqualTo(before);

        removals.remove("qa", "directive", "D3");
        assertThat(removals.filesOf("qa"))
                .as("the control: an artifact the document does not name")
                .doesNotContainKey("directives/D3.directive.yaml")
                .containsEntry("lineage.graph.yaml", lineage);
    }

    @Test
    void theRemoveOperationOfTheWriteGateIsAskedAboutTheArtifactAndARefusalChangesNothing(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        List<String> asked = new ArrayList<>();
        WritePolicyGate refusing = new WritePolicyGate() {
            @Override
            public void authorize(Pack pack, Operation operation, Urn target) {
                asked.add(operation + " " + target);
                throw new CharterRefusal(RefusalCode.GOVERNANCE_POLICY_VIOLATION, "locked by an ancestor");
            }

            @Override
            public void authorizeStaged(Pack staged, List<Write> writes) {
                asked.add("staged " + writes);
                throw new CharterRefusal(RefusalCode.GOVERNANCE_POLICY_VIOLATION, "locked by an ancestor");
            }
        };
        Removals removals = new Removals(rig, refusing);
        Map<String, String> before = removals.filesOf("qa");

        assertThat(refusal(() -> removals.remove("qa", "directive", "D3")).code())
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);

        assertThat(asked).as("one question, about a removal of D3").hasSize(1);
        assertThat(asked.getFirst()).contains("REMOVE").contains("directive:D3");
        assertThat(removals.filesOf("qa")).isEqualTo(before);

        new Removals(rig, new PermissiveWritePolicyGate()).remove("qa", "directive", "D3");
        assertThat(removals.filesOf("qa"))
                .as("the control: a gate that allows it")
                .doesNotContainKey("directives/D3.directive.yaml");
    }

    // ---- an agent profile is removed as the five kinds are (operator decision 01M70ZFW6S2HG3TEE33RW8VGSB)
    // ---------------------

    private static final String JENNY = """
            profile-id: jenny
            name: Jenny
            roles:
              - implementer
            purpose: Implement.
            specialization:
              primary-focus: Java
            """;

    private static Packs qaWithAProfile() {
        return qa().file("agent_profiles/jenny.agent.yaml", JENNY);
    }

    @Test
    void anAgentProfileIsRemovedWithThePackRevisionAndALaterReadFindsNone(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qaWithAProfile()), Preconditions.none());
        Removals removals = new Removals(rig);
        PackRevision before = rig.writeStore.currentRevision(QA);
        assertThat(rig.repository.load(QA).artifacts(ArtifactKind.AGENT_PROFILE))
                .hasSize(1);

        PackRevision after = removals.service.remove(QA, "agent_profile", "jenny", before.token());

        assertThat(after).isNotEqualTo(before).isEqualTo(rig.writeStore.currentRevision(QA));
        assertThat(removals.filesOf("qa"))
                .doesNotContainKey("agent_profiles/jenny.agent.yaml")
                .containsKeys("directives/D1.directive.yaml", "pack.yaml");
        assertThat(rig.repository.load(QA).artifacts(ArtifactKind.AGENT_PROFILE))
                .as("a later read finds none")
                .isEmpty();
        assertThat(refusal(() -> removals.remove("qa", "agent_profile", "jenny"))
                        .code())
                .as("and a second removal is the profile's not-found")
                .isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
    }

    @Test
    void anAgentProfileTheGateRefusesIsAskedAboutAsARemovalAndNothingChanges(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qaWithAProfile()), Preconditions.none());
        List<String> asked = new ArrayList<>();
        WritePolicyGate refusing = new WritePolicyGate() {
            @Override
            public void authorize(Pack pack, Operation operation, Urn target) {
                asked.add(operation + " " + target);
                throw new CharterRefusal(RefusalCode.GOVERNANCE_POLICY_VIOLATION, "locked by an ancestor");
            }

            @Override
            public void authorizeStaged(Pack staged, List<Write> writes) {
                throw new CharterRefusal(RefusalCode.GOVERNANCE_POLICY_VIOLATION, "locked by an ancestor");
            }
        };
        Removals removals = new Removals(rig, refusing);
        Map<String, String> before = removals.filesOf("qa");

        assertThat(refusal(() -> removals.remove("qa", "agent_profile", "jenny"))
                        .code())
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);

        assertThat(asked).containsExactly("REMOVE agent_profile:jenny");
        assertThat(removals.filesOf("qa")).isEqualTo(before);
    }

    @Test
    void anAgentProfileAnotherArtifactPointsAtIsRefusedAsADirectiveIs(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register(
                "qa",
                rig.stage(qaWithAProfile()
                        .file(
                                "registered.graph.yaml",
                                RelationshipShard.write(List.of(Relationship.of(
                                        urn("directive:D2"), urn("agent_profile:jenny"), RelationType.REQUIRES))))),
                Preconditions.none());
        Removals removals = new Removals(rig);

        CharterRefusal refused = refusal(() -> removals.remove("qa", "agent_profile", "jenny"));

        assertThat(refused.code()).isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertThat(removals.filesOf("qa")).containsKey("agent_profiles/jenny.agent.yaml");
    }

    // ---- the precondition
    // ---------------------------------------------------------------------------------------------

    @Test
    void theRevisionIsRequiredAStaleOneIsRefusedAndTheCurrentOneOrAnyIsAccepted(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa()), Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.filesOf("qa");
        String stale = "\"sha256:" + "0".repeat(64) + "\"";

        assertThat(preconditionOf(() -> removals.service.remove(QA, "directive", "D3", null)))
                .isEqualTo(PreconditionException.Kind.MISSING);
        assertThat(preconditionOf(() -> removals.service.remove(QA, "directive", "D3", stale)))
                .isEqualTo(PreconditionException.Kind.STALE);
        assertThat(removals.filesOf("qa")).as("neither changed anything").isEqualTo(before);

        removals.service.remove(
                QA, "directive", "D3", rig.writeStore.currentRevision(QA).token());
        assertThat(removals.filesOf("qa"))
                .as("the control: the current revision")
                .doesNotContainKey("directives/D3.directive.yaml");
        removals.service.remove(QA, "directive", "D2", "*");
        assertThat(removals.filesOf("qa")).as("and any revision").doesNotContainKey("directives/D2.directive.yaml");
    }

    // ---- strictness (DM-01M6TQ67C9ZTNGZB9CY28JS54F)
    // -------------------------------------------------------------------

    private static final String SIBLING_A = PackRegistrationServiceTest.ulid('T');
    private static final String SIBLING_B = PackRegistrationServiceTest.ulid('K');

    @Test
    void aRemovalThatLeavesAClosureTheActivationRootsBrokenIsRefusedAndTheSameRemovalOtherwiseIsNot(
            @TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install(
                "sib-a",
                Packs.of(SIBLING_A, "sib-a", "1.0.0", "org")
                        .directive("SHARED_ONE", "Mine")
                        .directive("EXTRA", "x"));
        rig.install("sib-b", Packs.of(SIBLING_B, "sib-b", "1.0.0", "org").directive("SHARED_ONE", "Yours"));
        rig.activations.add(Optional.of(List.of("sib-a", "sib-b")));
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.filesOf("sib-a");

        CharterRefusal refused = refusal(() -> removals.remove("sib-a", "directive", "EXTRA"));

        assertThat(refused.code())
                .as("each composes alone and the activation lists them together: PR A does not repair a broken closure")
                .isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
        assertThat(removals.filesOf("sib-a")).as("nothing changed").isEqualTo(before);

        rig.activations.clear();
        removals.remove("sib-a", "directive", "EXTRA");
        assertThat(removals.filesOf("sib-a"))
                .as("the control: without the activation the same removal lands")
                .doesNotContainKey("directives/EXTRA.directive.yaml");
    }

    private static final String SIBLING_C = PackRegistrationServiceTest.ulid('V');

    @Test
    void aBrokenClosureTheTargetPackDoesNotJoinLeavesTheRemovalAlone(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install("target", Packs.of(SIBLING_C, "target", "1.0.0", "org").directive("EXTRA", "x"));
        rig.install("sib-a", Packs.of(SIBLING_A, "sib-a", "1.0.0", "org").directive("SHARED_ONE", "Mine"));
        rig.install("sib-b", Packs.of(SIBLING_B, "sib-b", "1.0.0", "org").directive("SHARED_ONE", "Yours"));
        rig.activations.add(Optional.of(List.of("sib-a", "sib-b")));
        Removals removals = new Removals(rig);

        removals.remove("target", "directive", "EXTRA");

        assertThat(removals.filesOf("target"))
                .as("the activation's closure is broken, but it does not hold the pack the removal is from")
                .doesNotContainKey("directives/EXTRA.directive.yaml");
    }
}
