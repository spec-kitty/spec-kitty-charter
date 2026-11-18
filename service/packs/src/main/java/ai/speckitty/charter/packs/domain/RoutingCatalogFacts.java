package ai.speckitty.charter.packs.domain;

import java.util.List;
import java.util.Objects;

/**
 * One routing catalog of a pack as plain facts (ADR-0080 decision 5): the file that declares it, its id, the ids of its
 * task types, the fits of each model and the task types of its tier constraints, each in written order. The packs domain
 * does not judge a catalog through the routing domain that types it, so {@code RoutingCatalogRules} judges these.
 *
 * @param file the pack-relative path of the catalog file
 * @param id the catalog's id
 * @param taskTypeIds the ids of its task types, as written
 * @param models its models, as written
 * @param constraintTaskTypes the task type of each tier constraint, as written
 */
public record RoutingCatalogFacts(
        String file, String id, List<String> taskTypeIds, List<ModelFacts> models, List<String> constraintTaskTypes) {

    /**
     * Checks the fields and copies the lists.
     *
     * @param file the file
     * @param id the id
     * @param taskTypeIds the task type ids
     * @param models the models
     * @param constraintTaskTypes the constraint task types
     */
    public RoutingCatalogFacts {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(id, "id");
        taskTypeIds = List.copyOf(taskTypeIds);
        models = List.copyOf(models);
        constraintTaskTypes = List.copyOf(constraintTaskTypes);
    }

    /**
     * One model as written.
     *
     * @param id the model's id
     * @param fitTaskTypes the task type of each of its fits, as written
     */
    public record ModelFacts(String id, List<String> fitTaskTypes) {

        /**
         * Checks the id and copies the list.
         *
         * @param id the id
         * @param fitTaskTypes the fits' task types
         */
        public ModelFacts {
            Objects.requireNonNull(id, "id");
            fitTaskTypes = List.copyOf(fitTaskTypes);
        }
    }
}
