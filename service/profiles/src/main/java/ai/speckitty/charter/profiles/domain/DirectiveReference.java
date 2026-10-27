package ai.speckitty.charter.profiles.domain;

import java.util.Objects;

/**
 * A directive an {@link AgentProfile} applies, with why: one item of {@code directive-references}.
 *
 * @param code the directive's code as authored, for example {@code "003"}; it is text, never a number
 * @param name the directive's name
 * @param rationale why the profile applies it
 */
public record DirectiveReference(String code, String name, String rationale) {

    /**
     * Checks that every field is present.
     *
     * @param code the code
     * @param name the name
     * @param rationale the rationale
     */
    public DirectiveReference {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(rationale, "rationale");
    }
}
