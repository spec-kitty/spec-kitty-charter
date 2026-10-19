package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.Urn;
import java.util.List;

/**
 * The governance that applies to one action of one mission type.
 *
 * @param action the action's URN
 * @param missionType the mission type's URN
 * @param guidance the artifacts in scope, sorted by URN; empty when nothing is in scope
 */
public record ActionContext(Urn action, Urn missionType, List<GuidanceEntry> guidance) {
    /**
     * Copies {@code guidance}.
     *
     * @param action the action's URN
     * @param missionType the mission type's URN
     * @param guidance the artifacts in scope
     */
    public ActionContext {
        guidance = List.copyOf(guidance);
    }
}
