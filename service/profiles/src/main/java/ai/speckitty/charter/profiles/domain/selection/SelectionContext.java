package ai.speckitty.charter.profiles.domain.selection;

import java.util.Objects;
import java.util.Optional;

/**
 * The context a step asks a role in (ADR-0069 decision 4, ADR-0078): the language and framework of the work, each
 * optional. Selection judges the language and framework lists a specialization declares against it.
 *
 * @param language the asked language, or empty
 * @param framework the asked framework, or empty
 */
public record SelectionContext(Optional<String> language, Optional<String> framework) {

    /** No context asked. */
    public static final SelectionContext NONE = new SelectionContext(Optional.empty(), Optional.empty());

    /**
     * Checks that both parts are present.
     *
     * @param language the asked language, or empty
     * @param framework the asked framework, or empty
     */
    public SelectionContext {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(framework, "framework");
    }
}
