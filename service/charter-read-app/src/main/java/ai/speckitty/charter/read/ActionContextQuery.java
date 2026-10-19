package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.CharterOffering;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.RefusalCode;
import ai.speckitty.charter.domain.Urn;
import java.util.List;
import java.util.Objects;

/**
 * Action-scoped context: the offering's walk for one action of one mission type, over the merged graph without
 * activation filtering (contracts/charter/scenarios/action_context.feature). Five breadth-first steps, see
 * {@link ActionContextWalk}: scope from the action node only, {@code requires}, {@code suggests} up to the depth,
 * the {@code requires} of the suggested artifacts, and {@code vocabulary}. What applies in one project (activation,
 * selection roots) is a later operation's, not this read's.
 */
public final class ActionContextQuery {

    /** The most {@code suggests} hops a request may ask for. A later version may raise it. */
    public static final int MAX_DEPTH = 2;

    /** The fewest {@code suggests} hops a request may ask for: 0 lists no suggested artifact. */
    public static final int MIN_DEPTH = 0;

    /** The {@code suggests} hops followed when a request names no depth. */
    public static final int DEFAULT_DEPTH = 2;

    private final ConsistentLoad load;

    /**
     * A query over the offering {@code packs} loads.
     *
     * @param load the consistent load; the offering is loaded afresh for every answer
     */
    public ActionContextQuery(ConsistentLoad load) {
        this.load = Objects.requireNonNull(load, "load");
    }

    /**
     * The context of one action of one mission type.
     *
     * @param missionType the mission type, for example {@code software-dev}
     * @param action the action, for example {@code plan}
     * @param depth how many {@code suggests} hops to follow, from {@link #MIN_DEPTH} to {@link #MAX_DEPTH}
     * @return the artifacts the offering says govern the action, with the offering's load findings
     * @throws IllegalArgumentException when {@code depth} is outside {@link #MIN_DEPTH} to {@link #MAX_DEPTH}; the
     *     inbound adapter refuses such a request before it asks
     * @throws CharterRefusal with {@link RefusalCode#MISSION_TYPE_NOT_FOUND} or {@link RefusalCode#ACTION_NOT_FOUND}
     *     when the merged graph has no such node, with {@link RefusalCode#STALE_INPUT} when the packs changed while
     *     the read was answered, or the repository's refusal when the offering cannot be loaded
     */
    public ActionContext answer(String missionType, String action, int depth) {
        if (depth < MIN_DEPTH || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("depth " + depth + " is outside " + MIN_DEPTH + " to " + MAX_DEPTH);
        }
        ConsistentLoad.Loaded loaded = load.load();
        CharterOffering offering = loaded.offering();
        DoctrineGraph graph = offering.mergedGraph();
        Urn missionTypeUrn = Urn.missionType(missionType);
        Urn actionUrn = Urn.action(missionType, action);
        if (!graph.contains(missionTypeUrn)) {
            throw new CharterRefusal(
                    RefusalCode.MISSION_TYPE_NOT_FOUND, missionTypeUrn + " is not a node of the merged graph.");
        }
        if (!graph.contains(actionUrn)) {
            throw new CharterRefusal(RefusalCode.ACTION_NOT_FOUND, actionUrn + " is not a node of the merged graph.");
        }
        List<GuidanceEntry> guidance = new ActionContextWalk(graph, actionUrn, missionTypeUrn).run(depth);
        return new ActionContext(
                actionUrn,
                missionTypeUrn,
                depth,
                offering.governanceFingerprint(),
                loaded.sourceFingerprint(),
                guidance,
                diagnostics(offering));
    }

    private static List<Diagnostic> diagnostics(CharterOffering offering) {
        return offering.packs().stream()
                .flatMap(pack -> pack.diagnostics().stream())
                .toList();
    }
}
