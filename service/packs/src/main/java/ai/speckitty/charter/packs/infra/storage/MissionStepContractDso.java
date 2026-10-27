package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a mission step contract: a flat mirror of the fields of a {@code *.step-contract.yaml}, not
 * the domain entity. Its record equality is the round-trip fidelity assertion: lists are order- and
 * duplicate-sensitive, and an absent optional differs from a present one, so a field written with its schema default
 * stays written and an omitted one stays omitted. An explicit {@code null}, an absent key and an empty list read alike
 * and are written as an omitted key, which ADR-0036 counts as the same meaning.
 *
 * @param id the contract's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param action the {@code action}
 * @param mission the {@code mission}
 * @param steps the steps, in authored order
 * @param gates the gate bindings, in authored order
 */
record MissionStepContractDso(
        String id, String schemaVersion, String action, String mission, List<Step> steps, List<Gate> gates) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param action the action
     * @param mission the mission
     * @param steps the steps
     * @param gates the gate bindings
     */
    MissionStepContractDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(mission, "mission");
        steps = List.copyOf(steps);
        gates = List.copyOf(gates);
    }

    /**
     * One step of the contract.
     *
     * @param id the step's {@code id}
     * @param description the step's {@code description}
     * @param command the {@code command}, when stated
     * @param inputs the {@code inputs}, in authored order
     * @param delegatesTo the {@code delegates_to}, when stated
     * @param guidance the {@code guidance}, when stated
     */
    record Step(
            String id,
            String description,
            Optional<String> command,
            List<Input> inputs,
            Optional<Delegation> delegatesTo,
            Optional<String> guidance) {
        /**
         * Checks the fields and copies the list.
         *
         * @param id the id
         * @param description the description
         * @param command the command
         * @param inputs the inputs
         * @param delegatesTo the delegation
         * @param guidance the guidance
         */
        Step {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(delegatesTo, "delegatesTo");
            Objects.requireNonNull(guidance, "guidance");
            inputs = List.copyOf(inputs);
        }
    }

    /**
     * One declared input of a step.
     *
     * @param flag the {@code flag}
     * @param source the {@code source}
     * @param optional the {@code optional} flag as written; empty when absent
     */
    record Input(String flag, String source, Optional<Boolean> optional) {
        /**
         * Checks that every field is present.
         *
         * @param flag the flag
         * @param source the source
         * @param optional the optional flag
         */
        Input {
            Objects.requireNonNull(flag, "flag");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(optional, "optional");
        }
    }

    /**
     * A step's delegation, carried verbatim.
     *
     * @param kind the artifact-kind token, as plain text
     * @param candidates the candidate ids, in authored order
     */
    record Delegation(String kind, List<String> candidates) {
        /**
         * Checks the kind and copies the candidates.
         *
         * @param kind the kind
         * @param candidates the candidates
         */
        Delegation {
            Objects.requireNonNull(kind, "kind");
            candidates = List.copyOf(candidates);
        }
    }

    /**
     * One gate binding, carried verbatim.
     *
     * @param onTransition the {@code on_transition}
     * @param handler the {@code handler}
     * @param handlerKind the {@code handler_kind} as written; empty when absent
     * @param schemaVersion the binding's {@code schema_version}
     * @param failOpen the {@code fail_open} as written; empty when absent
     * @param provenance the {@code provenance}, when stated
     */
    record Gate(
            String onTransition,
            String handler,
            Optional<String> handlerKind,
            String schemaVersion,
            Optional<Boolean> failOpen,
            Optional<String> provenance) {
        /**
         * Checks that every field is present.
         *
         * @param onTransition the transition
         * @param handler the handler
         * @param handlerKind the handler kind
         * @param schemaVersion the schema version
         * @param failOpen the fail-open flag
         * @param provenance the provenance
         */
        Gate {
            Objects.requireNonNull(onTransition, "onTransition");
            Objects.requireNonNull(handler, "handler");
            Objects.requireNonNull(handlerKind, "handlerKind");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(failOpen, "failOpen");
            Objects.requireNonNull(provenance, "provenance");
        }
    }
}
