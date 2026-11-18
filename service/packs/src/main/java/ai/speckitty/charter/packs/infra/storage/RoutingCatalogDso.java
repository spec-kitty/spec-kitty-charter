package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a routing catalog, {@code routing_catalogs/<id>.routing-catalog.yaml} (ADR-0080 decision 5),
 * as the file writes it. A number keeps the {@link Number} the YAML loader gave it (an {@code Integer}, a {@code Long} or
 * a {@code Double}), so the file writes back as it was read; the binding turns it into an exact decimal.
 *
 * @param id the {@code id}
 * @param description the {@code description}, when present
 * @param generatedAt the {@code generated_at}
 * @param taskTypes the {@code task_types}
 * @param models the {@code models}
 * @param policy the {@code routing_policy}
 * @param sources the {@code sources}
 */
record RoutingCatalogDso(
        String id,
        Optional<String> description,
        String generatedAt,
        List<TaskType> taskTypes,
        List<Model> models,
        Policy policy,
        List<Source> sources) {

    /**
     * Checks the members and copies the lists.
     *
     * @param id the id
     * @param description the description, possibly empty
     * @param generatedAt the generation time
     * @param taskTypes the task types
     * @param models the models
     * @param policy the policy
     * @param sources the sources
     */
    RoutingCatalogDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(generatedAt, "generatedAt");
        taskTypes = List.copyOf(taskTypes);
        models = List.copyOf(models);
        Objects.requireNonNull(policy, "policy");
        sources = List.copyOf(sources);
    }

    /**
     * A {@code task_types} item.
     *
     * @param id the {@code id}
     * @param title the {@code title}
     * @param description the {@code description}, when present
     * @param qualitySensitivity the {@code quality_sensitivity}, when present
     * @param costSensitivity the {@code cost_sensitivity}, when present
     */
    record TaskType(
            String id,
            String title,
            Optional<String> description,
            Optional<String> qualitySensitivity,
            Optional<String> costSensitivity) {}

    /**
     * A {@code models} item.
     *
     * @param id the {@code id}
     * @param provider the {@code provider}
     * @param family the {@code family}, when present
     * @param tools the {@code tools}
     * @param strengths the {@code strengths}
     * @param weaknesses the {@code weaknesses}
     * @param taskFit the {@code task_fit}
     * @param cost the {@code cost}
     * @param latencyTier the {@code latency_tier}, when present
     */
    record Model(
            String id,
            String provider,
            Optional<String> family,
            List<String> tools,
            List<String> strengths,
            List<String> weaknesses,
            List<Fit> taskFit,
            Cost cost,
            Optional<String> latencyTier) {

        /**
         * Copies the lists.
         *
         * @param id the id
         * @param provider the provider
         * @param family the family
         * @param tools the tools
         * @param strengths the strengths
         * @param weaknesses the weaknesses
         * @param taskFit the fits
         * @param cost the cost
         * @param latencyTier the latency tier
         */
        Model {
            tools = List.copyOf(tools);
            strengths = List.copyOf(strengths);
            weaknesses = List.copyOf(weaknesses);
            taskFit = List.copyOf(taskFit);
        }
    }

    /**
     * A {@code task_fit} item.
     *
     * @param taskType the {@code task_type}
     * @param score the {@code score}, as the loader read it
     * @param confidence the {@code confidence}, when present
     * @param rationale the {@code rationale}, when present
     */
    record Fit(String taskType, Number score, Optional<String> confidence, Optional<String> rationale) {}

    /**
     * A model's {@code cost}.
     *
     * @param tier the {@code tier}
     * @param inputPer1mUsd the {@code input_per_1m_usd}, when present
     * @param outputPer1mUsd the {@code output_per_1m_usd}, when present
     * @param currency the {@code currency}, when present
     * @param pricingSourceUrl the {@code pricing_source_url}, when present
     */
    record Cost(
            String tier,
            Optional<Number> inputPer1mUsd,
            Optional<Number> outputPer1mUsd,
            Optional<String> currency,
            Optional<String> pricingSourceUrl) {}

    /**
     * The {@code routing_policy}.
     *
     * @param objective the {@code objective}
     * @param weights the {@code weights}: quality, cost, risk and latency, as the loader read them
     * @param tierConstraints the {@code tier_constraints}
     */
    record Policy(String objective, Weights weights, List<Constraint> tierConstraints) {

        /**
         * Copies the list.
         *
         * @param objective the objective
         * @param weights the weights
         * @param tierConstraints the tier constraints
         */
        Policy {
            tierConstraints = List.copyOf(tierConstraints);
        }
    }

    /**
     * The {@code weights}.
     *
     * @param quality the {@code quality}
     * @param cost the {@code cost}
     * @param risk the {@code risk}
     * @param latency the {@code latency}
     */
    record Weights(Number quality, Number cost, Number risk, Number latency) {}

    /**
     * A {@code tier_constraints} item.
     *
     * @param taskType the {@code task_type}
     * @param maxTier the {@code max_tier}
     */
    record Constraint(String taskType, String maxTier) {}

    /**
     * A {@code sources} item.
     *
     * @param name the {@code name}
     * @param url the {@code url}
     * @param accessMethod the {@code access_method}
     * @param snapshotAt the {@code snapshot_at}
     * @param licenseNotes the {@code license_notes}, when present
     */
    record Source(String name, String url, String accessMethod, String snapshotAt, Optional<String> licenseNotes) {}
}
