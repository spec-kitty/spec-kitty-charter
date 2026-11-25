package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What the compose needs to know about one pack, read from one assembly of it: the artifacts its graph documents say it
 * changes, the artifacts it holds, the references it writes, the mutability policy it declares, the activation presets
 * it holds, the typed edges it writes, the slots its step prompts declare and its step enhancements fill, and the terms
 * its glossary contexts hold.
 *
 * @param changed the distinct targets of its {@code overrides} relations, in first-seen order (an {@code enhances}
 *     relation changes nothing, A14)
 * @param held the artifacts it holds as artifact files, as bare URNs ({@code <kind>:<id>}), in a stable order: what a
 *     preset's {@code must_have} item needs a pack of the declaring pack's own closure to hold (ruling R-l)
 * @param declared every URN its graph documents declare as a node, plus {@code held}, in a stable order: what a preset
 *     item needs a pack of the own closure to declare as its kind, and what a reference can name in the pack (a
 *     mission type step {@code action:<M>/<step>} is a graph node of its type's file, declared and never held)
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
 * @param prompts the slots each held step prompt declares and the fills each held step enhancement writes, from the
 *     same read (research R9); {@link PromptFacts#NONE} when it holds neither
 * @param terms the terms its glossary contexts hold, as bare URNs ({@code term:<context-id>/<term-id>}), from the same
 *     assembly, in authored order. A term is an item of its context, never an artifact, so it is not among
 *     {@code held} or {@code declared} (ADR-0049 decision 3); empty when the pack holds no glossary context
 * @param delegations the substep delegation candidates its mission types write with another pack's name, which the
 *     compose resolves in the closure (FR-005, {@link SubstepDelegationRule#qualifiedCandidates}); empty when none is
 */
public record PackCompositionFacts(
        List<String> changed,
        List<String> held,
        List<String> declared,
        List<String> references,
        MutabilityPolicySource policy,
        List<Preset> presets,
        List<Relationship> edges,
        Map<String, List<String>> languages,
        PromptFacts prompts,
        List<String> terms,
        List<SubstepDelegationRule.QualifiedCandidate> delegations) {

    /** A pack that changes, holds and writes nothing, declares no policy and holds no preset, prompt or enhancement. */
    public static final PackCompositionFacts NONE = new PackCompositionFacts(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            MutabilityPolicySource.empty(),
            List.of(),
            List.of(),
            Map.of(),
            PromptFacts.NONE,
            List.of(),
            List.of());

    /**
     * Checks the fields and copies the lists.
     *
     * @param changed the artifacts it changes
     * @param held the artifacts it holds as artifact files
     * @param declared the graph-node URNs it declares, {@code held} added to them
     * @param references the references it writes
     * @param policy the mutability policy it declares
     * @param presets the presets it holds
     * @param edges the typed edges it writes
     * @param languages the languages each held artifact applies to, by bare URN text
     * @param prompts the slots its step prompts declare and its step enhancements fill
     * @param terms the terms its glossary contexts hold, as bare URNs
     * @param delegations the qualified substep delegation candidates it writes
     */
    public PackCompositionFacts {
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(held, "held");
        Objects.requireNonNull(declared, "declared");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(presets, "presets");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(languages, "languages");
        Objects.requireNonNull(prompts, "prompts");
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(delegations, "delegations");
        changed = List.copyOf(changed);
        delegations = List.copyOf(delegations);
        held = List.copyOf(held);
        Set<String> declaredOrHeld = new LinkedHashSet<>(declared);
        declaredOrHeld.addAll(held);
        declared = List.copyOf(declaredOrHeld);
        references = List.copyOf(references);
        presets = List.copyOf(presets);
        edges = List.copyOf(edges);
        Map<String, List<String>> copy = new LinkedHashMap<>();
        languages.forEach((urn, applies) -> copy.put(urn, List.copyOf(applies)));
        languages = Collections.unmodifiableMap(copy);
        terms = List.copyOf(terms);
    }

    /**
     * What a reference can name in the pack, as bare URNs (operator ruling R-v): every artifact file it holds
     * ({@code held}), then every step ({@code action:<M>/<step>}) of a mission type it holds that its graph documents
     * declare, a step being a value of its type's file and never a file of its own (A7 Q1). A node of any other kind is no holding without its file (ruling R-l: an
     * {@code anti_pattern} too is held only by its file), so a reference to it does not resolve. It is derived from
     * {@code held} and {@code declared}, and is what the compose reads of the pack
     * ({@link PackCompositionResolver.PackUnderResolution#referableArtifacts}).
     *
     * @return the URNs, {@code held} first, in a stable order
     */
    public List<String> referable() {
        Set<String> referable = new LinkedHashSet<>(held);
        declared.stream()
                .filter(urn -> Urn.parse(urn)
                        .filter(parsed -> parsed.kind() == NodeKind.ACTION)
                        .flatMap(StepHolding::missionTypeOf)
                        .filter(type -> held.contains(type.toString()))
                        .isPresent())
                .forEach(referable::add);
        return List.copyOf(referable);
    }

    /**
     * Whether the pack holds what a bare URN names: the one answer the compose and the inbound scan share, over
     * {@link #referable()}. A term ({@code term:<context-id>/<term-id>}) is an item of its glossary context, not an
     * artifact (ADR-0049 decision 3), so the pack holds it when it holds the context and the context holds the term.
     *
     * @param urn the bare URN
     * @return {@code true} when {@link #referable()}, or for a term {@code terms} and its held context, name it
     */
    public boolean holds(Urn urn) {
        return holds(referable(), terms, urn);
    }

    /**
     * The rule of {@link #holds(Urn)} over what a reference can name in a pack and its terms, for a reader that holds
     * the two lists apart from the facts ({@link PackCompositionResolver.PackUnderResolution#holds}).
     *
     * @param referable what a reference can name in the pack ({@link #referable()}), as bare URN texts
     * @param terms the terms its glossary contexts hold, as bare URN texts
     * @param urn the bare URN
     * @return {@code true} when the pack holds it
     */
    public static boolean holds(Collection<String> referable, Collection<String> terms, Urn urn) {
        Objects.requireNonNull(urn, "urn");
        if (urn.kind() == NodeKind.TERM) {
            return terms.contains(urn.toString())
                    && GlossaryTerm.contextOf(urn)
                            .filter(context -> referable.contains(context.toString()))
                            .isPresent();
        }
        return referable.contains(urn.toString());
    }
}
