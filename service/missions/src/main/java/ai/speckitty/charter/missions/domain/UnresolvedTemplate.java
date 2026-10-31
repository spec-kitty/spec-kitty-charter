package ai.speckitty.charter.missions.domain;

import java.util.Objects;

/**
 * A mission type's output template that is not a template of its pack (#277, ADR-0044 decision 7 as amended): the
 * typed violation of the type, the step and the path. The pack store refuses the type with it, at load and before a
 * whole-pack write, and words it.
 *
 * @param missionType the type that names the template
 * @param reference the template path and the step that names it
 */
public record UnresolvedTemplate(MissionTypeIdentifier missionType, TemplateReference reference) {

    /**
     * Checks the fields.
     *
     * @param missionType the type
     * @param reference the reference that does not resolve
     */
    public UnresolvedTemplate {
        Objects.requireNonNull(missionType, "missionType");
        Objects.requireNonNull(reference, "reference");
    }
}
