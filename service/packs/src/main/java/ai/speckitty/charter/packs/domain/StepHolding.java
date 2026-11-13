package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The one predicate for "pack P holds step {@code M/s}" (OD-9, decision {@code 01M7037V93Y077H8M4W9V8Y6B3}), over the
 * artifacts a pack defines, in two forms:
 *
 * <ul>
 *   <li><strong>per pack</strong>: P defines {@code mission_type:M} and declares {@code action:M/s}. The merged reads
 *       take it from the lists of defined artifacts the closure carries. It is meant to serve the compose too, which
 *       holds the same lists in {@link PackCompositionFacts#defined()}: the step-enhancements mission is to call
 *       {@link #heldByPack} from there, and nothing in main calls it directly yet;
 *   <li><strong>on the closure</strong>: the closure holds the step when some closure pack holds it per pack, so a
 *       layer override of a held step is the winning version of a held step. A winning {@code mission_type:M} keeps
 *       the unique steps of the versions it replaced (A7, DIV-243): a step is a URN of its own, and a pack that holds it
 *       per pack holds it on the closure.
 * </ul>
 *
 * <p>A held step's <strong>place</strong> ({@link #placeOf}; research R8 as amended by operator ruling Q3) is the pack whose
 * version of the mission type every read and every slot check takes the step, and its prompt in force, from.
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
        Set<String> defined = new HashSet<>(definedArtifacts);
        defined.stream()
                .flatMap(text -> Urn.parse(text).stream())
                .filter(urn -> heldByPack(defined, urn))
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

    /**
     * The place of a step over the merged closure (research R8 as amended by operator ruling Q3, A7, DIV-243): the one
     * rule the step read, the action context's links, the prompt read and the slot check (C38) place a step by.
     *
     * <ol>
     *   <li>the pack of the winner of {@code mission_type:M}, when its version declares the step;
     *   <li>else the pack of the merged winner of {@code action:M/s}, when its version of {@code mission_type:M}
     *       declares the step: that pack defines the mission type and declares the action, so the closure holds the
     *       step, and a step that only a version the winner replaced keeps is placed there;
     *   <li>else no place: no version at either place declares the step.
     * </ol>
     *
     * <p>It reads only the merge and the facts each pack was read with ({@link PromptFacts#steps()}).
     *
     * @param step the step, {@code action:M/s}
     * @param merged the layer merge of the closure
     * @param facts the facts each pack of the closure was read with, by configured identifier
     * @return the place's pack; empty for no place, and for a URN that names no step
     */
    public static Optional<PackIdentifier> placeOf(
            Urn step, MergedClosure merged, Map<String, PackCompositionFacts> facts) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(facts, "facts");
        Optional<Urn> missionType = missionTypeOf(step);
        if (missionType.isEmpty()) {
            return Optional.empty();
        }
        return missionType
                .flatMap(merged::winnerOf)
                .map(MergedClosure.Winner::packId)
                .filter(pack -> declares(facts, pack, step))
                .or(() -> merged.winnerOf(step)
                        .map(MergedClosure.Winner::packId)
                        .filter(pack -> declares(facts, pack, step)));
    }

    private static boolean declares(Map<String, PackCompositionFacts> facts, PackIdentifier pack, Urn step) {
        return facts.getOrDefault(pack.value(), PackCompositionFacts.NONE)
                .prompts()
                .declaresStep(step);
    }

    /**
     * The one step a step enhancer enhances, when its shape is the one rule E6 amends (OD-6): the source is a
     * {@code step_enhancement} and it has exactly one {@code enhances} target, an {@code action:} URN. Every other shape
     * (two targets, a mixed target list, another kind whose targets are actions) is not this one and keeps rule E6 as
     * built. The shape is read from the merged edges before any edge to a non-winner is dropped.
     *
     * @param source the enhancer
     * @param enhancesTargets the distinct targets of the enhancer's {@code enhances} edges, all of them
     * @return the sole action target; empty for any other shape
     */
    public static Optional<Urn> soleActionTarget(Urn source, Collection<Urn> enhancesTargets) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(enhancesTargets, "enhancesTargets");
        if (source.kind() != NodeKind.STEP_ENHANCEMENT || enhancesTargets.size() != 1) {
            return Optional.empty();
        }
        return enhancesTargets.stream()
                .filter(target -> target.kind() == NodeKind.ACTION)
                .findFirst();
    }
}
