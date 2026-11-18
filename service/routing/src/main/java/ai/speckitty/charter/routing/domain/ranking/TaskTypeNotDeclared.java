package ai.speckitty.charter.routing.domain.ranking;

import java.io.Serial;

/**
 * A recommendation asked for a task type the catalog in force does not declare (ADR-0080 decision 9): the read maps it
 * to {@code 404 task_type_not_found}.
 */
public final class TaskTypeNotDeclared extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The task type asked for. */
    private final String taskType;

    /** The catalog's id. */
    private final String catalogId;

    /**
     * The refusal.
     *
     * @param taskType the task type asked for
     * @param catalogId the catalog in force
     */
    public TaskTypeNotDeclared(String taskType, String catalogId) {
        super("the routing catalog " + catalogId + " declares no task type " + taskType);
        this.taskType = taskType;
        this.catalogId = catalogId;
    }

    /**
     * The task type asked for.
     *
     * @return its id
     */
    public String taskType() {
        return taskType;
    }

    /**
     * The catalog in force.
     *
     * @return its id
     */
    public String catalogId() {
        return catalogId;
    }
}
