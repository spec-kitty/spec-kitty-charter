package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The two compose checks of a step enhancement (ADR-0066 decision 8, FR-002), each a pure rule over a composed closure:
 *
 * <ul>
 *   <li><strong>C37, the target held</strong>: the step {@code action:M/s} a step enhancement enhances is held
 *       ({@link StepHolding#heldByPack}, OD-9) by the pack its {@code enhances} edge names, and that pack is the
 *       enhancing pack or a pack of its {@code depends_on} closure. A bare target names the enhancing pack itself; a
 *       qualified one names the pack of that name. A qualifier naming no pack of the closure is
 *       {@code pack_reference_pack_missing}; a named pack that does not hold the step is
 *       {@code pack_reference_artifact_missing}. It closes, for step enhancements only, the gap the reference check
 *       leaves open: an {@code action:} reference names no artifact kind, so that check never finds one missing.
 *   <li><strong>The inherited lock</strong> (C22 and C25 as amended, OD-2 (A)): a step takes its mission type's
 *       mutability level, so an enhancement of {@code action:M/s} is refused when {@code mission_type:M} is
 *       {@link MutabilityLevel#LOCKED} along the closure, judged without the enhancing pack's own policy (B8, as the
 *       {@code overrides} rule and the write gate judge). {@link MutabilityLevel#EXTENDABLE} takes an enhancement.
 * </ul>
 *
 * <p>A step enhancement is the one shape rule E6 amends (OD-6): a {@code step_enhancement} written by its own pack (a
 * bare source) whose {@code enhances} edges name exactly one distinct target, an {@code action:} URN
 * ({@link StepHolding#soleActionTarget}). Every other shape (two targets, a mixed list, another kind whose target is an
 * action) is not judged here and keeps rule E6 as built. The rules read only their arguments, visit each edge once and
 * look each step up in a hash set, so their work is linear in the edges and the held artifacts.
 */
public final class EnhancementCompose {

    private EnhancementCompose() {}

    /**
     * One step enhancement as its pack writes it: the enhancement and the one step its {@code enhances} edge names.
     *
     * @param enhancement the {@code step_enhancement:} URN
     * @param target the step, {@code action:M/s}
     * @param targetPack the pack name the edge's target is qualified with; empty for a bare target
     */
    public record StepEnhancement(Urn enhancement, Urn target, Optional<String> targetPack) {

        /**
         * Checks the fields.
         *
         * @param enhancement the enhancement
         * @param target the step
         * @param targetPack the qualifier of the target
         */
        public StepEnhancement {
            Objects.requireNonNull(enhancement, "enhancement");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(targetPack, "targetPack");
        }

        /**
         * The target as written: {@code <pack>::action:M/s}, or {@code action:M/s} when bare.
         *
         * @return the written target
         */
        public String writtenTarget() {
            return targetPack.map(pack -> pack + "::" + target).orElse(target.toString());
        }
    }

    /**
     * A step enhancement whose target is not held (C37).
     *
     * @param code {@code pack_reference_pack_missing} or {@code pack_reference_artifact_missing}
     * @param enhancement the enhancement
     * @param target the step it names
     * @param packs the packs the refusal names: the enhancing pack, then the pack the target names when the compose
     *     holds it
     * @param detail what is wrong, naming the enhancement, its pack and the target as written
     */
    public record TargetViolation(
            RefusalCode code, Urn enhancement, Urn target, List<RefusalPack> packs, String detail) {

        /**
         * Checks the fields.
         *
         * @param code the refusal code
         * @param enhancement the enhancement
         * @param target the step it names
         * @param packs the packs the refusal names
         * @param detail what is wrong
         */
        public TargetViolation {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(enhancement, "enhancement");
            Objects.requireNonNull(target, "target");
            packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            Objects.requireNonNull(detail, "detail");
        }

        /**
         * The wire refusal a compose throws for it: the code, the detail and the packs named.
         *
         * @return the refusal
         */
        public CharterRefusal toCharterRefusal() {
            return new CharterRefusal(code, detail, packs);
        }
    }

    /**
     * An enhancement of a step whose mission type is locked along the closure (the inherited lock).
     *
     * @param enhancement the enhancement
     * @param target the step it enhances
     * @param missionType the step's mission type, {@code mission_type:M}
     * @param enhancingPack the pack that writes the enhancement
     * @param lockedBy the first pack, in closure order, that locks the mission type; empty when the policies name a
     *     pack the closure does not hold
     */
    public record LockedEnhancement(
            Urn enhancement,
            Urn target,
            Urn missionType,
            PackUnderResolution enhancingPack,
            Optional<PackUnderResolution> lockedBy) {

        /**
         * Checks the fields.
         *
         * @param enhancement the enhancement
         * @param target the step
         * @param missionType the mission type
         * @param enhancingPack the enhancing pack
         * @param lockedBy the locking pack
         */
        public LockedEnhancement {
            Objects.requireNonNull(enhancement, "enhancement");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(missionType, "missionType");
            Objects.requireNonNull(enhancingPack, "enhancingPack");
            Objects.requireNonNull(lockedBy, "lockedBy");
        }

        /**
         * The packs a refusal names: the enhancing pack, then the pack that locks the mission type.
         *
         * @return the packs, each once
         */
        public List<RefusalPack> packs() {
            List<RefusalPack> named = new ArrayList<>(List.of(refusalPack(enhancingPack)));
            lockedBy.filter(pack -> !pack.equals(enhancingPack))
                    .map(EnhancementCompose::refusalPack)
                    .ifPresent(named::add);
            return List.copyOf(named);
        }

        /**
         * What is wrong, naming the enhancement, both packs, the step and its mission type.
         *
         * @return the detail
         */
        public String detail() {
            return enhancement + " in pack `" + enhancingPack.descriptor().name() + "` enhances " + target
                    + ", whose mission type " + missionType + " is " + MutabilityLevel.LOCKED.token()
                    + " along the closure"
                    + lockedBy.map(pack -> " (set by pack `" + pack.descriptor().name() + "`)")
                            .orElse("")
                    + "; a step takes its mission type's level, and a locked step takes no enhancement";
        }
    }

    /**
     * The step enhancements a pack writes, each once, in the order of their first edge: every bare
     * {@code step_enhancement} source whose {@code enhances} edges name exactly one distinct target, an action.
     *
     * @param edges the typed edges the pack writes, as written
     * @return its step enhancements
     */
    public static List<StepEnhancement> stepEnhancementsOf(Collection<Relationship> edges) {
        Objects.requireNonNull(edges, "edges");
        Map<Urn, Map<Urn, Relationship>> targetsBySource = new LinkedHashMap<>();
        for (Relationship edge : edges) {
            if (edge.type() == RelationType.ENHANCES && edge.fromPack().isEmpty()) {
                targetsBySource
                        .computeIfAbsent(edge.from(), source -> new LinkedHashMap<>())
                        .putIfAbsent(edge.to(), edge);
            }
        }
        List<StepEnhancement> enhancements = new ArrayList<>();
        targetsBySource.forEach((source, targets) -> StepHolding.soleActionTarget(source, targets.keySet())
                .ifPresent(step -> enhancements.add(new StepEnhancement(
                        source,
                        step,
                        Objects.requireNonNull(targets.get(step), "a target").toPack()))));
        return enhancements;
    }

    /**
     * C37 over one pack: the first of its step enhancements whose step the named pack of its {@code depends_on}
     * closure does not hold.
     *
     * @param closure the composed closure, every pack once
     * @param enhancing the pack whose edges are judged, a pack of {@code closure}
     * @param edges the typed edges {@code enhancing} writes, as written
     * @return the first violation, in the order of the edges; empty when every step enhancement's step is held
     */
    public static Optional<TargetViolation> targetHeld(
            List<PackUnderResolution> closure, PackUnderResolution enhancing, Collection<Relationship> edges) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(enhancing, "enhancing");
        List<StepEnhancement> enhancements = stepEnhancementsOf(edges);
        if (enhancements.isEmpty()) {
            return Optional.empty();
        }
        Map<String, PackUnderResolution> byId = new HashMap<>();
        Map<String, PackUnderResolution> byName = new HashMap<>();
        for (PackUnderResolution pack : closure) {
            byId.putIfAbsent(pack.descriptor().packId(), pack);
            byName.putIfAbsent(pack.descriptor().name(), pack);
        }
        Set<String> dependsOn =
                PackCompositionResolver.dependsOnClosure(enhancing.descriptor().packId(), byId);
        Map<String, Set<String>> heldBy = new HashMap<>();
        for (StepEnhancement enhancement : enhancements) {
            Optional<TargetViolation> violation = judge(enhancement, enhancing, byName, dependsOn, heldBy);
            if (violation.isPresent()) {
                return violation;
            }
        }
        return Optional.empty();
    }

    private static Optional<TargetViolation> judge(
            StepEnhancement enhancement,
            PackUnderResolution enhancing,
            Map<String, PackUnderResolution> byName,
            Set<String> dependsOn,
            Map<String, Set<String>> heldBy) {
        String writes = enhancement.enhancement() + " in pack `"
                + enhancing.descriptor().name() + "` enhances `" + enhancement.writtenTarget() + "`, but ";
        Optional<PackUnderResolution> named = enhancement.targetPack().map(byName::get);
        if (enhancement.targetPack().isPresent() && named.isEmpty()) {
            return Optional.of(violation(
                    RefusalCode.PACK_REFERENCE_PACK_MISSING,
                    enhancement,
                    List.of(enhancing),
                    writes + "the compose holds no pack named `"
                            + enhancement.targetPack().orElseThrow() + "`"));
        }
        PackUnderResolution holder = named.orElse(enhancing);
        if (!dependsOn.contains(holder.descriptor().packId())) {
            return Optional.of(violation(
                    RefusalCode.PACK_REFERENCE_PACK_MISSING,
                    enhancement,
                    List.of(enhancing, holder),
                    writes + "pack `" + holder.descriptor().name() + "` is outside its depends_on closure"));
        }
        Set<String> held = heldBy.computeIfAbsent(
                holder.descriptor().packId(), packId -> new HashSet<>(holder.definedArtifacts()));
        if (!StepHolding.heldByPack(held, enhancement.target())) {
            return Optional.of(violation(
                    RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING,
                    enhancement,
                    holder.equals(enhancing) ? List.of(enhancing) : List.of(enhancing, holder),
                    writes + "pack `" + holder.descriptor().name() + "` does not hold that step: it does not define "
                            + StepHolding.missionTypeOf(enhancement.target())
                                    .map(Urn::toString)
                                    .orElse("its mission type")
                            + " and declare " + enhancement.target()));
        }
        return Optional.empty();
    }

    private static TargetViolation violation(
            RefusalCode code, StepEnhancement enhancement, List<PackUnderResolution> packs, String detail) {
        return new TargetViolation(
                code,
                enhancement.enhancement(),
                enhancement.target(),
                packs.stream().map(EnhancementCompose::refusalPack).toList(),
                detail);
    }

    /**
     * The inherited lock over a closure: every step enhancement whose step's mission type is
     * {@link MutabilityLevel#LOCKED} along the closure, each judged without its own pack's policy.
     *
     * @param closure the composed closure, in closure order
     * @param edgesOf the typed edges each pack of the closure writes, as written
     * @param policy the policies of the closure's packs, by descriptor {@code pack_id}, each scoped to what its pack and
     *     the packs it builds on hold (A15)
     * @return the locked enhancements, by pack in closure order and then in the order of their edges; empty when none
     */
    public static List<LockedEnhancement> lockedTargets(
            List<PackUnderResolution> closure,
            Function<PackUnderResolution, ? extends Collection<Relationship>> edgesOf,
            PerPackPolicy policy) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(edgesOf, "edgesOf");
        Objects.requireNonNull(policy, "policy");
        List<String> closurePackIds =
                closure.stream().map(pack -> pack.descriptor().packId()).toList();
        List<LockedEnhancement> locked = new ArrayList<>();
        for (PackUnderResolution enhancing : closure) {
            List<StepEnhancement> enhancements = stepEnhancementsOf(edgesOf.apply(enhancing));
            if (enhancements.isEmpty()) {
                continue;
            }
            List<String> judging = closurePackIds.stream()
                    .filter(packId -> !packId.equals(enhancing.descriptor().packId()))
                    .toList();
            for (StepEnhancement enhancement : enhancements) {
                StepHolding.missionTypeOf(enhancement.target()).ifPresent(missionType -> {
                    PerPackPolicy.Level level = policy.levelIn(judging, missionType);
                    if (level.level() == MutabilityLevel.LOCKED) {
                        locked.add(new LockedEnhancement(
                                enhancement.enhancement(),
                                enhancement.target(),
                                missionType,
                                enhancing,
                                level.setBy()
                                        .flatMap(packId -> closure.stream()
                                                .filter(pack -> pack.descriptor()
                                                        .packId()
                                                        .equals(packId))
                                                .findFirst())));
                    }
                });
            }
        }
        return List.copyOf(locked);
    }

    private static RefusalPack refusalPack(PackUnderResolution pack) {
        return new RefusalPack(
                pack.descriptor().packId(),
                pack.configured().id().value(),
                pack.descriptor().name());
    }
}
