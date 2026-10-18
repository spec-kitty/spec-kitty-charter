package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.Urn;
import java.util.List;

/** The governance that applies to one action of one mission type. */
public record ActionContext(Urn action, Urn missionType, List<GuidanceEntry> guidance) {
    public ActionContext {
        guidance = List.copyOf(guidance);
    }
}
