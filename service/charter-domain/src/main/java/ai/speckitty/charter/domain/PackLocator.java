package ai.speckitty.charter.domain;

import java.util.Objects;

/**
 * Where a pack lives, as the {@link PackElementRepository} port is asked to reach it.
 *
 * @param locator the location; opaque to the domain, the adapter resolves it
 */
public record PackLocator(String locator) {
    /**
     * Checks that the locator is present.
     *
     * @param locator the opaque location
     */
    public PackLocator {
        Objects.requireNonNull(locator, "locator");
    }
}
