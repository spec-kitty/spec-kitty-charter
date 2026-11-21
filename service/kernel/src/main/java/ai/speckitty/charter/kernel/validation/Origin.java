package ai.speckitty.charter.kernel.validation;

import java.io.Serializable;
import java.util.Objects;

/**
 * Which check produced a {@link Reason} (#614, slice 1 of #636): the name of the check, a slug such as
 * {@code closure} or {@code closure.presets}. It says nothing of where the reason points; that is its
 * {@link Location}.
 *
 * <p>A closure check names itself; every other reason comes from the rule that gave it, so its origin is that rule's
 * name ({@link Reason#rule()}).
 *
 * @param name the check's name; never blank
 */
public record Origin(String name) implements Serializable {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** Rejects a null or blank name. */
    public Origin {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("An origin names the check that produced a reason; it is blank.");
        }
    }
}
