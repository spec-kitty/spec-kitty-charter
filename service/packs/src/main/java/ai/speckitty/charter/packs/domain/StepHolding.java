package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The one predicate for "pack P holds step {@code M/s}" (OD-9, decision {@code 01M7037V93Y077H8M4W9V8Y6B3}), over the
 * artifacts a pack defines, in two forms:
 *
 * <ul>
 *   <li><strong>per pack</strong>: P defines {@code mission_type:M} and declares {@code action:M/s}. The compose reads it
 *       from {@link PackCompositionFacts#defined()}, the merged reads from the same lists the closure carries, so one
 *       function serves both;
 *   <li><strong>on the closure</strong>: the closure holds the step when some closure pack holds it per pack. The step's
 *       place is the winner of {@code action:M/s} on the merged view, so a layer override of a held step is the winning
 *       version of a held step. A winning {@code mission_type:M} does not take the unique steps of the versions it
 *       replaced (A7): a step is a URN of its own.
 * </ul>
 *
 * <p>A pack that declares {@code action:M/s} while no closure pack defines {@code mission_type:M} alongside it holds no
 * step. The functions are pure and read only their arguments, so no answer depends on the order of the packs.
 */
public final class StepHolding {

    private StepHolding() {}

    /**
     * The mission type a step belongs to.
     *
     * @param step a URN
     * @return {@code mission_type:M} for {@code action:M/s}; empty for any other URN, and for an action that names no step
     */
    public static Optional<Urn> missionTypeOf(Urn step) {
        Objects.requireNonNull(step, "step");
        if (step.kind() != NodeKind.ACTION) {
            return Optional.empty();
        }
        int slash = step.id().indexOf('/');
        if (slash <= 0 || slash == step.id().length() - 1) {
            return Optional.empty();
        }
        return Optional.of(Urn.missionType(step.id().substring(0, slash)));
    }

    /**
     * Whether one pack holds a step: it defines the step's mission type and declares the step.
     *
     * @param definedArtifacts the artifacts the pack defines, as bare URN text ({@code <kind>:<id>})
     * @param step the step, {@code action:M/s}
     * @return {@code true} when the pack defines {@code mission_type:M} and declares {@code action:M/s}
     */
    public static boolean heldByPack(Collection<String> definedArtifacts, Urn step) {
        Objects.requireNonNull(definedArtifacts, "definedArtifacts");
        return missionTypeOf(step)
                .filter(missionType ->
                        definedArtifacts.contains(missionType.toString()) && definedArtifacts.contains(step.toString()))
                .isPresent();
    }

    /**
     * Whether the closure holds a step: some closure pack holds it per pack.
     *
     * @param packs the artifacts each closure pack defines, as bare URN text
     * @param step the step
     * @return {@code true} when some pack holds it
     */
    public static boolean heldByClosure(Collection<? extends Collection<String>> packs, Urn step) {
        Objects.requireNonNull(packs, "packs");
        return packs.stream().anyMatch(defined -> heldByPack(defined, step));
    }

    /**
     * The steps one pack holds.
     *
     * @param definedArtifacts the artifacts the pack defines, as bare URN text
     * @return its held steps, in URN order
     */
    public static Set<Urn> stepsHeldByPack(Collection<String> definedArtifacts) {
        Objects.requireNonNull(definedArtifacts, "definedArtifacts");
        Set<Urn> steps = new TreeSet<>();
        definedArtifacts.stream()
                .flatMap(defined -> Urn.parse(defined).stream())
                .filter(urn -> heldByPack(definedArtifacts, urn))
                .forEach(steps::add);
        return steps;
    }

    /**
     * The steps the closure holds.
     *
     * @param packs the artifacts each closure pack defines, as bare URN text
     * @return the steps some pack holds, each once, in URN order
     */
    public static Set<Urn> stepsHeldByClosure(Collection<? extends Collection<String>> packs) {
        Objects.requireNonNull(packs, "packs");
        Set<Urn> steps = new TreeSet<>();
        packs.forEach(defined -> steps.addAll(stepsHeldByPack(defined)));
        return steps;
    }
}
