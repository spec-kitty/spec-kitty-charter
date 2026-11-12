package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.markdown.SlotId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What the compose knows of one pack's step prompts and step enhancements (research R9, squad A1): the slots each held
 * step prompt declares and the fills each held step enhancement writes. They are part of {@link PackCompositionFacts},
 * so the slot check and the prompt read judge the verified, fingerprint-cached and staged-aware read, and nothing is
 * read again. A prompt's body is not kept: the read takes it from the stored companion.
 *
 * <p>Both maps are in the order of their keys ({@link Urn#compareTo}), whatever order they were given in, so equal
 * facts are equal maps in equal order.
 *
 * @param declared per held step, {@code action:<type>/<step>}, the slots its prompt declares, in declaration order; a
 *     step whose prompt declares none maps to an empty list, and a step with no prompt is absent
 * @param fills per held step enhancement, {@code step_enhancement:<id>}, its fills in authored order; an enhancement
 *     with no {@code prompt_fragments} entry maps to an empty list
 */
public record PromptFacts(Map<Urn, List<DeclaredSlot>> declared, Map<Urn, List<Fill>> fills) {

    /** A pack with no step prompt and no step enhancement. */
    public static final PromptFacts NONE = new PromptFacts(Map.of(), Map.of());

    private static final String MISSIONS = "missions/";
    private static final String STEPS = "/steps/";
    private static final String PROMPT_SUFFIX = ".prompt.md";

    /**
     * Checks the keys and copies the maps and lists, in key order.
     *
     * @param declared the slots each step prompt declares
     * @param fills the fills each step enhancement writes
     * @throws IllegalArgumentException when a key of {@code declared} is not an {@code action:} URN, or a key of
     *     {@code fills} is not a {@code step_enhancement:} URN
     */
    public PromptFacts {
        declared = sortedCopy(declared, NodeKind.ACTION, "declared");
        fills = sortedCopy(fills, NodeKind.STEP_ENHANCEMENT, "fills");
    }

    /**
     * One slot a step prompt declares.
     *
     * @param id the slot's id
     * @param holdsText whether text lies between its markers (a legacy slot; the load rule warns or refuses by the
     *     pack's schema version)
     */
    public record DeclaredSlot(SlotId id, boolean holdsText) {
        /**
         * Checks the id.
         *
         * @param id the slot's id
         * @param holdsText whether it holds text
         */
        public DeclaredSlot {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * One fill a step enhancement writes: an entry of its {@code prompt_fragments}.
     *
     * @param slot the slot it fills
     * @param text the canonical text of its fragment companion, {@code MarkdownCodec.write(document)}, the text the load
     *     rule judges (A5)
     */
    public record Fill(SlotId slot, String text) {
        /**
         * Checks the parts.
         *
         * @param slot the slot it fills
         * @param text the fragment's canonical text
         */
        public Fill {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * The prompt facts of a pack, from what its verified read already holds; nothing is read here.
     *
     * <ul>
     *   <li>A prompt at {@code missions/<type>/steps/<step>.prompt.md} is the prompt of {@code action:<type>/<step>},
     *       an on-demand step's as any other's; every other file is no step prompt and is left out. Its slots are the
     *       parse's declared slots, in order, a slot holding text when the parse found it not empty.
     *   <li>Each enhancement fills, in authored order, the entries whose slot id is a {@link SlotId} and whose
     *       companion, beside the enhancement file, was read. Any other entry is a fault the load refuses first, so it
     *       gives no fill here.
     * </ul>
     *
     * @param prompts the slot parse of each step prompt, over its canonical text ({@link PackContents#promptSlots()})
     * @param enhancements the step enhancements and their companions' canonical texts
     * @return the facts
     */
    public static PromptFacts of(List<PackContents.PromptSlotFacts> prompts, StepEnhancementFacts enhancements) {
        Objects.requireNonNull(prompts, "prompts");
        Objects.requireNonNull(enhancements, "enhancements");
        Map<Urn, List<DeclaredSlot>> declared = new HashMap<>();
        for (PackContents.PromptSlotFacts prompt : prompts) {
            stepOf(prompt.file()).ifPresent(step -> declared.put(step, declaredSlots(prompt.parse())));
        }
        Map<String, StepEnhancementFacts.Companion> companions = new HashMap<>();
        enhancements.companions().forEach(companion -> companions.putIfAbsent(companion.path(), companion));
        Map<Urn, List<Fill>> fills = new HashMap<>();
        for (StepEnhancementFacts.Enhancement enhancement : enhancements.enhancements()) {
            fills.put(new Urn(NodeKind.STEP_ENHANCEMENT, enhancement.id()), fillsOf(enhancement, companions));
        }
        return new PromptFacts(declared, fills);
    }

    /**
     * The facts once an artifact is removed: a mission type takes its steps' prompts with it, as they are its
     * companions, and a step enhancement takes its fills; any other artifact changes nothing.
     *
     * @param artifact the artifact removed
     * @return the facts after the removal; these same facts when nothing changes
     */
    public PromptFacts without(Urn artifact) {
        Objects.requireNonNull(artifact, "artifact");
        if (artifact.kind() == NodeKind.MISSION_TYPE) {
            String steps = artifact.id() + "/";
            Map<Urn, List<DeclaredSlot>> kept = new LinkedHashMap<>(declared);
            kept.keySet().removeIf(action -> action.id().startsWith(steps));
            return kept.size() == declared.size() ? this : new PromptFacts(kept, fills);
        }
        if (artifact.kind() == NodeKind.STEP_ENHANCEMENT && fills.containsKey(artifact)) {
            Map<Urn, List<Fill>> kept = new LinkedHashMap<>(fills);
            kept.remove(artifact);
            return new PromptFacts(declared, kept);
        }
        return this;
    }

    /** The action of a step prompt's path, {@code missions/<type>/steps/<step>.prompt.md}; empty for any other path. */
    private static Optional<Urn> stepOf(String file) {
        if (!file.startsWith(MISSIONS) || !file.endsWith(PROMPT_SUFFIX)) {
            return Optional.empty();
        }
        String inner = file.substring(MISSIONS.length(), file.length() - PROMPT_SUFFIX.length());
        int steps = inner.indexOf(STEPS);
        if (steps <= 0) {
            return Optional.empty();
        }
        String type = inner.substring(0, steps);
        String step = inner.substring(steps + STEPS.length());
        return type.contains("/") || step.isEmpty() || step.contains("/")
                ? Optional.empty()
                : Optional.of(Urn.action(type, step));
    }

    private static List<DeclaredSlot> declaredSlots(PromptSlots.Parse parse) {
        return parse.slots().stream()
                .map(slot -> new DeclaredSlot(slot.id(), !slot.empty()))
                .toList();
    }

    private static List<Fill> fillsOf(
            StepEnhancementFacts.Enhancement enhancement, Map<String, StepEnhancementFacts.Companion> companions) {
        List<Fill> fills = new ArrayList<>();
        for (StepEnhancementFacts.Entry entry : enhancement.entries()) {
            Optional<SlotId> slot = SlotId.tryParse(entry.slot());
            StepEnhancementFacts.Companion companion = companions.get(enhancement.companionPath(entry));
            if (slot.isPresent() && companion != null && !companion.unreadable()) {
                fills.add(new Fill(slot.get(), companion.canonicalText()));
            }
        }
        return fills;
    }

    private static <V> Map<Urn, List<V>> sortedCopy(Map<Urn, List<V>> map, NodeKind kind, String name) {
        Objects.requireNonNull(map, name);
        Map<Urn, List<V>> sorted = new TreeMap<>();
        map.forEach((urn, values) -> {
            if (urn.kind() != kind) {
                throw new IllegalArgumentException(
                        "A key of " + name + " must be a " + kind.token() + ": URN, but is '" + urn + "'");
            }
            sorted.put(urn, List.copyOf(values));
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
