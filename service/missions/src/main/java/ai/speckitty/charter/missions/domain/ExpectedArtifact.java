package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An artifact a step or the mission produces: one of its postconditions, checked in the mission's workspace (ADR-0044,
 * ruling Q2). Its key is opaque: the same key may stand at two steps, and its dotted segments carry no meaning the other
 * fields depend on. Its class says what the artifact is to the mission ({@link ArtifactClass#INPUT} is the mission's
 * input document, such as the specification a specify step produces).
 *
 * @param artifactKey the opaque dotted key, kept verbatim
 * @param artifactClass what the artifact is to the mission
 * @param pathPattern a glob in the mission's workspace, not a pack file
 * @param blocking whether a missing artifact blocks the step
 * @param templatePath the template that produces the artifact, relative to the mission type's file, when there is one
 */
public record ExpectedArtifact(
        String artifactKey,
        ArtifactClass artifactClass,
        String pathPattern,
        boolean blocking,
        Optional<String> templatePath) {

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]*(\\.[a-z0-9_-]+)+");
    private static final Pattern PARENT = Pattern.compile("(^|/)\\.\\.(/|$)");
    private static final Pattern DRIVE = Pattern.compile("[A-Za-z]:");

    private static final ModularRuleset<String> KEY_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "An expected artifact's key is dotted: a letter-first segment, then at least one more.",
                    ExpectedArtifact::isDottedKey));
    private static final ModularRuleset<String> PATH_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "An expected artifact's path pattern is relative and stays in the workspace.",
                    ExpectedArtifact::isRelativeInside));
    /** The directory beside its mission type that a template path starts with. */
    static final String TEMPLATES = "templates/";

    /** A {@code .} or empty segment: a spelling of a path that is not the template's own (#277). */
    private static final Pattern NON_CANONICAL = Pattern.compile("(^|/)\\.?(/|$)");

    static final ModularRuleset<String> TEMPLATE_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "An expected artifact's template path is under templates/ beside its mission type and stays there.",
                    path -> path.startsWith(TEMPLATES) && isRelativeInside(path)))
            .must(Constraints.of(
                    "An expected artifact's template path is spelled as its template's id: no '.' or empty segment.",
                    path -> !NON_CANONICAL.matcher(path).find()));

    /**
     * Checks every component.
     *
     * @param artifactKey the key
     * @param artifactClass the class
     * @param pathPattern the workspace glob
     * @param blocking whether it blocks
     * @param templatePath the template path, possibly empty
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the key is not dotted, a path is
     *     absolute, climbs out or uses a backslash or a drive, or the template path is not under {@code templates/} or
     *     has a {@code .} or empty segment
     */
    public ExpectedArtifact {
        KEY_RULES.guard(Objects.requireNonNull(artifactKey, "artifactKey"));
        Objects.requireNonNull(artifactClass, "artifactClass");
        PATH_RULES.guard(Objects.requireNonNull(pathPattern, "pathPattern"));
        Objects.requireNonNull(templatePath, "templatePath").ifPresent(TEMPLATE_RULES::guard);
    }

    /**
     * Whether a key is dotted: a letter-first segment, then at least one more.
     *
     * @param key the key
     * @return {@code true} when it is
     */
    static boolean isDottedKey(String key) {
        return KEY.matcher(key).matches();
    }

    /**
     * Whether a path is relative, {@code /}-separated and does not climb out: no leading {@code /}, no {@code ..}
     * segment, no backslash and no drive ({@code C:}), whatever the host.
     *
     * @param path the path
     * @return {@code true} when it is
     */
    static boolean isRelativeInside(String path) {
        return !path.isEmpty()
                && !path.startsWith("/")
                && path.indexOf('\\') < 0
                && !DRIVE.matcher(path).lookingAt()
                && !PARENT.matcher(path).find();
    }
}
