package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The pack-relative paths a {@code pack.yaml} may declare, grouped as one value.
 *
 * @param packDrg the pack-relative {@code pack_drg} path; empty when absent or null
 * @param mutabilityPolicy the pack-relative {@code mutability_policy} path (ADR-0063), only {@code policy.yaml} in this
 *     version; empty when absent
 */
public record PackPaths(Optional<String> packDrg, Optional<String> mutabilityPolicy) {

    private static final PackPaths NONE = new PackPaths(Optional.empty(), Optional.empty());

    /**
     * Checks that every component is present (possibly as an empty optional).
     *
     * @param packDrg the {@code pack_drg} path, possibly empty
     * @param mutabilityPolicy the {@code mutability_policy} path, possibly empty
     */
    public PackPaths {
        Objects.requireNonNull(packDrg, "packDrg");
        Objects.requireNonNull(mutabilityPolicy, "mutabilityPolicy");
    }

    /**
     * The paths of a descriptor that declares none.
     *
     * @return the shared empty value
     */
    public static PackPaths none() {
        return NONE;
    }
}
