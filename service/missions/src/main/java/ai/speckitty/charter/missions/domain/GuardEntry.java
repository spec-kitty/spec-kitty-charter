package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import java.util.Objects;
import java.util.Optional;

/**
 * One guard a transition names (ADR-0054 decision 2), in lifecycles now and in mission types and step enhancements
 * later. {@code config} is checked against the guard's {@code config_schema} at load, by the store, which knows both.
 *
 * @param ref the guard
 * @param config the entry's configuration, as authored, when stated
 * @param blocking whether a failure blocks this transition, as authored, when stated
 */
public record GuardEntry(GuardReference ref, Optional<ConfigValue> config, Optional<Boolean> blocking) {

    /**
     * Checks the parts.
     *
     * @param ref the guard
     * @param config the configuration, possibly empty
     * @param blocking the blocking flag, possibly empty
     */
    public GuardEntry {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(blocking, "blocking");
        if (config.isPresent() && !(config.get() instanceof ConfigValue.Mapping)) {
            throw new IllegalArgumentException("a guard entry's config is a mapping");
        }
    }

    /**
     * Whether a failing guard blocks this transition, with the default applied.
     *
     * @return {@code true} unless the entry states {@code blocking: false}
     */
    public boolean blocks() {
        return blocking.orElse(true);
    }
}
