package ai.speckitty.charter.kernel.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The two pack-relative paths a {@code pack.yaml} may declare, grouped as one value.
 *
 * @param activeCharter the pack-relative {@code active_charter} path; empty when absent or null
 * @param packDrg the pack-relative {@code pack_drg} path; empty when absent or null
 */
public record PackPaths(Optional<String> activeCharter, Optional<String> packDrg) {

    private static final PackPaths NONE = new PackPaths(Optional.empty(), Optional.empty());

    /**
     * Checks that both components are present (possibly as empty optionals).
     *
     * @param activeCharter the {@code active_charter} path, possibly empty
     * @param packDrg the {@code pack_drg} path, possibly empty
     */
    public PackPaths {
        Objects.requireNonNull(activeCharter, "activeCharter");
        Objects.requireNonNull(packDrg, "packDrg");
    }

    /**
     * The paths of a descriptor that declares neither.
     *
     * @return the shared empty value
     */
    public static PackPaths none() {
        return NONE;
    }
}
