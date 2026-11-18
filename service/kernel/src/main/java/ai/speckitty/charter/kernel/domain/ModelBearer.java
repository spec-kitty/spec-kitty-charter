package ai.speckitty.charter.kernel.domain;

import java.util.Optional;

/**
 * An artifact that names the model it runs on (ADR-0080 decision 13): an agent profile, with its {@code model} and
 * {@code effort}. The kernel names the facts so a slice can read them, such as the profile candidate of a model
 * recommendation under an activation, without depending on the slice that owns the artifact, as {@link RoleBearer}
 * does for a profile's roles.
 */
public interface ModelBearer {

    /**
     * The model the artifact names.
     *
     * @return the model; empty when it names none
     */
    Optional<String> model();

    /**
     * The effort the artifact names for its model.
     *
     * @return the effort; empty when it names none
     */
    Optional<String> effort();
}
