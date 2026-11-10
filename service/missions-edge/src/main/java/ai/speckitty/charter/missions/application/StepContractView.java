package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A mission step contract as the missions slice reads it (D11): the stored fields of the packs slice's step contract,
 * which the {@link StepContracts} port narrows, so the missions rings never name the packs slice's type.
 *
 * @param id the contract's id
 * @param schemaVersion its schema version
 * @param action the step its {@code action} names
 * @param mission the mission type its {@code mission} names
 * @param steps its steps, in stored order
 * @param gates its gates, in stored order
 */
public record StepContractView(
        String id, String schemaVersion, String action, String mission, List<Step> steps, List<Gate> gates) {

    /**
     * Checks and copies the fields.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param action the action
     * @param mission the mission type
     * @param steps the steps
     * @param gates the gates
     */
    public StepContractView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(mission, "mission");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        gates = List.copyOf(Objects.requireNonNull(gates, "gates"));
    }

    /**
     * The contract as the binding of its step.
     *
     * @return its id and the step its {@code action} names
     */
    public MissionTypeHolding.BoundContract bound() {
        return new MissionTypeHolding.BoundContract(id, action);
    }

    /**
     * One step of the contract.
     *
     * @param id its id
     * @param description what it does
     * @param command the command it runs, if any
     * @param inputs its inputs, in stored order
     * @param delegatesTo the delegation it names, if any
     * @param guidance its guidance, if any
     */
    public record Step(
            String id,
            String description,
            Optional<String> command,
            List<Input> inputs,
            Optional<Delegation> delegatesTo,
            Optional<String> guidance) {

        /**
         * Checks and copies the fields.
         *
         * @param id the id
         * @param description the description
         * @param command the command
         * @param inputs the inputs
         * @param delegatesTo the delegation
         * @param guidance the guidance
         */
        public Step {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(command, "command");
            inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
            Objects.requireNonNull(delegatesTo, "delegatesTo");
            Objects.requireNonNull(guidance, "guidance");
        }
    }

    /**
     * One input of a step.
     *
     * @param flag its flag
     * @param source where its value comes from
     * @param optional whether it may be left out, when the contract says
     */
    public record Input(String flag, String source, Optional<Boolean> optional) {

        /**
         * Checks the fields.
         *
         * @param flag the flag
         * @param source the source
         * @param optional the optionality
         */
        public Input {
            Objects.requireNonNull(flag, "flag");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(optional, "optional");
        }
    }

    /**
     * The delegation of a step: a kind and the candidate ids of that kind.
     *
     * @param kind the candidates' kind
     * @param candidates the candidate ids, in stored order
     */
    public record Delegation(ArtifactKind kind, List<String> candidates) {

        /**
         * Checks and copies the fields.
         *
         * @param kind the kind
         * @param candidates the candidates
         */
        public Delegation {
            Objects.requireNonNull(kind, "kind");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    /**
     * One gate of the contract.
     *
     * @param onTransition the lane transition it guards
     * @param handler its handler
     * @param handlerKind the handler's kind, if any
     * @param schemaVersion its schema version
     * @param failOpen whether it fails open, when the contract says
     * @param provenance where it comes from, if stated
     */
    public record Gate(
            String onTransition,
            String handler,
            Optional<String> handlerKind,
            String schemaVersion,
            Optional<Boolean> failOpen,
            Optional<String> provenance) {

        /**
         * Checks the fields.
         *
         * @param onTransition the transition
         * @param handler the handler
         * @param handlerKind the handler's kind
         * @param schemaVersion the schema version
         * @param failOpen whether it fails open
         * @param provenance the provenance
         */
        public Gate {
            Objects.requireNonNull(onTransition, "onTransition");
            Objects.requireNonNull(handler, "handler");
            Objects.requireNonNull(handlerKind, "handlerKind");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(failOpen, "failOpen");
            Objects.requireNonNull(provenance, "provenance");
        }
    }
}
