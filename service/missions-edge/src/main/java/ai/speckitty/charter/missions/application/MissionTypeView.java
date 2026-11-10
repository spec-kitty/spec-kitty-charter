package ai.speckitty.charter.missions.application;

import ai.speckitty.charter.missions.domain.MissionType;
import java.util.List;
import java.util.Objects;

/**
 * A mission type as its read serves it: its stored fields, its steps in stored order then its on-demand steps, and its
 * skeletons ordered by name in code-point order.
 *
 * @param type the mission type, for its stored fields
 * @param steps its steps, then its on-demand steps
 * @param skeletons its skeletons, by name
 */
public record MissionTypeView(MissionType type, List<StepEntry> steps, List<SkeletonEntry> skeletons) {

    /**
     * A view, its lists copied.
     *
     * @param type the mission type
     * @param steps its steps
     * @param skeletons its skeletons
     */
    public MissionTypeView {
        Objects.requireNonNull(type, "type");
        steps = List.copyOf(steps);
        skeletons = List.copyOf(skeletons);
    }

    /**
     * One step as its type lists it.
     *
     * @param id the step's id
     * @param title the step's title
     * @param onDemand whether it is one of the type's on-demand steps
     */
    public record StepEntry(String id, String title, boolean onDemand) {}

    /**
     * One skeleton as its type lists it.
     *
     * @param name its one-segment name (D9)
     * @param path its path beside the type, {@code templates/} and its path there
     */
    public record SkeletonEntry(String name, String path) {}
}
