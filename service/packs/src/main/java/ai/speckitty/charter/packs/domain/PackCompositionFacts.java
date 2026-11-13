package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Relationship;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the compose needs to know about one pack, read from one assembly of it: the artifacts its graph documents say it
 * changes, the artifacts it holds, the references it writes, the mutability policy it declares, the activation presets
 * it holds, the typed edges it writes and the slots its step prompts declare and its step enhancements fill.
 *
 * @param changed the distinct targets of its {@code overrides} relations, in first-seen order (an {@code enhances}
 *     relation changes nothing, A14)
 * @param defined the artifacts it holds, as bare URNs ({@code <kind>:<id>}), in a stable order
 * @param references the references it writes, as written (bare or pack-qualified), in first-seen order
 * @param policy the mutability policy its descriptor names (ADR-0063); {@link MutabilityPolicySource#empty()} when it
 *     names none
 * @param presets the activation presets the pack holds ({@link Pack#presets()}), from the same assembly; empty when it
 *     holds none
 * @param edges every typed edge the pack writes, as written: the kernel {@link Relationship} with its relation type and
 *     any {@code <pack>::} qualifier of either endpoint, from its graph documents, its components' derived relationships and its
 *     registered relationship shard, each (from, relation, to, qualifiers) once, in first-seen order; empty when it
 *     writes none
 * @param languages the {@code applies_to_languages} of each held artifact that declares any, by bare URN text, from the
 *     same assembly (a tactic, a styleguide, a toolguide, a procedure or an agent profile); an artifact that declares
 *     none is absent
 * @param bindings the mission type each held artifact is bound to, by bare URN text, from the same assembly: a mission
 *     step contract is bound to the mission its {@code mission} names (A7 Q1, rule E6); an artifact bound to none is
 *     absent
 * @param prompts the slots each held step prompt declares and the fills each held step enhancement writes, from the
 *     same read (research R9); {@link PromptFacts#NONE} when it holds neither
 */
public record PackCompositionFacts(
        List<String> changed,
        List<String> defined,
        List<String> references,
        MutabilityPolicySource policy,
        List<Preset> presets,
        List<Relationship> edges,
        Map<String, List<String>> languages,
        Map<String, String> bindings,
        PromptFacts prompts) {

    /** A pack that changes, holds and writes nothing, declares no policy and holds no preset, prompt or enhancement. */
    public static final PackCompositionFacts NONE = new PackCompositionFacts(List.of(), List.of(), List.of());

    /**
     * Checks the fields and copies the lists.
     *
     * @param changed the artifacts it changes
     * @param defined the artifacts it holds
     * @param references the references it writes
     * @param policy the mutability policy it declares
     * @param presets the presets it holds
     * @param edges the typed edges it writes
     * @param languages the languages each held artifact applies to, by bare URN text
     * @param bindings the mission type each held artifact is bound to, by bare URN text
     * @param prompts the slots its step prompts declare and its step enhancements fill
     */
    public PackCompositionFacts {
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(defined, "defined");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(presets, "presets");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(languages, "languages");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(prompts, "prompts");
        changed = List.copyOf(changed);
        defined = List.copyOf(defined);
        references = List.copyOf(references);
        presets = List.copyOf(presets);
        edges = List.copyOf(edges);
        Map<String, List<String>> copy = new LinkedHashMap<>();
        languages.forEach((urn, declared) -> copy.put(urn, List.copyOf(declared)));
        languages = Collections.unmodifiableMap(copy);
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }

    /**
     * The facts of a pack that holds no step prompt and no step enhancement.
     *
     * @param changed the artifacts it changes
     * @param defined the artifacts it holds
     * @param references the references it writes
     * @param policy the mutability policy it declares
     * @param presets the presets it holds
     * @param edges the typed edges it writes
     * @param languages the languages each held artifact applies to, by bare URN text
     * @param bindings the mission type each held artifact is bound to, by bare URN text
     */
    public PackCompositionFacts(
            List<String> changed,
            List<String> defined,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets,
            List<Relationship> edges,
            Map<String, List<String>> languages,
            Map<String, String> bindings) {
        this(changed, defined, references, policy, presets, edges, languages, bindings, PromptFacts.NONE);
    }

    /**
     * The facts of a pack whose artifacts are bound to no mission type.
     *
     * @param changed the artifacts it changes
     * @param defined the artifacts it holds
     * @param references the references it writes
     * @param policy the mutability policy it declares
     * @param presets the presets it holds
     * @param edges the typed edges it writes
     * @param languages the languages each held artifact applies to, by bare URN text
     */
    public PackCompositionFacts(
            List<String> changed,
            List<String> defined,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets,
            List<Relationship> edges,
            Map<String, List<String>> languages) {
        this(changed, defined, references, policy, presets, edges, languages, Map.of());
    }

    /**
     * The facts of a pack whose artifacts declare no language.
     *
     * @param changed the artifacts it changes
     * @param defined the artifacts it holds
     * @param references the references it writes
     * @param policy the mutability policy it declares
     * @param presets the presets it holds
     * @param edges the typed edges it writes
     */
    public PackCompositionFacts(
            List<String> changed,
            List<String> defined,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets,
            List<Relationship> edges) {
        this(changed, defined, references, policy, presets, edges, Map.of());
    }

    /**
     * The facts of a pack that writes no typed edge.
     *
     * @param changed the artifacts it changes
     * @param defined the artifacts it holds
     * @param references the references it writes
     * @param policy the mutability policy it declares
     * @param presets the presets it holds
     */
    public PackCompositionFacts(
            List<String> changed,
            List<String> defined,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets) {
        this(changed, defined, references, policy, presets, List.of());
    }

    /**
     * The facts of a pack that holds no preset.
     *
     * @param changed the artifacts it changes
     * @param defined the artifacts it holds
     * @param references the references it writes
     * @param policy the mutability policy it declares
     */
    public PackCompositionFacts(
            List<String> changed, List<String> defined, List<String> references, MutabilityPolicySource policy) {
        this(changed, defined, references, policy, List.of());
    }

    /**
     * The facts of a pack that declares no mutability policy and holds no preset.
     *
     * @param changed the artifacts it changes
     * @param defined the artifacts it holds
     * @param references the references it writes
     */
    public PackCompositionFacts(List<String> changed, List<String> defined, List<String> references) {
        this(changed, defined, references, MutabilityPolicySource.empty());
    }
}
