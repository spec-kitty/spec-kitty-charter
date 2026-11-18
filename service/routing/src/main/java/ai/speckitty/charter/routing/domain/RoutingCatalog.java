package ai.speckitty.charter.routing.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A model-task routing catalog, the artifact of the kind {@link ArtifactKind#ROUTING_CATALOG} (ADR-0080 decision 5): the
 * task types, the models with their fit per task type, cost and latency, the routing policy and the sources the content
 * came from. It relates to nothing, so it derives no relationship; an org pack replaces it by its id across levels. The
 * catalog has no schema version: the pack schema versions the shape.
 *
 * @param id the catalog's id, a letter-first kebab slug
 * @param packId the pack that holds it
 * @param description what the catalog is for, when written
 * @param generatedAt when its content was generated, as written: provenance only, never read by a rule
 * @param taskTypes the task types, at least one
 * @param models the models, at least one
 * @param policy how it ranks
 * @param sources where its content came from, at least one
 */
public record RoutingCatalog(
        String id,
        PackIdentifier packId,
        Optional<String> description,
        String generatedAt,
        List<TaskType> taskTypes,
        List<ModelEntry> models,
        RoutingPolicy policy,
        List<CatalogSource> sources)
        implements CharterArtifact {

    /**
     * Checks the id and the texts, and copies the lists, each of which holds at least one item.
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
    public RoutingCatalog {
        Checks.id(Objects.requireNonNull(id, "id"));
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(description, "description").ifPresent(Checks::text);
        Checks.dateTime(Objects.requireNonNull(generatedAt, "generatedAt"));
        taskTypes = Checks.notEmpty(taskTypes);
        models = Checks.notEmpty(models);
        Objects.requireNonNull(policy, "policy");
        sources = Checks.notEmpty(sources);
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.ROUTING_CATALOG;
    }

    @Override
    public String localId() {
        return id;
    }

    @Override
    public Optional<String> schemaVersion() {
        return Optional.empty();
    }

    /**
     * The catalog's label: its id, since a catalog has no title of its own.
     *
     * @return the id
     */
    @Override
    public String label() {
        return id;
    }

    /**
     * The task type the catalog declares under an id.
     *
     * @param taskTypeId the task type's id
     * @return the task type, or empty when the catalog does not declare it
     */
    public Optional<TaskType> taskType(String taskTypeId) {
        return taskTypes.stream().filter(type -> type.id().equals(taskTypeId)).findFirst();
    }
}
