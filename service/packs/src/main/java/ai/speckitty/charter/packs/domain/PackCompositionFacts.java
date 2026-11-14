package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the compose needs to know about one pack, read from one assembly of it: the artifacts its graph documents say it
 * changes, the artifacts it holds, the references it writes, the mutability policy it declares, the activation presets
 * it holds, the typed edges it writes, the slots its step prompts declare and its step enhancements fill, and the terms
 * its glossary contexts hold.
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
 * @param terms the terms its glossary contexts hold, as bare URNs ({@code term:<context-id>/<term-id>}), from the same
 *     assembly, in authored order. A term is an item of its context, never an artifact, so it is not among
 *     {@code defined} (ADR-0049 decision 3); empty when the pack holds no glossary context
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
        PromptFacts prompts,
        List<String> terms) {

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
     * @param terms the terms its glossary contexts hold, as bare URNs
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
        Objects.requireNonNull(terms, "terms");
        changed = List.copyOf(changed);
        defined = List.copyOf(defined);
        references = List.copyOf(references);
        presets = List.copyOf(presets);
        edges = List.copyOf(edges);
        Map<String, List<String>> copy = new LinkedHashMap<>();
        languages.forEach((urn, declared) -> copy.put(urn, List.copyOf(declared)));
        languages = Collections.unmodifiableMap(copy);
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
        terms = List.copyOf(terms);
    }

    /**
     * The facts of a pack that holds no glossary context.
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
    public PackCompositionFacts(
            List<String> changed,
            List<String> defined,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets,
            List<Relationship> edges,
            Map<String, List<String>> languages,
            Map<String, String> bindings,
            PromptFacts prompts) {
        this(changed, defined, references, policy, presets, edges, languages, bindings, prompts, List.of());
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

    /**
     * Whether the pack holds what a bare URN names: the one answer the compose and the inbound scan share. A term
     * ({@code term:<context-id>/<term-id>}) is an item of its glossary context, not an artifact (ADR-0049 decision 3),
     * so the pack holds it when it holds the context and the context holds the term.
     *
     * @param urn the bare URN
     * @return {@code true} when {@code defined}, or for a term {@code terms} and its held context, name it
     */
    public boolean holds(Urn urn) {
        return holds(defined, terms, urn);
    }

    /**
     * The rule of {@link #holds(Urn)} over a pack's held artifacts and terms, for a reader that holds the two lists
     * apart from the facts ({@link PackCompositionResolver.PackUnderResolution#holds}).
     *
     * @param defined the artifacts the pack holds, as bare URN texts
     * @param terms the terms its glossary contexts hold, as bare URN texts
     * @param urn the bare URN
     * @return {@code true} when the pack holds it
     */
    public static boolean holds(Collection<String> defined, Collection<String> terms, Urn urn) {
        Objects.requireNonNull(urn, "urn");
        if (urn.kind() == NodeKind.TERM) {
            return terms.contains(urn.toString())
                    && GlossaryTerm.contextOf(urn)
                            .filter(context -> defined.contains(context.toString()))
                            .isPresent();
        }
        return defined.contains(urn.toString());
    }
}
