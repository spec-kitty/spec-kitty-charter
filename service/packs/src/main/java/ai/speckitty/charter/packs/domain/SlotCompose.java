package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * C38, the slot declared (ADR-0066 decision 8, FR-001, FR-006): every slot a step enhancement <strong>in force</strong>
 * names is declared by the prompt in force of its step. A pure rule over the merged closure, the prompt facts each pack
 * was read with and the in-force states of one request:
 *
 * <ul>
 *   <li>Only an enhancement in force is judged (OD-1 (B)): one held and not in force never refuses, whatever it names.
 *   <li>Only the winning copy is judged: the fills and the {@code enhances} edge of the version the layer merge keeps,
 *       read from the facts of the pack that holds it. A losing copy of the same id names nothing.
 *   <li>The prompt in force is the one at the step's place ({@link StepHolding#placeOf}; research R8 as amended by
 *       operator ruling Q3): the winner of the mission type when its version declares the step, else the merged winner
 *       of {@code action:M/s} when its version does, the prompt the effective prompt read serves. A step whose place
 *       holds no prompt declares no slot; a step with no place has no prompt in force and is not judged.
 * </ul>
 *
 * <p>The shape judged is the one {@link EnhancementCompose} judges: a {@code step_enhancement} whose {@code enhances}
 * edges name exactly one target, an action. Its target held is C37's to judge; an enhancement whose step the closure does
 * not hold is not judged here. Each enhancement is visited once and each pack's edges are indexed once, so the work is
 * linear in the winners, the edges and the fills.
 */
public final class SlotCompose {

    private SlotCompose() {}

    /**
     * A slot an enhancement in force names that the prompt in force of its step does not declare.
     *
     * @param enhancement the enhancement, {@code step_enhancement:<id>}
     * @param enhancingPack the configured pack that holds the winning copy of the enhancement
     * @param step the step it enhances, {@code action:M/s}
     * @param slot the slot it fills
     * @param promptPack the configured pack at the step's place, which holds the prompt in force
     * @param promptHeld whether that place holds a prompt for the step; a place with none declares no slot
     */
    public record SlotViolation(
            Urn enhancement,
            PackIdentifier enhancingPack,
            Urn step,
            SlotId slot,
            PackIdentifier promptPack,
            boolean promptHeld) {

        /**
         * Checks the fields.
         *
         * @param enhancement the enhancement
         * @param enhancingPack the pack of its winning copy
         * @param step the step
         * @param slot the slot
         * @param promptPack the pack of the prompt in force
         * @param promptHeld whether a prompt is held there
         */
        public SlotViolation {
            Objects.requireNonNull(enhancement, "enhancement");
            Objects.requireNonNull(enhancingPack, "enhancingPack");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(promptPack, "promptPack");
        }

        /**
         * The packs a refusal names: the enhancing pack, then the pack of the prompt in force.
         *
         * @return the configured packs, each once
         */
        public List<PackIdentifier> packs() {
            return enhancingPack.equals(promptPack) ? List.of(enhancingPack) : List.of(enhancingPack, promptPack);
        }

        /**
         * What is wrong, naming the enhancement, its pack, the slot, the step and the pack of the prompt in force.
         *
         * @param nameOf the name a pack is given in a detail
         * @return the detail
         */
        public String detail(Function<PackIdentifier, String> nameOf) {
            Objects.requireNonNull(nameOf, "nameOf");
            String fills = enhancement + " in pack `" + nameOf.apply(enhancingPack) + "` fills the slot `"
                    + slot.value() + "` of " + step + ", but ";
            return promptHeld
                    ? fills + "the prompt in force there, held by pack `" + nameOf.apply(promptPack)
                            + "`, declares no such slot"
                    : fills + "pack `" + nameOf.apply(promptPack)
                            + "`, which holds the step in force, holds no prompt for it, so no slot is declared";
        }
    }

    /**
     * Every slot the enhancements in force name that the prompt in force of their step does not declare.
     *
     * @param merged the layer merge of the closure
     * @param facts the facts each pack of the closure was read with, by configured identifier
     * @param inForce what is in force under the request
     * @return the violations, by enhancement in the order the closure first holds them and then in the authored order of
     *     its fills; empty when every slot is declared
     */
    public static List<SlotViolation> missing(
            MergedClosure merged, Map<String, PackCompositionFacts> facts, InForceStates inForce) {
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(inForce, "inForce");
        Map<PackIdentifier, Map<Urn, EnhancementCompose.StepEnhancement>> enhancementsByPack = new HashMap<>();
        List<SlotViolation> found = new ArrayList<>();
        merged.winners().forEach((urn, winner) -> {
            if (urn.kind() != NodeKind.STEP_ENHANCEMENT || !inForce(inForce, urn)) {
                return;
            }
            PackCompositionFacts held = factsOf(facts, winner.packId());
            Optional.ofNullable(enhancementsByPack
                            .computeIfAbsent(winner.packId(), pack -> index(held))
                            .get(urn))
                    .ifPresent(enhancement -> judge(merged, facts, held, winner.packId(), enhancement, found));
        });
        return List.copyOf(found);
    }

    private static void judge(
            MergedClosure merged,
            Map<String, PackCompositionFacts> facts,
            PackCompositionFacts held,
            PackIdentifier enhancingPack,
            EnhancementCompose.StepEnhancement enhancement,
            List<SlotViolation> found) {
        Optional<PackIdentifier> place = StepHolding.placeOf(enhancement.target(), merged, facts);
        if (place.isEmpty()) {
            return;
        }
        PackIdentifier promptPack = place.get();
        Optional<List<PromptFacts.DeclaredSlot>> prompt = Optional.ofNullable(
                factsOf(facts, promptPack).prompts().declared().get(enhancement.target()));
        Set<SlotId> declared = new HashSet<>();
        prompt.ifPresent(slots -> slots.forEach(slot -> declared.add(slot.id())));
        for (PromptFacts.Fill fill : held.prompts().fills().getOrDefault(enhancement.enhancement(), List.of())) {
            if (!declared.contains(fill.slot())) {
                found.add(new SlotViolation(
                        enhancement.enhancement(),
                        enhancingPack,
                        enhancement.target(),
                        fill.slot(),
                        promptPack,
                        prompt.isPresent()));
            }
        }
    }

    private static boolean inForce(InForceStates states, Urn urn) {
        return states.stateOf(urn).filter(InForceStates.ArtifactState::active).isPresent();
    }

    private static PackCompositionFacts factsOf(Map<String, PackCompositionFacts> facts, PackIdentifier pack) {
        return facts.getOrDefault(pack.value(), PackCompositionFacts.NONE);
    }

    /** The step enhancements a pack writes, by URN, read once from its edges. */
    private static Map<Urn, EnhancementCompose.StepEnhancement> index(PackCompositionFacts held) {
        Map<Urn, EnhancementCompose.StepEnhancement> byUrn = new HashMap<>();
        EnhancementCompose.stepEnhancementsOf(held.edges())
                .forEach(enhancement -> byUrn.put(enhancement.enhancement(), enhancement));
        return byUrn;
    }
}
