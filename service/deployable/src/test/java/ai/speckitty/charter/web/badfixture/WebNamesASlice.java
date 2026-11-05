package ai.speckitty.charter.web.badfixture;

import ai.speckitty.charter.packs.domain.DirectiveIdentifier;

/** A shared web transport class that names a slice's domain type: the known-bad of the rule that it names no slice. */
public final class WebNamesASlice {

    private WebNamesASlice() {}

    /**
     * A slice's type held in the shared transport.
     *
     * @param id the type named
     * @return the same value
     */
    public static Object hold(DirectiveIdentifier id) {
        return id;
    }
}
