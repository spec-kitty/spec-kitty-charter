package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A mission step contract of a {@link Pack}: the structural steps of one mission action, identified by its
 * {@link MissionStepContractIdentifier}. Two contracts are equal when they have the same identity, whatever their other
 * fields hold.
 *
 * <p>It owns its steps and its gate bindings; their order and duplicates are significant. Build one with
 * {@link #builder()}.
 */
public final class MissionStepContract implements CharterArtifact {

    // Duplicate step ids are not refused here: validate mode owns that rule (PackPolicy step rules, research D5).
    private static final ModularRuleset<MissionStepContract> RULES = ModularRuleset.<MissionStepContract>emptyRules()
            .must(Constraints.of(
                    "A mission step contract needs a schema version that is not blank.",
                    contract -> !contract.schemaVersion.isBlank()))
            .must(Constraints.of(
                    "A mission step contract needs an action that is not blank.",
                    contract -> !contract.action.isBlank()))
            .must(Constraints.of(
                    "A mission step contract needs a mission that is not blank.",
                    contract -> !contract.mission.isBlank()))
            .must(Constraints.of(
                    "A mission step contract needs at least one step.", contract -> !contract.steps.isEmpty()));

    private final MissionStepContractIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String action;
    private final String mission;
    private final List<ContractStep> steps;
    private final List<GateBinding> gates;

    /**
     * Reconstructs a mission step contract from its parts; reachable only inside the domain package.
     *
     * @param id the contract's identity
     * @param packId the pack that owns the contract
     * @param schemaVersion the schema version text
     * @param action the action the contract structures
     * @param mission the mission the action belongs to
     * @param steps the steps, in authored order with duplicates kept
     * @param gates the gate bindings, in authored order with duplicates kept
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    MissionStepContract(
            MissionStepContractIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String action,
            String mission,
            List<ContractStep> steps,
            List<GateBinding> gates) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.action = Objects.requireNonNull(action, "action");
        this.mission = Objects.requireNonNull(mission, "mission");
        this.steps = List.copyOf(steps);
        this.gates = List.copyOf(gates);
        RULES.guard(this);
    }

    /**
     * Starts a builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The contract's identity.
     *
     * @return the identifier
     */
    public MissionStepContractIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.MISSION_STEP_CONTRACT;
    }

    /**
     * The pack that owns this contract.
     *
     * @return the pack's identifier
     */
    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public String localId() {
        return id.value();
    }

    /**
     * The contract's {@code schema_version}, as text.
     *
     * @return the schema version
     */
    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(schemaVersion);
    }

    /**
     * The contract's {@code action}.
     *
     * @return the action
     */
    public String action() {
        return action;
    }

    /**
     * The contract's label, which is its {@code action}, as the DRG node's label is.
     *
     * @return the action
     */
    @Override
    public String label() {
        return action;
    }

    /**
     * A mission step contract has no description field.
     *
     * @return always empty
     */
    @Override
    public Optional<String> description() {
        return Optional.empty();
    }

    /**
     * The contract's {@code mission}.
     *
     * @return the mission
     */
    public String mission() {
        return mission;
    }

    /**
     * The steps, in authored order with duplicates kept.
     *
     * @return an immutable list, never empty
     */
    public List<ContractStep> steps() {
        return steps;
    }

    /**
     * The gate bindings, in authored order with duplicates kept.
     *
     * @return an immutable list; empty when the contract declares none
     */
    public List<GateBinding> gates() {
        return gates;
    }

    /**
     * A mission step contract mints no relationships. Its step delegations are values of the artifact; the pinned
     * shard has no step-contract edges, and minting them is #148's.
     *
     * @return an empty list
     */
    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof MissionStepContract that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "MissionStepContract[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for a {@link MissionStepContract}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable MissionStepContractIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String action;
        private @Nullable String mission;
        private final List<ContractStep> steps = new ArrayList<>();
        private final List<GateBinding> gates = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the contract's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(MissionStepContractIdentifier value) {
            this.id = value;
            return this;
        }

        /**
         * Sets the owning pack.
         *
         * @param value the pack's identifier
         * @return this builder
         */
        public Builder packId(PackIdentifier value) {
            this.packId = value;
            return this;
        }

        /**
         * Sets the schema version text.
         *
         * @param value the schema version
         * @return this builder
         */
        public Builder schemaVersion(String value) {
            this.schemaVersion = value;
            return this;
        }

        /**
         * Sets the action.
         *
         * @param value the action
         * @return this builder
         */
        public Builder action(String value) {
            this.action = value;
            return this;
        }

        /**
         * Sets the mission.
         *
         * @param value the mission
         * @return this builder
         */
        public Builder mission(String value) {
            this.mission = value;
            return this;
        }

        /**
         * Appends a step.
         *
         * @param value the step
         * @return this builder
         */
        public Builder step(ContractStep value) {
            this.steps.add(value);
            return this;
        }

        /**
         * Appends a gate binding.
         *
         * @param value the gate binding
         * @return this builder
         */
        public Builder gate(GateBinding value) {
            this.gates.add(value);
            return this;
        }

        /**
         * Builds the mission step contract.
         *
         * @return the validated contract
         * @throws NullPointerException when identity, pack, schema version, action or mission was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public MissionStepContract build() {
            return new MissionStepContract(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(packId, "packId"),
                    Objects.requireNonNull(schemaVersion, "schemaVersion"),
                    Objects.requireNonNull(action, "action"),
                    Objects.requireNonNull(mission, "mission"),
                    steps,
                    gates);
        }
    }
}
