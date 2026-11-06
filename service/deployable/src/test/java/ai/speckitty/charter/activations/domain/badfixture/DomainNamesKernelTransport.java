package ai.speckitty.charter.activations.domain.badfixture;

import ai.speckitty.charter.kernel.transport.EntityTags;

/** An activations domain type that names the kernel's transport vocabulary: the known-bad of the domain rule. */
public final class DomainNamesKernelTransport {

    private DomainNamesKernelTransport() {}

    /**
     * A transport question asked in the domain.
     *
     * @param value an entity tag
     * @return whether it is the wildcard
     */
    public static boolean wildcard(String value) {
        return EntityTags.isWildcard(value);
    }
}
