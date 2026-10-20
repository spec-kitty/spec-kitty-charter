package ai.speckitty.charter.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One pack root, as the {@link PackInspector} port is asked to read it.
 *
 * @param name the name the pack is configured under, or its directory name for a bare path
 * @param configuredLevel the level the pack is configured at; empty for a bare-path validation
 * @param locator where the pack lives; opaque to the domain, the adapter resolves it
 */
public record PackRoot(String name, Optional<PackLevel> configuredLevel, String locator) {
    /**
     * Checks that every field is present.
     *
     * @param name the pack's configured name
     * @param configuredLevel the configured level, possibly empty
     * @param locator the opaque location of the pack
     */
    public PackRoot {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(configuredLevel, "configuredLevel");
        Objects.requireNonNull(locator, "locator");
    }
}
