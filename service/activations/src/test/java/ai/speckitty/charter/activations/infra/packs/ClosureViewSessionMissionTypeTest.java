package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.HeldBytes;
import ai.speckitty.charter.activations.application.HeldMissionType;
import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.EdgeIndex;
import ai.speckitty.charter.packs.application.MissionTypeHoldingRead;
import ai.speckitty.charter.packs.application.UnknownRootException;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.MissionStepContractIdentifier;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.TemplateIdentifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link ClosureViewSession#missionType} (#99, FR-007): the winner of a mission type across the closure's levels, what the
 * pack that holds it stores beside it (read by the packs slice's composite read, at the session's own fingerprint) and
 * the merged closure's step contracts bound to it, each the winner of its own id. The closure is merged for real from
 * hand-built packs.
 */
class ClosureViewSessionMissionTypeTest {

    private static final String FINGERPRINT = "sha256:source";

    private final List<String> holdingsAsked = new ArrayList<>();
    private String holdingFingerprint = FINGERPRINT;

    /**
     * org holds the type sd and the contract a bound to it; proj, at the project level, holds its own sd, the contract b
     * bound to sd, and c bound to another type.
     */
    private PacksSession session() {
        Closure closure = new Closure()
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of(),
                        List.of("mission_type:sd", "mission_step_contract:a"),
                        Map.of("mission_step_contract:a", "mission_type:sd"))
                .pack(
                        "proj",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("mission_type:sd", "mission_step_contract:b", "mission_step_contract:c"),
                        Map.of(
                                "mission_step_contract:b", "mission_type:sd",
                                "mission_step_contract:c", "mission_type:other"));
        return closure.session((pack, type) -> {
            holdingsAsked.add(pack.value() + ":" + type);
            return new MissionTypeHoldingRead.Holding(
                    entity(pack, ArtifactKind.MISSION_TYPE, type),
                    List.of(template(pack, type + "/a.md", type), template(pack, type + "/b.md", type)),
                    List.of(),
                    Map.of(
                            "specify",
                            new PackCompanionFiles.StepFiles(
                                    Optional.of(Payload.of(bytes("# prompt"))), Optional.empty())),
                    holdingFingerprint);
        });
    }

    @Test
    void theWinnerIsTheProjectsCopyWithItsPacksFilesAndTheMergedStepContractsBoundToIt() {
        HeldMissionType held = session().missionType("sd").orElseThrow();

        assertThat(held.packId()).isEqualTo("proj");
        assertThat(held.level()).isEqualTo(PackLevel.PROJECT);
        assertThat(held.missionType().packId()).isEqualTo(new PackIdentifier("proj"));
        assertThat(held.missionType().localId()).isEqualTo("sd");
        assertThat(holdingsAsked).as("the holding pack's files, once").containsExactly("proj:sd");
        assertThat(held.skeletons())
                .containsExactly(
                        new HeldMissionType.Skeleton("sd/a.md", HeldBytes.of(bytes("proj sd/a.md"))),
                        new HeldMissionType.Skeleton("sd/b.md", HeldBytes.of(bytes("proj sd/b.md"))));
        assertThat(held.stepFiles())
                .containsExactly(Map.entry(
                        "specify",
                        new HeldMissionType.StepFiles(Optional.of(HeldBytes.of(bytes("# prompt"))), Optional.empty())));
        assertThat(held.stepContracts())
                .as("each winner bound to sd, from whichever pack holds it; c is bound to another type")
                .containsExactly(
                        new HeldMissionType.StepContract("a", "act-a", "org"),
                        new HeldMissionType.StepContract("b", "act-b", "proj"));
    }

    @Test
    void aTypeNoPackOfTheClosureHoldsIsEmptyAndNoPackFileIsRead() {
        assertThat(session().missionType("research")).isEmpty();
        assertThat(holdingsAsked).isEmpty();
    }

    @Test
    void aHoldingReadAtAnotherFingerprintIsStaleInput() {
        holdingFingerprint = "sha256:moved";
        PacksSession session = session();

        assertThatThrownBy(() -> session.missionType("sd"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aSessionBuiltWithoutTheCompositeReadIsAWiringFault() {
        Closure closure = new Closure().pack("org", PackLevel.ORG, List.of(), List.of("mission_type:sd"), Map.of());
        PacksSession session = closure.session(ClosureViewSession.Holdings.NONE);

        assertThatThrownBy(() -> session.missionType("sd")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aSessionWhoseRootsNameNoConfiguredPackHoldsNoMissionType() {
        PacksAvailability packs = new PacksAvailability(roots -> {
            throw new UnknownRootException(List.of("ghost"));
        });

        assertThat(packs.session(Optional.of(List.of("ghost"))).missionType("sd"))
                .isEmpty();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Template template(PackIdentifier pack, String id, String missionType) {
        return Template.builder()
                .id(new TemplateIdentifier(id))
                .packId(pack)
                .content(Payload.of(bytes(pack.value() + " " + id)))
                .missionType(missionType)
                .build();
    }

    /** A closure of hand-built packs, merged for real; a winner is read as a stub, a step contract as one. */
    private static final class Closure {

        private final List<PackUnderResolution> packs = new ArrayList<>();
        private final Map<String, PackCompositionFacts> facts = new LinkedHashMap<>();
        private final Map<String, MutabilityPolicySource> policies = new LinkedHashMap<>();

        Closure pack(
                String name, PackLevel level, List<String> dependsOn, List<String> held, Map<String, String> bindings) {
            String id = "id-" + name;
            PackDescriptor descriptor = new PackDescriptor(
                    id,
                    "1.0.0",
                    Optional.empty(),
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    PackPaths.none(),
                    dependsOn.stream()
                            .map(other -> new PackDependency("id-" + other, ">=0"))
                            .toList());
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), held));
            MutabilityPolicySource policy = MutabilityPolicySource.empty();
            facts.put(
                    name,
                    new PackCompositionFacts(
                            List.of(), held, List.of(), policy, List.of(), List.of(), Map.of(), bindings));
            policies.put(id, policy);
            return this;
        }

        PacksSession session(ClosureViewSession.Holdings holdings) {
            ClosureView view = new ClosureView(
                    List.of(),
                    Map.<ArtifactKind, Set<String>>of(),
                    Map.of(),
                    new RequiredFloor(Map.of()),
                    new PerPackPolicy(policies),
                    packs.stream().map(pack -> pack.descriptor().packId()).toList(),
                    List.of(),
                    Map.of(),
                    FINGERPRINT,
                    EdgeIndex.of(packs, facts),
                    new ClosureView.MergeInputs(
                            packs,
                            facts,
                            (pack, kind, id) -> Optional.of(
                                    kind == ArtifactKind.MISSION_STEP_CONTRACT
                                            ? contract(pack, id)
                                            : entity(pack, kind, id))),
                    "sha256:closure");
            return new ClosureViewSession(view, PacksAvailability.snapshotOf(view), holdings);
        }
    }

    private static MissionStepContract contract(PackIdentifier pack, String id) {
        return MissionStepContract.builder()
                .id(new MissionStepContractIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .action("act-" + id)
                .mission("whatever the file says")
                .step(new ContractStep(
                        "first", "The first step", Optional.empty(), List.of(), Optional.empty(), Optional.empty()))
                .build();
    }

    private static CharterArtifact entity(PackIdentifier pack, ArtifactKind kind, String id) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return kind;
            }

            @Override
            public PackIdentifier packId() {
                return pack;
            }

            @Override
            public String localId() {
                return id;
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return id;
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }
}
