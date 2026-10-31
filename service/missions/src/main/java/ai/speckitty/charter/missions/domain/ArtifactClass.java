package ai.speckitty.charter.missions.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What an {@link ExpectedArtifact} is to the mission, as the schema's {@code artifact_class} names it. */
public enum ArtifactClass {
    /** The mission's input document, such as its specification. */
    INPUT,
    /** An artifact the step produces. */
    OUTPUT,
    /** An artifact that evidences the work. */
    EVIDENCE,
    /** An artifact of the workflow itself, such as a task list. */
    WORKFLOW,
    /** An artifact the runtime writes. */
    RUNTIME;

    /**
     * The spelling in the schema.
     *
     * @return the lower-case name, for example {@code evidence}
     */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Looks a class up by its schema spelling.
     *
     * @param token the spelling, for example {@code output}
     * @return the class; empty when no class has that spelling
     */
    public static Optional<ArtifactClass> fromToken(String token) {
        return Arrays.stream(values()).filter(c -> c.token().equals(token)).findFirst();
    }
}
