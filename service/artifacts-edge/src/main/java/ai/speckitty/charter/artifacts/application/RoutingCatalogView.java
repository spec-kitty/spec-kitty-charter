package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A routing catalog as the edge answers it (ADR-0080 decision 6), the packs adapter's copy of the routing slice's
 * value, so the application ring names no other slice. Every token is the wire's; every number the exact decimal the
 * file writes.
 *
 * @param id the catalog's id
 * @param packId the pack that holds it
 * @param description its description, when declared
 * @param generatedAt when its content was generated, as written
 * @param taskTypes its task types, in file order
 * @param models its models, in file order
 * @param policy its routing policy
 * @param sources its sources, in file order
 */
public record RoutingCatalogView(
        String id,
        PackIdentifier packId,
        Optional<String> description,
        String generatedAt,
        List<TaskTypeView> taskTypes,
        List<ModelView> models,
        PolicyView policy,
        List<SourceView> sources) {

    /**
     * Checks the members and copies the lists.
     *
     * @param id the id
     * @param packId the pack
     * @param description the description, possibly empty
     * @param generatedAt the generation time
     * @param taskTypes the task types
     * @param models the models
     * @param policy the policy
     * @param sources the sources
     */
    public RoutingCatalogView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(generatedAt, "generatedAt");
        taskTypes = List.copyOf(taskTypes);
        models = List.copyOf(models);
        Objects.requireNonNull(policy, "policy");
        sources = List.copyOf(sources);
    }

    /**
     * A task type.
     *
     * @param id its id
     * @param title its title
     * @param description its description, when declared
     * @param qualitySensitivity its quality sensitivity token, when declared
     * @param costSensitivity its cost sensitivity token, when declared
     */
    public record TaskTypeView(
            String id,
            String title,
            Optional<String> description,
            Optional<String> qualitySensitivity,
            Optional<String> costSensitivity) {

        /**
         * Checks the members.
         *
         * @param id the id
         * @param title the title
         * @param description the description
         * @param qualitySensitivity the quality sensitivity
         * @param costSensitivity the cost sensitivity
         */
        public TaskTypeView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(qualitySensitivity, "qualitySensitivity");
            Objects.requireNonNull(costSensitivity, "costSensitivity");
        }
    }

    /**
     * A model.
     *
     * @param id its id
     * @param provider its provider
     * @param family its family, when declared
     * @param tools its tools
     * @param strengths its strengths
     * @param weaknesses its weaknesses
     * @param taskFit its fits
     * @param cost its cost
     * @param latencyTier its latency tier token, when declared
     */
    public record ModelView(
            String id,
            String provider,
            Optional<String> family,
            List<String> tools,
            List<String> strengths,
            List<String> weaknesses,
            List<FitView> taskFit,
            CostView cost,
            Optional<String> latencyTier) {

        /**
         * Checks the members and copies the lists.
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
        public ModelView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(family, "family");
            tools = List.copyOf(tools);
            strengths = List.copyOf(strengths);
            weaknesses = List.copyOf(weaknesses);
            taskFit = List.copyOf(taskFit);
            Objects.requireNonNull(cost, "cost");
            Objects.requireNonNull(latencyTier, "latencyTier");
        }
    }

    /**
     * A model's fit for one task type.
     *
     * @param taskType the task type's id
     * @param score the fit, an exact decimal
     * @param confidence its confidence token, when declared
     * @param rationale its rationale, when declared
     */
    public record FitView(String taskType, BigDecimal score, Optional<String> confidence, Optional<String> rationale) {

        /**
         * Checks the members.
         *
         * @param taskType the task type
         * @param score the score
         * @param confidence the confidence
         * @param rationale the rationale
         */
        public FitView {
            Objects.requireNonNull(taskType, "taskType");
            Objects.requireNonNull(score, "score");
            Objects.requireNonNull(confidence, "confidence");
            Objects.requireNonNull(rationale, "rationale");
        }
    }

    /**
     * A model's cost.
     *
     * @param tier its cost tier token
     * @param inputPer1mUsd its input price, when declared
     * @param outputPer1mUsd its output price, when declared
     * @param currency its currency, when declared
     * @param pricingSourceUrl where its prices come from, when declared
     */
    public record CostView(
            String tier,
            Optional<BigDecimal> inputPer1mUsd,
            Optional<BigDecimal> outputPer1mUsd,
            Optional<String> currency,
            Optional<String> pricingSourceUrl) {

        /**
         * Checks the members.
         *
         * @param tier the tier
         * @param inputPer1mUsd the input price
         * @param outputPer1mUsd the output price
         * @param currency the currency
         * @param pricingSourceUrl the pricing source
         */
        public CostView {
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(inputPer1mUsd, "inputPer1mUsd");
            Objects.requireNonNull(outputPer1mUsd, "outputPer1mUsd");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(pricingSourceUrl, "pricingSourceUrl");
        }
    }

    /**
     * A routing policy.
     *
     * @param objective its objective token
     * @param quality the quality weight
     * @param cost the cost weight
     * @param risk the risk weight
     * @param latency the latency weight
     * @param tierConstraints its tier constraints
     */
    public record PolicyView(
            String objective,
            BigDecimal quality,
            BigDecimal cost,
            BigDecimal risk,
            BigDecimal latency,
            List<ConstraintView> tierConstraints) {

        /**
         * Checks the members and copies the list.
         *
         * @param objective the objective
         * @param quality the quality weight
         * @param cost the cost weight
         * @param risk the risk weight
         * @param latency the latency weight
         * @param tierConstraints the constraints
         */
        public PolicyView {
            Objects.requireNonNull(objective, "objective");
            Objects.requireNonNull(quality, "quality");
            Objects.requireNonNull(cost, "cost");
            Objects.requireNonNull(risk, "risk");
            Objects.requireNonNull(latency, "latency");
            tierConstraints = List.copyOf(tierConstraints);
        }
    }

    /**
     * A tier constraint.
     *
     * @param taskType the task type's id
     * @param maxTier the highest cost tier token advised
     */
    public record ConstraintView(String taskType, String maxTier) {

        /**
         * Checks the members.
         *
         * @param taskType the task type
         * @param maxTier the tier
         */
        public ConstraintView {
            Objects.requireNonNull(taskType, "taskType");
            Objects.requireNonNull(maxTier, "maxTier");
        }
    }

    /**
     * A source.
     *
     * @param name its name
     * @param url where it is
     * @param accessMethod how it was read, as a token
     * @param snapshotAt when it was read, as written
     * @param licenseNotes its licence notes, when declared
     */
    public record SourceView(
            String name, String url, String accessMethod, String snapshotAt, Optional<String> licenseNotes) {

        /**
         * Checks the members.
         *
         * @param name the name
         * @param url the url
         * @param accessMethod the access method
         * @param snapshotAt the snapshot time
         * @param licenseNotes the licence notes
         */
        public SourceView {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(accessMethod, "accessMethod");
            Objects.requireNonNull(snapshotAt, "snapshotAt");
            Objects.requireNonNull(licenseNotes, "licenseNotes");
        }
    }
}
