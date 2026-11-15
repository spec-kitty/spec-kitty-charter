package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every candidate of a substep's {@code delegates_to} resolves to an artifact of the delegation's kind, and the step's
 * action has a {@code scope} edge to it (operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9, FR-005). A delegation is data;
 * the edge puts the candidate in force for the step's context.
 *
 * <p>The rule judges, at load and in validate mode, what one pack can answer alone (squad finding M3):
 *
 * <ul>
 *   <li>{@value #UNRESOLVED}: a bare candidate, or one written with the pack's own name, is an artifact of the kind the
 *       pack holds. A candidate written with another pack's name resolves in the closure, which the compose judges
 *       ({@link #qualifiedCandidates});
 *   <li>{@value #UNSCOPED}: one of the pack's graph documents holds {@code action:<type>/<step> -scope-> <candidate>},
 *       its source written bare or with the pack's own name, its target the candidate's reference as the candidate
 *       writes it ({@code [<pack>::]<kind>:<id>}: a qualified candidate's edge is the qualified edge).
 * </ul>
 *
 * <p>An unresolved candidate is not judged for its edge as well, so each candidate has at most one fault. A mission type
 * whose id a second file repeats is judged once. A fault is at the mission type file, at the candidate's place. It is
 * pure.
 */
public final class SubstepDelegationRule {

    /** The violation code of a candidate that does not resolve to an artifact of its kind. */
    public static final String UNRESOLVED = "substep_delegation_unresolved";

    /** The violation code of a candidate the step's action has no {@code scope} edge to. */
    public static final String UNSCOPED = "substep_delegation_unscoped";

    private static final String CANDIDATES = "candidates";

    private SubstepDelegationRule() {}

    /**
     * One fault.
     *
     * @param file the mission type file
     * @param code {@value #UNRESOLVED} or {@value #UNSCOPED}
     * @param place the candidate's place in the file
     * @param reason what is wrong, naming the type, the step, the substep and the candidate
     */
    public record Fault(String file, String code, List<String> place, String reason) {
        /**
         * Checks the fields and copies the place.
         *
         * @param file the file
         * @param code the code
         * @param place the place
         * @param reason the reason
         */
        public Fault {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(code, "code");
            place = List.copyOf(place);
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * One candidate written with another pack's name, for the compose to resolve in the closure.
     *
     * @param file the mission type file
     * @param missionTypeId the mission type's id
     * @param stepId the step's id
     * @param substepId the substep's id
     * @param reference the candidate's reference, {@code <pack>::<kind>:<id>}
     */
    public record QualifiedCandidate(
            String file, String missionTypeId, String stepId, String substepId, PackQualifiedReference reference) {
        /**
         * Checks the fields.
         *
         * @param file the file
         * @param missionTypeId the type's id
         * @param stepId the step's id
         * @param substepId the substep's id
         * @param reference the reference
         */
        public QualifiedCandidate {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(missionTypeId, "missionTypeId");
            Objects.requireNonNull(stepId, "stepId");
            Objects.requireNonNull(substepId, "substepId");
            Objects.requireNonNull(reference, "reference");
        }

        /**
         * What a refusal says of the candidate, naming the code.
         *
         * @param holder the pack the candidate names, as the refusal labels it
         * @return the reason
         */
        public String unresolved(String holder) {
            return "the substep " + substepId + " of the step " + stepId + " of mission_type:" + missionTypeId
                    + " (" + file + ") delegates to " + reference + ", which " + holder + " does not hold ("
                    + UNRESOLVED + ")";
        }
    }

    /**
     * Judges a pack's mission types.
     *
     * @param packName the name composition knows the pack by; a reference written with it is the pack's own
     * @param types the mission types the pack holds, in file order
     * @param held the artifacts the pack holds, as bare URNs {@code <kind>:<id>}
     * @param edges the edges of the pack's graph documents, in document order
     * @return the faults, per type in file order, per substep in written order, per candidate in written order
     */
    public static List<Fault> judge(
            String packName, List<MissionTypeFacts> types, Set<String> held, List<MissionStepLinkRule.Edge> edges) {
        Set<String> scoped = new HashSet<>();
        for (MissionStepLinkRule.Edge edge : edges) {
            if (RelationType.SCOPE.token().equals(edge.relation())) {
                own(edge.source(), packName)
                        .ifPresent(source -> own(edge.target(), packName)
                                .or(() -> reference(edge.target()).map(PackQualifiedReference::toString))
                                .ifPresent(target -> scoped.add(source + " " + target)));
            }
        }
        List<Fault> faults = new ArrayList<>();
        Set<String> judged = new HashSet<>();
        for (MissionTypeFacts type : types) {
            if (!judged.add(type.id())) {
                continue;
            }
            for (MissionTypeFacts.SubstepFacts substep : type.substeps()) {
                substep.delegation()
                        .ifPresent(delegation -> judge(packName, type, substep, delegation, held, scoped)
                                .forEach(faults::add));
            }
        }
        return List.copyOf(faults);
    }

    /**
     * The candidates of a pack's mission types written with another pack's name, which the compose resolves in the
     * closure.
     *
     * @param packName the name composition knows the pack by
     * @param types the mission types the pack holds
     * @return the candidates, in the order {@link #judge} visits them
     */
    public static List<QualifiedCandidate> qualifiedCandidates(String packName, List<MissionTypeFacts> types) {
        List<QualifiedCandidate> out = new ArrayList<>();
        for (MissionTypeFacts type : types) {
            for (MissionTypeFacts.SubstepFacts substep : type.substeps()) {
                if (substep.delegation().isEmpty()) {
                    continue;
                }
                MissionTypeFacts.Delegation delegation = substep.delegation().get();
                for (String candidate : delegation.candidates()) {
                    reference(delegation.kind(), candidate)
                            .filter(reference -> reference
                                    .packName()
                                    .filter(name -> !name.equals(packName))
                                    .isPresent())
                            .ifPresent(reference -> out.add(new QualifiedCandidate(
                                    type.file(), type.id(), substep.stepId(), substep.id(), reference)));
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * The {@code scope} edges the pack's mission types need, one per candidate, each as {@link #scopeKey} spells it:
     * what a relationship removal must leave in place (FR-005).
     *
     * @param packName the name composition knows the pack by
     * @param types the mission types the pack holds
     * @return the keys of the needed edges
     */
    public static Set<String> neededScopes(String packName, List<MissionTypeFacts> types) {
        Set<String> needed = new HashSet<>();
        for (MissionTypeFacts type : types) {
            for (MissionTypeFacts.SubstepFacts substep : type.substeps()) {
                substep.delegation()
                        .ifPresent(delegation -> delegation.candidates().stream()
                                .map(candidate -> reference(delegation.kind(), candidate))
                                .flatMap(Optional::stream)
                                .forEach(reference -> needed.add(
                                        scopeKey(packName, Urn.action(type.id(), substep.stepId()), reference))));
            }
        }
        return needed;
    }

    /**
     * One {@code scope} edge from a step's action as the rule matches it: the action, then the target, bare when written
     * bare or with the pack's own name, else as written.
     *
     * @param packName the name composition knows the pack by
     * @param action the step's action
     * @param target the edge's target as written
     * @return the key
     */
    public static String scopeKey(String packName, Urn action, PackQualifiedReference target) {
        boolean ownPack = target.packName().map(packName::equals).orElse(true);
        return action + " " + (ownPack ? target.urn() : target.toString());
    }

    private static List<Fault> judge(
            String packName,
            MissionTypeFacts type,
            MissionTypeFacts.SubstepFacts substep,
            MissionTypeFacts.Delegation delegation,
            Set<String> held,
            Set<String> scoped) {
        List<Fault> faults = new ArrayList<>();
        String action = Urn.action(type.id(), substep.stepId()).toString();
        for (int k = 0; k < delegation.candidates().size(); k++) {
            String candidate = delegation.candidates().get(k);
            Optional<PackQualifiedReference> reference = reference(delegation.kind(), candidate);
            if (reference.isEmpty()) {
                // A candidate or a kind the schema refuses is refused there, on its own.
                continue;
            }
            List<String> place = new ArrayList<>(substep.place());
            place.addAll(List.of("delegates_to", CANDIDATES, Integer.toString(k)));
            String where = "the substep " + substep.id() + " of the step " + substep.stepId() + " of mission_type:"
                    + type.id() + " delegates to " + delegation.kind() + " " + candidate;
            boolean ownPack = reference.get().packName().map(packName::equals).orElse(true);
            if (ownPack && !held.contains(reference.get().urn())) {
                faults.add(new Fault(
                        type.file(),
                        UNRESOLVED,
                        place,
                        where + ", which the pack does not hold as a " + delegation.kind()));
                continue;
            }
            String target = ownPack ? reference.get().urn() : reference.get().toString();
            if (!scoped.contains(action + " " + target)) {
                faults.add(new Fault(
                        type.file(),
                        UNSCOPED,
                        place,
                        where + ", and no graph document holds " + action + " scope " + target));
            }
        }
        return faults;
    }

    /**
     * The reference a candidate makes for a delegation of this kind, {@code [<pack>::]<kind>:<id>}; empty when the kind
     * is no scope target of an action or the candidate is malformed.
     *
     * @param kindToken the delegation's kind token
     * @param candidate the candidate as written
     * @return the reference
     */
    static Optional<PackQualifiedReference> reference(String kindToken, String candidate) {
        Optional<ArtifactKind> kind = ArtifactKind.fromToken(kindToken)
                .filter(token -> RelationType.SCOPE.allows(NodeKind.ACTION, NodeKind.of(token)));
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PackQualifiedReference.typed(kindToken, candidate));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** The URN text of a reference written bare or with the pack's own name; empty for another pack's or a malformed. */
    private static Optional<String> own(String written, String packName) {
        return reference(written)
                .filter(reference -> reference.packName().map(packName::equals).orElse(true))
                .map(PackQualifiedReference::urn);
    }

    private static Optional<PackQualifiedReference> reference(String written) {
        try {
            return Optional.of(PackQualifiedReference.parse(written));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
