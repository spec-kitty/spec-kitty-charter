package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.PromptFacts;
import ai.speckitty.charter.packs.domain.StepHolding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The packs side of the effective-prompt read (FR-003, FR-005; ADR-0066 decisions 4, 5 and 9): for one step of a merged
 * closure, the prompt in force as stored, every slot it declares and, per slot, the fragments of the step enhancements
 * in force that fill it. A pure function of its inputs: it reads nothing but what it is handed.
 *
 * <ul>
 *   <li>The prompt in force is the stored companion at the step's place, which the caller gives
 *       ({@link ClosureView#stepPlace}; research R8 as amended by operator ruling Q3, 2026-11-13): the winner of the
 *       mission type when its version declares the step, else the merged winner of {@code action:M/s} when its version
 *       does, so the prompt read, the step read and the action context name one pack. It is served as stored (decision
 *       9); a step with no place or no companion answers no byte, no slot and no pack.
 *   <li>Its slots are that pack's prompt facts for the step, in declaration order. A slot that holds text is listed
 *       like any other, with its fragments and no flag (OD-8), and gives a {@code prompt_slot_not_empty} diagnostic
 *       (OD-7).
 *   <li>A fragment comes from the winning copy of a step enhancement in force whose sole {@code enhances} target is the
 *       step, one per entry of its {@code prompt_fragments} (decision 4: nothing pulls an enhancement into force). The
 *       fragments of a slot are ordered by level, then the descriptor {@code name} of the enhancing pack, then the
 *       enhancement's id, each by code point (decision 5).
 * </ul>
 *
 * <p>The slot check ({@link ClosureView#requireSlots}) runs before this query on every read, so no fill names an
 * undeclared slot here. Each winner and each declared slot is visited once (NFR-002).
 */
public final class EffectivePromptQuery {

    /** ADR-0066 decision 5: level (built-in, org, project), then pack name, then enhancement id, each by code point. */
    private static final Comparator<Fragment> ORDER = Comparator.comparing(Fragment::level)
            .thenComparing(Fragment::packName, EffectivePromptQuery::byCodePoint)
            .thenComparing(Fragment::enhancement, EffectivePromptQuery::byCodePoint);

    private EffectivePromptQuery() {}

    /**
     * A count of the work the query does (NFR-002): one call per visit of an enhancement or a slot. The reads never use
     * one; an operation-count test hands one in.
     */
    @FunctionalInterface
    interface Visits {

        /** Counts nothing. */
        Visits NONE = () -> {};

        /** Counts one visit of an enhancement or a slot. */
        void visit();
    }

    /**
     * One fragment of a slot, with where it came from.
     *
     * @param packId the configured identifier of the pack that holds the winning copy of the enhancement
     * @param packName the descriptor {@code name} of that pack, the second ordering key
     * @param level the level of that pack, the first ordering key
     * @param enhancement the enhancement's id, without its kind, the third ordering key
     * @param text the canonical text of the fragment, as the pack's prompt facts hold it
     */
    public record Fragment(PackIdentifier packId, String packName, PackLevel level, String enhancement, String text) {

        /**
         * Checks the fields.
         *
         * @param packId the enhancing pack
         * @param packName its descriptor name
         * @param level its level
         * @param enhancement the enhancement's id
         * @param text the fragment's text
         */
        public Fragment {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(packName, "packName");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(enhancement, "enhancement");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * One slot the prompt in force declares, with the fragments that fill it.
     *
     * @param slot the slot's id
     * @param fragments its fragments, by level, then pack name, then enhancement id, each by code point; empty when
     *     no enhancement in force fills it
     */
    public record Slot(SlotId slot, List<Fragment> fragments) {

        /**
         * Checks and copies the fields.
         *
         * @param slot the slot's id
         * @param fragments its fragments, in order
         */
        public Slot {
            Objects.requireNonNull(slot, "slot");
            fragments = List.copyOf(Objects.requireNonNull(fragments, "fragments"));
        }
    }

    /**
     * The effective prompt of a step.
     *
     * @param prompt the stored bytes of the prompt in force, unchanged (ADR-0066 decision 9); no byte when the step has
     *     no prompt
     * @param promptPackId the configured identifier of the pack that holds the prompt in force; empty when the step has
     *     no prompt
     * @param slots every slot the prompt declares, in declaration order
     * @param diagnostics a {@code prompt_slot_not_empty} per declared slot that holds text (OD-7), in declaration order
     */
    public record EffectivePrompt(
            Payload prompt, Optional<PackIdentifier> promptPackId, List<Slot> slots, List<Diagnostic> diagnostics) {

        /** The answer for a step with no prompt: no byte, no pack, no slot. */
        public static final EffectivePrompt NONE =
                new EffectivePrompt(Payload.of(new byte[0]), Optional.empty(), List.of(), List.of());

        /**
         * Checks and copies the fields.
         *
         * @param prompt the prompt's bytes
         * @param promptPackId the pack of the prompt
         * @param slots the declared slots
         * @param diagnostics the diagnostics
         */
        public EffectivePrompt {
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(promptPackId, "promptPackId");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /**
     * The effective prompt of a step.
     *
     * @param step the step, {@code action:M/s}
     * @param place the pack of the step's place ({@link ClosureView#stepPlace}); empty for a step with no place
     * @param merged the layer merge of the closure
     * @param facts the facts each pack of the closure was read with, by configured identifier
     * @param inForce whether an artifact is in force under the request
     * @param packNames the descriptor {@code name} of each pack of the closure
     * @param promptOf the stored prompt of the step in a pack; empty when that pack holds none
     * @return the prompt in force, its slots and their fragments
     */
    public static EffectivePrompt answer(
            Urn step,
            Optional<PackIdentifier> place,
            MergedClosure merged,
            Map<String, PackCompositionFacts> facts,
            Predicate<Urn> inForce,
            Function<PackIdentifier, String> packNames,
            Function<PackIdentifier, Optional<Payload>> promptOf) {
        return answer(step, place, merged, facts, inForce, packNames, promptOf, Visits.NONE);
    }

    static EffectivePrompt answer(
            Urn step,
            Optional<PackIdentifier> place,
            MergedClosure merged,
            Map<String, PackCompositionFacts> facts,
            Predicate<Urn> inForce,
            Function<PackIdentifier, String> packNames,
            Function<PackIdentifier, Optional<Payload>> promptOf,
            Visits visits) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(inForce, "inForce");
        Objects.requireNonNull(packNames, "packNames");
        Objects.requireNonNull(promptOf, "promptOf");
        Objects.requireNonNull(visits, "visits");
        if (place.isEmpty()) {
            return EffectivePrompt.NONE;
        }
        PackIdentifier promptPack = place.get();
        Optional<Payload> prompt = promptOf.apply(promptPack);
        if (prompt.isEmpty()) {
            return EffectivePrompt.NONE;
        }
        List<PromptFacts.DeclaredSlot> declared =
                factsOf(facts, promptPack).prompts().declared().getOrDefault(step, List.of());
        Map<SlotId, List<Fragment>> bySlot = new LinkedHashMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (PromptFacts.DeclaredSlot slot : declared) {
            visits.visit();
            bySlot.put(slot.id(), new ArrayList<>());
            if (slot.holdsText()) {
                diagnostics.add(notEmpty(step, slot.id()));
            }
        }
        fill(step, merged, facts, inForce, packNames, bySlot, visits);
        List<Slot> slots = new ArrayList<>();
        bySlot.forEach((slot, fragments) -> {
            fragments.sort(ORDER);
            slots.add(new Slot(slot, fragments));
        });
        return new EffectivePrompt(prompt.get(), Optional.of(promptPack), slots, diagnostics);
    }

    /**
     * Files the fills of the winning copy of each step enhancement in force whose sole target is the step under the
     * slots it names. Each winner is visited once and each pack's edges are indexed once, so the work is linear in the
     * winners, the edges and the fills. A fill of an undeclared slot is left out: the slot check
     * ({@link ClosureView#requireSlots}) refuses one before this query runs.
     */
    private static void fill(
            Urn step,
            MergedClosure merged,
            Map<String, PackCompositionFacts> facts,
            Predicate<Urn> inForce,
            Function<PackIdentifier, String> packNames,
            Map<SlotId, List<Fragment>> bySlot,
            Visits visits) {
        if (bySlot.isEmpty()) {
            return;
        }
        Map<PackIdentifier, Set<Urn>> enhancersByPack = new HashMap<>();
        merged.winners().forEach((urn, winner) -> {
            if (urn.kind() != NodeKind.STEP_ENHANCEMENT) {
                return;
            }
            visits.visit();
            if (!inForce.test(urn)) {
                return;
            }
            PackCompositionFacts held = factsOf(facts, winner.packId());
            if (!enhancersByPack
                    .computeIfAbsent(winner.packId(), pack -> enhancersOf(held, step, visits))
                    .contains(urn)) {
                return;
            }
            String packName = packNames.apply(winner.packId());
            for (PromptFacts.Fill fill : held.prompts().fills().getOrDefault(urn, List.of())) {
                visits.visit();
                Optional.ofNullable(bySlot.get(fill.slot()))
                        .ifPresent(fragments -> fragments.add(
                                new Fragment(winner.packId(), packName, winner.level(), urn.id(), fill.text())));
            }
        });
    }

    /**
     * The step enhancements a pack writes whose sole {@code enhances} target is the step, read once per pack from its
     * edges; each edge read is a visit, so a rescan per winner shows in the count (NFR-002).
     */
    private static Set<Urn> enhancersOf(PackCompositionFacts held, Urn step, Visits visits) {
        List<Relationship> enhances = new ArrayList<>();
        for (Relationship edge : held.edges()) {
            visits.visit();
            if (edge.type() == RelationType.ENHANCES) {
                enhances.add(edge);
            }
        }
        Set<Urn> enhancers = new HashSet<>();
        EnhancementCompose.stepEnhancementsOf(enhances).stream()
                .filter(enhancement -> enhancement.target().equals(step))
                .forEach(enhancement -> enhancers.add(enhancement.enhancement()));
        return enhancers;
    }

    private static Diagnostic notEmpty(Urn step, SlotId slot) {
        String file = StepHolding.missionTypeOf(step)
                .map(missionType -> "missions/" + missionType.id() + "/steps/"
                        + step.id().substring(missionType.id().length() + 1) + ".prompt.md")
                .orElse(step.toString());
        return new Diagnostic(
                Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY,
                file + ": slot " + slot.value() + " holds text; the text is kept and the slot's fragments follow it");
    }

    private static PackCompositionFacts factsOf(Map<String, PackCompositionFacts> facts, PackIdentifier pack) {
        return facts.getOrDefault(pack.value(), PackCompositionFacts.NONE);
    }

    private static int byCodePoint(String left, String right) {
        return Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());
    }
}
