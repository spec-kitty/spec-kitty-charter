package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The content rules of a mission type's steps (#554, FR-001, T031): a substep id is unique within its step. Each is a
 * kernel rule over the facts of the one read ({@link PackContents}); it reads no file and knows no surface.
 */
public final class StepRules {

    /**
     * A substep id declared twice within one step of one mission type (FR-004), judged over that type's facts: one
     * {@link PackCodes#DUPLICATE_STEP_ID} per repeated id, at its second occurrence ({@code /<list>/<step>/substeps/<n>/id}
     * of the type's file; R2 row 9), in written order.
     */
    private static final Rule<MissionTypeFacts> SUBSTEP_ID_REPEATED_IN_TYPE = StepRules::repeatedSubstepIds;

    /** {@link #SUBSTEP_ID_REPEATED_IN_TYPE} over every mission type of the pack, in file order. */
    public static final Rule<PackContents> SUBSTEP_ID_REPEATED =
            SUBSTEP_ID_REPEATED_IN_TYPE.forEach(PackContents::missionTypes);

    /** Every step rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES =
            ModularRuleset.<PackContents>emptyRules().must(SUBSTEP_ID_REPEATED);

    private StepRules() {}

    /**
     * The repeated substep ids of one mission type: the rule's function, which the store half also records on the
     * aggregate for the composing read (S7).
     *
     * @param type the facts of one mission type
     * @return one finding per repeated id, at its second occurrence, in written order
     */
    public static List<Reason> repeatedSubstepIds(MissionTypeFacts type) {
        List<Reason> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> reported = new HashSet<>();
        for (MissionTypeFacts.SubstepFacts substep : type.substeps()) {
            String key = substep.list() + "/" + substep.stepIndex() + "/" + substep.id();
            if (!seen.add(key) && reported.add(key)) {
                List<String> place = new ArrayList<>(substep.place());
                place.add("id");
                findings.add(Findings.at(
                        PackCodes.DUPLICATE_STEP_ID,
                        type.file(),
                        place,
                        type.file() + ": substep id '" + substep.id() + "' is declared more than once in the step '"
                                + substep.stepId() + "'"));
            }
        }
        return findings;
    }
}
