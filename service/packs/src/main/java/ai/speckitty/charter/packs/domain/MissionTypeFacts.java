package ai.speckitty.charter.packs.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One mission type of a pack as plain facts (operator rulings DM-01M7B1YQR5ZKQPDW6DX64KPNRN and
 * DM-01M7B1YH1GF15RD0G4ZPSA8KW9): the file that declares it, its id, the ids of its steps and its on-demand steps, and
 * the substeps of each. The packs domain does not judge a mission type through the missions domain that types it, so
 * {@link MissionStepLinkRule} and {@link SubstepDelegationRule} judge these.
 *
 * @param file the pack-relative path of the mission type file
 * @param id the mission type's id
 * @param stepIds the ids of its steps, then of its on-demand steps, as written
 * @param substeps the substeps of every step, steps first, then on-demand steps, each in written order
 */
public record MissionTypeFacts(String file, String id, List<String> stepIds, List<SubstepFacts> substeps) {
    /**
     * Checks the fields and copies the lists.
     *
     * @param file the file
     * @param id the id
     * @param stepIds the step ids
     * @param substeps the substeps
     */
    public MissionTypeFacts {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(id, "id");
        stepIds = List.copyOf(stepIds);
        substeps = List.copyOf(substeps);
    }

    /**
     * Whether a substep id stands twice within one step: the domain refuses such a type, so the load holds none, and no
     * rule over the facts judges it (validate mode reports {@link PackFindingCode#DUPLICATE_STEP_ID} instead).
     *
     * @return {@code true} when a step repeats a substep id
     */
    public boolean repeatsASubstepId() {
        Set<String> seen = new HashSet<>();
        return substeps.stream()
                .anyMatch(substep -> !seen.add(substep.list() + "/" + substep.stepIndex() + "/" + substep.id()));
    }

    /**
     * One substep as written.
     *
     * @param list the list its step stands in: {@code steps} or {@code on_demand_steps}
     * @param stepIndex the index of its step in that list
     * @param stepId the id of its step
     * @param index its index in the step's {@code substeps}
     * @param id its id
     * @param delegation its {@code delegates_to}, when it has one
     */
    public record SubstepFacts(
            String list, int stepIndex, String stepId, int index, String id, Optional<Delegation> delegation) {
        /**
         * Checks the fields.
         *
         * @param list the list
         * @param stepIndex the step's index
         * @param stepId the step's id
         * @param index the substep's index
         * @param id the substep's id
         * @param delegation the delegation, possibly empty
         */
        public SubstepFacts {
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(stepId, "stepId");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(delegation, "delegation");
        }

        /**
         * The place of the substep in its file, as a finding names it.
         *
         * @return {@code [<list>, <stepIndex>, substeps, <index>]}
         */
        public List<String> place() {
            return List.of(list, Integer.toString(stepIndex), "substeps", Integer.toString(index));
        }
    }

    /**
     * A substep's {@code delegates_to} as written.
     *
     * @param kind the kind token
     * @param candidates the candidates, written {@code [<pack>::]<id>}, in written order
     */
    public record Delegation(String kind, List<String> candidates) {
        /**
         * Checks the kind and copies the candidates.
         *
         * @param kind the kind
         * @param candidates the candidates
         */
        public Delegation {
            Objects.requireNonNull(kind, "kind");
            candidates = List.copyOf(candidates);
        }
    }
}
