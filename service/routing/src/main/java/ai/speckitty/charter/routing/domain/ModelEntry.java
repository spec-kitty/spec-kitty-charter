package ai.speckitty.charter.routing.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One model of a catalog (ADR-0080 decision 5): who provides it, how well it fits each task type, what it costs and how
 * fast it answers.
 *
 * @param id the model's id, unique in its catalog (a content rule)
 * @param provider who provides it
 * @param family its family, when written
 * @param tools the tools it supports, as written
 * @param strengths its strengths, as written
 * @param weaknesses its weaknesses, as written
 * @param taskFit its fit per task type, at least one, at most one per task type (a content rule)
 * @param cost what it costs
 * @param latencyTier how fast it answers, when written
 */
public record ModelEntry(
        String id,
        String provider,
        Optional<String> family,
        List<String> tools,
        List<String> strengths,
        List<String> weaknesses,
        List<TaskFit> taskFit,
        ModelCost cost,
        Optional<Grade> latencyTier) {

    /**
     * Checks the texts, copies the lists and requires a fit.
     *
     * @param id the id
     * @param provider the provider
     * @param family the family, possibly empty
     * @param tools the tools
     * @param strengths the strengths
     * @param weaknesses the weaknesses
     * @param taskFit the fits, at least one
     * @param cost the cost
     * @param latencyTier the latency tier, possibly empty
     */
    public ModelEntry {
        Checks.text(Objects.requireNonNull(id, "id"));
        Checks.text(Objects.requireNonNull(provider, "provider"));
        Objects.requireNonNull(family, "family").ifPresent(Checks::text);
        tools = List.copyOf(tools);
        strengths = List.copyOf(strengths);
        weaknesses = List.copyOf(weaknesses);
        taskFit = Checks.notEmpty(taskFit);
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(latencyTier, "latencyTier");
    }

    /**
     * The model's fit for a task type.
     *
     * @param taskTypeId the task type's id
     * @return the fit, or empty when the model has none for it
     */
    public Optional<TaskFit> fitFor(String taskTypeId) {
        return taskFit.stream().filter(fit -> fit.taskType().equals(taskTypeId)).findFirst();
    }
}
