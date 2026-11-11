package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.validation.Constraint;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An activation preset of a pack ({@code presets/<id>.yaml}): a named selection of artifacts to activate, in the
 * sanitized shape of ADR-0042. A preset is a pack-level value, not an artifact: core gives it no URN, and the pack holds
 * it beside its artifacts ({@link Pack#presets()}).
 *
 * <p>A preset may name artifacts of any activatable kind, skills, glossary packs and mission types included (one kind
 * model, IC-20, FR-041). Each item may be must-have. The must-have items are the preset's {@linkplain #required()
 * required set}, derived and never declared a second time (ADR-0031 decisions 1 and 2), and a preset with any must-have
 * item is {@linkplain #governing() governing}; one without is a convenience preset. There is no kind gate. A kind the
 * preset selects nothing of has no list: an empty list is not a value (one spelling per meaning, ADR-0042). Equal by
 * value.
 *
 * @param id the preset's identity: lowercase words joined by single hyphens, at most 64 characters; its file stem
 * @param description what applying the preset puts in force; not blank
 * @param artifacts the items of each kind the preset names ({@link #ACTIVATION_KINDS}), each list non-empty, in authored
 *     order, each id once
 */
public record Preset(String id, String description, Map<ArtifactKind, List<PresetItem>> artifacts) {

    /** The kinds a preset can carry an activation list of: every activatable kind of the kernel registry. */
    public static final Set<ArtifactKind> ACTIVATION_KINDS =
            Collections.unmodifiableSet(Arrays.stream(ArtifactKind.values())
                    .filter(ArtifactKind::activatable)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(ArtifactKind.class))));

    /**
     * Checks every invariant, failing fast on the first breach with a message that names the preset and what breaks
     * it, so two bad presets in one load are told apart. The ruleset is built per preset because each message names
     * the preset and the offending value, which a static constraint message cannot.
     */
    private static void guard(String id, String description, Map<ArtifactKind, List<PresetItem>> artifacts) {
        String preset = "Preset '" + id + "': ";
        List<Constraint<String>> rules = new ArrayList<>();
        rules.add(Constraints.of(
                preset + "the id must be lowercase words joined by single hyphens, at most 64 characters.",
                IdentifierGrammar::isLetterFirstSlug));
        rules.add(Constraints.of(preset + "the description must not be blank.", candidate -> !description.isBlank()));
        for (Map.Entry<ArtifactKind, List<PresetItem>> list : artifacts.entrySet()) {
            String plural = list.getKey().plural();
            if (!ACTIVATION_KINDS.contains(list.getKey())) {
                rules.add(breached(preset + plural + " are never activated, so a preset carries no list of them."));
            }
            if (list.getValue().isEmpty()) {
                rules.add(breached(preset + "the activation list of " + plural
                        + " must not be empty; leave the kind out instead."));
            }
            firstRepeat(list.getValue().stream().map(PresetItem::id).toList())
                    .ifPresent(item -> rules.add(breached(preset + "the activation list of " + plural
                            + " must name each id once; '" + item + "' is listed twice.")));
        }
        ModularRuleset.of(rules).guard(id);
    }

    /** A constraint already known to be breached, with the message that says why. */
    private static Constraint<String> breached(String message) {
        return Constraints.of(message, candidate -> false);
    }

    /** The first value that appears a second time, in list order. */
    private static <T> Optional<T> firstRepeat(List<T> values) {
        Set<T> seen = new HashSet<>();
        return values.stream().filter(value -> !seen.add(value)).findFirst();
    }

    /**
     * Copies the collections and checks every invariant.
     *
     * @param id the preset's id
     * @param description the description
     * @param artifacts the items per kind
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    public Preset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        Map<ArtifactKind, List<PresetItem>> copy = new EnumMap<>(ArtifactKind.class);
        artifacts.forEach((kind, items) -> copy.put(kind, List.copyOf(items)));
        artifacts = Collections.unmodifiableMap(copy);
        guard(id, description, artifacts);
    }

    /**
     * The must-have item ids of each kind, mission types included, in authored order; a kind without one is absent.
     *
     * @return an unmodifiable map
     */
    public Map<ArtifactKind, List<String>> required() {
        Map<ArtifactKind, List<String>> out = new EnumMap<>(ArtifactKind.class);
        artifacts.forEach((kind, items) -> {
            List<String> ids = mustHaveIds(items);
            if (!ids.isEmpty()) {
                out.put(kind, ids);
            }
        });
        return Collections.unmodifiableMap(out);
    }

    /**
     * Whether the preset governs: it marks at least one item must-have. Derived, never declared.
     *
     * @return {@code true} for a governing preset, {@code false} for a convenience preset
     */
    public boolean governing() {
        return !required().isEmpty();
    }

    private static List<String> mustHaveIds(List<PresetItem> items) {
        return items.stream().filter(PresetItem::mustHave).map(PresetItem::id).toList();
    }
}
