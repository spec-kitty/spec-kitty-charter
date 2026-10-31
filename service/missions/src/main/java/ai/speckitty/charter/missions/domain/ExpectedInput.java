package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;

/**
 * An artifact a step needs before it runs: one of its preconditions, checked in the mission's workspace (ADR-0044,
 * ruling Q2). It has no class and no template: those describe what a step produces.
 *
 * @param artifactKey the opaque dotted key, kept verbatim
 * @param pathPattern a glob in the mission's workspace, not a pack file
 * @param blocking whether a missing input blocks the step
 */
public record ExpectedInput(String artifactKey, String pathPattern, boolean blocking) {

    private static final ModularRuleset<String> KEY_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "An expected input's key is dotted: a letter-first segment, then at least one more.",
                    ExpectedArtifact::isDottedKey));
    private static final ModularRuleset<String> PATH_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "An expected input's path pattern is relative and stays in the workspace.",
                    ExpectedArtifact::isRelativeInside));

    /**
     * Checks every component.
     *
     * @param artifactKey the key
     * @param pathPattern the workspace glob
     * @param blocking whether it blocks
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the key is not dotted or the path
     *     is absolute or climbs out
     */
    public ExpectedInput {
        KEY_RULES.guard(Objects.requireNonNull(artifactKey, "artifactKey"));
        PATH_RULES.guard(Objects.requireNonNull(pathPattern, "pathPattern"));
    }
}
