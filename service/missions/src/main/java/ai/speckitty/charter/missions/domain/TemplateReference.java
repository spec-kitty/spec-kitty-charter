package ai.speckitty.charter.missions.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * An output template a mission type names (ADR-0044 decision 7, as amended for #277): the {@code template_path} of an
 * expected output, kept as written, with the step that names it. The path is under the {@code templates/} directory
 * beside the mission type; it names the pack's template whose id is {@code <type>/<path after templates/>}, the
 * template URN's local part.
 *
 * @param step the id of the step whose output names the template; empty when the type's own output names it
 * @param templatePath the template path as written
 */
public record TemplateReference(Optional<String> step, String templatePath) {

    /**
     * Checks the fields.
     *
     * @param step the naming step's id, if a step names it
     * @param templatePath the path as written; an expected output's template path
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the path breaks an expected
     *     output's template path rules
     */
    public TemplateReference {
        Objects.requireNonNull(step, "step");
        ExpectedArtifact.TEMPLATE_RULES.guard(Objects.requireNonNull(templatePath, "templatePath"));
    }

    /**
     * The id of the template the path names, for a type: {@code <type>/<path after templates/>}, as written.
     *
     * @param missionType the type that names it
     * @return the template id
     */
    public String templateId(MissionTypeIdentifier missionType) {
        return missionType.value() + "/" + templatePath.substring(ExpectedArtifact.TEMPLATES.length());
    }
}
