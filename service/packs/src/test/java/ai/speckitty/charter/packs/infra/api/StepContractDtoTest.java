package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.MissionStepContract;
import ai.speckitty.charter.api.model.MissionStepContractStep;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ArtifactPosition;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.GateBinding;
import ai.speckitty.charter.packs.domain.MissionStepContractIdentifier;
import ai.speckitty.charter.packs.domain.StepDelegation;
import ai.speckitty.charter.packs.domain.StepInput;
import ai.speckitty.charter.packs.infra.storage.MarkdownGuideFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The wire shape of a mission step contract item (#99): its stored fields, each delegation candidate with an
 * {@code artifact} link only when the read found its pack holding it, and the item's {@code mission-type} and
 * {@code step} links only when the pack holds them.
 */
class StepContractDtoTest {

    private static final PackIdentifier PACK = new PackIdentifier("main");
    private static final Hypermedia LINKS = new Hypermedia("/api/v1");
    private static final String BASE = "/api/v1/packs/main/artifacts";

    private static ai.speckitty.charter.packs.domain.MissionStepContract contract() {
        return ai.speckitty.charter.packs.domain.MissionStepContract.builder()
                .id(new MissionStepContractIdentifier("specify"))
                .packId(PACK)
                .schemaVersion("1.0")
                .action("specify")
                .mission("software-dev")
                .step(new ContractStep(
                        "bootstrap",
                        "Load charter context for this action",
                        Optional.of("spec-kitty charter context --action specify --json"),
                        List.of(new StepInput("--mission", "mission_slug", Optional.of(true))),
                        Optional.empty(),
                        Optional.empty()))
                .step(new ContractStep(
                        "capture_intent",
                        "Capture confirmed user intent",
                        Optional.empty(),
                        List.of(),
                        Optional.of(new StepDelegation(ArtifactKind.DIRECTIVE, List.of("GUARD", "NOPE"))),
                        Optional.of("Ask before you write.")))
                .step(new ContractStep(
                        "map_examples",
                        "Capture concrete rules and examples",
                        Optional.empty(),
                        List.of(),
                        Optional.of(new StepDelegation(ArtifactKind.PROCEDURE, List.of("example-mapping-workshop"))),
                        Optional.empty()))
                .gate(new GateBinding(
                        "in_progress->for_review",
                        "spec-kitty-pre-review",
                        Optional.of("mission_step_contract"),
                        "1.0",
                        Optional.of(true),
                        Optional.of("built-in")))
                .build();
    }

    private static Artifact answered(ArtifactReadService.Item.OfMissionStepContract item) {
        return ArtifactDtos.artifact(
                item,
                new MarkdownGuideFormat(),
                ArtifactDtos.itemLinks(item, "mission_step_contract", "main", "specify", LINKS),
                LINKS);
    }

    @Test
    void aHeldStepContractLinksItsMissionTypeItsStepAndItsHeldCandidatesOnly() {
        Artifact envelope = answered(new ArtifactReadService.Item.OfMissionStepContract(
                contract(), true, true, Set.of(new ArtifactPosition(ArtifactKind.DIRECTIVE, "GUARD"))));

        assertThat(envelope.getKind()).isEqualTo("mission_step_contract");
        assertThat(envelope.getMissionType()).isNull();
        assertThat(envelope.getLinks())
                .extractingByKeys("self", "collection", "mission-type", "step")
                .extracting(Link::getHref)
                .containsExactly(
                        BASE + "/mission_step_contract/specify",
                        BASE + "?kind=mission_step_contract",
                        BASE + "/mission_type/software-dev",
                        BASE + "/mission_type/software-dev/steps/specify");
        MissionStepContract member = envelope.getMissionStepContract();
        assertThat(member.getId()).isEqualTo("specify");
        assertThat(member.getSchemaVersion()).isEqualTo("1.0");
        assertThat(member.getAction()).isEqualTo("specify");
        assertThat(member.getMission()).isEqualTo("software-dev");
        assertThat(member.getSteps())
                .extracting(MissionStepContractStep::getId)
                .containsExactly("bootstrap", "capture_intent", "map_examples");
        MissionStepContractStep bootstrap = member.getSteps().getFirst();
        assertThat(bootstrap.getCommand()).isEqualTo("spec-kitty charter context --action specify --json");
        assertThat(bootstrap.getDelegatesTo()).isNull();
        assertThat(bootstrap.getGuidance()).isNull();
        assertThat(bootstrap.getInputs()).singleElement().satisfies(input -> {
            assertThat(input.getFlag()).isEqualTo("--mission");
            assertThat(input.getSource()).isEqualTo("mission_slug");
            assertThat(input.getOptional()).isTrue();
        });
        MissionStepContractStep capture = member.getSteps().get(1);
        assertThat(capture.getInputs())
                .as("a step without inputs answers an empty list")
                .isEmpty();
        assertThat(capture.getGuidance()).isEqualTo("Ask before you write.");
        assertThat(capture.getDelegatesTo().getKind()).isEqualTo("directive");
        assertThat(capture.getDelegatesTo().getCandidates())
                .satisfiesExactly(
                        guard -> {
                            assertThat(guard.getId()).isEqualTo("GUARD");
                            assertThat(guard.getLinks()).containsOnlyKeys("artifact");
                            assertThat(guard.getLinks().get("artifact").getHref())
                                    .isEqualTo(BASE + "/directive/GUARD");
                        },
                        nope -> {
                            assertThat(nope.getId()).isEqualTo("NOPE");
                            assertThat(nope.getLinks()).as("not held: no link").isEmpty();
                        });
        assertThat(member.getSteps().get(2).getDelegatesTo().getCandidates())
                .singleElement()
                .satisfies(procedure -> assertThat(procedure.getLinks())
                        .as("the procedure is listed but not held: no link")
                        .isEqualTo(Map.of()));
        assertThat(member.getGates()).singleElement().satisfies(gate -> {
            assertThat(gate.getOnTransition()).isEqualTo("in_progress->for_review");
            assertThat(gate.getHandler()).isEqualTo("spec-kitty-pre-review");
            assertThat(gate.getHandlerKind()).isEqualTo("mission_step_contract");
            assertThat(gate.getSchemaVersion()).isEqualTo("1.0");
            assertThat(gate.getFailOpen()).isTrue();
            assertThat(gate.getProvenance()).isEqualTo("built-in");
        });
    }

    @Test
    void aStepContractWhoseMissionTypeOrStepIsNotHeldHasNoSuchLink() {
        assertThat(answered(new ArtifactReadService.Item.OfMissionStepContract(contract(), false, false, Set.of()))
                        .getLinks())
                .containsOnlyKeys("self", "collection");
        assertThat(answered(new ArtifactReadService.Item.OfMissionStepContract(contract(), true, false, Set.of()))
                        .getLinks())
                .as("the type is held, the step is not")
                .containsOnlyKeys("self", "collection", "mission-type");
    }
}
