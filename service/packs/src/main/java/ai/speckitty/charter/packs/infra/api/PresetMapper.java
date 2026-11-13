package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.PresetList;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Maps a pack's {@link Preset}s to the contract's {@code Preset} and {@code PresetList} (#98, research R7): the id, the
 * description and one array per kind the preset lists ({@link Preset#ACTIVATION_KINDS}), each item with its must-have
 * mark, the items in the order the file lists them. A kind the preset lists nothing of is left out, as the file leaves
 * it out. Each preset links {@code self} and
 * {@code pack}, and so does the list.
 *
 * <p>The array of a kind is {@code activated<Plural>}, the camel case of the file's {@code activated_<plural>} key: both
 * come from {@link ArtifactKind#plural()}. The generated DTO has one typed setter per array, so {@link #setterOf}
 * names them in an exhaustive switch; {@code PresetsControllerTest} holds every kind's wire name to the camel case of
 * its plural, so a setter bound to the wrong kind, or a kind the contract does not carry, fails there.
 */
@Component
final class PresetMapper {

    private static final String PACKS = "packs";
    private static final String PRESETS = "presets";

    private final Hypermedia links;

    /**
     * A mapper over the link builder.
     *
     * @param links the link builder
     */
    @Autowired
    PresetMapper(Hypermedia links) {
        this.links = Objects.requireNonNull(links, "links");
    }

    /**
     * The list answer: every preset, in the order given, with the list's {@code self} and {@code pack}.
     *
     * @param packId the pack as the request names it
     * @param presets the presets, in id order
     * @return the contract shape
     */
    PresetList list(String packId, List<Preset> presets) {
        Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
        own.put(Relation.SELF, links.link(PACKS, packId, PRESETS));
        own.put(Relation.PACK, links.link(PACKS, packId));
        return new PresetList(
                presets.stream().map(preset -> preset(packId, preset)).toList(), Hypermedia.wire(own));
    }

    /**
     * One preset, with its {@code self} and {@code pack}.
     *
     * @param packId the pack as the request names it
     * @param preset the preset
     * @return the contract shape
     */
    ai.speckitty.charter.api.model.Preset preset(String packId, Preset preset) {
        Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
        own.put(Relation.SELF, links.link(PACKS, packId, PRESETS, preset.id()));
        own.put(Relation.PACK, links.link(PACKS, packId));
        ai.speckitty.charter.api.model.Preset body =
                new ai.speckitty.charter.api.model.Preset(preset.id(), preset.description(), Hypermedia.wire(own));
        for (ArtifactKind kind : Preset.ACTIVATION_KINDS) {
            List<PresetItem> items = preset.artifacts().getOrDefault(kind, List.of());
            if (!items.isEmpty()) {
                setterOf(kind)
                        .accept(body, items.stream().map(PresetMapper::item).toList());
            }
        }
        return body;
    }

    private static ai.speckitty.charter.api.model.PresetItem item(PresetItem item) {
        return new ai.speckitty.charter.api.model.PresetItem(item.id(), item.mustHave());
    }

    /**
     * The setter of the array of a kind: {@code activated<Plural>}. The switch names every kind, so a kind added to the
     * registry does not compile until it is placed here.
     */
    private static BiConsumer<ai.speckitty.charter.api.model.Preset, List<ai.speckitty.charter.api.model.PresetItem>>
            setterOf(ArtifactKind kind) {
        return switch (kind) {
            case DIRECTIVE -> ai.speckitty.charter.api.model.Preset::setActivatedDirectives;
            case TACTIC -> ai.speckitty.charter.api.model.Preset::setActivatedTactics;
            case STYLEGUIDE -> ai.speckitty.charter.api.model.Preset::setActivatedStyleguides;
            case TOOLGUIDE -> ai.speckitty.charter.api.model.Preset::setActivatedToolguides;
            case PARADIGM -> ai.speckitty.charter.api.model.Preset::setActivatedParadigms;
            case PROCEDURE -> ai.speckitty.charter.api.model.Preset::setActivatedProcedures;
            case AGENT_PROFILE -> ai.speckitty.charter.api.model.Preset::setActivatedAgentProfiles;
            case MISSION_STEP_CONTRACT -> ai.speckitty.charter.api.model.Preset::setActivatedMissionStepContracts;
            case GLOSSARY_PACK -> ai.speckitty.charter.api.model.Preset::setActivatedGlossaryPacks;
            case SKILL -> ai.speckitty.charter.api.model.Preset::setActivatedSkills;
            case ANTI_PATTERN -> ai.speckitty.charter.api.model.Preset::setActivatedAntiPatterns;
            case MISSION_TYPE -> ai.speckitty.charter.api.model.Preset::setActivatedMissionTypes;
            case STEP_ENHANCEMENT -> ai.speckitty.charter.api.model.Preset::setActivatedStepEnhancements;
            case ASSET, TEMPLATE, GUARD, LIFECYCLE ->
                throw new IllegalStateException("a preset carries no list of " + kind.plural());
        };
    }
}
