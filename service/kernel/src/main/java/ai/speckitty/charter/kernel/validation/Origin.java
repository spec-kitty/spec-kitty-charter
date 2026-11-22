package ai.speckitty.charter.kernel.validation;

import java.io.Serializable;
import java.util.Objects;

/**
 * The element a {@link Reason} is about (#614, ADR-0082 decision 4): an opaque identifier the ledger records beside
 * where the reason points ({@link Location}) and which check gave it ({@link Reason#rule()}).
 *
 * <p>The kernel never names a pack, so it never reads the identifier: the module that judges an element fills it, the
 * packs domain with a pack id, or a pack id and a URN. A reason about no element (a value object's invariant, a
 * {@link Constraint}) carries {@link #NONE}; its rule is never copied here.
 *
 * @param id the element's identifier; never blank
 */
public record Origin(String id) implements Serializable {

    /** Serialization version. */
    private static final long serialVersionUID = 2L;

    /** The origin of a reason about no element. No pack id or URN is spelled this way. */
    public static final Origin NONE = new Origin("(none)");

    /** Rejects a null or blank identifier. */
    public Origin {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException(
                    "An origin identifies the element a reason is about; it is blank. Use Origin.NONE for none.");
        }
    }
}
