package ai.speckitty.charter.profiles.domain;

import java.util.Objects;

/**
 * A tactic, toolguide or styleguide an {@link AgentProfile} applies, with why: one item of
 * {@code tactic-references}, {@code toolguide-references} or {@code styleguide-references}.
 *
 * @param id the referenced artifact's id
 * @param rationale why the profile applies it
 */
public record ArtifactReference(String id, String rationale) {

    /**
     * Checks that both fields are present.
     *
     * @param id the id
     * @param rationale the rationale
     */
    public ArtifactReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rationale, "rationale");
    }
}
