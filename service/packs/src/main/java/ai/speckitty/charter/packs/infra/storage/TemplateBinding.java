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
     * Where a template is written.
     *
     * @param template the template
     * @return {@code templates/<id>}, pack-relative
     */
    String pathOf(Template template) {
        return PackFileSet.TEMPLATES + template.id().value();
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
