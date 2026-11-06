package ai.speckitty.charter.packs.domain;

import java.util.List;
import java.util.Objects;

/**
 * What the compose needs to know about one pack, read from one assembly of it: the artifacts its graph documents say it
 * changes, the artifacts it holds, the references it writes, the mutability policy it declares and the activation presets
 * it holds.
 *
 * @param changed the distinct targets of its {@code enhances} and {@code overrides} relations, in first-seen order
 * @param defined the artifacts it holds, as bare URNs ({@code <kind>:<id>}), in a stable order
 * @param references the references it writes, as written (bare or pack-qualified), in first-seen order
 * @param policy the mutability policy its descriptor names (ADR-0063); {@link MutabilityPolicySource#empty()} when it
 *     names none
 * @param presets the activation presets the pack holds ({@link Pack#presets()}), from the same assembly; empty when it
 *     holds none
 */
public record PackCompositionFacts(
        List<String> changed,
        List<String> defined,
        List<String> references,
        MutabilityPolicySource policy,
        List<Preset> presets) {

    /** A pack that changes, holds and writes nothing, declares no policy and holds no preset. */
    public static final PackCompositionFacts NONE = new PackCompositionFacts(List.of(), List.of(), List.of());

    /**
     * Checks the fields and copies the lists.
     *
     * @param changed the artifacts it changes
     * @param defined the artifacts it holds
     * @param references the references it writes
     * @param policy the mutability policy it declares
     * @param presets the presets it holds
     */
    public PackCompositionFacts {
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(defined, "defined");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(presets, "presets");
        changed = List.copyOf(changed);
        defined = List.copyOf(defined);
        references = List.copyOf(references);
        presets = List.copyOf(presets);
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
