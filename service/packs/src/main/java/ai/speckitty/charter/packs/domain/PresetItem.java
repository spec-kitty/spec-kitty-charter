package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * One item of an activation preset's list: the id of an artifact or a mission type, and whether it is must-have
 * (ADR-0031, operator ruling on #195). The must-have items of a preset are its required set. Equal by value.
 *
 * @param id the artifact's or mission type's id, as its own file declares it (never a file stem)
 * @param mustHave whether the item is part of the preset's required set
 */
public record PresetItem(String id, boolean mustHave) {

    private static final ModularRuleset<String> ID_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A preset item's id must not be blank.", id -> id != null && !id.isBlank()));

    /**
     * Checks the id.
     *
     * @param id the item's id
     * @param mustHave whether the item is must-have
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the id is blank
     */
    public PresetItem {
        ID_RULES.guard(id);
    }

    /**
     * An item that is not must-have.
     *
     * @param id the item's id
     * @return the item
     */
    public static PresetItem of(String id) {
        return new PresetItem(id, false);
    }

    /**
     * A must-have item.
     *
     * @param id the item's id
     * @return the item
     */
    public static PresetItem mustHave(String id) {
        return new PresetItem(id, true);
    }
}
