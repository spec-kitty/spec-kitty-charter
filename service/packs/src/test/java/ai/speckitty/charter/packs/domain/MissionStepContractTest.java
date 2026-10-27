package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Mission step contract entity")
class MissionStepContractTest {

    private static final PackIdentifier PACK = new PackIdentifier("01PACK");

    private static ContractStep step(String id) {
        return new ContractStep(
                id, "Description of " + id, Optional.empty(), List.of(), Optional.empty(), Optional.empty());
    }

    private static GateBinding gate(String handler) {
        return new GateBinding(
                "for_review->in_review", handler, Optional.empty(), "1", Optional.of(true), Optional.empty());
    }

    private static MissionStepContract.Builder valid(String id) {
        return MissionStepContract.builder()
                .id(new MissionStepContractIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .action("Action of " + id)
                .mission("software-dev")
                .step(step("bootstrap"));
    }

    @Test
    @DisplayName("a contract with two steps and one gate builds and keeps their order")
    void stepsAndGatesKeepOrder() {
        ContractStep withEverything = new ContractStep(
                "implement",
                "",
                Optional.of("spec-kitty agent action implement"),
                List.of(new StepInput("--wp", "wp_id", Optional.of(false))),
                Optional.of(new StepDelegation(ArtifactKind.TACTIC, List.of("tdd-red-green", "tdd-red-green"))),
                Optional.of("Folded guidance.\n"));

        MissionStepContract contract = MissionStepContract.builder()
                .id(new MissionStepContractIdentifier("implement"))
                .packId(PACK)
                .schemaVersion("1.0")
                .action("implement")
                .mission("software-dev")
                .step(withEverything)
                .step(step("bootstrap"))
                .gate(gate("review-gate"))
                .build();

        assertThat(contract.steps()).containsExactly(withEverything, step("bootstrap"));
        assertThat(contract.gates()).containsExactly(gate("review-gate"));
        assertThat(contract.steps().getFirst().delegatesTo())
                .get()
                .extracting(StepDelegation::candidates)
                .isEqualTo(List.of("tdd-red-green", "tdd-red-green"));
        assertThat(contract.action()).isEqualTo("implement");
        assertThat(contract.mission()).isEqualTo("software-dev");
    }

    @Test
    @DisplayName("the artifact facts: kind, local id, URN, label, no description and no relationships")
    void artifactFacts() {
        MissionStepContract contract = valid("implement").build();

        assertThat(contract.kind()).isEqualTo(ArtifactKind.MISSION_STEP_CONTRACT);
        assertThat(contract.localId()).isEqualTo("implement");
        assertThat(contract.urn()).isEqualTo(new Urn(NodeKind.MISSION_STEP_CONTRACT, "implement"));
        assertThat(contract.urn()).hasToString("mission_step_contract:implement");
        assertThat(contract.label()).isEqualTo("Action of implement");
        assertThat(contract.description()).isEmpty();
        assertThat(contract.schemaVersion()).contains("1.0");
        assertThat(contract.packId()).isEqualTo(PACK);
        assertThat(contract.relationships()).isEmpty();
        assertThat(contract).hasToString("MissionStepContract[implement of pack 01PACK]");
    }

    @Test
    @DisplayName("a blank action is refused while the same fixture with an action builds")
    void blankActionRefused() {
        assertThat(valid("plan").build().action()).isEqualTo("Action of plan");

        assertThatThrownBy(() -> valid("plan").action(" ").build()).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a blank mission is refused")
    void blankMissionRefused() {
        assertThatThrownBy(() -> valid("plan").mission("").build()).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a blank schema version is refused, and any other text is kept")
    void blankSchemaVersionRefused() {
        assertThat(valid("plan").schemaVersion("2.1").build().schemaVersion()).contains("2.1");

        assertThatThrownBy(() -> valid("plan").schemaVersion(" ").build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a contract without steps is refused")
    void noStepsRefused() {
        MissionStepContract.Builder noSteps = MissionStepContract.builder()
                .id(new MissionStepContractIdentifier("plan"))
                .packId(PACK)
                .schemaVersion("1.0")
                .action("plan")
                .mission("software-dev");

        assertThatThrownBy(noSteps::build).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a step with a blank id is refused")
    void blankStepIdRefused() {
        assertThatThrownBy(() -> step(" ")).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a step may have an empty description, as the schema allows")
    void emptyStepDescriptionAccepted() {
        ContractStep step =
                new ContractStep("bootstrap", "", Optional.empty(), List.of(), Optional.empty(), Optional.empty());

        assertThat(step.description()).isEmpty();
    }

    @Test
    @DisplayName("a delegation with no candidates is refused")
    void emptyCandidatesRefused() {
        assertThatThrownBy(() -> new StepDelegation(ArtifactKind.TACTIC, List.of()))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a delegation with a blank candidate is refused")
    void blankCandidateRefused() {
        assertThatThrownBy(() -> new StepDelegation(ArtifactKind.PARADIGM, List.of("ddd", " ")))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("an input with a blank flag is refused, and so is one with a blank source")
    void blankInputFieldsRefused() {
        assertThatThrownBy(() -> new StepInput("", "wp_id", Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new StepInput("--wp", " ", Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("two steps with the same id are accepted: validate mode reports them, not the entity")
    void duplicateStepIdsAccepted() {
        MissionStepContract contract = valid("plan").step(step("bootstrap")).build();

        assertThat(contract.steps()).extracting(ContractStep::id).containsExactly("bootstrap", "bootstrap");
    }

    @Test
    @DisplayName("equality is by identity, not by other fields")
    void identityEquality() {
        MissionStepContract one = valid("plan").build();

        assertThat(one)
                .isEqualTo(valid("plan").action("Other").gate(gate("other")).build())
                .hasSameHashCodeAs(valid("plan").build());
        assertThat(one).isNotEqualTo(valid("specify").build());
    }

    @Test
    @DisplayName("an input keeps an optional written as false apart from one left absent")
    void inputOptionalKeptAsWritten() {
        StepInput writtenFalse = new StepInput("--wp", "wp_id", Optional.of(false));
        StepInput absent = new StepInput("--wp", "wp_id", Optional.empty());

        assertThat(writtenFalse.optional()).contains(false);
        assertThat(absent.optional()).isEmpty();
        assertThat(writtenFalse).isNotEqualTo(absent);
    }

    @Test
    @DisplayName("a gate binding keeps every field as written")
    void gateBindingKeepsFields() {
        GateBinding binding = new GateBinding(
                "for_review->in_review",
                "review-gate",
                Optional.of("asset"),
                "1",
                Optional.of(false),
                Optional.of("issue #155"));

        assertThat(binding.handlerKind()).contains("asset");
        assertThat(binding.failOpen()).contains(false);
        assertThat(binding.provenance()).contains("issue #155");
    }
}
