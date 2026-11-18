package ai.speckitty.charter.routing.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * How well a model fits one task type (ADR-0080 decision 5). The score is an exact decimal (NFR-002), so equal fits
 * compare equal.
 *
 * @param taskType the id of the task type, which the catalog declares (a content rule)
 * @param score the fit, from 0 to 1
 * @param confidence how sure the catalog is of the score, when written
 * @param rationale why the model fits, when written
 */
public record TaskFit(String taskType, BigDecimal score, Optional<Grade> confidence, Optional<String> rationale) {

    /**
     * Checks the task type id, the score's range and the rationale.
     *
     * @param taskType the task type id
     * @param score the score
     * @param confidence the confidence, possibly empty
     * @param rationale the rationale, possibly empty
     */
    public TaskFit {
        Checks.id(Objects.requireNonNull(taskType, "taskType"));
        Checks.unit(Objects.requireNonNull(score, "score"));
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(rationale, "rationale").ifPresent(Checks::text);
    }
}
