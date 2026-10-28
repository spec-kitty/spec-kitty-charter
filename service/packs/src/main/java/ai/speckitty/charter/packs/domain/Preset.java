package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.validation.Constraint;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An activation preset of a pack ({@code presets/<id>.yaml}): a named selection of artifacts and mission types to
 * activate, in the sanitized shape of ADR-0043. A preset is a pack-level value, not an artifact: core gives it no URN,
 * and the pack holds it beside its artifacts ({@link Pack#presets()}).
 *
 * <p>Each item may be must-have. The must-have items are the preset's {@linkplain #required() required set}, derived and
 * never declared a second time (ADR-0031 decisions 1 and 2), and a preset with any must-have item is
 * {@linkplain #governing() governing}; one without is a convenience preset. The kind gate {@link #activatedKinds()}
 * names the kinds the preset activates; empty means the preset gates no kind. A kind the preset selects nothing of has
 * no list: an empty list is not a value (one spelling per meaning, ADR-0043). Equal by value.
 *
 * @param id the preset's identity: lowercase words joined by single hyphens, at most 64 characters; its file stem
 * @param description what applying the preset puts in force; not blank
 * @param activatedKinds the kind gate, in authored order, each kind once; empty when the preset gates no kind
 * @param artifacts the items of each artifact kind a preset may activate ({@link #ACTIVATION_KINDS}), each list
 *     non-empty, in authored order, each id once
 * @param missionTypes the mission-type items, in authored order, each id once; empty when the preset selects none
 */
public record Preset(
        String id,
        String description,
        List<ArtifactKind> activatedKinds,
        Map<ArtifactKind, List<PresetItem>> artifacts,
        List<PresetItem> missionTypes) {

    /**
     * The artifact kinds a preset can carry an activation list of: the activatable kinds, less the skill and the
     * glossary pack, which presets do not govern (core's {@code preset_activation_keys}).
     */
    public static final Set<ArtifactKind> ACTIVATION_KINDS = Collections.unmodifiableSet(EnumSet.of(
            ArtifactKind.DIRECTIVE,
            ArtifactKind.TACTIC,
            ArtifactKind.STYLEGUIDE,
            ArtifactKind.TOOLGUIDE,
            ArtifactKind.PARADIGM,
            ArtifactKind.PROCEDURE,
            ArtifactKind.AGENT_PROFILE,
            ArtifactKind.MISSION_STEP_CONTRACT,
            ArtifactKind.ANTI_PATTERN));

    /** The id grammar of a preset, as the {@code preset} schema has it. */
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

    private static final int MAX_ID_LENGTH = 64;

    /**
     * Checks every invariant, failing fast on the first breach with a message that names the preset and what breaks
     * it, so two bad presets in one load are told apart.
     */
    private static void guard(
            String id,
            String description,
            List<ArtifactKind> activatedKinds,
            Map<ArtifactKind, List<PresetItem>> artifacts,
            List<PresetItem> missionTypes) {
        String preset = "Preset '" + id + "': ";
        List<Constraint<String>> rules = new ArrayList<>();
        rules.add(Constraints.of(
                preset + "the id must be lowercase words joined by single hyphens, at most 64 characters.",
                candidate -> candidate.length() <= MAX_ID_LENGTH
                        && ID.matcher(candidate).matches()));
        rules.add(Constraints.of(preset + "the description must not be blank.", candidate -> !description.isBlank()));
        firstRepeat(activatedKinds)
                .ifPresent(kind -> rules.add(breached(
                        preset + "the kind gate must name each kind once; '" + kind.plural() + "' is named twice.")));
        for (Map.Entry<ArtifactKind, List<PresetItem>> list : artifacts.entrySet()) {
            String plural = list.getKey().plural();
            if (!ACTIVATION_KINDS.contains(list.getKey())) {
                rules.add(breached(preset + "presets do not govern " + plural + "; a preset carries activation lists"
                        + " only of directives, tactics, styleguides, toolguides, paradigms, procedures, agent profiles,"
                        + " mission step contracts and anti-patterns."));
            }
            if (list.getValue().isEmpty()) {
                rules.add(breached(preset + "the activation list of " + plural
                        + " must not be empty; leave the kind out instead."));
            }
            firstRepeat(list.getValue().stream().map(PresetItem::id).toList())
                    .ifPresent(item -> rules.add(breached(preset + "the activation list of " + plural
                            + " must name each id once; '" + item + "' is listed twice.")));
        }
        firstRepeat(missionTypes.stream().map(PresetItem::id).toList())
                .ifPresent(item -> rules.add(breached(
                        preset + "the mission types must name each id once; '" + item + "' is listed twice.")));
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
     * @param activatedKinds the kind gate
     * @param artifacts the artifact items per kind
     * @param missionTypes the mission-type items
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    public Preset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        activatedKinds = List.copyOf(activatedKinds);
        Map<ArtifactKind, List<PresetItem>> copy = new EnumMap<>(ArtifactKind.class);
        artifacts.forEach((kind, items) -> copy.put(kind, List.copyOf(items)));
        artifacts = Collections.unmodifiableMap(copy);
        missionTypes = List.copyOf(missionTypes);
        guard(id, description, activatedKinds, artifacts, missionTypes);
    }

    /**
     * The must-have item ids of each artifact kind, in authored order; a kind without one is absent.
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
     * The must-have mission-type ids, in authored order.
     *
     * @return an unmodifiable list; empty when no mission type is must-have
     */
    public List<String> requiredMissionTypes() {
        return mustHaveIds(missionTypes);
    }

    /**
     * Whether the preset governs: it marks at least one item must-have. Derived, never declared.
     *
     * @return {@code true} for a governing preset, {@code false} for a convenience preset
     */
    public boolean governing() {
        return !required().isEmpty() || !requiredMissionTypes().isEmpty();
    }

    private static List<String> mustHaveIds(List<PresetItem> items) {
        return items.stream().filter(PresetItem::mustHave).map(PresetItem::id).toList();
    }
}
