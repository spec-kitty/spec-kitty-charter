package ai.speckitty.charter.packs.fixtures;

import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PromptFacts;
import java.util.List;
import java.util.Map;

/**
 * Composition facts a test writes with the leading parts only, the others empty: the overloads that moved out of
 * {@link PackCompositionFacts}, which only tests called (#659). Each part not given is what a pack that has none of it
 * reads as.
 */
public final class CompositionFacts {

    private CompositionFacts() {}

    /**
     * Facts with the first 4 parts, the rest empty.
     *
     * @param changed the distinct targets of its {@code overrides} relations
     * @param held the artifacts it holds, as bare URNs
     * @param declared every URN its graph documents declare, plus {@code held}
     * @param references the references it writes, as written
     * @return the facts
     */
    public static PackCompositionFacts of(
            List<String> changed, List<String> held, List<String> declared, List<String> references) {
        return new PackCompositionFacts(
                changed,
                held,
                declared,
                references,
                MutabilityPolicySource.empty(),
                List.of(),
                List.of(),
                Map.of(),
                PromptFacts.NONE,
                List.of(),
                List.of());
    }

    /**
     * Facts with the first 5 parts, the rest empty.
     *
     * @param changed the distinct targets of its {@code overrides} relations
     * @param held the artifacts it holds, as bare URNs
     * @param declared every URN its graph documents declare, plus {@code held}
     * @param references the references it writes, as written
     * @param policy the mutability policy its descriptor names
     * @return the facts
     */
    public static PackCompositionFacts of(
            List<String> changed,
            List<String> held,
            List<String> declared,
            List<String> references,
            MutabilityPolicySource policy) {
        return new PackCompositionFacts(
                changed,
                held,
                declared,
                references,
                policy,
                List.of(),
                List.of(),
                Map.of(),
                PromptFacts.NONE,
                List.of(),
                List.of());
    }

    /**
     * Facts with the first 6 parts, the rest empty.
     *
     * @param changed the distinct targets of its {@code overrides} relations
     * @param held the artifacts it holds, as bare URNs
     * @param declared every URN its graph documents declare, plus {@code held}
     * @param references the references it writes, as written
     * @param policy the mutability policy its descriptor names
     * @param presets the activation presets it holds
     * @return the facts
     */
    public static PackCompositionFacts of(
            List<String> changed,
            List<String> held,
            List<String> declared,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets) {
        return new PackCompositionFacts(
                changed,
                held,
                declared,
                references,
                policy,
                presets,
                List.of(),
                Map.of(),
                PromptFacts.NONE,
                List.of(),
                List.of());
    }

    /**
     * Facts with the first 7 parts, the rest empty.
     *
     * @param changed the distinct targets of its {@code overrides} relations
     * @param held the artifacts it holds, as bare URNs
     * @param declared every URN its graph documents declare, plus {@code held}
     * @param references the references it writes, as written
     * @param policy the mutability policy its descriptor names
     * @param presets the activation presets it holds
     * @param edges every typed edge it writes, as written
     * @return the facts
     */
    public static PackCompositionFacts of(
            List<String> changed,
            List<String> held,
            List<String> declared,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets,
            List<Relationship> edges) {
        return new PackCompositionFacts(
                changed,
                held,
                declared,
                references,
                policy,
                presets,
                edges,
                Map.of(),
                PromptFacts.NONE,
                List.of(),
                List.of());
    }

    /**
     * Facts with the first 8 parts, the rest empty.
     *
     * @param changed the distinct targets of its {@code overrides} relations
     * @param held the artifacts it holds, as bare URNs
     * @param declared every URN its graph documents declare, plus {@code held}
     * @param references the references it writes, as written
     * @param policy the mutability policy its descriptor names
     * @param presets the activation presets it holds
     * @param edges every typed edge it writes, as written
     * @param languages the {@code applies_to_languages} of each held artifact that declares any
     * @return the facts
     */
    public static PackCompositionFacts of(
            List<String> changed,
            List<String> held,
            List<String> declared,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets,
            List<Relationship> edges,
            Map<String, List<String>> languages) {
        return new PackCompositionFacts(
                changed,
                held,
                declared,
                references,
                policy,
                presets,
                edges,
                languages,
                PromptFacts.NONE,
                List.of(),
                List.of());
    }

    /**
     * Facts with the first 9 parts, the rest empty.
     *
     * @param changed the distinct targets of its {@code overrides} relations
     * @param held the artifacts it holds, as bare URNs
     * @param declared every URN its graph documents declare, plus {@code held}
     * @param references the references it writes, as written
     * @param policy the mutability policy its descriptor names
     * @param presets the activation presets it holds
     * @param edges every typed edge it writes, as written
     * @param languages the {@code applies_to_languages} of each held artifact that declares any
     * @param prompts the slots its step prompts declare and the fills its step enhancements write
     * @return the facts
     */
    public static PackCompositionFacts of(
            List<String> changed,
            List<String> held,
            List<String> declared,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets,
            List<Relationship> edges,
            Map<String, List<String>> languages,
            PromptFacts prompts) {
        return new PackCompositionFacts(
                changed, held, declared, references, policy, presets, edges, languages, prompts, List.of(), List.of());
    }

    /**
     * Facts with the first 10 parts, the rest empty.
     *
     * @param changed the distinct targets of its {@code overrides} relations
     * @param held the artifacts it holds, as bare URNs
     * @param declared every URN its graph documents declare, plus {@code held}
     * @param references the references it writes, as written
     * @param policy the mutability policy its descriptor names
     * @param presets the activation presets it holds
     * @param edges every typed edge it writes, as written
     * @param languages the {@code applies_to_languages} of each held artifact that declares any
     * @param prompts the slots its step prompts declare and the fills its step enhancements write
     * @param terms the terms its glossary contexts hold
     * @return the facts
     */
    public static PackCompositionFacts of(
            List<String> changed,
            List<String> held,
            List<String> declared,
            List<String> references,
            MutabilityPolicySource policy,
            List<Preset> presets,
            List<Relationship> edges,
            Map<String, List<String>> languages,
            PromptFacts prompts,
            List<String> terms) {
        return new PackCompositionFacts(
                changed, held, declared, references, policy, presets, edges, languages, prompts, terms, List.of());
    }
}
