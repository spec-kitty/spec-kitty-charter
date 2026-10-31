package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.TemplateIdentifier;

/**
 * How a template is stored: a file of the {@code bytes} content form under the pack's top-level {@code templates/}
 * directory, whose path under that directory is the template's id. It is concrete, not a {@link KindBinding}: that
 * interface is YAML- and schema-shaped, and a template is neither. The store's one bytes branch and {@link PackLayout}
 * call it; the interface is extracted when a second non-YAML kind arrives.
 */
enum TemplateBinding {
    /** The binding. */
    INSTANCE;

    /**
     * Builds a template from a file the store read.
     *
     * @param pack the owning pack
     * @param pathUnderTemplates the file's path relative to {@code templates/}, {@code /}-separated
     * @param content the file's bytes
     * @return the template
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the path is no template id
     */
    Template toEntity(PackIdentifier pack, String pathUnderTemplates, Payload content) {
        return Template.builder()
                .id(new TemplateIdentifier(pathUnderTemplates))
                .packId(pack)
                .content(content)
                .build();
    }

    /**
     * Builds a template from a file the store read at {@code place}: a mission type's template when the place is under
     * {@code missions/<type>/templates/} (#194, ADR-0044), a top-level one otherwise.
     *
     * @param pack the owning pack
     * @param place the file's pack-relative path
     * @param content the file's bytes
     * @return the template, its id {@link PackFileSet#templateId}
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the path is no template id
     */
    Template toEntityAt(PackIdentifier pack, String place, Payload content) {
        String id = PackFileSet.templateId(place);
        String missionType = place.startsWith(PackFileSet.TEMPLATES) ? null : id.substring(0, id.indexOf('/'));
        return Template.builder()
                .id(new TemplateIdentifier(id))
                .packId(pack)
                .content(content)
                .missionType(missionType)
                .build();
    }

    /**
     * Where a template is written.
     *
     * @param template the template
     * @return {@code templates/<id>}, or {@code missions/<type>/templates/<path>} for a mission type's, pack-relative
     */
    String pathOf(Template template) {
        String id = template.id().value();
        return template.missionType()
                .map(type -> "missions/" + type + "/templates/" + id.substring(type.length() + 1))
                .orElse(PackFileSet.TEMPLATES + id);
    }

    /**
     * What a template's file holds.
     *
     * @param template the template
     * @return its bytes
     */
    Payload payloadOf(Template template) {
        return template.content();
    }
}
